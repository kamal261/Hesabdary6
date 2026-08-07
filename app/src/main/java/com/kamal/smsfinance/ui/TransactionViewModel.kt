// SmsFinance file version: 2 — added unidentified-SMS review flow, category usage counts (for the quick-pick UI), counterparty balance summary, today/dashboard helpers, CSV import passthrough, small-amount settings, and a restore-confirmation flow to fix the id-mapping bug on non-empty restores
package com.kamal.smsfinance.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.kamal.smsfinance.SmsFinanceApp
import com.kamal.smsfinance.data.*
import com.kamal.smsfinance.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

data class UiMessage(val text: String, val isError: Boolean = false)

class TransactionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TransactionRepository = (application as SmsFinanceApp).repository
    private val settings = SettingsStore(application)

    // --- Transactions ---
    val allTransactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uncategorizedTransactions: StateFlow<List<Transaction>> = repository.uncategorizedTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Categories ---
    val allCategories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Counterparties ---
    val allCounterparties: StateFlow<List<Counterparty>> = repository.allCounterparties
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Checks ---
    val allChecks: StateFlow<List<Check>> = repository.allChecks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val checksDueSoon: StateFlow<List<Check>> = repository.checksDueSoon()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Smart rules ---
    val allRules: StateFlow<List<SmartRule>> = repository.allRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Unidentified SMS (explainable review list) ---
    val unidentifiedSms: StateFlow<List<UnidentifiedSms>> = repository.unidentifiedSms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun dismissUnidentifiedSms(item: UnidentifiedSms) {
        viewModelScope.launch { repository.dismissUnidentifiedSms(item.id) }
    }

    fun dismissAllUnidentifiedSms() {
        viewModelScope.launch { repository.dismissAllUnidentifiedSms() }
    }

    // --- Settings ---
    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val webhookUrl: StateFlow<String> = settings.webhookUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val firstScanDone: StateFlow<Boolean> = settings.firstScanDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val scanDaysBack: StateFlow<Int> = settings.scanDaysBack
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    val smallAmountEnabled: StateFlow<Boolean> = settings.smallAmountEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val smallAmountThreshold: StateFlow<Long> = settings.smallAmountThreshold
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100_000L)
    val smallAmountCategoryId: StateFlow<Long?> = settings.smallAmountCategoryId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setSmallAmountEnabled(enabled: Boolean) { viewModelScope.launch { settings.setSmallAmountEnabled(enabled) } }
    fun setSmallAmountThreshold(threshold: Long) { viewModelScope.launch { settings.setSmallAmountThreshold(threshold) } }
    fun setSmallAmountCategoryId(categoryId: Long?) { viewModelScope.launch { settings.setSmallAmountCategoryId(categoryId) } }

    // --- UI state ---
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val _lastExportedFile = MutableStateFlow<File?>(null)
    val lastExportedFile: StateFlow<File?> = _lastExportedFile.asStateFlow()

    // Set when a restore is attempted into a non-empty database -- the UI
    // should show a confirmation dialog and call confirmRestoreReplacingExisting()
    // or cancelPendingRestore().
    private val _pendingRestoreUri = MutableStateFlow<Uri?>(null)
    val pendingRestoreConfirmation: StateFlow<Uri?> = _pendingRestoreUri.asStateFlow()

    fun clearMessage() { _message.value = null }

    // --- SMS scanning ---

    fun scanInbox() {
        scanInboxInternal(null)
    }

    private fun scanInboxInternal(daysBack: Int?) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val added = if (daysBack != null) {
                    repository.scanInboxAndImport(daysBack)
                } else {
                    repository.scanInboxAndImport()
                }
                _message.value = UiMessage("$added تراکنش جدید از پیامک‌ها شناسایی و ذخیره شد")
            } catch (e: Exception) {
                _message.value = UiMessage("خطا در اسکن پیامک‌ها: ${e.message}", isError = true)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Call this on first launch if user hasn't done initial scan yet. Shows a dialog to pick day range. */
    fun maybeShowFirstScanDialog(onShowDialog: (Int) -> Unit) {
        viewModelScope.launch {
            val done = settings.firstScanDone.value
            if (!done) {
                val days = settings.scanDaysBack.value
                onShowDialog(days)
            }
        }
    }

    fun confirmFirstScan(daysBack: Int) {
        viewModelScope.launch {
            settings.setFirstScanDone(true)
            settings.setScanDaysBack(daysBack)
            scanInboxInternal(daysBack)
        }
    }

    fun skipFirstScan() {
        viewModelScope.launch {
            settings.setFirstScanDone(true)
            scanInboxInternal(null)
        }
    }

    // --- Manual transactions ---

    fun addManualTransaction(
        amountToman: Long,
        type: TransactionType,
        bankName: String,
        description: String,
        date: Long,
        categoryId: Long?,
        counterpartyId: Long?
    ) {
        viewModelScope.launch {
            repository.addManual(
                Transaction(
                    amountToman = amountToman,
                    type = type,
                    bankName = bankName.ifBlank { "دستی" },
                    description = description,
                    date = date,
                    source = TransactionSource.MANUAL,
                    categoryId = categoryId,
                    counterpartyId = counterpartyId
                )
            )
            _message.value = UiMessage("تراکنش با موفقیت ثبت شد")
        }
    }

    fun addIndirectSettlement(
        amountToman: Long,
        type: TransactionType,
        counterpartyId: Long?,
        description: String,
        date: Long,
        categoryId: Long?
    ) {
        viewModelScope.launch {
            repository.addIndirectSettlement(amountToman, type, counterpartyId, description, date, categoryId)
            _message.value = UiMessage("تسویه غیرمستقیم ثبت شد")
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.delete(transaction) }
    }

    fun assignCategory(transactionId: Long, categoryId: Long?) {
        viewModelScope.launch { repository.assignCategory(transactionId, categoryId) }
    }

    fun assignCounterparty(transactionId: Long, counterpartyId: Long?) {
        viewModelScope.launch { repository.assignCounterparty(transactionId, counterpartyId) }
    }

    // --- Smart rules (Explainable Rule Engine) ---

    fun addRule(pattern: String, categoryId: Long?, counterpartyId: Long?) {
        viewModelScope.launch {
            repository.addRule(pattern, categoryId, counterpartyId)
            _message.value = UiMessage("قانون ذخیره شد؛ پیامک‌های مشابه بعدی خودکار دسته‌بندی می‌شوند")
        }
    }

    fun deleteRule(rule: SmartRule) {
        viewModelScope.launch { repository.deleteRule(rule) }
    }

    // --- Categories ---

    fun addCategory(name: String, kind: CategoryKind, parentId: Long? = null) {
        viewModelScope.launch { repository.addCategory(name, kind, parentId) }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { repository.deleteCategory(category) }
    }

    /** Live usage count per category, used to surface the 4 most-used ones -- never stored, always derived. */
    fun categoryUsageCounts(list: List<Transaction> = allTransactions.value): Map<Long, Int> =
        list.mapNotNull { it.categoryId }.groupingBy { it }.eachCount()

    fun importCategoriesCsv(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val db = (getApplication<Application>() as SmsFinanceApp).database
                val result = CsvImporter.importCategories(getApplication(), uri, db)
                _message.value = UiMessage("${result.imported} دسته اضافه شد، ${result.skipped} مورد تکراری نادیده گرفته شد")
            } catch (e: Exception) {
                _message.value = UiMessage("خطا در وارد کردن CSV: ${e.message}", isError = true)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- Counterparties ---

    fun addCounterparty(name: String, type: CounterpartyType, phone: String?, address: String?, description: String?) {
        viewModelScope.launch { repository.addCounterparty(name, type, phone, address, description) }
    }

    fun updateCounterparty(counterparty: Counterparty) {
        viewModelScope.launch { repository.updateCounterparty(counterparty) }
    }

    fun deleteCounterparty(counterparty: Counterparty) {
        viewModelScope.launch { repository.deleteCounterparty(counterparty) }
    }

    fun updateCounterpartyNotes(counterparty: Counterparty, notes: String) {
        viewModelScope.launch { repository.updateCounterparty(counterparty.copy(notes = notes.ifBlank { null })) }
    }

    fun transactionsForCounterparty(id: Long) = repository.transactionsForCounterparty(id)
    fun balanceForCounterparty(id: Long) = repository.balanceForCounterparty(id)
    fun totalVolumeForCounterparty(id: Long) = repository.totalVolumeForCounterparty(id)

    fun importCounterpartiesCsv(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val db = (getApplication<Application>() as SmsFinanceApp).database
                val result = CsvImporter.importCounterparties(getApplication(), uri, db)
                _message.value = UiMessage("${result.imported} طرف‌حساب اضافه شد، ${result.skipped} مورد تکراری نادیده گرفته شد")
            } catch (e: Exception) {
                _message.value = UiMessage("خطا در وارد کردن CSV: ${e.message}", isError = true)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Owed-to-me total and I-owe total, summed across every counterparty -- derived live, never stored. */
    fun counterpartyBalanceSummary(list: List<Transaction> = allTransactions.value): Pair<Long, Long> {
        val byCounterparty = list.filter { it.counterpartyId != null }.groupBy { it.counterpartyId!! }
        var owedToMe = 0L
        var iOwe = 0L
        byCounterparty.values.forEach { txns ->
            val balance = txns.sumOf { if (it.type == TransactionType.INCOME) it.amountToman else -it.amountToman }
            if (balance > 0) owedToMe += balance else iOwe += -balance
        }
        return owedToMe to iOwe
    }

    // --- Checks ---

    fun addCheck(check: Check) {
        viewModelScope.launch {
            repository.addCheck(check)
            _message.value = UiMessage("چک ثبت شد")
        }
    }

    fun settleCheck(check: Check) {
        viewModelScope.launch {
            repository.settleCheck(check)
            _message.value = UiMessage("چک به‌عنوان تسویه‌شده ثبت شد و تراکنش مربوطه ایجاد شد")
        }
    }

    fun markCheckBounced(check: Check) {
        viewModelScope.launch {
            repository.markCheckBounced(check)
            _message.value = UiMessage("چک به‌عنوان برگشتی علامت‌گذاری شد")
        }
    }

    fun deleteCheck(check: Check) {
        viewModelScope.launch { repository.deleteCheck(check) }
    }

    // --- Export / backup ---

    fun exportCsv() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val file = CsvExporter.export(getApplication(), allTransactions.value, recurringIds(allTransactions.value))
                _lastExportedFile.value = file
                _message.value = UiMessage("فایل CSV با موفقیت ساخته شد")
            } catch (e: Exception) {
                _message.value = UiMessage("خطا در خروجی گرفتن: ${e.message}", isError = true)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun uploadToSheets() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val url = webhookUrl.value
                when (val result = GoogleSheetsUploader.upload(url, allTransactions.value)) {
                    is GoogleSheetsUploader.UploadResult.Success ->
                        _message.value = UiMessage("${result.count} تراکنش به Google Sheet ارسال شد")
                    is GoogleSheetsUploader.UploadResult.Failure ->
                        _message.value = UiMessage(result.message, isError = true)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setWebhookUrl(url: String) {
        viewModelScope.launch { settings.setWebhookUrl(url) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun createLocalBackup() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val db = (getApplication<Application>() as SmsFinanceApp).database
                val file = BackupManager.createBackup(getApplication(), db)
                _message.value = UiMessage("پشتیبان در ${file.name} ذخیره شد")
            } catch (e: Exception) {
                _message.value = UiMessage("خطا در پشتیبان‌گیری: ${e.message}", isError = true)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Entry point from the UI file picker. Routes through a confirmation step if the database isn't empty. */
    fun restoreLocalBackup(uri: Uri) {
        viewModelScope.launch { initiateRestore(uri) }
    }

    fun confirmRestoreReplacingExisting() {
        val uri = _pendingRestoreUri.value ?: return
        _pendingRestoreUri.value = null
        viewModelScope.launch {
            val db = (getApplication<Application>() as SmsFinanceApp).database
            BackupManager.wipeForRestore(db)
            performRestore(uri, db)
        }
    }

    fun cancelPendingRestore() {
        _pendingRestoreUri.value = null
    }

    private suspend fun initiateRestore(uri: Uri) {
        val db = (getApplication<Application>() as SmsFinanceApp).database
        if (BackupManager.hasExistingData(db)) {
            _pendingRestoreUri.value = uri
        } else {
            performRestore(uri, db)
        }
    }

    private suspend fun performRestore(uri: Uri, db: AppDatabase) {
        _isLoading.value = true
        try {
            val count = BackupManager.restoreBackup(getApplication(), uri, db)
            _message.value = UiMessage("$count تراکنش بازیابی شد")
        } catch (e: Exception) {
            _message.value = UiMessage("خطا در بازیابی: ${e.message}", isError = true)
        } finally {
            _isLoading.value = false
        }
    }

    fun deleteAllTransactions() {
        viewModelScope.launch {
            repository.deleteAll()
            _message.value = UiMessage("همه تراکنش‌ها حذف شدند")
        }
    }

    // --- Google Drive backup (optional) ---

    fun driveSignInIntent(): Intent = GoogleSignInHelper.signInIntent(getApplication())

    fun driveSignedInAccount(): GoogleSignInAccount? = GoogleSignInHelper.lastSignedInAccount(getApplication())

    fun handleDriveSignInResult(data: Intent?) {
        val account = GoogleSignInHelper.handleSignInResult(data)
        _message.value = if (account != null) {
            UiMessage("ورود با ${account.email} موفق بود")
        } else {
            UiMessage("ورود به Google ناموفق بود", isError = true)
        }
    }

    fun signOutDrive() {
        GoogleSignInHelper.signOut(getApplication())
    }

    fun backupToDrive() {
        val account = driveSignedInAccount() ?: run {
            _message.value = UiMessage("ابتدا با حساب گوگل وارد شوید", isError = true)
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val app: Application = getApplication()
                val token = GoogleSignInHelper.getAccessToken(app, account)
                if (token == null) {
                    _message.value = UiMessage("دریافت توکن دسترسی ناموفق بود", isError = true)
                    return@launch
                }
                val db = (app as SmsFinanceApp).database
                val file = BackupManager.createBackup(app, db)
                when (val result = GoogleDriveUploader.upload(token, file)) {
                    is GoogleDriveUploader.DriveResult.Success -> _message.value = UiMessage(result.message)
                    is GoogleDriveUploader.DriveResult.Failure -> _message.value = UiMessage(result.message, isError = true)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun restoreFromDrive() {
        val account = driveSignedInAccount() ?: run {
            _message.value = UiMessage("ابتدا با حساب گوگل وارد شوید", isError = true)
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val app: Application = getApplication()
                val token = GoogleSignInHelper.getAccessToken(app, account)
                if (token == null) {
                    _message.value = UiMessage("دریافت توکن دسترسی ناموفق بود", isError = true)
                    return@launch
                }
                val tempFile = File(app.cacheDir, "drive_restore_temp.json")
                when (val result = GoogleDriveUploader.downloadLatestBackup(token, tempFile)) {
                    is GoogleDriveUploader.DriveResult.Success -> {
                        _isLoading.value = false
                        initiateRestore(android.net.Uri.fromFile(tempFile))
                    }
                    is GoogleDriveUploader.DriveResult.Failure -> _message.value = UiMessage(result.message, isError = true)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- Statistics helpers (computed in-memory from the already-loaded list) ---

    fun totalIncome(list: List<Transaction> = allTransactions.value) =
        list.filter { it.type == TransactionType.INCOME }.sumOf { it.amountToman }

    fun totalExpense(list: List<Transaction> = allTransactions.value) =
        list.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountToman }

    private fun categoryKindOf(transaction: Transaction, categories: List<Category>): CategoryKind? =
        categories.firstOrNull { it.id == transaction.categoryId }?.kind

    fun realIncome(list: List<Transaction> = allTransactions.value, categories: List<Category> = allCategories.value) =
        list.filter { it.type == TransactionType.INCOME && categoryKindOf(it, categories) != CategoryKind.DEBT_COLLECTION }
            .sumOf { it.amountToman }

    fun realExpense(list: List<Transaction> = allTransactions.value, categories: List<Category> = allCategories.value) =
        list.filter { it.type == TransactionType.EXPENSE && categoryKindOf(it, categories) != CategoryKind.DEBT_PAYMENT }
            .sumOf { it.amountToman }

    fun estimatedProfit(list: List<Transaction> = allTransactions.value, categories: List<Category> = allCategories.value) =
        realIncome(list, categories) - realExpense(list, categories)

    fun debtCollected(list: List<Transaction> = allTransactions.value, categories: List<Category> = allCategories.value) =
        list.filter { categoryKindOf(it, categories) == CategoryKind.DEBT_COLLECTION }.sumOf { it.amountToman }

    fun debtPaid(list: List<Transaction> = allTransactions.value, categories: List<Category> = allCategories.value) =
        list.filter { categoryKindOf(it, categories) == CategoryKind.DEBT_PAYMENT }.sumOf { it.amountToman }

    fun thisMonthTransactions(list: List<Transaction> = allTransactions.value): List<Transaction> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val startOfMonth = cal.timeInMillis
        return list.filter { it.date >= startOfMonth }
    }

    fun todayTransactions(list: List<Transaction> = allTransactions.value): List<Transaction> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        return list.filter { it.date >= startOfDay }
    }

    fun todayIncome(list: List<Transaction> = allTransactions.value) = totalIncome(todayTransactions(list))
    fun todayExpense(list: List<Transaction> = allTransactions.value) = totalExpense(todayTransactions(list))

    fun byBank(list: List<Transaction> = allTransactions.value): Map<String, Long> =
        list.groupBy { it.bankName }.mapValues { (_, txns) -> txns.sumOf { it.amountToman } }

    fun byCategory(list: List<Transaction> = allTransactions.value, categories: List<Category> = allCategories.value): Map<String, Long> {
        val nameById = categories.associateBy { it.id }
        return list.groupBy { nameById[it.categoryId]?.name ?: "بدون دسته" }
            .mapValues { (_, txns) -> txns.sumOf { it.amountToman } }
    }

    fun recurringIds(list: List<Transaction> = allTransactions.value): Set<Long> =
        RecurringDetector.computeRecurringIds(list)

    fun recurringOnly(list: List<Transaction> = allTransactions.value): List<Transaction> {
        val ids = recurringIds(list)
        return list.filter { it.id in ids }
    }
}
