// SmsFinance file version: 1 — persistent reference guide (same content as the onboarding
// tour), accessible any time from Settings for a user who forgets how a section works. Static
// content on purpose -- no state, no network, matches "بدون جعبه سیاه" (everything explainable).
package com.kamal.smsfinance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class HelpSection(val title: String, val body: String)

private val HELP_SECTIONS = listOf(
    HelpSection(
        "برنامه چطور کار می‌کنه؟",
        "وقتی برنامه رو باز می‌کنید، پیامک‌های جدید بانکی از صندوق پیامک بررسی و به تراکنش تبدیل می‌شن. " +
            "در حالت عادی همه‌چیز روی خودِ گوشی پردازش می‌شه و پیامک‌ها به سرور فرستاده نمی‌شن. برنامه دائماً در پس‌زمینه اجرا نمی‌شه و " +
            "هنگام بازکردن یا برگشتن به برنامه بررسی می‌کنه. در اولین اجرا، نتیجه اسکن مثل تعداد پیامک‌ها و تراکنش‌های تازه نشان داده می‌شه. " +
            "اگر اجازه پیامک رو نداده باشید، ثبت دستی و گزارش‌ها همچنان کار می‌کنن."
    ),
    HelpSection(
        "تراکنش‌ها",
        "لیست کامل تراکنش‌های شناسایی‌شده. برای پیامک برداشت، برنامه به‌صورت خودکار فقط دسته‌های هزینه را نشان می‌دهد؛ " +
            "برای پیامک واریز هم فقط دسته‌های درآمد نمایش داده می‌شود. اگر نوع تراکنش اشتباه بود، از گزینه «تغییر نوع» " +
            "در صفحه جزئیات تراکنش استفاده کنید. بعد از انتخاب دسته، گزینه افزودن یادداشت در انتهای همین بخش قرار داره. " +
            "یادداشت همراه همان تراکنش روی گوشی ذخیره می‌شه؛ برای پیدا کردن دوباره، از جستجو در متن یادداشت " +
            "یا فیلتر «فقط یادداشت‌دار» در صفحه تراکنش‌ها استفاده کنید. از صندوق بررسی صفحه اصلی هم می‌توانید وارد صفحه «همه یادداشت‌ها» شوید. " +
            "روی کارت هر تراکنش، مسیر کامل دسته در بخش «دسته فعلی» نمایش داده می‌شود."
    ),
    HelpSection(
        "طلب و بدهی",
        "برای پیگیری بدهی و طلب با اشخاص (نه بانک). وقتی یک پرداخت هیچ‌وقت در پیامک بانکی ظاهر نمی‌شود " +
            "(مثلاً یکی نقدی بهتون پول داده)، می‌تونید دستی به‌عنوان «تسویه غیرمستقیم» ثبتش کنید."
    ),
    HelpSection(
        "چک‌ها",
        "چک‌های دریافتی و پرداختی رو با تاریخ سررسید ثبت کنید تا قبل از موعد بهتون یادآوری بشه."
    ),
    HelpSection(
        "دخل و خرج",
        "جمع درآمد و هزینه و تفکیک بر اساس بانک و دسته. عدد «سود تقریبی» عمداً وصول طلب و پرداخت " +
            "بدهی رو حساب نمی‌کنه، چون این‌ها جابه‌جایی پولن، نه سود واقعی."
    ),
    HelpSection(
        "پیامک‌های شناسایی‌نشده",
        "پیامک‌هایی که شبیه بانکی به‌نظر می‌رسیدن ولی برنامه کامل نتونست تشخیص بده. هیچ‌وقت بی‌سروصدا " +
            "دور انداخته نمی‌شن -- هر پیام رو می‌بینید و یا دستی ثبت می‌کنید یا نادیده می‌گیرید."
    ),
    HelpSection(
        "دسته‌بندی خودکار مبالغ کوچک",
        "از تنظیمات قابل‌فعال‌سازیه: هر تراکنش زیر یه سقف مشخص، بدون این‌که هر بار بپرسه، خودکار " +
            "علامت می‌خوره."
    ),
    HelpSection(
        "دسته‌بندی‌ها و زیردسته‌ها",
        "از تنظیمات → مدیریت دسته‌بندی‌ها، هم دسته جدید می‌سازید هم می‌تونید یه دسته رو زیرمجموعه‌ی " +
            "یه دسته دیگه کنید (مثلاً «سوپرمارکت» زیر «خواروبار»)."
    ),
    HelpSection(
        "بکاپ و بازیابی",
        "از تنظیمات، هم بکاپ محلی JSON می‌گیرید هم می‌تونید به Google Drive وصل بشید. برنامه زمان " +
            "آخرین بکاپ محلی را نشان می‌دهد و اگر مدت زیادی گذشته باشد یادآوری می‌کند. بازیابی همیشه " +
            "قبلش یه تأییدیه صریح می‌خواد."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("راهنمای استفاده") },
                navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(HELP_SECTIONS) { section ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(section.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
