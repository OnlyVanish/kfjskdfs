# گزارش بازسازی سورس PinBom

مبنای کار: ریپازیتوری `aliaccuontemail-star/kklklklklklklklklklklk`، کامیت `a81869a7f7b1d94eb1191ffc119c0623688502cb`.

## چه چیزی بازسازی شد؟

مخزن اولیه فقط دو فایل `data/extras` را داشت؛ کلاس‌های اصلی مورد استفادهٔ UI، WorkManager و Hilt وجود نداشتند. این نسخه شامل پیاده‌سازی واقعی شبکه و ذخیره‌سازی است، نه دادهٔ نمایشی یا تابع‌های خالی:

- مدل‌های API برای پین، بورد، پروفایل، جستجو، نظر، اعلان، علاقه‌مندی و نشست دستگاه.
- `SessionManager`: نشست ماندگار در SharedPreferences خصوصی برنامه، استخراج نام نمایشی از claimهای `usr` و `username`، خروج و جریان ابطال نشست. claimها برای تعیین مجوز استفاده نمی‌شوند.
- `ApiClient`: OkHttp مشترک با Bearer token، پشتیبانی از لغو coroutine، timeout، تبدیل خطای HTTP به `HttpException` مورد انتظار UI، refresh یک‌بار و با Mutex روی 401. درخواست‌های login/register توکن قبلی را ارسال نمی‌کنند. redirect خودکار غیرفعال است.
- `PinBomRepository`: فراخوانی‌های شبکهٔ مورد استفادهٔ ViewModelها، آپلود multipart با پیشرفت واقعی و کش Room محدود به ۳۰۰ ردیف.
- `LocalDb` و `PinDao`، ذخیرهٔ جستجوهای اخیر، انتقال نام فایل‌های برند قدیمی و پاکسازی فایل‌های موقت.
- ورودی اشتراک‌گذاری: فقط URI با scheme محتوایی، بررسی MIME و معتبر بودن تصویر، محدودیت ۳۲ MiB و پاکسازی نسخهٔ ناقص.
- `ExtrasRepository` به همان کلاینت شبکه متصل شد تا خطاهای نشست و refresh دو رفتار متفاوت نداشته باشند.

## اصلاحات ضروری دیگر در سورس موجود

علاوه بر لایهٔ داده، سورس UI اولیه هم خطاهای کامپایل داشت. بدون حذف صفحه‌ها، importهای اشتباه `matchParentSize` و `weight` حذف شدند، importهای جاافتاده اضافه شدند، خواندن CompositionLocal بیرون از draw/effect منتقل شد، نوع formatter و AnimationSpec درست شد، تداخل نام اندازهٔ دکمه با اندازهٔ Canvas اصلاح شد، callbackها و وضعیت صفحه‌بندی ProfileWall از والد پاس داده شدند و فراخوانی gesture اصلاح شد. دو رشتهٔ انگلیسی apostrophe بدون escape داشتند و دو animator مقدار `sequential` به‌جای `sequentially` داشتند؛ این‌ها هم اصلاح شدند. قواعد wildcard نامعتبر baseline profile با ورودی‌های مشخص شروع برنامه جایگزین شدند؛ این profile دست‌نویس است و ادعای اندازه‌گیری کارایی ندارد.

## قرارداد API: چه چیزهایی قطعی و چه چیزهایی فرض هستند؟

**سورس بک‌اند و OpenAPI در مخزن وجود ندارند. از این محیط برای `http://185.26.33.188:8080`، درخواست‌های GET به health، feed، interests و openapi با timeout مواجه شدند. بنابراین سازگاری سرتاسری با سرور واقعی تأیید نشده است.**

- پیشوند `/v1/` از `ExtrasRepository` موجود و `ServerAddressTest` گرفته شده است؛ README تاریخی در مثالی `/api/v1/` دارد. این نسخه از قرارداد سورس اجرایی و تست‌ها یعنی `/v1/` پیروی می‌کند.
- الگوی صفحه‌بندی `items` و `next_cursor`، فیلدهای UI و بسیاری از ساختار DTOها از فراخوانی‌ها و PreviewData موجود به دست آمده‌اند.
- مسیرهای بخش extras مانند `users/{username}`، follow/block/report، ابزارهای نظر و بورد و تنظیمات اعلان از پیاده‌سازی اصلی موجود گرفته شده‌اند.
- مسیرهای لایهٔ اصلیِ حذف‌شده و شکل دقیق پاسخ آن‌ها استنباط شده‌اند. مهم‌ترین‌ها در جدول زیر آمده‌اند؛ برای تضمین عملکرد، باید با بک‌اند واقعی تطبیق داده شوند.

| قابلیت | قرارداد پیاده‌سازی‌شده، پس از `/v1/` |
|---|---|
| ورود / ثبت‌نام / refresh / خروج | POST `auth/login`, `auth/register`, `auth/refresh`, `auth/logout` |
| پاسخ ورود | `access_token`, `refresh_token`, پروفایل اختیاری `user` |
| فید | GET `feed/{kind}` با `cursor` و `limit` |
| جستجو | GET `search/pins`, `search/users`, `search/boards` با `q` |
| جزئیات / موارد مرتبط / نظر | GET `pins/{id}`, `pins/{id}/related`, `pins/{id}/comments` |
| پسند / ذخیره | POST و DELETE روی `pins/{id}/like` و `pins/{id}/save`؛ شناسهٔ بورد در JSON با `board_id` |
| ثبت نظر | POST `pins/{id}/comments` با `body` |
| فهرست‌های کاربر | GET `users/{username}/boards`, `/pins`, `/saved` |
| بررسی نشست / ویرایش / حذف حساب | GET، PATCH و DELETE `users/me` |
| بوردها | POST `boards`، GET/PATCH/DELETE `boards/{id}` |
| اعلان‌ها | GET `notifications`، POST `notifications/read`؛ شمارش خوانده‌نشده در `unread` |
| علاقه‌مندی | GET `interests`، GET/PUT `users/me/interests` با `ids` |
| نشست‌ها | GET `auth/sessions`، DELETE `auth/sessions/{id}` و `auth/sessions/others` |
| آپلود پین | POST `pins`، multipart شامل `file`, `title`, `description`, `tags`, `board_id` اختیاری |
| تصویر پروفایل | POST `users/me/avatar` یا `users/me/cover` با multipart `file` |

مدل جزئیات پین، پروفایل و بورد در ریشهٔ پاسخ JSON فرض شده است. اگر سرور envelopeهایی مانند `{"pin":...}` برمی‌گرداند، این قرارداد باید در decoder اصلاح شود. فیلد بورد خصوصی هر دو نام `private_board` و `private` را می‌پذیرد. لیست‌ها و فیلدهای حذف‌شده یا null تا حد امکان مقدار پیش‌فرض دارند.

## امنیت و محدودیت‌های این تحویل

- بنا بر انتخاب صاحب پروژه، امضای release با یک کلید RSA 3072 اختصاصی جدید است؛ نه کلید Android Debug.
- آدرس پیش‌فرض release: `http://185.26.33.188:8080/`. استثنای cleartext فقط برای IP `185.26.33.188` اضافه شده؛ سایر میزبان‌ها در release مجاز به HTTP نیستند.
- **HTTP رمزنگاری ندارد و گذرواژه و توکن را در برابر شنود شبکه محافظت نمی‌کند. این پیکربندی برای انتشار عمومی توصیه نمی‌شود؛ سرور باید HTTPS داشته باشد.**
- توکن‌ها در فضای خصوصی برنامه نگه‌داری می‌شوند، ولی رمزگذاری جداگانهٔ Android Keystore برای آن‌ها پیاده‌سازی نشده است. backup در manifest غیرفعال است.
- این APK با کلید جدید روی نصب دارای امضای متفاوت به‌شکل آپدیت نصب نمی‌شود. نصب موازی هم به‌خاطر applicationId یکسان ممکن نیست. برای حفظ داده‌ها باید کلید قبلی را در اختیار داشت؛ حذف نسخهٔ قبلی داده‌های محلی آن را پاک می‌کند.
- طرح Room بازسازی‌شده است و مهاجرت سازگار با طرح ناشناختهٔ DB نسخه‌های قدیمی ادعا نمی‌شود.
- Firebase فقط با تنظیمات چهارگانهٔ مخزن فعال می‌شود؛ در این تحویل تنظیم نشده است. کد polling موجود اعلان‌ها حفظ شده است.
- تست روی گوشی فیزیکی انجام نشده؛ نتایج JVM/Robolectric و بررسی امضا جداگانه در گزارش تحویل ثبت می‌شوند.

## بازتولید بیلد

نیازمندی‌ها: JDK 21، Gradle Wrapper 9.6.0، Android SDK platform 37.0، Build-tools 36.0.0 (انتخاب AGP) و 37.0.0، CMake 3.22.1 و NDK 28.2.13676358. لایسنس‌های SDK باید پذیرفته شده باشند.

1. مسیر SDK را در `local.properties` با کلید `sdk.dir` تنظیم کنید یا `ANDROID_HOME` را قرار دهید.
2. برای release، کیستور خصوصی را در مسیر دلخواه بگذارید و `keystore.properties` را مطابق فایل مثال بسازید. راه دیگر استفاده از Gradle property یا environment variableهای `PINBOM_STORE_FILE`, `PINBOM_STORE_PASSWORD`, `PINBOM_KEY_ALIAS`, `PINBOM_KEY_PASSWORD` است.
3. `./gradlew :app:assembleRelease :app:testDebugUnitTest --no-daemon --console=plain`
4. APK در `app/build/outputs/apk/release/app-release.apk` ساخته می‌شود. R8 و resource shrinking فعال‌اند. نبود کلید معتبر نباید به امضای debug منجر شود.

فایل `keystore.properties`، رمزها و خود کیستور در آرشیو سورس عمومی قرار ندارند. بستهٔ امضا را محرمانه نگه دارید و از آن پشتیبان بگیرید. مستندات تاریخی مخزن به بیلدهای دیگری اشاره دارند؛ وضعیت این بازسازی را از همین سند و گزارش تحویل بخوانید.

## یادداشت محیط تست

در اولین اجرای کامل، ۵۹ تست موفق و تست اولین اجرا با `AppNotIdleException` ناموفق شد؛ علت آن مشغول ماندن Compose با حلقه‌های جلوهٔ تزئینی بود. Seed تست‌های UI اکنون جلوه‌های ویژه را با همان preference کاربری غیرفعال می‌کند و تست اولین اجرا علاوه بر فارسی بودن متن، حضور دکمهٔ ورود و فیلد شناسه را صریحاً بررسی می‌کند. این تغییر در تنظیمات تست است، نه غیرفعال کردن جلوه‌ها در APK. شبیه‌سازی ذرات جداگانه با تست Kotlin و تست C++ بررسی می‌شود. نتیجهٔ اجرای مجدد در گزارش تحویل آمده است.
