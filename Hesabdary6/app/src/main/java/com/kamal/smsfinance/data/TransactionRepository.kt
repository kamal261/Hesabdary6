// SmsFinance file version: 3 — two fixes, ported from the same fixes already applied to the
// simpler sibling branch:
// 1. tryInsertUnidentified now dedups via DedupEngine (SHA-256 + sliding window -- this file
//    already existed in sms/DedupEngine.kt but was never actually called from anywhere) instead
//    of an exact (sender, body, timestamp) match, which missed near-duplicate broadcast retries.
// 2. tryInsertTransaction resolves parsed.defaultCategoryName (set by SmsParser for bank
//    fees/insurance) to a real categoryId, creating the category on first use if needed.
//    Priority: SmartRule > small-amount > default category from parser > uncategorized.
package com.kamal.smsfinance.data

import android.content.Context
import androidx.room.withTransaction
import com.kamal.smsfinance.sms.DedupEngine
import com.kamal.smsfinance.sms.ParsedSms
import com.kamal.smsfinance.sms.SmsParser
import com.kamal.smsfinance.sms.SmsParseResult
import com.kamal.smsfinance.sms.SmsReaderUtil
import com.kamal.smsfinance.util.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class TransactionRepository(
    private val db: AppDatabase,
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

    /**
     * Inserts a manual transaction only when its category is compatible with
     * the transaction direction. The check is repeated here because imports,
     * restore and future UI paths must not be able to create contradictory data.
     */
    suspend fun addManual(transaction: Transaction): Boolean {
        if (!isCategoryCompatible(transaction.type, transaction.categoryId)) return false
        return transactionDao.insert(transaction.copy(source = TransactionSource.MANUAL)) > 0
    }

    suspend fun update(transaction: Transaction) = transactionDao.update(transaction)
    suspend fun delete(transaction: Transaction) = transactionDao.delete(transaction)
    suspend fun deleteAll() = transactionDao.deleteAll()

    suspend fun assignCategory(transactionId: Long, categoryId: Long?): Boolean {
        val transaction = transactionDao.getById(transactionId) ?: return false
        if (!isCategoryCompatible(transaction.type, categoryId)) return false
        transactionDao.assignCategory(transactionId, categoryId)
        return true
    }

    private suspend fun isCategoryCompatible(type: TransactionType, categoryId: Long?): Boolean {
        val category = categoryId?.let { categoryDao.getById(it) } ?: return true
        return category.kind.isCompatibleWith(type)
    }

    suspend fun assignCounterparty(transactionId: Long, counterpartyId: Long?) =
        transactionDao.assignCounterparty(transactionId, counterpartyId)

    suspend fun updateTransactionNotes(transactionId: Long, notes: String?) =
        transactionDao.updateNotes(transactionId, notes?.takeIf { it.isNotBlank() })

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
    ): Boolean {
        if (!isCategoryCompatible(type, categoryId)) return false
        return transactionDao.insert(
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
        ) > 0
    }

    /**
     * Scans the full SMS inbox (READ_SMS) once, parses every recognizable bank
     * message, and stores new ones silently (no notification). Returns how
     * many new transactions were added. Safe to call repeatedly -- existing
     * rows are skipped via existsExact().
     */
    suspend fun scanInboxAndImport(sinceMillis: Long? = null): Int {
        val messages = SmsReaderUtil.readInbox(context, sinceMillis)
        var added = 0
        for (msg in messages) {
            if (handleParseResult(SmsParser.parse(msg.sender, msg.body, msg.timestamp))) added++
        }
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
        else -> false
    }

    private suspend fun tryInsertUnidentified(sender: String, body: String, timestamp: Long) {
        if (DedupEngine.isDuplicate(sender, body, timestamp)) return
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

        // Parser-suggested default category (bank fees / insurance premiums) -- lowest
        // priority, only applies when nothing more specific already claimed this transaction.
        if (categoryId == null && parsed.defaultCategoryName != null) {
            categoryId = resolveOrCreateCategory(parsed.defaultCategoryName, CategoryKind.EXPENSE)
        }

        // A stale or incorrectly configured rule must never assign a category
        // belonging to the opposite cash direction.
        if (!isCategoryCompatible(parsed.type, categoryId)) categoryId = null

        transactionDao.insert(parsed.toTransaction(categoryId, ruleMatch.counterpartyId))
        return true
    }

    private suspend fun smallAmountCategoryIdIfApplicable(amountToman: Long): Long? {
        if (!settings.smallAmountEnabled.first()) return null
        val threshold = settings.smallAmountThreshold.first()
        if (amountToman >= threshold) return null
        return settings.smallAmountCategoryId.first()
    }

    /** Finds an existing category by name (case-insensitive) or creates it. Used only for the
     * small, fixed set of parser-suggested category names (کارمزد بانکی، بیمه). */
    private suspend fun resolveOrCreateCategory(name: String, kind: CategoryKind): Long? {
        val existing = categoryDao.getAllOnce().firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) return existing.id
        return categoryDao.insert(Category(name = name, kind = kind, isDefault = false)).takeIf { it > 0 }
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

    suspend fun addCategory(name: String, kind: CategoryKind, parentId: Long? = null) =
        categoryDao.insert(Category(name = name, kind = kind, isDefault = false, parentId = parentId))

    /** Re-parents any children to top-level first -- there's no DB-level cascade for
     * parentId, so a plain delete would otherwise leave children pointing at a dead id. */
    suspend fun deleteCategory(category: Category) {
        categoryDao.clearParent(category.id)
        categoryDao.delete(category)
    }

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
    fun checksDueSoon() = checkDao.getDueSoon(System.currentTimeMillis())
    fun checksForCounterparty(id: Long) = checkDao.getByCounterparty(id)

    suspend fun addCheck(check: Check) = checkDao.insert(check)
    suspend fun updateCheck(check: Check) = checkDao.update(check)
    suspend fun deleteCheck(check: Check) = checkDao.delete(check)

    /**
     * Marks a check as settled and automatically creates the corresponding
     * transaction (RECEIVABLE -> income / PAYABLE -> expense), linked to the
     * same counterparty, so the counterparty balance stays correct without
     * the user re-entering the amount.
     *
     * P0 fix: wrapped in db.withTransaction{} with a re-fetch + PENDING guard
     * INSIDE the transaction. Previously this ran as two separate, unguarded
     * writes (insert transaction, then update check) -- calling this twice in
     * quick succession (double-tap, or two concurrent callers) could create
     * two settlement transactions for the same check before the second
     * status update overwrote the first. Re-reading the check's current
     * status from the DB inside the transaction (not trusting the [check]
     * parameter, which could be stale) makes a second call a safe no-op.
     */
    suspend fun settleCheck(check: Check, settledDate: Long = System.currentTimeMillis()) {
        db.withTransaction {
            val current = checkDao.getById(check.id) ?: return@withTransaction
            if (current.status != CheckStatus.PENDING) return@withTransaction

            val txnId = transactionDao.insert(
                Transaction(
                    amountToman = current.amountToman,
                    type = if (current.type == CheckType.RECEIVABLE) TransactionType.INCOME else TransactionType.EXPENSE,
                    bankName = "چک",
                    description = current.description?.takeIf { it.isNotBlank() }
                        ?: if (current.type == CheckType.RECEIVABLE) "وصول چک" else "پرداخت چک",
                    date = settledDate,
                    source = TransactionSource.CHECK_SETTLEMENT,
                    counterpartyId = current.counterpartyId
                )
            )
            checkDao.update(current.copy(status = CheckStatus.CLEARED, paidDate = settledDate, settledTransactionId = txnId))
        }
    }

    suspend fun markCheckBounced(check: Check) {
        db.withTransaction {
            val current = checkDao.getById(check.id) ?: return@withTransaction
            // A cleared check already has a settlement transaction and must not
            // be changed to BOUNCED using a stale UI object.
            if (current.status != CheckStatus.PENDING) return@withTransaction
            checkDao.update(current.copy(status = CheckStatus.BOUNCED, paidDate = null, settledTransactionId = null))
        }
    }
}
