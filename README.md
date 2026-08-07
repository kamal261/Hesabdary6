# SmsFinance — راهنمای فنی و نصب

این فایل فقط برای **نصب، ساخت پروژه، ساختار کد، و نکات فنی توسعه** است. برای معرفی محصول، فلسفه طراحی، و شرح کامل قابلیت‌ها به `SmsFinance-Report.md` مراجعه کنید (تا مستندات دوباره‌کاری/هم‌پوشانی نداشته باشند).

## پشته فنی
- Kotlin + Jetpack Compose (Material 3)
- Coroutines + Flow
- Room (SQLite)، نسخه فعلی schema: **۴**
- DataStore Preferences
- OkHttp (فقط برای Google Sheets/Drive اختیاری)
- Google Sign-In (`play-services-auth`) فقط برای بکاپ Drive
- minSdk 26 / targetSdk 34

## نحوه ساخت پروژه

### روش ۱: کاملاً آنلاین، بدون نصب هیچی (GitHub Actions)
پروژه از قبل شامل آیکون پیش‌فرض و `gradlew` است، پس این روش نیازی به Android Studio ندارد:
1. یک ریپازیتوری جدید در [github.com](https://github.com) بساز (رایگان).
2. کل پوشه `SmsFinance` رو در همون ریپازیتوری آپلود/push کن (از طریق دکمه "Add file → Upload files" در وب‌سایت گیت‌هاب هم ممکنه، بدون نیاز به Git خط‌فرمان).
3. برو به تب **Actions** بالای صفحه ریپازیتوری. یک workflow به اسم «Build APK» به‌صورت خودکار اجرا می‌شه (یا اگه اجرا نشد، دستی از همون تب روی «Run workflow» بزن).
4. بعد از حدود ۵-۱۰ دقیقه، پایین صفحه همون اجرا (run) بخش **Artifacts** رو باز کن و فایل `SmsFinance-debug-apk` رو دانلود کن — این همون APK قابل‌نصبه.
5. فایل رو روی گوشی کپی کن و نصب کن (نیاز به فعال‌کردن «نصب از منابع ناشناس» داره).

این فایل APK امضای دیباگ داره (برای تست/استفاده شخصی کافیه)، نه امضای انتشار رسمی؛ برای انتشار در کافه‌بازار/Google Play باید روش ۲ (زیر) رو دنبال کنی.

### روش ۲: با Android Studio (برای توسعه/دیباگ/انتشار رسمی)
1. Android Studio (نسخه Koala یا جدیدتر) → **Open** → پوشه `SmsFinance`.
2. صبر برای Gradle sync.
3. یک آیکون پیش‌فرض ساده از قبل توی پروژه هست، پس build بدون مشکل انجام می‌شه؛ اگه خواستی آیکون اختصاصی بذاری، از `File → New → Image Asset` استفاده کن.
4. برای فعال‌سازی بکاپ Google Drive (اختیاری):
   - در Google Cloud Console یک OAuth Client ID از نوع Android بسازید (package name `com.kamal.smsfinance` + SHA-1 امضای اپ).
   - Drive API را در همان پروژه فعال کنید. نیازی به client secret یا سرور نیست.
5. Run روی دستگاه/شبیه‌ساز.

## ساختار پروژه
```
app/src/main/java/com/kamal/smsfinance/
├── MainActivity.kt              # نقطه ورود + ناوبری ۵ تبی + صفحات overlay
├── SmsFinanceApp.kt             # Application: DB + Repository (بدون notification channel)
├── data/
│   ├── Transaction.kt / Category.kt / Counterparty.kt / Check.kt / SmartRule.kt
│   ├── *Dao.kt                  # کوئری‌های Room هر Entity
│   ├── AppDatabase.kt           # نسخه ۴ + seed دسته‌های پیش‌فرض
│   ├── TransactionRepository.kt # لایه دامنه (تمام Entityها + dedup + rule matching)
│   ├── RecurringDetector.kt     # تابع خالص، بدون وابستگی DB
│   └── RuleEngine.kt            # تابع خالص، بدون وابستگی DB
├── sms/
│   ├── SmsParser.kt             # هسته پردازش regex پیامک بانکی
│   ├── SmsReaderUtil.kt
│   └── SmsReceiver.kt           # ثبت بی‌صدا، بدون نوتیفیکیشن
├── ui/
│   ├── TransactionViewModel.kt
│   ├── screens/                 # لیست، افزودن دستی، دسته‌ها، طرف‌حساب، چک، قوانین، آمار، تنظیمات
│   └── theme/
├── permission/SmsPermissionGate.kt
└── util/
    ├── CsvExporter.kt / BackupManager.kt / GoogleSheetsUploader.kt
    └── GoogleSignInHelper.kt / GoogleDriveUploader.kt / SettingsStore.kt
```

## موتور تشخیص پیامک (`SmsParser.kt`)
1. **شناسایی بانک**: از روی شماره فرستنده (short-code) یا نام آن. الگوهای اولیه برای بیش از ۱۵ بانک ایرانی پیاده‌سازی شده‌اند و با تغییر قالب پیامک بانک‌ها ممکن است نیاز به به‌روزرسانی داشته باشند — این یک لیست ثابت و همیشگی‌تضمین‌شده نیست، صرفاً پوشش اولیه‌ای است که باید با گزارش موارد واقعی گسترش پیدا کند.
2. **شناسایی نوع تراکنش** با جدول کلیدواژه (خرید/برداشت/قسط/کارمزد → هزینه؛ واریز/دریافت/بازگشت وجه → درآمد). پیامک‌های تبلیغاتی/رمز یکبارمصرف/موجودی نادیده گرفته می‌شوند.
3. **استخراج مبلغ**: اعداد فارسی/لاتین، جداکننده هزارگان، تومان/ریال (تبدیل خودکار).

برای افزودن بانک جدید یا اصلاح الگو، map مربوطه در `SmsParser.BANK_SENDERS` یا لیست کلیدواژه‌ها را گسترش دهید.

## سیاست جلوگیری از ثبت تکراری (Dedup)
دو لایه، به ترتیب اولویت:

1. **دقیق (`existsExact`)**: اگر `(smsSender, rawSms, date)` دقیقاً با یک ردیف موجود یکسان باشد، پیامک نادیده گرفته می‌شود. این تنها معیاری است که همیشه و بدون استثنا درست کار می‌کند.
2. **پشتیبان (`existsSimilar`)**: فقط وقتی پیامک شامل چهار رقم آخر شماره کارت/حساب (`accountTail`) باشد اجرا می‌شود — اگر یک تراکنش با همان `accountTail` + `amountToman` + `type` در بازه ±۱۰ دقیقه از زمان پیامک جدید موجود باشد، آن هم رد می‌شود. این لایه برای زمانی است که بانک short-code فرستنده را عوض کند (که لایه اول را دور می‌زند)؛ عمداً محدود به وقتی نگه داشته شده که `accountTail` مشخص است، تا هیچ‌وقت دو تراکنش واقعاً متفاوت را با هم ادغام نکند.

اگر متن پیامک یک بانک به‌طور جزئی تغییر کند (مثلاً یک کلمه اضافه/کم شود) ولی زمان و فرستنده همان باشد، لایه اول همچنان به‌درستی آن را به‌عنوان تکراری تشخیص نمی‌دهد چون `rawSms` دقیقاً match نمی‌شود — این یک محدودیت شناخته‌شده است (نه یک باگ): فرض بر این است که یک تراکنش واقعی، در عمل، در یک بازه کوتاه زمانی فقط یک‌بار پیامک می‌فرستد.

## اتصال اختیاری به Google Sheets
یک Google Apps Script کوچک روی شیت خودتان Deploy کنید:

```javascript
function doPost(e) {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
  var rows = JSON.parse(e.postData.contents);
  rows.forEach(function (r) {
    sheet.appendRow([r.id, r.date, r.bank, r.type, r.amount, r.description]);
  });
  return ContentService.createTextOutput("OK");
}
```
آدرس `.../exec` را در تب «تنظیمات» اپ وارد کنید. این اتصال **append-only** است (بدون update/delete sync) — جزئیات و دلیل تصمیم در doc-comment بالای `GoogleSheetsUploader.kt`.

## سیاست سازگاری بکاپ محلی (JSON)
`BackupManager` یک عدد `version` مستقل از نسخه schema خود Room نگه می‌دارد (الان: ۳). هر فیلد/جدول با `root.has(...)` خوانده می‌شود، پس یک بکاپ قدیمی‌تر که یک جدول جدید (مثلاً `smartRules`) را ندارد، بدون خطا فقط آن بخش را خالی برمی‌گرداند؛ نیازی به migration ladder رسمی نیست چون فایل بکاپ همیشه محلی و تک‌کاربره است.

**نکته مهم بازیابی**: چون ردیف‌های Transaction/Check به `categoryId`/`counterpartyId`/`settledTransactionId` بر اساس id اصلی (auto-generate) ارجاع می‌دهند، بازیابی روی یک دیتابیس *غیرخالی* می‌تواند این ارتباط‌ها را به‌هم بریزد. برای بازیابی تمیز، ابتدا «حذف تمام تراکنش‌ها» را بزنید.

## محدودیت‌های فنی شناخته‌شده
- `RecurringDetector` روی (بانک، نوع) گروه‌بندی می‌کند و مبالغ را تا ۱۰٪ اختلاف «همان پرداخت» در نظر می‌گیرد (برای اجاره/حقوق متغیر)؛ این یک آستانه ثابت است، نه یادگیری تطبیقی.
- `RuleEngine` فقط روی پیامک‌های **جدید** اعمال می‌شود، نه تراکنش‌های قبلاً ثبت‌شده (تصمیم آگاهانه برای سادگی مسیر نوشتن داده).
- هیچ fallback heuristic برای وقتی که regex اصلی match نمی‌شود وجود ندارد؛ طبق YAGNI، این قابلیت تا مشاهده یک مورد واقعی شکست‌خورده اضافه نمی‌شود — بانک ناشناخته با کلیدواژه/مبلغ واضح، با برچسب «نامشخص» ثبت می‌شود (نه رد کامل).
- `Transaction.date` فقط زمان پیامک را نگه می‌دارد؛ هیچ فیلد جدای «تاریخ ثبت در برنامه» وجود ندارد. اگر پیامک با تأخیر برسد، تاریخ واقعی رویداد ممکن است کمی متفاوت باشد — این هنوز یک مشکل گزارش‌شده واقعی نیست.
