// SmsFinance file version: 2 — added UnidentifiedSms overlay, dashboard data wiring, restore-confirmation dialog, small-amount settings + CSV import passthrough, counterparty notes wiring
package com.kamal.smsfinance

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.kamal.smsfinance.ui.TransactionViewModel
import com.kamal.smsfinance.ui.screens.*
import com.kamal.smsfinance.ui.theme.SmsFinanceTheme
import com.kamal.smsfinance.util.CsvExporter
import com.kamal.smsfinance.util.ThemeMode

private enum class Tab(val label: String) {
    LIST("تراکنش‌ها"), COUNTERPARTIES("طرف حساب‌ها"), CHECKS("چک‌ها"), STATS("آمار"), SETTINGS("تنظیمات")
}

private sealed class Overlay {
    object AddManual : Overlay()
    object Categories : Overlay()
    object Rules : Overlay()
    object UnidentifiedSms : Overlay()
    data class CounterpartyProfile(val id: Long) : Overlay()
    data class SmsContext(val sender: String, val timestamp: Long) : Overlay()
    data class BankTransactions(val bank: String) : Overlay()
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
                    SmsPermissionGate(onGranted = { viewModel.scanInbox() }) {
                        AppRoot(viewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(viewModel: TransactionViewModel) {
    val transactions by viewModel.allTransactions.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
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
    val initialScanDone by viewModel.initialScanDone.collectAsState()
    var showInitialScanDialog by remember { mutableStateOf(!initialScanDone) }

    var tab by remember { mutableStateOf(Tab.LIST) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var driveAccountEmail by remember { mutableStateOf(viewModel.driveSignedInAccount()?.email) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val driveSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleDriveSignInResult(result.data)
        driveAccountEmail = viewModel.driveSignedInAccount()?.email
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

    // First-run dialog: let the user choose how far back to scan SMS.
    if (showInitialScanDialog) {
        AlertDialog(
            onDismissRequest = { /* cannot dismiss — must choose */ },
            title = { Text("اسکن اولیه پیامک‌ها") },
            text = {
                Column {
                    Text(
                        "برای شروع، تراکنش‌های چه مدت زمانی را از پیامک‌ها وارد کنیم؟\n" +
                            "(بعداً از دکمه «اسکن پیامک‌ها» می‌توانید کل صندوق را اسکن کنید.)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    val options = listOf(7L to "یک هفته اخیر", 30L to "یک ماه اخیر", 90L to "سه ماه اخیر", 365L to "یک سال اخیر", 0L to "همه پیامک‌ها")
                    options.forEach { (days, label) ->
                        Button(
                            onClick = {
                                viewModel.setInitialScanWindowDays(days)
                                showInitialScanDialog = false
                                if (days == 0L) viewModel.scanInboxFull() else viewModel.scanInbox()
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        )
    }

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
                onAdd = { name, kind -> viewModel.addCategory(name, kind) },
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
                CounterpartyProfileScreen(
                    counterparty = counterparty,
                    transactions = cpTransactions,
                    balance = balance,
                    totalVolume = volume,
                    onBack = { overlay = null },
                    onDelete = {
                        viewModel.deleteCounterparty(counterparty)
                        overlay = null
                    },
                    onSaveNotes = { notes -> viewModel.updateCounterpartyNotes(counterparty, notes) }
                )
            }
            return
        }
        is Overlay.SmsContext -> {
            SmsContextScreen(
                sender = current.sender,
                txnTimestamp = current.timestamp,
                onBack = { overlay = null }
            )
            return
        }
        is Overlay.BankTransactions -> {
            val bankTxns = remember(current.bank, transactions) {
                transactions.filter { it.bankName == current.bank }
            }
            BankTransactionsScreen(
                bank = current.bank,
                transactions = bankTxns,
                categories = categories,
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
                    val (owedToMe, iOwe) = viewModel.counterpartyBalanceSummary(transactions)
                    TransactionListScreen(
                        transactions = transactions,
                        categories = categories,
                        categoryUsageCounts = viewModel.categoryUsageCounts(transactions),
                        recurringIds = viewModel.recurringIds(transactions),
                        isLoading = isLoading,
                        unidentifiedSmsCount = unidentifiedSms.size,
                        dashboard = DashboardData(
                            todayIncome = viewModel.todayIncome(transactions),
                            todayExpense = viewModel.todayExpense(transactions),
                            estimatedProfitThisMonth = viewModel.estimatedProfit(viewModel.thisMonthTransactions(transactions), categories),
                            totalOwedToMe = owedToMe,
                            totalIOwe = iOwe,
                            checksDueSoonCount = checksDueSoon.size
                        ),
                        onScanInbox = { viewModel.scanInboxFull() },
                        onDelete = { viewModel.deleteTransaction(it) },
                        onAddManual = { overlay = Overlay.AddManual },
                        onAssignCategory = { txn, catId -> viewModel.assignCategory(txn.id, catId) },
                        onCreateRule = { pattern, categoryId -> viewModel.addRule(pattern, categoryId, null) },
                        onOpenUnidentifiedSms = { overlay = Overlay.UnidentifiedSms },
                        onOpenChecks = { tab = Tab.CHECKS },
                        onOpenCounterparties = { tab = Tab.COUNTERPARTIES },
                        onOpenSmsContext = { sender, timestamp -> overlay = Overlay.SmsContext(sender, timestamp) }
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
                    transactions = transactions,
                    totalIncome = viewModel.totalIncome(transactions),
                    totalExpense = viewModel.totalExpense(transactions),
                    estimatedProfitThisMonth = viewModel.estimatedProfit(viewModel.thisMonthTransactions(transactions), categories),
                    debtCollected = viewModel.debtCollected(transactions, categories),
                    debtPaid = viewModel.debtPaid(transactions, categories),
                    byBank = viewModel.byBank(transactions),
                    byCategory = viewModel.byCategory(transactions, categories),
                    recurring = viewModel.recurringOnly(transactions),
                    onBankClick = { bank -> overlay = Overlay.BankTransactions(bank) }
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
                    onImportCounterpartiesCsv = { uri -> viewModel.importCounterpartiesCsv(uri) }
                )
            }
        }
    }
}
