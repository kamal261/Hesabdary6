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
import com.kamal.smsfinance.sms.RawSms
import com.kamal.smsfinance.sms.SmsParser
import com.kamal.smsfinance.sms.SmsParseResult
import com.kamal.smsfinance.sms.SmsReaderUtil
import com.kamal.smsfinance.util.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

data class ScanResult(
    val added: Int,
    val scanned: Int,
    val latestTimestamp: Long?,
    val latestSmsId: String?
)

class TransactionRepository(
    private val db: AppDatabase,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val counterpartyDao: CounterpartyDao,
    private val counterpartyReminderDao: CounterpartyReminderDao,
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

    suspend fun addManual(transaction: Transaction): Boolean {
        if (!isCategoryCompatible(transaction.type, transaction.categoryId)) return false
        transactionDao.insert(transaction.copy(source = TransactionSource.MANUAL))
        return true
    }

    suspend fun update(transaction: Transaction): Boolean {
        if (!isCategoryCompatible(transaction.type, transaction.categoryId)) return false
        transactionDao.update(transaction)
        return true
    }

    suspend fun delete(transaction: Transaction) = transactionDao.delete(transaction)
    suspend fun deleteAll() = transactionDao.deleteAll()

    suspend fun assignCategory(transactionId: Long, categoryId: Long?): Boolean {
        val transaction = transactionDao.getById(transactionId) ?: return false
        if (!isCategoryCompatible(transaction.type, categoryId)) return false
        transactionDao.assignCategory(transactionId, categoryId)
        return true
    }

    private suspend fun isCategoryCompatible(type: TransactionType, categoryId: Long?): Boolean {
        val kind = categoryId?.let { categoryDao.getById(it)?.kind }
        return categoryId == null || kind != null && TransactionCategoryRules.isCompatible(type, kind)
    }

    suspend fun changeTransactionType(transactionId: Long, type: TransactionType) =
        transactionDao.changeTypeAndClearCategory(transactionId, type)

    suspend fun assignCounterparty(transactionId: Long, counterpartyId: Long?) =
        transactionDao.assignCounterparty(transactionId, counterpartyId)

    suspend fun updateTransactionNotes(transactionId: Long, notes: String?) =
        transactionDao.updateNotes(transactionId, notes?.takeIf { it.isNotBlank() })

    /**
     * Applies a user-approved personal-transfer group only when its financial invariant is valid.
     * The read/validate/update sequence is one SQLite transaction so callers cannot race a
     * transaction into two groups or create a group with an invalid composition.
     */
    suspend fun assignTransferGroup(transactionIds: List<Long>, groupId: Long): TransferGroupResult =
        db.withTransaction {
            val ids = transactionIds.distinct()
            if (ids.size < 2) return@withTransaction TransferGroupResult.Rejected(
                TransferGroupRejection.TOO_FEW_TRANSACTIONS
            )
            if (groupId <= 0L) return@withTransaction TransferGroupResult.Rejected(
                TransferGroupRejection.INVALID_GROUP_ID
            )

            val selected = ids.mapNotNull { transactionDao.getById(it) }
            if (selected.size != ids.size) return@withTransaction TransferGroupResult.Rejected(
                TransferGroupRejection.TRANSACTION_NOT_FOUND
            )

            val existingMembers = transactionDao.byTransferGroupOnce(groupId)
            FinancialLinkRules.validateTransfer(selected, existingMembers, groupId)?.let { rejection ->
                return@withTransaction TransferGroupResult.Rejected(rejection)
            }
            val finalMembers = (existingMembers + selected).distinctBy { it.id }
            transactionDao.assignTransferGroup(finalMembers.map { it.id }, groupId)
            TransferGroupResult.Success(groupId, finalMembers.map { it.id })
        }

    /**
     * Links an existing bank transaction to a pending check without creating a second row.
     * All financial compatibility checks live here rather than only in CheckMatcher/UI.
     */
    suspend fun linkTransactionToCheck(transactionId: Long, checkId: Long): CheckLinkResult =
        db.withTransaction {
            val transaction = transactionDao.getById(transactionId)
                ?: return@withTransaction CheckLinkResult.Rejected(CheckLinkRejection.TRANSACTION_NOT_FOUND)
            val check = checkDao.getById(checkId)
                ?: return@withTransaction CheckLinkResult.Rejected(CheckLinkRejection.CHECK_NOT_FOUND)
            FinancialLinkRules.validateCheckLink(transaction, check)?.let { rejection ->
                return@withTransaction CheckLinkResult.Rejected(rejection)
            }

            transactionDao.linkCheck(transactionId, checkId)
            checkDao.update(
                check.copy(
                    status = CheckStatus.CLEARED,
                    paidDate = transaction.date,
                    settledTransactionId = transaction.id
                )
            )
            CheckLinkResult.Success(transactionId, checkId)
        }

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
        return true
    }

    /**
     * Scans the inbox and returns both import counts and the newest provider cursor.
     * [incremental] uses the last committed timestamp/id; historical scans deliberately
     * bypass that cursor so they can fill an older gap without moving the daily cursor.
     */
    suspend fun scanInboxAndImport(
        sinceMillis: Long? = null,
        afterSmsId: String? = null,
        incremental: Boolean = true,
        commitCursor: Boolean = false
    ): ScanResult {
        val effectiveSince = if (incremental) settings.lastScanTimestamp.first() else sinceMillis
        val effectiveId = if (incremental) settings.lastScanSmsId.first() else afterSmsId
        val messages = SmsReaderUtil.readInbox(context, effectiveSince, effectiveId)
        var added = 0
        for (msg in messages) {
            if (handleParseResult(SmsParser.parse(msg.sender, msg.body, msg.timestamp))) added++
        }
        val latest = messages.maxWithOrNull(compareBy<RawSms> { it.timestamp }.thenBy { it.id ?: "" })
        if (commitCursor && latest != null) {
            settings.setLastScanCursor(latest.timestamp, latest.id)
        }
        return ScanResult(
            added = added,
            scanned = messages.size,
            latestTimestamp = latest?.timestamp,
            latestSmsId = latest?.id
        )
    }

    /** Imports one SMS through the same parser and dedup pipeline used by inbox scanning. */
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
    suspend fun activeUnidentifiedCount(): Int = unidentifiedSmsDao.countActive()
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

    // --- Counterparty reminders ---

    fun remindersForCounterparty(counterpartyId: Long) = counterpartyReminderDao.forCounterparty(counterpartyId)
    val dueCounterpartyReminders: Flow<List<CounterpartyReminder>> =
        counterpartyReminderDao.due(System.currentTimeMillis())

    suspend fun addCounterpartyReminder(reminder: CounterpartyReminder) = counterpartyReminderDao.insert(reminder)
    suspend fun updateCounterpartyReminder(reminder: CounterpartyReminder) = counterpartyReminderDao.update(reminder)
    suspend fun deleteCounterpartyReminder(reminder: CounterpartyReminder) = counterpartyReminderDao.delete(reminder)

    suspend fun setCounterpartyReminderDone(reminder: CounterpartyReminder, done: Boolean) {
        counterpartyReminderDao.setDone(
            id = reminder.id,
            done = done,
            completedAt = if (done) System.currentTimeMillis() else null
        )
    }

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
                    counterpartyId = current.counterpartyId,
                    linkedCheckId = current.id
                )
            )
            checkDao.update(current.copy(status = CheckStatus.CLEARED, paidDate = settledDate, settledTransactionId = txnId))
        }
    }

    suspend fun markCheckBounced(check: Check) {
        checkDao.update(check.copy(status = CheckStatus.BOUNCED))
    }
}
