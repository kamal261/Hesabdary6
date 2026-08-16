// SmsFinance file version: 1 — first-run onboarding wizard for non-technical users (per product
// direction: "کاربران ما افراد غیرمتخصص و گاه کاملا عامی هستن"). Five steps: philosophy in
// plain language, a tour of the five tabs, small-amount auto-categorization (asked first, per
// explicit ordering), category/subcategory quick-builder (curated Persian suggestions, one
// level of nesting), then the SMS scan range (folded in here instead of a separate popup, so
// there's one continuous flow instead of two interruptions back to back).
package com.kamal.smsfinance.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.data.CategoryKind
import com.kamal.smsfinance.util.normalizeDigits
import com.kamal.smsfinance.util.toPositiveLongOrNull
import kotlinx.coroutines.launch

/** One suggested starter category, optionally with one level of children. Kept short and
 * common-denominator on purpose -- this is a starting point the user edits later from
 * دسته‌بندی‌ها, not a complete taxonomy. */
data class SuggestedCategory(
    val name: String,
    val kind: CategoryKind,
    val children: List<String> = emptyList(),
    val recommended: Boolean = true
)

val ONBOARDING_CATEGORY_SUGGESTIONS = listOf(
    SuggestedCategory("خواروبار", CategoryKind.EXPENSE, listOf("سوپرمارکت", "میوه و تره‌بار", "نانوایی")),
    SuggestedCategory("حمل و نقل", CategoryKind.EXPENSE, listOf("بنزین", "تاکسی و اسنپ")),
    SuggestedCategory("قبض و خدمات", CategoryKind.EXPENSE, listOf("برق", "آب", "گاز", "اینترنت و تلفن")),
    SuggestedCategory("رستوران و کافه", CategoryKind.EXPENSE),
    SuggestedCategory("قسط و وام", CategoryKind.EXPENSE, recommended = false),
    SuggestedCategory("پوشاک", CategoryKind.EXPENSE, recommended = false),
    SuggestedCategory("سلامت و درمان", CategoryKind.EXPENSE, recommended = false),
    SuggestedCategory("تفریح و سرگرمی", CategoryKind.EXPENSE, recommended = false),
    SuggestedCategory("حقوق", CategoryKind.INCOME),
    SuggestedCategory("درآمد آزاد", CategoryKind.INCOME, recommended = false),
)

private data class TourTab(val title: String, val description: String)
private val TOUR_TABS = listOf(
    TourTab("تراکنش‌ها", "لیست همه چیزهایی که از پیامک بانکی خونده شده. روی هرکدوم بزنید تا دسته‌بندیش کنید."),
    TourTab("طرف‌حساب‌ها", "بدهی و طلب با دوستان، همکاران یا مشتری‌ها رو اینجا جدا پیگیری می‌کنید."),
    TourTab("چک‌ها", "چک‌های دریافتی و پرداختی، با یادآوری قبل از سررسید."),
    TourTab("آمار", "جمع درآمد/هزینه، تفکیک بر اساس بانک و دسته، در یک نگاه."),
    TourTab("تنظیمات", "دسته‌بندی‌ها، بکاپ، و همه چیزهایی که فقط یک‌بار لازمه تنظیم کنید.")
)

@Composable
fun OnboardingFlow(
    onSetSmallAmount: (enabled: Boolean, threshold: Long) -> Unit,
    onCreateCategories: suspend (List<SuggestedCategory>) -> Unit,
    onFinish: (scanDays: Int?) -> Unit
) {
    var step by remember { mutableStateOf(0) }
    val totalSteps = 5
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            Column(Modifier.padding(16.dp)) {
                LinearProgressIndicator(
                    progress = { (step + 1) / totalSteps.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (step) {
                0 -> WelcomeStep(onNext = { step = 1 })
                1 -> TourStep(onNext = { step = 2 }, onBack = { step = 0 })
                2 -> SmallAmountStep(
                    onNext = { enabled, threshold ->
                        onSetSmallAmount(enabled, threshold)
                        step = 3
                    },
                    onBack = { step = 1 }
                )
                3 -> CategoryBuilderStep(
                    onNext = { chosen ->
                        scope.launch { onCreateCategories(chosen) }
                        step = 4
                    },
                    onBack = { step = 2 }
                )
                4 -> ScanRangeStep(onFinish = onFinish, onBack = { step = 3 })
            }
        }
    }
}

@Composable
private fun StepScaffold(
    title: String,
    onNext: () -> Unit,
    onBack: (() -> Unit)? = null,
    nextLabel: String = "بعدی",
    nextEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { content() }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onBack != null) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("قبلی") }
            }
            Button(onClick = onNext, enabled = nextEnabled, modifier = Modifier.weight(2f)) { Text(nextLabel) }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    StepScaffold(title = "به SmsFinance خوش اومدید 👋", onNext = onNext, nextLabel = "بریم") {
        Text(
            "این برنامه پیامک‌های بانکی گوشیتون رو هنگام بازشدن بررسی می‌کنه و به دفتر مالی تبدیل می‌کنه — " +
                "دیگه لازم نیست همه خرج‌هاتون رو دستی بنویسید.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))
        listOf(
            "همه‌چیز روی خودِ گوشی شما پردازش می‌شه و پیامک‌ها به سرور فرستاده نمی‌شن.",
            "برنامه فقط پیشنهاد می‌ده — هیچ تصمیمی (حذف، ادغام) بدون تأیید شما انجام نمی‌شه.",
            "اگر اجازه پیامک را ندهید، برنامه هنوز برای ثبت دستی و گزارش‌گیری قابل استفاده است."
        ).forEach { line ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
                Text("•  ", style = MaterialTheme.typography.bodyLarge)
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "توی چند صفحه بعدی، سریع باهم برنامه رو راه می‌ندازیم. کمتر از دو دقیقه طول می‌کشه.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TourStep(onNext: () -> Unit, onBack: () -> Unit) {
    StepScaffold(title = "پنج بخش اصلی برنامه", onNext = onNext, onBack = onBack) {
        TOUR_TABS.forEach { tab ->
            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(tab.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(tab.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "این توضیحات همیشه توی «تنظیمات → راهنمای استفاده» در دسترسه، اگه یادتون رفت.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SmallAmountStep(onNext: (enabled: Boolean, threshold: Long) -> Unit, onBack: () -> Unit) {
    var enabled by remember { mutableStateOf(true) }
    var thresholdText by remember { mutableStateOf("20000") }

    StepScaffold(
        title = "خرج‌های خیلی کوچیک",
        onNext = { onNext(enabled, thresholdText.toPositiveLongOrNull() ?: 20000L) },
        onBack = onBack
    ) {
        Text(
            "خیلی از پیامک‌های بانکی، خرج‌های ریز و پرتکرارن (پارکینگ، اتوبوس، ...) که دسته‌بندی " +
                "دستی‌شون وقت‌گیره. می‌تونید بگید هر مبلغی زیر یه سقف مشخص، خودکار به‌عنوان «خرج جزئی» " +
                "علامت بخوره، بدون این‌که هر بار ازتون بپرسه.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = enabled, onCheckedChange = { enabled = it })
            Spacer(Modifier.width(8.dp))
            Text("فعال باشه")
        }
        if (enabled) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = thresholdText,
                onValueChange = { input -> if (input.normalizeDigits().all { it.isDigit() || it == ',' || it == '٬' || it == ' ' }) thresholdText = input },
                label = { Text("سقف مبلغ (تومان)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "پیشنهاد پیش‌فرض ۲۰,۰۰۰ تومانه؛ هر وقت خواستید از تنظیمات عوضش کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryBuilderStep(onNext: (List<SuggestedCategory>) -> Unit, onBack: () -> Unit) {
    val selected = remember {
        mutableStateMapOf<String, Boolean>().apply {
            ONBOARDING_CATEGORY_SUGGESTIONS.forEach { put(it.name, it.recommended) }
        }
    }

    StepScaffold(
        title = "دسته‌بندی‌های شروع",
        onNext = { onNext(ONBOARDING_CATEGORY_SUGGESTIONS.filter { selected[it.name] == true }) },
        onBack = onBack,
        nextLabel = "ساخت و ادامه"
    ) {
        Text(
            "چندتا دسته رایج رو براتون آماده کردیم — هرکدوم رو می‌خواید نگه دارید، بقیه رو بردارید. " +
                "بعداً هم از «تنظیمات → مدیریت دسته‌بندی‌ها» می‌تونید هر تعداد دسته و زیردسته دلخواه اضافه کنید.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        ONBOARDING_CATEGORY_SUGGESTIONS.forEach { suggestion ->
            val isChecked = selected[suggestion.name] == true
            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = { selected[suggestion.name] = !isChecked }) {
                            Icon(
                                if (isChecked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                        Column {
                            Text(suggestion.name, style = MaterialTheme.typography.titleSmall)
                            if (suggestion.children.isNotEmpty()) {
                                Text(
                                    "زیردسته: " + suggestion.children.joinToString("، "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanRangeStep(onFinish: (Int?) -> Unit, onBack: () -> Unit) {
    StepScaffold(
        title = "آخرین قدم: بررسی پیامک‌های قدیمی",
        onNext = { onFinish(30) },
        onBack = onBack,
        nextLabel = "همینو انتخاب کن"
    ) {
        Text(
            "چند وقت گذشته بررسی بشه؟ هر وقت خواستید از تنظیمات، بازه‌ی دیگه‌ای رو دوباره اسکن کنید.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))
        SCAN_RANGE_OPTIONS.forEach { option ->
            OutlinedButton(onClick = { onFinish(option.days) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(option.label)
            }
        }
    }
}
