// SmsFinance file version: 1 — first-run guide for non-technical users ("کاربران ما افراد
// غیرمتخصص و گاه کاملاً عامی هستن"). Shown once, after the scan-range choice, before the user
// ever sees the main app. Mandatory (not a skippable tooltip series) because the product's core
// audience explicitly can't be assumed to figure the app out on their own -- but every step
// still has an honest "skip"/"بعداً" path, since forcing completion of the category builder
// would violate "بدون تصمیم مالی غیرقابل‌بازگشت بدون تأیید کاربر" in spirit (nothing here is
// irreversible, but nothing should feel mandatory-to-the-point-of-frustrating either).
package com.kamal.smsfinance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.data.CategoryKind
import com.kamal.smsfinance.util.normalizeDigits
import com.kamal.smsfinance.util.toPositiveLongOrNull
import kotlinx.coroutines.launch

private enum class OnboardingStep { WELCOME, TOUR, SMALL_AMOUNT, CATEGORIES, DONE }

private data class TourItem(val icon: ImageVector, val title: String, val body: String)

private val TOUR_ITEMS = listOf(
    TourItem(Icons.Filled.Receipt, "تراکنش‌ها", "لیست همه چیزهایی که برنامه از پیامک‌های بانکی شما پیدا کرده. با زدن روی هرکدوم می‌تونید دسته‌شو عوض کنید یا جزئیاتشو ببینید."),
    TourItem(Icons.Filled.BarChart, "آمار", "جمع درآمد و هزینه، تفکیک بر اساس بانک و دسته‌بندی -- برای این‌که در چند ثانیه بفهمید وضعیت مالیتون چطوره."),
    TourItem(Icons.Filled.Description, "چک‌ها", "ثبت چک‌های دریافتی و پرداختی، با یادآوری قبل از سررسید."),
    TourItem(Icons.Filled.People, "طرف‌حساب‌ها", "برای کسایی که باهاشون حساب‌وکتاب دارید -- مانده و تعداد تراکنش هرکدوم خودکار محاسبه می‌شه."),
    TourItem(Icons.Filled.Settings, "تنظیمات", "مدیریت دسته‌بندی‌ها، قوانین خودکار، پشتیبان‌گیری، و اسکن دوباره پیامک‌ها -- هرچیزی که بخواید تغییرش بدید، اینجاست.")
)

private data class SuggestedCategory(val name: String, val subcategories: List<String> = emptyList())

private val SUGGESTED_EXPENSE = listOf(
    SuggestedCategory("خواروبار", listOf("سوپرمارکت", "میوه و سبزی")),
    SuggestedCategory("حمل‌ونقل", listOf("بنزین", "تاکسی/اسنپ")),
    SuggestedCategory("قبض‌ها", listOf("برق و گاز و آب", "اینترنت و موبایل")),
    SuggestedCategory("رستوران و کافه"),
    SuggestedCategory("پوشاک"),
    SuggestedCategory("درمان و دارو"),
    SuggestedCategory("تفریح و سرگرمی"),
    SuggestedCategory("قسط و وام")
)
private val SUGGESTED_INCOME = listOf(
    SuggestedCategory("حقوق"),
    SuggestedCategory("درآمد آزاد/فریلنس"),
    SuggestedCategory("سود سپرده")
)

/**
 * @param existingExpenseCategoryId id of the default "هزینه" category (seeded at first install)
 * -- suggested expense chips are added as its subcategories. Same for [existingIncomeCategoryId]
 * and "درآمد".
 */
@Composable
fun OnboardingScreen(
    existingExpenseCategoryId: Long?,
    existingIncomeCategoryId: Long?,
    smallAmountEnabled: Boolean,
    smallAmountThreshold: Long,
    onSmallAmountEnabledChange: (Boolean) -> Unit,
    onSmallAmountThresholdChange: (Long) -> Unit,
    onCreateCategory: suspend (name: String, kind: CategoryKind, parentId: Long?) -> Long?,
    onFinish: () -> Unit
) {
    var step by remember { mutableStateOf(OnboardingStep.WELCOME) }
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            Column(Modifier.padding(16.dp)) {
                LinearProgressIndicator(
                    progress = { (step.ordinal + 1f) / OnboardingStep.values().size },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (step != OnboardingStep.WELCOME) {
                        TextButton(onClick = { step = OnboardingStep.values()[step.ordinal - 1] }) { Text("قبلی") }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    Button(onClick = {
                        if (step == OnboardingStep.DONE) onFinish()
                        else step = OnboardingStep.values()[step.ordinal + 1]
                    }) {
                        Text(if (step == OnboardingStep.DONE) "شروع کن" else "بعدی")
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (step) {
                OnboardingStep.WELCOME -> WelcomeStep()
                OnboardingStep.TOUR -> TourStep()
                OnboardingStep.SMALL_AMOUNT -> SmallAmountStep(
                    enabled = smallAmountEnabled,
                    threshold = smallAmountThreshold,
                    onEnabledChange = onSmallAmountEnabledChange,
                    onThresholdChange = onSmallAmountThresholdChange
                )
                OnboardingStep.CATEGORIES -> CategoryBuilderStep(
                    existingExpenseCategoryId = existingExpenseCategoryId,
                    existingIncomeCategoryId = existingIncomeCategoryId,
                    onCreateCategory = { name, kind, parentId ->
                        scope.launch { onCreateCategory(name, kind, parentId) }
                    }
                )
                OnboardingStep.DONE -> DoneStep()
            }
        }
    }
}

@Composable
private fun StepScaffold(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
private fun WelcomeStep() {
    StepScaffold(
        title = "به SmsFinance خوش اومدید",
        subtitle = "این چند صفحه کوتاه رو بخونید تا از همون اول با برنامه راحت کار کنید. فقط چند دقیقه طول می‌کشه."
    ) {
        val points = listOf(
            "این برنامه پیامک‌های بانکی گوشیتونو می‌خونه و خودش تشخیص می‌ده کجا پول خرج شده یا واریز شده -- شما لازم نیست چیزی تایپ کنید.",
            "همه‌چیز فقط روی گوشی خودتون پردازش می‌شه. هیچ پیامکی به هیچ سروری فرستاده نمی‌شه.",
            "برنامه هیچ‌وقت خودسر تصمیم قطعی نمی‌گیره -- اگه یه پیامک رو کامل نفهمه، بهتون نشون می‌ده تا خودتون بگید تراکنشه یا نه.",
            "دسته‌بندی‌ها کاملاً دست خودتونه و بعداً هم می‌تونید عوضشون کنید -- چیزی این‌جا برای همیشه ثابت نمی‌مونه."
        )
        points.forEach { p ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp, top = 2.dp).size(18.dp))
                Text(p, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TourStep() {
    StepScaffold(
        title = "پنج بخش اصلی برنامه",
        subtitle = "پایین صفحه همیشه همین پنج تب هست -- هر کدوم برای چی‌کاره:"
    ) {
        TOUR_ITEMS.forEach { item ->
            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallAmountStep(
    enabled: Boolean,
    threshold: Long,
    onEnabledChange: (Boolean) -> Unit,
    onThresholdChange: (Long) -> Unit
) {
    var thresholdText by remember(threshold) { mutableStateOf(threshold.toString()) }
    StepScaffold(
        title = "قبل از هرچی: مبالغ خیلی کوچیک رو فیلتر کنیم؟",
        subtitle = "کارمزدهای چندتومنی پیامک بانکی معمولاً برای گزارش مالی مهم نیستن و فقط شلوغی ایجاد می‌کنن. اگه فعال کنید، هر تراکنش زیر مبلغی که تعیین می‌کنید، خودکار تو یه دسته مشخص میره و لازم نیست هر بار جداگونه دسته‌بندیش کنید."
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
            Spacer(Modifier.width(12.dp))
            Text("فعال باشه", style = MaterialTheme.typography.bodyLarge)
        }
        if (enabled) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = thresholdText,
                onValueChange = { input ->
                    if (input.normalizeDigits().all { it.isDigit() || it == ',' || it == '٬' || it == ' ' }) {
                        thresholdText = input
                        input.toPositiveLongOrNull()?.let(onThresholdChange)
                    }
                },
                label = { Text("زیر چند تومان؟") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "پیشنهاد: عددی مثل ۵۰۰۰ تا ۱۰,۰۰۰ تومان معمولاً کارمزدها رو می‌گیره بدون این‌که خریدهای واقعی کوچیک رو قاطی کنه.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "هر وقت خواستید می‌تونید این تنظیم رو از «تنظیمات» عوض کنید.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CategoryBuilderStep(
    existingExpenseCategoryId: Long?,
    existingIncomeCategoryId: Long?,
    onCreateCategory: (name: String, kind: CategoryKind, parentId: Long?) -> Unit
) {
    // Local "added" tracking only -- the real source of truth is the database (via
    // onCreateCategory); this just drives the checkmark so tapping twice doesn't create
    // duplicates within this screen.
    val added = remember { mutableStateOf(setOf<String>()) }

    fun addChip(name: String, kind: CategoryKind, parentId: Long?) {
        onCreateCategory(name, kind, parentId)
        added.value = added.value + name
    }

    StepScaffold(
        title = "چندتا دسته آماده بسازیم؟",
        subtitle = "این‌ها پرکاربردترین دسته‌های خانوارهای ایرانی‌ان -- هرکدومو بخواید با یه لمس اضافه کنید. هرچی این‌جا نسازید، بعداً هم از «تنظیمات» می‌تونید اضافه کنید."
    ) {
        Text("هزینه", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SuggestedChipGrid(SUGGESTED_EXPENSE, added.value) { sc ->
            addChip(sc.name, CategoryKind.EXPENSE, existingExpenseCategoryId)
            sc.subcategories.forEach { addChip(it, CategoryKind.EXPENSE, existingExpenseCategoryId) }
        }
        Spacer(Modifier.height(20.dp))
        Text("درآمد", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SuggestedChipGrid(SUGGESTED_INCOME, added.value) { sc ->
            addChip(sc.name, CategoryKind.INCOME, existingIncomeCategoryId)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestedChipGrid(items: List<SuggestedCategory>, added: Set<String>, onAdd: (SuggestedCategory) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { sc ->
            val isAdded = sc.name in added
            FilterChip(
                selected = isAdded,
                onClick = { if (!isAdded) onAdd(sc) },
                label = { Text(sc.name) },
                leadingIcon = if (isAdded) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
    }
}

@Composable
private fun DoneStep() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.AccountBalanceWallet,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("آماده‌اید!", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "برنامه داره پیامک‌هاتونو بررسی می‌کنه. هر سؤالی هم باشه، از «تنظیمات» به همین راهنما و بقیه امکانات دسترسی دارید.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
