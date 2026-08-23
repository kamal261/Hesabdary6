// SmsFinance file version: 2 — added UnidentifiedSms overlay, dashboard data wiring, restore-confirmation dialog, small-amount settings + CSV import passthrough, counterparty notes wiring
package com.kamal.smsfinance

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import com.kamal.smsfinance.permission.SmsPermissionGate
import com.kamal.smsfinance.data.CategoryTree
import com.kamal.smsfinance.ui.TransactionViewModel
import com.kamal.smsfinance.ui.screens.*
import com.kamal.smsfinance.ui.components.ScanRangeDialog
import com.kamal.smsfinance.ui.theme.SmsFinanceTheme
import com.kamal.smsfinance.util.CsvExporter
import com.kamal.smsfinance.util.ThemeMode
import com.kamal.smsfinance.widget.FinanceWidgetProvider

private enum class Tab(val label: String) {
    LIST("تراکنش‌ها"), COUNTERPARTIES("طلب و بدهی"), CHECKS("چک‌ها"), STATS("دخل و خرج"), SETTINGS("تنظیمات")
}

private sealed class Overlay {
    object AddManual : Overlay()
    object Categories : Overlay()
    object Rules : Overlay()
    object UnidentifiedSms : Overlay()
    object Help : Overlay()
    data class CounterpartyProfile(val id: Long) : Overlay()
    data class BankTransactions(val bank: String) : Overlay()
    data class CategoryTransactions(val path: String) : Overlay()
    object Notes : Overlay()
    data class SmsContext(val sender: String, val txnTimestamp: Long) : Overlay()
}

class MainActivity : ComponentActivity() {

    private val viewModel: TransactionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            SmsFinanceTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier, color = MaterialTheme.colorScheme.background) {
                    SmsPermissionGate(onGranted = { viewModel.onSmsPermissionGranted() }) { smsPermissionGranted, requestSmsPermission ->
                        val showOnboarding by viewModel.showScanRangeDialog.collectAsState()
                        val onboardingScanSummary by viewModel.onboardingScanSummary.collectAsState()
                        val onboardingScanRunning by viewModel.onboardingScanRunning.collectAsState()
                        if (showOnboarding) {
                            com.kamal.smsfinance.ui.components.OnboardingFlow(
                                onSetSmallAmount = { enabled, threshold ->
                                    viewModel.setSmallAmountEnabled(enabled)
                                    viewModel.setSmallAmountThreshold(threshold)
                                },
                                onCreateCategories = { chosen -> viewModel.createSuggestedCategories(chosen) },
                                onFinish = { days -> viewModel.completeOnboardingScan(days) },
                                scanSummary = onboardingScanSummary,
                                scanRunning = onboardingScanRunning,
                                onScanSummaryDone = { viewModel.clearOnboardingScanSummary() }
                            )
                        } else {
                            AppRoot(
                                viewModel = viewModel,
                                smsPermissionGranted = smsPermissionGranted,
                                onRequestSmsPermission = requestSmsPermission
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(
    viewModel: TransactionViewModel,
    smsPermissionGranted: Boolean,
    onRequestSmsPermission: () -> Unit
) {
    val transactions by viewModel.allTransactions.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val categoryPaths = remember(categories) { CategoryTree.pathsOf(categories) }
    val counterparties by viewModel.allCounterparties.collectAsState()
    val checks by viewModel.allChecks.collectAsState()
    val checksDueSoon by viewModel.checksDueSoon.collectAsState()
    val rules by viewModel.allRules.collectAsState()
    val unidentifiedSms by viewModel.unidentifiedSms.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val webhookUrl by viewModel.webhookUrl.collectAsState()
    val lastExportedFile by viewModel.lastExportedFile.collectAsState()
    val pendingRestoreUri by viewModel.pendingRestoreConfirmation.collectAsState()
    val smallAmountEnabled by viewModel.smallAmountEnabled.collectAsState()
    val smallAmountThreshold by viewModel.smallAmountThreshold.collectAsState()
    val smallAmountCategoryId by viewModel.smallAmountCategoryId.collectAsState()
    val smartSuggestions by viewModel.smartSuggestions.collectAsState()
    val lastScanTimestamp by viewModel.lastScanTimestamp.collectAsState()
    val lastLocalBackupTimestamp by viewModel.lastLocalBackupTimestamp.collectAsState()
    val shouldShowBackupReminder by viewModel.shouldShowBackupReminder.collectAsState()
    val onboardingScanDone by viewModel.onboardingScanDone.collectAsState()

    var tab by remember { mutableStateOf(Tab.LIST) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var driveAccountEmail by remember { mutableStateOf(viewModel.driveSignedInAccount()?.email) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner

    DisposableEffect(lifecycleOwner, smsPermissionGranted, onboardingScanDone) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && smsPermissionGranted && onboardingScanDone) {
                viewModel.scanInboxOnResume()
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    val driveSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleDriveSignInResult(result.data)
        driveAccountEmail = viewModel.driveSignedInAccount()?.email
    }

    LaunchedEffect(transactions) {
        FinanceWidgetProvider.requestUpdate(context)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it.text)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(lastExportedFile) {
        lastExportedFile?.let { file ->
            val intent = CsvExporter.shareIntent(context, file)
            context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری فایل CSV"))
        }
    }

    // Restore-into-non-empty-database confirmation (fixes the id-mapping bug).
    if (pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingRestore() },
            title = { Text("جایگزینی داده‌های فعلی؟") },
            text = {
                Text(
                    "دیتابیس فعلی خالی نیست. برای بازیابی صحیح، پیشنهاد می‌شود ابتدا تراکنش‌ها، طرف‌حساب‌ها، " +
                        "چک‌ها و قوانین فعلی حذف و با نسخه پشتیبان جایگزین شوند (دسته‌بندی‌های شما حفظ می‌مانند)."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRestoreReplacingExisting() }) {
                    Text("حذف و بازیابی", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelPendingRestore() }) { Text("انصراف") }
            }
        )
    }

    // Hardware/gesture back: closes an open overlay screen instead of exiting the app, and once
    // no overlay is open, returns to the LIST tab from any other tab before exiting. Only one
    // of these is ever enabled at a time.
    BackHandler(enabled = overlay != null) { overlay = null }
    BackHandler(enabled = overlay == null && tab != Tab.LIST) { tab = Tab.LIST }

    when (val current = overlay) {
        Overlay.AddManual -> {
            AddTransactionScreen(
                categories = categories,
                categoryUsageCounts = viewModel.categoryUsageCounts(transactions),
                counterparties = counterparties,
                onSave = { amount, type, bank, desc, date, categoryId, counterpartyId ->
                    viewModel.addManualTransaction(amount, type, bank, desc, date, categoryId, counterpartyId)
                    overlay = null
                },
                onSaveIndirectSettlement = { amount, type, counterpartyId, desc, date, categoryId ->
                    viewModel.addIndirectSettlement(amount, type, counterpartyId, desc, date, categoryId)
                    overlay = null
                },
                onCancel = { overlay = null }
            )
            return
        }
        Overlay.Categories -> {
            CategoriesScreen(
                categories = categories,
                onAdd = { name, kind, parentId -> viewModel.addCategory(name, kind, parentId) },
                onDelete = { viewModel.deleteCategory(it) },
                onBack = { overlay = null }
            )
            return
        }
        Overlay.Rules -> {
            RulesScreen(
                rules = rules,
                categories = categories,
                counterparties = counterparties,
                onAdd = { pattern, categoryId, counterpartyId -> viewModel.addRule(pattern, categoryId, counterpartyId) },
                onDelete = { viewModel.deleteRule(it) },
                onBack = { overlay = null }
            )
            return
        }
        Overlay.UnidentifiedSms -> {
            UnidentifiedSmsScreen(
                items = unidentifiedSms,
                onDismiss = { viewModel.dismissUnidentifiedSms(it) },
                onDismissAll = { viewModel.dismissAllUnidentifiedSms() },
                onExport = { viewModel.exportUnidentifiedSms() },
                onBack = { overlay = null }
            )
            return
        }
        is Overlay.CounterpartyProfile -> {
            val counterparty = counterparties.firstOrNull { it.id == current.id }
            if (counterparty == null) {
                overlay = null
            } else {
                val cpTransactions by viewModel.transactionsForCounterparty(current.id).collectAsState(initial = emptyList())
                val balance by viewModel.balanceForCounterparty(current.id).collectAsState(initial = 0L)
                val volume by viewModel.totalVolumeForCounterparty(current.id).collectAsState(initial = 0L)
                val reminders by viewModel.remindersForCounterparty(current.id).collectAsState(initial = emptyList())
                CounterpartyProfileScreen(
                    counterparty = counterparty,
                    transactions = cpTransactions,
                    balance = balance,
                    totalVolume = volume,
                    reminders = reminders,
                    onBack = { overlay = null },
                    onDelete = {
                        viewModel.deleteCounterparty(counterparty)
                        overlay = null
                    },
                    onSaveNotes = { notes -> viewModel.updateCounterpartyNotes(counterparty, notes) },
                    onAddReminder = { viewModel.addCounterpartyReminder(it) },
                    onToggleReminder = { reminder, done -> viewModel.setCounterpartyReminderDone(reminder, done) },
                    onDeleteReminder = { viewModel.deleteCounterpartyReminder(it) }
                )
            }
            return
        }
        is Overlay.BankTransactions -> {
            BankTransactionsScreen(
                bank = current.bank,
                transactions = transactions.filter { it.bankName == current.bank },
                categories = categories,
                onBack = { overlay = null }
            )
            return
        }
        is Overlay.CategoryTransactions -> {
            TransactionDrilldownScreen(
                title = "تراکنش‌های دسته ${current.path}",
                transactions = transactions.filter { (categoryPaths[it.categoryId] ?: "بدون دسته") == current.path },
                categories = categories,
                onBack = { overlay = null }
            )
            return
        }
        Overlay.Notes -> {
            NotesScreen(
                transactions = transactions,
                categories = categories,
                onBack = { overlay = null },
                onSaveNote = { txn, notes -> viewModel.updateTransactionNotes(txn.id, notes) }
            )
            return
        }
        Overlay.Help -> {
            HelpScreen(onBack = { overlay = null })
            return
        }
        is Overlay.SmsContext -> {
            SmsContextScreen(
                sender = current.sender,
                txnTimestamp = current.txnTimestamp,
                onBack = { overlay = null }
            )
            return
        }
        null -> Unit
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.LIST, onClick = { tab = Tab.LIST },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    label = { Text(Tab.LIST.label) }
                )
                NavigationBarItem(
                    selected = tab == Tab.COUNTERPARTIES, onClick = { tab = Tab.COUNTERPARTIES },
                    icon = { Icon(Icons.Filled.People, contentDescription = null) },
                    label = { Text(Tab.COUNTERPARTIES.label) }
                )
                NavigationBarItem(
                    selected = tab == Tab.CHECKS, onClick = { tab = Tab.CHECKS },
                    icon = { Icon(Icons.Filled.Receipt, contentDescription = null) },
                    label = { Text(Tab.CHECKS.label) }
                )
                NavigationBarItem(
                    selected = tab == Tab.STATS, onClick = { tab = Tab.STATS },
                    icon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                    label = { Text(Tab.STATS.label) }
                )
                NavigationBarItem(
                    selected = tab == Tab.SETTINGS, onClick = { tab = Tab.SETTINGS },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(Tab.SETTINGS.label) }
                )
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                Tab.LIST -> {
                    val recurringIds = remember(transactions) {
                        viewModel.recurringIds(transactions)
                    }
                    val categoryUsageCounts = remember(transactions) {
                        viewModel.categoryUsageCounts(transactions)
                    }
                    val uncategorizedCount = remember(transactions) {
                        transactions.count { it.categoryId == null }
                    }
                    val notesCount = remember(transactions) {
                        transactions.count { !it.notes.isNullOrBlank() }
                    }
                    val (owedToMe, iOwe) = remember(transactions, categories) {
                        viewModel.counterpartyBalanceSummary(transactions)
                    }
                    val todayIncome = remember(transactions) {
                        viewModel.todayIncome(transactions)
                    }
                    val todayExpense = remember(transactions) {
                        viewModel.todayExpense(transactions)
                    }
                    val estimatedProfitThisMonth = remember(transactions, categories) {
                        viewModel.estimatedProfit(
                            viewModel.thisMonthTransactions(transactions),
                            categories
                        )
                    }
                    TransactionListScreen(
                        transactions = transactions,
                        categories = categories,
                        categoryUsageCounts = categoryUsageCounts,
                        recurringIds = recurringIds,
                        isLoading = isLoading,
                        unidentifiedSmsCount = unidentifiedSms.size,
                        notesCount = notesCount,
                        uncategorizedCount = uncategorizedCount,
                        dashboard = DashboardData(
                            todayIncome = todayIncome,
                            todayExpense = todayExpense,
                            estimatedProfitThisMonth = estimatedProfitThisMonth,
                            totalOwedToMe = owedToMe,
                            totalIOwe = iOwe,
                            checksDueSoonCount = checksDueSoon.size
                        ),
                        smartSuggestions = smartSuggestions,
                        lastScanTimestamp = lastScanTimestamp,
                        backupReminderVisible = shouldShowBackupReminder,
                        lastBackupTimestamp = lastLocalBackupTimestamp,
                        smsPermissionGranted = smsPermissionGranted,
                        onCreateBackup = { viewModel.createLocalBackup() },
                        onSnoozeBackupReminder = { viewModel.snoozeBackupReminder() },
                        onRequestSmsPermission = onRequestSmsPermission,
                        onAcceptSuggestion = { viewModel.acceptSuggestion(it) },
                        onRejectSuggestion = { viewModel.rejectSuggestion(it) },
                        onScanInbox = { viewModel.scanInbox() },
                        onDelete = { viewModel.deleteTransaction(it) },
                        onAddManual = { overlay = Overlay.AddManual },
                        onAssignCategory = { txn, catId -> viewModel.assignCategory(txn.id, catId) },
                        onChangeType = { txn, type -> viewModel.changeTransactionType(txn.id, type) },
                        onCreateCategory = { name, kind, parentId -> viewModel.addCategory(name, kind, parentId) },
                        onCreateRule = { pattern, categoryId -> viewModel.addRule(pattern, categoryId, null) },
                        onOpenUnidentifiedSms = { overlay = Overlay.UnidentifiedSms },
                        onOpenNotes = { overlay = Overlay.Notes },
                        onOpenChecks = { tab = Tab.CHECKS },
                        onOpenCounterparties = { tab = Tab.COUNTERPARTIES },
                        onSaveNotes = { txn, notes -> viewModel.updateTransactionNotes(txn.id, notes) },
                        onViewSmsContext = { sender, timestamp -> overlay = Overlay.SmsContext(sender, timestamp) }
                    )
                }
                Tab.COUNTERPARTIES -> CounterpartiesScreen(
                    counterparties = counterparties,
                    balanceOf = { id ->
                        // Quick derived balance from the already-loaded transaction list,
                        // avoiding a separate Flow subscription per list row.
                        transactions.filter { it.counterpartyId == id }
                            .fold(0L) { acc, t ->
                                if (t.type == com.kamal.smsfinance.data.TransactionType.INCOME) acc + t.amountToman else acc - t.amountToman
                            }
                    },
                    onAdd = { name, type, phone, address, desc -> viewModel.addCounterparty(name, type, phone, address, desc) },
                    onOpenProfile = { cp -> overlay = Overlay.CounterpartyProfile(cp.id) }
                )
                Tab.CHECKS -> ChecksScreen(
                    checks = checks,
                    dueSoon = checksDueSoon,
                    counterparties = counterparties,
                    onAdd = { viewModel.addCheck(it) },
                    onSettle = { viewModel.settleCheck(it) },
                    onMarkBounced = { viewModel.markCheckBounced(it) },
                    onDelete = { viewModel.deleteCheck(it) }
                )
                Tab.STATS -> StatisticsScreen(
                    totalIncome = viewModel.totalIncome(transactions),
                    totalExpense = viewModel.totalExpense(transactions),
                    estimatedProfitThisMonth = viewModel.estimatedProfit(viewModel.thisMonthTransactions(transactions), categories),
                    debtCollected = viewModel.debtCollected(transactions, categories),
                    debtPaid = viewModel.debtPaid(transactions, categories),
                    byBank = viewModel.byBank(transactions),
                    byCategory = viewModel.byCategory(transactions, categories),
                    recurring = viewModel.recurringOnly(transactions),
                    onBankClick = { bank -> overlay = Overlay.BankTransactions(bank) },
                    onCategoryClick = { path -> overlay = Overlay.CategoryTransactions(path) }
                )
                Tab.SETTINGS -> SettingsScreen(
                    themeMode = themeMode,
                    onThemeChange = { viewModel.setThemeMode(it) },
                    webhookUrl = webhookUrl,
                    onWebhookUrlChange = { viewModel.setWebhookUrl(it) },
                    onExportCsv = { viewModel.exportCsv() },
                    onUploadToSheets = { viewModel.uploadToSheets() },
                    onCreateBackup = { viewModel.createLocalBackup() },
                    onRestoreBackup = { uri -> viewModel.restoreLocalBackup(uri) },
                    onDeleteAll = { viewModel.deleteAllTransactions() },
                    onManageCategories = { overlay = Overlay.Categories },
                    onManageRules = { overlay = Overlay.Rules },
                    onOpenUnidentifiedSms = { overlay = Overlay.UnidentifiedSms },
                    unidentifiedSmsCount = unidentifiedSms.size,
                    driveSignedInEmail = driveAccountEmail,
                    onDriveSignIn = { driveSignInLauncher.launch(viewModel.driveSignInIntent()) },
                    onDriveSignOut = {
                        viewModel.signOutDrive()
                        driveAccountEmail = null
                    },
                    onDriveBackup = { viewModel.backupToDrive() },
                    onDriveRestore = { viewModel.restoreFromDrive() },
                    categories = categories,
                    smallAmountEnabled = smallAmountEnabled,
                    onSmallAmountEnabledChange = { viewModel.setSmallAmountEnabled(it) },
                    smallAmountThreshold = smallAmountThreshold,
                    onSmallAmountThresholdChange = { viewModel.setSmallAmountThreshold(it) },
                    smallAmountCategoryId = smallAmountCategoryId,
                    onSmallAmountCategoryChange = { viewModel.setSmallAmountCategoryId(it) },
                    onImportCategoriesCsv = { uri -> viewModel.importCategoriesCsv(uri) },
                    onImportCounterpartiesCsv = { uri -> viewModel.importCounterpartiesCsv(uri) },
                    lastScanTimestamp = lastScanTimestamp,
                    lastLocalBackupTimestamp = lastLocalBackupTimestamp,
                    backupReminderVisible = shouldShowBackupReminder,
                    onSnoozeBackupReminder = { viewModel.snoozeBackupReminder() },
                    onRescan = { days ->
                        viewModel.scanInbox(
                            sinceMillis = days?.let { System.currentTimeMillis() - it * 24L * 60 * 60 * 1000 },
                            incremental = false,
                            commitCursor = false
                        )
                    },
                    onOpenHelp = { overlay = Overlay.Help }
                )
            }
        }
    }
}
