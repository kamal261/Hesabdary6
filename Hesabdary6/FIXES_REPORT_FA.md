# گزارش اصلاحات نسخه Hesabdary6

## خلاصه

اصلاحات زیر روی clone فعلی شاخه `main` ریپوی `kamal261/Hesabdary6` اعمال شده است. تمرکز این مرحله روی خطاهای بحرانی گزارش قبلی بوده است: صحت مانده طرف‌حساب، جلوگیری از ترکیب ناسازگار نوع تراکنش و دسته، ایمنی restore، جلوگیری از حذف ناخواسته، وضعیت یادآوری چک، تاریخ شمسی، ورودی ارقام فارسی، dedup پیامک و حریم خصوصی backup سیستم.

این نسخه هنوز باید روی Android Studio دارای SDK 34 و یک دستگاه آزمایشی واقعی بررسی شود. در محیط بررسی، Gradle 8.11.1 شروع شد اما به‌دلیل نبود Android SDK، تست‌ها تا مرحله اجرای کد نرسیدند.

## تغییرات اصلی

| حوزه | اصلاح انجام‌شده | نتیجه |
|---|---|---|
| مانده طرف‌حساب | لیست طرف‌حساب‌ها در `MainActivity` اکنون از تابع دامنه مشترک `counterpartyBalance` استفاده می‌کند. | عدد لیست، داشبورد و پروفایل از یک منطق استفاده می‌کنند. |
| سازگاری مالی | تابع `CategoryKind.isCompatibleWith` اضافه شد و در CategoryPicker، ثبت دستی، ویرایش دسته، Repository و parser اعمال شد. | دسته «وصول طلب» برای برداشت و «پرداخت بدهی» برای واریز قابل انتخاب/ثبت نیست. |
| restore | پاک‌سازی replacement restore داخل همان `db.withTransaction` قرار گرفت و wipe جداگانه از ViewModel حذف شد. | فایل خراب یا رکورد نامعتبر نباید دیتابیس قبلی را نیمه‌کاره پاک کند. |
| ارجاع‌های backup | شناسه‌های دسته، طرف‌حساب، چک و قانون که در backup وجود ندارند دیگر بی‌صدا null نمی‌شوند و restore را متوقف می‌کنند. | ارتباط‌های مالی پنهانی از بین نمی‌روند. |
| migration | `fallbackToDestructiveMigration()` حذف شد. | برنامه دیگر دیتابیس مالی را بی‌صدا پاک نمی‌کند؛ schemaهای قدیمی‌تر از مسیر migration واقعی نیاز به بررسی دارند. |
| حذف تراکنش | پیش از حذف، دیالوگ تأیید با شرح و مبلغ نمایش داده می‌شود. | حذف با لمس اشتباه دشوارتر و قابل فهم‌تر شده است. |
| یادآوری چک | query از `reminderDays` واقعی هر چک استفاده می‌کند و چک‌های عقب‌افتاده را نیز برمی‌گرداند. وضعیت‌های امروز، نزدیک سررسید و عقب‌افتاده در UI نمایش داده می‌شوند. | انتخاب یادآوری هر چک اثر واقعی دارد. |
| وضعیت چک | تغییر چک تسویه‌شده یا برگشتی با object قدیمی مسدود می‌شود. | برگشتی کردن چک تسویه‌شده و ناسازگاری با تراکنش تسویه جلوگیری می‌شود. |
| تاریخ | نمایش تراکنش، چک، پروفایل طرف‌حساب، پیامک ناشناخته و CSV از `JalaliDate` استفاده می‌کند. | تفاوت تاریخ میلادی و شمسی در مسیرهای اصلی کاهش می‌یابد. |
| ارقام | ابزار `toAsciiDigits` برای تبدیل ارقام فارسی و عربی به لاتین اضافه شد و در فرم مبلغ ثبت دستی استفاده شد. | ورودی «۵۰۰۰۰» مانند «50000» قابل پردازش است. |
| dedup | ثبت/بررسی dedup هم‌زمان synchronized شد و fingerprint از hash کوتاه به SHA-256 کامل تغییر کرد. | race condition و احتمال برخورد hash کاهش می‌یابد. |
| حریم خصوصی | قواعد `backup_rules.xml` و `data_extraction_rules.xml` اضافه و به Manifest متصل شد. | دیتابیس، فایل‌ها و shared preferences وارد Android Auto Backup یا Device Transfer نمی‌شوند؛ backup صریح داخل برنامه باقی می‌ماند. |
| تست دامنه | تست‌های واحد برای مانده، سازگاری دسته و چهار وضعیت یادآوری چک اضافه شد. | منطق اصلی قابل آزمون مستقل است. |

## فایل‌های مهم تغییرکرده

تغییرات اصلی در این فایل‌ها اعمال شده‌اند: `MainActivity.kt`، `AppDatabase.kt`، `Category.kt`، `Check.kt`، `CheckDao.kt`، `TransactionRepository.kt`، `DedupEngine.kt`، `TransactionViewModel.kt`، `CategoryPicker.kt`، `AddTransactionScreen.kt`، `ChecksScreen.kt`، `CounterpartyProfileScreen.kt`، `TransactionListScreen.kt`، `UnidentifiedSmsScreen.kt`، `BackupManager.kt`، `CsvExporter.kt` و `UnidentifiedSmsExporter.kt`. دو فایل XML مربوط به backup، ابزار `NumberInput.kt` و تست `FinancialDomainTest.kt` نیز اضافه شده‌اند.

## نتیجه build و تست

دستور زیر اجرا شد:

```bash
./gradlew test --stacktrace
```

Gradle Wrapper با نسخه 8.11.1 اجرا شد، اما build به‌دلیل نبود Android SDK متوقف شد:

```text
SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable
```

بنابراین در این محیط نمی‌توان ادعا کرد که APK نهایی build شده است. پس از دریافت فایل، روی محیط Android Studio این دستورات را اجرا کنید:

```bash
chmod +x gradlew
./gradlew test
./gradlew assembleDebug
```

سپس روی دستگاه یا emulator نیز تست کنید:

```bash
./gradlew connectedCheck
```

## نکته مهم migration

حذف `fallbackToDestructiveMigration()` تصمیم ایمنی است، اما به‌معنای حل شدن migration نسخه‌های بسیار قدیمی نیست. اگر دستگاهی با schemaهای قدیمی‌تر از migrationهای موجود برنامه داشته باشد، برنامه باید به‌جای پاک کردن اطلاعات با خطای migration متوقف شود. برای انتشار عمومی، schema نسخه‌های قبلی باید از APKهای قدیمی استخراج و migration واقعی و آزموده برای آن‌ها اضافه شود.

## محدودیت‌های باقی‌مانده

این مرحله هنوز انتقال بین حساب‌های خود کاربر را به‌عنوان رویداد مستقل مدل نکرده است. همچنین پردازش پیامک زنده هنوز در `BroadcastReceiver` از coroutine کوتاه‌عمر استفاده می‌کند و برای دوام کامل، retry و صف پایدار بهتر است به WorkManager یا جدول inbox منتقل شود. CSV نیز هنوز خروجی کامل روابط حسابداری نیست و parser باید با نمونه‌های واقعی و ناشناس‌سازی‌شده بانک‌ها تست گسترده شود.

## استفاده از فایل‌ها

فایل `Hesabdary6-fixed.zip` یک snapshot از سورس اصلاح‌شده است. فایل `Hesabdary6-fixes.patch` فقط تغییرات را به‌صورت patch ارائه می‌کند. برای اعمال patch روی clone تمیز همان commit، از دستور زیر استفاده کنید:

```bash
git apply Hesabdary6-fixes.patch
```

قبل از merge یا انتشار، ابتدا test و assembleDebug را روی محیطی با Android SDK 34 اجرا کنید و سپس سناریوهای restore، حذف تراکنش، ثبت دسته، تسویه چک و ورودی ارقام فارسی را دستی بررسی کنید.

## اعتبارسنجی deliverable

Patch با نام `Hesabdary6-fixes.patch` روی یک clone تمیز از همان ریپو با موفقیت از مراحل `git apply --check` و `git apply` عبور کرد. در مجموع ۲۳ فایل تغییر یا اضافه شده‌اند و `git diff --check` نیز بدون خطا است. بسته ZIP نیز با `unzip -t` بررسی شد و فایل‌های موقتی مانند `.gradle`، `local.properties` و خروجی `app/build` در آن قرار نگرفته‌اند.
