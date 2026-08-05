// SmsFinance file version: 2 — handles SmsParser's new sealed SmsParseResult (stores Unidentified messages for review instead of dropping them), added small-amount auto-categorization for SMS-imported expenses
package com.kamal.smsfinance.data

import android.content.Context
import com.kamal.smsfinance.sms.ParsedSms
import com.kamal.smsfinance.sms.SmsParser
import com.kamal.smsfinance.sms.SmsParseResult
import com.kamal.smsfinance.sms.SmsReaderUtil
import com.kamal.smsfinance.util.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val counterpartyDao: CounterpartyDao,
    private val checkDao: CheckDao,
    private val smartRuleDao: SmartRuleDao,
    private val unidentifiedSmsDao: UnidentifiedSmsDao,
    private val context: Context
) {
    private val ruleEngine = RuleEngine()
    private val settings = SettingsStore(context)

    // --- Transactions ---

    val allTransactions: Flow<List<Transaction>> = transactionDao.getAll()
    val uncategorizedTransactions: Flow<List<Transaction>> = transactionDao.getUncategorized()

    fun transactionsByType(type: TransactionType) = transactionDao.getByType(type)
    fun transactionsByCategory(categoryId: Long) = transactionDao.getByCategory(categoryId)
    fun search(query: String) = transactionDao.search(query)
    fun incomeBetween(from: Long, to: Long) = transactionDao.sumIncome(from, to)
    fun expenseBetween(from: Long, to: Long) = transactionDao.sumExpense(from, to)

    suspend fun addManual(transaction: Transaction) {
        transactionDao.insert(transaction.copy(source = TransactionSource.MANUAL))
    }

    suspend fun update(transaction: Transaction) = transactionDao.update(transaction)
    suspend fun delete(transaction: Transaction) = transactionDao.delete(transaction)
    suspend fun deleteAll() = transactionDao.deleteAll()

    suspend fun assignCategory(transactionId: Long, categoryId: Long?) =
        transactionDao.assignCategory(transactionId, categoryId)

    suspend fun assignCounterparty(transactionId: Long, counterpartyId: Long?) =
        transactionDao.assignCounterparty(transactionId, counterpartyId)

    /**
     * Records a payment someone else made on the user's behalf (or vice
     * versa) that will never appear in the user's own bank SMS -- the
     * "third-party settlement" reminder flow. Always stored as a debt
     * collection/payment tied to a counterparty.
     */
    suspend fun addIndirectSettlement(
        amountToman: Long,
        type: TransactionType,
        counterpartyId: Long?,
        description: String,
        date: Long,
        categoryId: Long?
    ) {
        transactionDao.insert(
            Transaction(
                amountToman = amountToman,
                type = type,
                bankName = "غیرمستقیم",
                description = description,
                date = date,
                source = TransactionSource.MANUAL,
                categoryId = categoryId,
                counterpartyId = counterpartyId,
                isIndirectSettlement = true
            )
        )
    }

    /**
     * Scans the SMS inbox and imports recognized bank transactions.
     *
     * First run (user-chosen window): scans only the last N days the user
     * picked on first launch (default 7), then marks the initial scan done.
     * After that, automatic scans only pick up SMS newer than the last scan
     * (never a full re-scan, and never re-asks for permission).
     */
    suspend fun scanInboxAndImport(): Int {
        val messages = SmsReaderUtil.readInbox(context)
        if (messages.isEmpty()) return 0

        val initialDone = settings.initialScanDone.first()
        val cutoff = if (!initialDone) {
            // First run: only the user-chosen window (e.g. last 7 days).
            val windowDays = settings.initialScanWindowDays.first()
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(windowDays)
        } else {
            // Later automatic runs: only messages newer than the last scan.
            settings.lastScanTimestamp.first().takeIf { it > 0 } ?: 0L
        }

        var added = 0
        for (msg in messages) {
            if (msg.timestamp < cutoff) continue
            if (handleParseResult(SmsParser.parse(msg.sender, msg.body, msg.timestamp))) added++
        }
        settings.setLastScanTimestamp(System.currentTimeMillis())
        settings.setInitialScanDone(true)
        return added
    }

    /** Manual "scan" button: full re-scan of the whole inbox (dedup prevents duplicates). */
    suspend fun scanInboxAndImportFull(): Int {
        val messages = SmsReaderUtil.readInbox(context)
        var added = 0
        for (msg in messages) {
            if (handleParseResult(SmsParser.parse(msg.sender, msg.body, msg.timestamp))) added++
        }
        settings.setLastScanTimestamp(System.currentTimeMillis())
        settings.setInitialScanDone(true)
        return added
    }

    /** Called from SmsReceiver when a new SMS arrives in real time. Silent -- no notification. */
    suspend fun importSingleSms(sender: String, body: String, timestamp: Long) {
        handleParseResult(SmsParser.parse(sender, body, timestamp))
    }

    /** Routes a parse outcome to the right table. Returns true if a new transaction was stored. */
    private suspend fun handleParseResult(result: SmsParseResult): Boolean = when (result) {
        is SmsParseResult.Recognized -> tryInsertTransaction(result.parsed)
        is SmsParseResult.Unidentified -> {
            tryInsertUnidentified(result.sender, result.body, result.timestamp)
            false
        }
        SmsParseResult.Ignored -> false
    }

    private suspend fun tryInsertUnidentified(sender: String, body: String, timestamp: Long) {
        val exists = unidentifiedSmsDao.existsExact(sender, body, timestamp) > 0
        if (exists) return
        unidentifiedSmsDao.insert(UnidentifiedSms(sender = sender, body = body, timestamp = timestamp))
    }

    private suspend fun tryInsertTransaction(parsed: ParsedSms): Boolean {
        val exists = transactionDao.existsExact(parsed.sender, parsed.rawSms, parsed.timestamp) > 0
        if (exists) return false

        // Fallback: if this SMS exposed an account tail, also guard against
        // the same real-world transaction arriving under a different sender
        // short-code (banks do this occasionally) within a 10-minute window.
        val tail = parsed.accountTail
        if (tail != null) {
            val similar = transactionDao.existsSimilar(
                accountTail = tail,
                amount = parsed.amountToman,
                type = parsed.type,
                date = parsed.timestamp,
                windowMillis = TimeUnit.MINUTES.toMillis(10)
            ) > 0
            if (similar) return false
        }

        val ruleMatch = ruleEngine.evaluate(parsed.rawSms, smartRuleDao.getAllRulesOnce())
        var categoryId = ruleMatch.categoryId

        // Small-amount auto-categorization: only applies when no rule already
        // claimed this transaction, and only to expenses (per product scope).
        if (categoryId == null && parsed.type == TransactionType.EXPENSE) {
            categoryId = smallAmountCategoryIdIfApplicable(parsed.amountToman)
        }

        transactionDao.insert(parsed.toTransaction(categoryId, ruleMatch.counterpartyId))
        return true
    }

    private suspend fun smallAmountCategoryIdIfApplicable(amountToman: Long): Long? {
        if (!settings.smallAmountEnabled.first()) return null
        val threshold = settings.smallAmountThreshold.first()
        if (amountToman >= threshold) return null
        return settings.smallAmountCategoryId.first()
    }

    private fun ParsedSms.toTransaction(categoryId: Long?, counterpartyId: Long?) = Transaction(
        amountToman = amountToman,
        type = type,
        bankName = bankName,
        description = description,
        date = timestamp,
        source = TransactionSource.SMS_AUTO,
        rawSms = rawSms,
        smsSender = sender,
        accountTail = accountTail,
        categoryId = categoryId,
        counterpartyId = counterpartyId
    )

    // --- Unidentified SMS (explainable alternative to auto-guessing) ---

    val unidentifiedSms: Flow<List<UnidentifiedSms>> = unidentifiedSmsDao.getActive()
    suspend fun dismissUnidentifiedSms(id: Long) = unidentifiedSmsDao.dismiss(id)
    suspend fun dismissAllUnidentifiedSms() = unidentifiedSmsDao.dismissAll()

    // --- Smart rules (Explainable Rule Engine) ---

    val allRules: Flow<List<SmartRule>> = smartRuleDao.getAllRules()

    suspend fun addRule(pattern: String, categoryId: Long?, counterpartyId: Long?) =
        smartRuleDao.insertRule(SmartRule(pattern = pattern, categoryId = categoryId, counterpartyId = counterpartyId))

    suspend fun updateRule(rule: SmartRule) = smartRuleDao.updateRule(rule)
    suspend fun deleteRule(rule: SmartRule) = smartRuleDao.deleteRule(rule)

    // --- Categories ---

    val allCategories: Flow<List<Category>> = categoryDao.getAll()

    suspend fun addCategory(name: String, kind: CategoryKind) =
        categoryDao.insert(Category(name = name, kind = kind, isDefault = false))

    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)

    // --- Counterparties ---

    val allCounterparties: Flow<List<Counterparty>> = counterpartyDao.getAll()
    fun counterpartiesByType(type: CounterpartyType) = counterpartyDao.getByType(type)
    fun counterparty(id: Long) = counterpartyDao.observeById(id)
    fun transactionsForCounterparty(id: Long) = counterpartyDao.transactionsFor(id)
    fun balanceForCounterparty(id: Long) = counterpartyDao.balanceFor(id)
    fun totalVolumeForCounterparty(id: Long) = counterpartyDao.totalVolumeFor(id)

    suspend fun addCounterparty(
        name: String,
        type: CounterpartyType,
        phone: String?,
        address: String?,
        description: String?
    ) = counterpartyDao.insert(
        Counterparty(name = name, type = type, phone = phone, address = address, description = description)
    )

    suspend fun updateCounterparty(counterparty: Counterparty) = counterpartyDao.update(counterparty)
    suspend fun deleteCounterparty(counterparty: Counterparty) = counterpartyDao.delete(counterparty)

    // --- Checks ---

    val allChecks: Flow<List<Check>> = checkDao.getAll()
    fun checksByStatus(status: CheckStatus) = checkDao.getByStatus(status)
    fun checksDueSoon(withinDays: Long = 7) = checkDao.getDueSoon(System.currentTimeMillis(), withinDays)
    fun checksForCounterparty(id: Long) = checkDao.getByCounterparty(id)

    suspend fun addCheck(check: Check) = checkDao.insert(check)
    suspend fun updateCheck(check: Check) = checkDao.update(check)
    suspend fun deleteCheck(check: Check) = checkDao.delete(check)

    /**
     * Marks a check as settled and automatically creates the corresponding
     * transaction (RECEIVABLE -> income / PAYABLE -> expense), linked to the
     * same counterparty, so the counterparty balance stays correct without
     * the user re-entering the amount.
     */
    suspend fun settleCheck(check: Check, settledDate: Long = System.currentTimeMillis()) {
        val txnId = transactionDao.insert(
            Transaction(
                amountToman = check.amountToman,
                type = if (check.type == CheckType.RECEIVABLE) TransactionType.INCOME else TransactionType.EXPENSE,
                bankName = "چک",
                description = check.description?.takeIf { it.isNotBlank() }
                    ?: if (check.type == CheckType.RECEIVABLE) "وصول چک" else "پرداخت چک",
                date = settledDate,
                source = TransactionSource.CHECK_SETTLEMENT,
                counterpartyId = check.counterpartyId
            )
        )
        checkDao.update(check.copy(status = CheckStatus.CLEARED, paidDate = settledDate, settledTransactionId = txnId))
    }

    suspend fun markCheckBounced(check: Check) {
        checkDao.update(check.copy(status = CheckStatus.BOUNCED))
    }
}
