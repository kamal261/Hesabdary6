# گزارش تغییرات نهایی SmsFinance

## مشخصات نسخه

| عنوان | مقدار |
|---|---|
| نام محصول | Hesabdary6 / SmsFinance |
| نسخه | `v0.5.0` |
| کد نسخه | `6` |
| تاریخ میلادی | ۲۰۲۶/۰۸/۱۶ |
| تاریخ شمسی | ۱۴۰۵/۰۵/۲۵ |
| شاخه مبنا | `manus-version` |
| توسعه‌دهنده و طراح | سید کمال حقیقی |
| وضعیت تحویل | ZIP سورس؛ بدون APK و بدون انتشار در GitHub |

## خلاصه

این نسخه بر اساس گزارش نهایی یکپارچه فنی و کاربری و گزارش اصلاح‌شده پیشنهادهای کاربر ساخته شد. تمرکز تغییرات بر حفاظت از داده، قابل‌مشاهده‌کردن وضعیت اسکن، امکان استفاده بدون مجوز SMS، کاهش سردرگمی کاربر، ساده‌سازی معماری دریافت پیامک، نمایش اقدام بعدی برای چک و طرف‌حساب و افزودن ویجت محلی بود.

اصلاحات مالی نسخه قبلی نیز حفظ شده‌اند: گارد اتمیک گروه انتقال در Repository، گارد اتصال تراکنش به چک، remap امن restore، پیشنهاد review-only برای چک تسویه‌شده، تحلیل الگوی exact/tolerant، migration schema ۹ و بهینه‌سازی `remember`.

## ۱. تغییر مدل دریافت پیامک

### وضعیت قبلی

برنامه از `SMS_RECEIVED` و receiverهای پس‌زمینه برای واردکردن لحظه‌ای پیامک استفاده می‌کرد و مجوزهای `READ_SMS` و `RECEIVE_SMS` را درخواست می‌کرد. این مدل در برخی گوشی‌ها ممکن بود به‌دلیل مدیریت تهاجمی برنامه‌های پس‌زمینه، بدون علامت واضح متوقف شود.

### تغییر انجام‌شده

receiverهای پس‌زمینه SMS/MMS حذف شدند و مجوز `RECEIVE_SMS` نیز از Manifest حذف شد. اکنون مسیر اصلی چنین است:

1. برنامه هنگام بازشدن و رویداد `ON_RESUME` اسکن افزایشی انجام می‌دهد.
2. فقط پیامک‌های بعد از cursor ذخیره‌شده بررسی می‌شوند.
3. dedup موجود از ایجاد تراکنش تکراری جلوگیری می‌کند.
4. اسکن دستی صفحه اصلی همچنان فعال است.
5. اسکن تاریخی از تنظیمات انجام می‌شود و cursor روزانه را جابه‌جا نمی‌کند.

برای جلوگیری از اجرای چند اسکن هم‌زمان، `TransactionViewModel` یک cooldown یک‌دقیقه‌ای و guard وضعیت loading دارد.

### فایل‌های اصلی

```text
app/src/main/AndroidManifest.xml
app/src/main/java/com/kamal/smsfinance/ui/TransactionViewModel.kt
app/src/main/java/com/kamal/smsfinance/MainActivity.kt
app/src/main/java/com/kamal/smsfinance/data/TransactionRepository.kt
app/src/main/java/com/kamal/smsfinance/sms/SmsReceiver.kt              حذف شد
app/src/main/java/com/kamal/smsfinance/sms/SmsDeliverReceiver.kt       حذف شد
app/src/main/java/com/kamal/smsfinance/sms/SmsSentReceiver.kt          حذف شد
app/src/main/java/com/kamal/smsfinance/sms/MmsReceiver.kt              حذف شد
```

## ۲. حالت استفاده بدون مجوز SMS

در نسخه قبلی اگر `READ_SMS` داده نمی‌شد، کل محتوای برنامه پشت صفحه مجوز متوقف می‌ماند. اکنون این رفتار اصلاح شده است.

کاربر می‌تواند یکی از دو مسیر را انتخاب کند:

| انتخاب کاربر | نتیجه |
|---|---|
| اجازه خواندن پیامک | اسکن هنگام بازشدن و اسکن دستی فعال می‌شود |
| ادامه بدون پیامک | ثبت دستی، چک‌ها، طرف‌حساب‌ها، گزارش‌ها و تنظیمات فعال می‌مانند |

در حالت بدون مجوز، دکمه اسکن غیرفعال و علت آن واضح است، اما کاربر در یک صفحه بن‌بست قرار نمی‌گیرد. از داخل بنر صفحه اصلی نیز می‌تواند دوباره درخواست مجوز کند.

متن مجوز توضیح می‌دهد که پردازش روی گوشی انجام می‌شود، پیامک به سرور ارسال نمی‌شود و هیچ تصمیم مالی بدون تأیید کاربر انجام نمی‌گیرد.

فایل اصلی:

```text
app/src/main/java/com/kamal/smsfinance/permission/SmsPermissionGate.kt
```

## ۳. یادآوری پشتیبان‌گیری

برای حفاظت از اطلاعات مالی، timestamp آخرین backup موفق در DataStore ثبت شد. وضعیت backup محلی از backup Google Drive جدا نگه داشته می‌شود.

رفتار جدید:

- بعد از ساخت موفق backup محلی، زمان آن ذخیره می‌شود.
- در صورت خطا، زمان قبلی تغییر نمی‌کند.
- اگر هنوز backup ساخته نشده باشد یا بیش از ۳۰ روز گذشته باشد، بنر هشدار نمایش داده می‌شود.
- کاربر می‌تواند هشدار را برای هفت روز به تعویق بیندازد.
- زمان آخرین backup در تنظیمات نیز نمایش داده می‌شود.
- سیاست هشدار در `BackupReminderPolicy` استخراج و با چهار تست خالص پوشش داده شده است.

فایل‌های اصلی:

```text
app/src/main/java/com/kamal/smsfinance/util/SettingsStore.kt
app/src/main/java/com/kamal/smsfinance/util/BackupReminderPolicy.kt
app/src/main/java/com/kamal/smsfinance/ui/TransactionViewModel.kt
app/src/main/java/com/kamal/smsfinance/ui/components/AttentionBanners.kt
app/src/main/java/com/kamal/smsfinance/ui/screens/SettingsScreen.kt
app/src/main/java/com/kamal/smsfinance/ui/screens/TransactionListScreen.kt
```

## ۴. صندوق موارد نیازمند بررسی

در بالای فهرست تراکنش‌ها، کارت «مواردی که باید بررسی شوند» اضافه شد. این کارت تعداد موارد مهم را جمع‌بندی می‌کند:

- تراکنش‌های بدون دسته؛
- پیامک‌های شناسایی‌نشده؛
- پیشنهادهای هوشمند؛
- چک‌های نزدیک سررسید.

کاربر برای پیامک ناشناس و چک نزدیک سررسید مسیر مستقیم دارد. پیشنهادهای هوشمند نیز در همان صفحه با توضیح، confidence و دکمه‌های تأیید و رد نمایش داده می‌شوند. متن کارت تأکید می‌کند که تصمیم نهایی با کاربر است.

## ۵. اقدام بعدی برای چک و طرف‌حساب

برای هر چک، متن «قدم بعدی» بر اساس وضعیت و فاصله تا سررسید اضافه شد:

| وضعیت | متن نمونه |
|---|---|
| چک تسویه‌شده | بررسی تراکنش مرتبط |
| چک برگشتی | پیگیری و ثبت یادداشت |
| چک نزدیک موعد | آماده‌شدن برای سررسید |
| چک دور از موعد | انتظار رسید یا پیامک |
| چک گذشته از موعد | بررسی وضعیت چک |

در پروفایل طرف‌حساب نیز کارت «قدم بعدی» اضافه شد. متن آن بر اساس یادآوری باز، طلب، بدهی یا نبودن تعهد فعال تغییر می‌کند. این بخش فقط راهنمای کاربر است و هیچ مبلغ یا رابطه مالی جدیدی ایجاد نمی‌کند.

فایل‌های اصلی:

```text
app/src/main/java/com/kamal/smsfinance/ui/screens/ChecksScreen.kt
app/src/main/java/com/kamal/smsfinance/ui/screens/CounterpartyProfileScreen.kt
```

## ۶. ویجت محلی صفحه اصلی

یک widget کوچک با `AppWidgetProvider` و `RemoteViews` اضافه شد. ویجت بدون worker دائمی و بدون شبکه، داده محلی زیر را نشان می‌دهد:

- ورودی امروز؛
- خروجی امروز؛
- زمان آخرین بررسی پیامک.

با کلیک روی widget، برنامه باز می‌شود. ویجت بعد از اسکن و با تغییر فهرست تراکنش‌ها به‌روزرسانی می‌شود. در متن توضیح widget روشن شده است که داده تا آخرین بررسی برنامه تازه است و لحظه‌ای نیست.

فایل‌های اصلی:

```text
app/src/main/java/com/kamal/smsfinance/widget/FinanceWidgetProvider.kt
app/src/main/res/layout/widget_finance.xml
app/src/main/res/xml/finance_widget_info.xml
app/src/main/res/values/strings.xml
app/src/main/AndroidManifest.xml
```

## ۷. همسان‌سازی onboarding و راهنما

متن onboarding و HelpScreen با معماری جدید هماهنگ شد. اکنون برنامه توضیح می‌دهد که پیامک‌ها هنگام بازشدن بررسی می‌شوند و در صورت ندادن مجوز، ثبت دستی و گزارش‌ها همچنان فعال هستند. بخش backup نیز وجود یادآوری زمان آخرین پشتیبان‌گیری را توضیح می‌دهد.

فایل‌ها:

```text
app/src/main/java/com/kamal/smsfinance/ui/components/OnboardingFlow.kt
app/src/main/java/com/kamal/smsfinance/ui/screens/HelpScreen.kt
```

## ۸. اصلاحات قبلی که حفظ شدند

قابلیت‌های زیر در نسخه جدید حذف یا تضعیف نشده‌اند:

| قابلیت | وضعیت |
|---|---|
| گارد گروه انتقال | اتمیک و در Repository |
| گارد اتصال چک | بررسی مبلغ، نوع و وضعیت |
| restore | remap شناسه‌های دسته، طرف‌حساب، چک و گروه انتقال |
| چک CLEARED + پیامک بعدی | `POSSIBLE_DUPLICATE_CHECK` و review-only |
| تحلیل الگو | exact و tolerant با تلورانس ۱۰٪ و cadence دوهفته‌ای |
| `remember` تب اصلی | حفظ شده برای جلوگیری از تحلیل تکراری |
| ایندکس‌های Room | `transactions.date` و `checks(status, dueDate)` |
| migration | schema ۸ به ۹، غیرمخرب |
| قوانین مالی | بدون حسابداری دوبل و بدون تصمیم خودکار |

## ۹. شماره نسخه و تاریخ

شماره و تاریخ نسخه در نقاط زیر ثبت شد:

```text
app/build.gradle.kts
app/src/main/java/com/kamal/smsfinance/BuildInfo.kt
README.md
PROJECT.md
```

مشخصات نسخه:

```text
versionName: 0.5.0
versionCode: 6
Gregorian: 2026-08-16
Jalali: 1405-05-25
Developer/Designer: سید کمال حقیقی
```

نام فایل ZIP نیز همین اطلاعات را دارد:

```text
Hesabdary6-manus-version-v0.5.0-2026-08-16.zip
```

## ۱۰. تست و اعتبارسنجی

| آزمون | نتیجه |
|---|---|
| `:app:testDebugUnitTest` | موفق |
| تعداد تست‌های واحد | ۷۶ |
| failure | صفر |
| error | صفر |
| تست سیاست backup reminder | ۴ تست جدید، همگی موفق |
| `:app:compileDebugAndroidTestKotlin` | موفق |
| migration instrumentation compile | در AndroidTest موجود و قابل کامپایل |
| اجرای واقعی instrumentation | انجام نشد؛ `adb` و emulator در محیط موجود نبودند |
| ساخت APK تحویلی | عمداً انجام نشد |
| انتشار GitHub | عمداً انجام نشد |
| `git diff --check` | بدون خطای whitespace |

## ۱۱. محدودیت‌های صادقانه

اجرای واقعی widget و AndroidTest به دستگاه یا emulator نیاز دارد. در محیط فعلی `adb` در دسترس نبود؛ بنابراین فقط compile کد AndroidTest و migration اعتبارسنجی شد. پیشنهاد می‌شود پس از انتقال ZIP به محیط توسعه، سناریوهای زیر روی چند گوشی واقعی آزمایش شوند: بازکردن برنامه با پیامک جدید، رد مجوز SMS، grant مجوز پس از استفاده دستی، راه‌اندازی مجدد گوشی، اسکن چندباره و widget روی launcher.

همچنین حذف receiver یعنی برنامه دیگر هنگام رسیدن پیامک در پس‌زمینه تراکنش را ثبت نمی‌کند؛ داده‌ها هنگام بازشدن برنامه یا اسکن دستی خوانده می‌شوند. این رفتار در README، HelpScreen و onboarding توضیح داده شده است.

## ۱۲. محتویات ZIP و موارد حذف‌شده

ZIP شامل سورس کامل، schemaهای Room، تست‌ها، مستندات پروژه، README به‌روز و همین گزارش است.

موارد زیر عمداً داخل ZIP قرار نمی‌گیرند:

- پوشه `.git`؛
- cache و `.gradle`؛
- `local.properties`؛
- خروجی‌های build؛
- APK؛
- logهای محیطی و فایل‌های موقت.

## نتیجه

نسخه `v0.5.0` پیشنهادهای اصلی گزارش اصلاح‌شده را به کد قابل اجرا تبدیل می‌کند: backup reminder، fallback بدون مجوز SMS، scan-on-resume، حذف receiverهای پس‌زمینه، صندوق بررسی، اقدام بعدی چک و طرف‌حساب، widget محلی و همسان‌سازی مستندات. هسته مالی و guardهای قبلی نیز حفظ شده‌اند.

این نسخه بدون GitHub و بدون APK تحویل می‌شود تا کاربر بتواند ZIP را در محیط خود باز کند، روی دستگاه آزمایشی نصب کند و آزمون عملی چنددستگاهی را انجام دهد.
