# گزارش ممیزی و تغییرات UI/Motion پروژهٔ PinBom

## مشخصات
- پکیج: `ir.pinbom.app`
- محدودهٔ مرور: `ui/components`, `ui/components/icons`, `ui/screens`, `ui/nav`, `ui/theme` به‌همراه اتصال تنظیمات در `MainActivity.kt` و `ThemeStore.kt`
- در اسکن اولیه، ۴۲ فایل Kotlin در شاخه‌های components/screens بررسی شدند.

## ممیزی پیش از تغییر

1. **زیرساخت موجود:** `Motion.kt` شامل `pressSpring`، `popSpring`، `layoutSpring`، `pinbomClick` و shimmer بود. `Shape.kt`، `CardEntrance.kt`، `HeartBurst.kt`، `ActionToast.kt`، `PinGridSkeleton.kt` و `UploadProgressCard.kt` هم از قبل وجود داشتند؛ بازاستفاده از این‌ها به‌جای ساخت سیستم موازی در اولویت قرار گرفت.
2. **کنترل‌های خارج از الگوی طراحی:** سوییچ Material در صفحهٔ تنظیمات و شروع اولیه، `IconButton` در کارت پین و عملیات رسانهٔ پروفایل، و بعضی tapها از مسیر press-feedback مشترک عبور نمی‌کردند.
3. **تعامل‌ها:** کلیک مستقیم در کارت پین، چیپ‌ها، SearchBar، ActionToast و گزینه‌های رنگ/زبان شروع اولیه شناسایی شد. پس از تغییر فقط دو `clickable` مستقیم عمدی باقی مانده‌اند: scrim منوی سریع پین (برای آنکه کل scrim scale نشود) و helper ناوبری‌ای که press feedback والد را می‌گیرد.
4. **رنگ‌ها:** در چند صفحهٔ ورود، شروع اولیه، تنظیمات و اعلان‌ها رنگ ثابت خارج از `Color.kt` وجود داشت. مقدارها بدون تغییر بصری به توکن مرکزی انتقال داده شدند؛ در اسکن ایستا هیچ `Color(0x…)`، `Color.White` یا `Color.Black` خارج از `ui/theme/Color.kt` در کامپوننت‌ها/صفحه‌های UI پیدا نشد.
5. **انتقال مشترک:** Shared Element پیش‌تر با یک constant خاموش شده بود. فعال‌سازی آن می‌تواند هزینهٔ Lookahead داشته باشد؛ اکنون فقط وقتی Special Effects روشن است و Reduce Motion سیستم فعال نیست، `SharedTransitionLayout` ساخته می‌شود.
6. **ورود/خروج:** مسیرهای navigation و دیالوگ مشترک transition داشتند اما scale ظریف ورود/خروج یا snap سازگار با Reduce Motion در همهٔ مسیرها یکسان نبود.

## اصلاح‌های اعمال‌شده در این نسخه

### شکل‌ها و توکن‌ها
- `PinBomSquircleShape` با منحنی cubic سبک به‌عنوان تقریب Squircle اضافه شد و در shapeهای پین، کاور برد، کاشی جست‌وجو، thumbnail و hero استفاده می‌شود.
- شعاع شیت و دیالوگ مشترک ۳۲dp شد و `PinBomShape.pill` برای دکمه‌ها و کنترل‌های کوچک استفاده می‌شود.
- رنگ‌های ثابت موجود برای پالت Accent، پنجرهٔ ورود و انواع اعلان‌ها به `Color.kt` منتقل شدند؛ رنگ‌های فعلی/برند تغییر نکردند.

### لمس و کنترل‌های پایه
- `PinBomSwitch.kt` اضافه شد: حرکت فنری thumb، کشش کوچک، tint متحرک، RTL/LTR و snap در Reduce Motion.
- سوییچ‌های شروع اولیه و تنظیمات از کنترل مشترک استفاده می‌کنند.
- دکمه‌های اصلی و ثانویه تغییر رنگ فعال/غیرفعال، morph محتوای Loading، `animateContentSize` و glow گرادیانی کم‌رنگ هنگام فشار دارند.
- چیپ‌ها، اکشن‌های کارت پین، پاک‌کردن/لغو جست‌وجو و اکشن ActionToast به الگوی `pinbomClick` نزدیک شدند.
- دیالوگ سفارشی scale/fade ورود دارد؛ در Reduce Motion فوراً نمایش داده می‌شود.

### حرکت، افکت و تنظیمات
- `LocalSpecialEffectsEnabled` و تنظیم پایدار DataStore با کلید `special_effects_enabled` اضافه شد.
- صفحهٔ تنظیمات گزینهٔ «افکت‌ها و انیمیشن‌های ویژه / حالت سبک (Lite mode)» دارد. با خاموش‌کردن آن shimmer، CardEntrance، Particle Burst و shared-element transition غیرفعال می‌شوند؛ حرکت‌های ضروری/معمول کاملاً حذف نمی‌شوند.
- transition ورود/خروج صفحه‌ها در navigation با scale بسیار ظریف ترکیب شده است.
- `Particles.kt` اضافه شد: یک clock برای هر burst، Canvas سبک، سقف ۶۰ ذره، خاتمهٔ خودکار و توقف در Reduce Motion، Lite mode یا Lifecycle غیرقابل‌مشاهده.
- ذرات قلب در جزئیات پین با لایک/دوبارلمس، sparkle هنگام ذخیره در برد و confetti محدود در موفقیت آپلود فعال می‌شوند.
- Upload shimmer و draw-on چک‌مارک از Reduce Motion و Lite mode تبعیت می‌کنند.
- veil تغییر زبان در خاموش‌بودن انیمیشن سیستم دیگر transition اجرا نمی‌کند.

## بررسی‌های ایستا انجام‌شده
- `git diff --check`: بدون خطا.
- فایل‌های `values/strings.xml` و `values-fa/strings.xml`: با XML parser معتبر بودند.
- اسکن رنگ خام خارج از `Color.kt` در لایهٔ UI: نتیجه‌ای پیدا نشد.
- اسکن `clickable` باقی‌مانده در components/screens: فقط scrim منوی سریع پین و helper ناوبری؛ این دو استفاده عمدی‌اند.

## محدودیت مهم Build
Build کامل در این محیط **تأیید نشده است**. Wrapper به Gradle `9.6.0` نیاز دارد، درحالی‌که آن نسخه در کش محلی موجود نیست و درخواست دانلود از `services.gradle.org` به‌علت `UnknownHostException` شکست خورد. بنابراین نمی‌توانم ادعا کنم که پروژه در این محیط کامپایل شده است. پس از دریافت ZIP، در Android Studio یا محیط دارای دسترسی Gradle اجرا شود:

```bash
./gradlew :app:assembleDebug
./gradlew test
```

در ویندوز می‌توان از `gradlew.bat :app:assembleDebug` و `gradlew.bat test` استفاده کرد.

## موارد باقی‌مانده در پایان دور اول

> وضعیت موارد این بخش بعد از اجرای دور دوم در بخش «دور دوم» به‌روزرسانی شده است.

## موارد باقی‌مانده برای دور بعد / تست دستگاه

این تغییر مجموعه‌ای هدفمند از زیرساخت و چند مسیر پرتکرار UI است؛ **تمام موارد طولانی فهرست نیازمندی هنوز به‌صورت کامل پیاده‌سازی نشده‌اند**. به‌ویژه این‌ها نیاز به دور جداگانه دارند:
- آزمایش کامل همهٔ Screenها برای یکسان‌سازی همهٔ Dialog/DropdownMenu/Progress و ripple/shape؛ این گزارش ممیزی ایستا را پوشش می‌دهد، نه بازبینی دستی تک‌تک حالت‌های اجرایی.
- ورود stagger برای حداکثر ۸ آیتم اول همهٔ گریدها؛ mesh/bokeh متحرک در Welcome و انتخاب علایق؛ پارالاکس کاور و blur واقعی سطح ناوبری.
- morph اختصاصی Follow، تکان badge اعلان، شمارندهٔ rolling برای همهٔ اعداد، progress زمان باقی‌مانده در Toast و theme circular reveal.
- predictive-back ویژهٔ Android 14+، بررسی pull-to-refresh روی دستگاه، و جزئیات فوکوس/لرزش خطای فیلدها.
- Shared Element روی گرید واقعی و دستگاه ضعیف باید با Profiler آزمایش شود؛ در صورت افت فریم، Lite mode را خاموش نگه دارید یا transition را برای آن route غیرفعال کنید.

**چک‌لیست دستگاه واقعی:** Android 12 و 14+؛ گوشی ضعیف؛ Light/Dark؛ فارسی RTL و انگلیسی LTR؛ انیمیشن سیستم روشن/خاموش؛ Lite mode روشن/خاموش؛ scroll سریع Home/Search/Profile؛ ورود/بازگشت Pin Detail؛ لایک، ذخیره، آپلود موفق/ناموفق؛ اندازه‌گیری frame time و تخصیص حافظه با Android Studio Profiler.


---

# دور دوم — اصلاحات A و پیاده‌سازی B/C

تاریخ: ۱۶ اکتبر ۲۰۲۶  
مبنای کار: همین نسخهٔ `pinbom-android-polished(1).zip` به‌همراه گزارش ممیزی بالا. محدودهٔ تغییرات این دور به لایهٔ UI، توکن‌های شکل/حرکت و اتصال بصری Splash محدود ماند؛ فایل‌های ViewModel، repository، شبکه و دیتابیس برای این دور تغییر داده نشدند.

## A — اصلاح‌های اولویت‌دار

### A1. تقارن PinBomSwitch

`ui/components/PinBomSwitch.kt` اکنون عرض track را ۴۲dp، قطر thumb را ۱۹dp و inset هر طرف را ۳dp نگه می‌دارد. مسافت جابه‌جایی:

`42 − 19 − (2 × 3) = 17dp`

| چیدمان | وضعیت خاموش | وضعیت روشن |
|---|---|---|
| LTR | چپ ۳dp، راست ۲۰dp | چپ ۲۰dp، راست ۳dp |
| RTL | چپ ۲۰dp، راست ۳dp | چپ ۳dp، راست ۲۰dp |

در RTL، `Alignment.CenterStart` شروع را سمت راست قرار می‌دهد و آفست به جهت منفی برمی‌گردد؛ بنابراین هر دو وضعیت ۳dp فاصلهٔ ثابت از لبهٔ نزدیک دارند. press، thumb را افقی کمی می‌کشد و سپس با spring برمی‌گرداند. اگر Reduce Motion یا Lite mode روشن باشد، حرکت به snap تبدیل می‌شود.

### A2. Shared Element محدودشده

- ثابت کلیدخاموش‌سازی: `ui/nav/SharedTransition.kt`، مقدار `PIN_SHARED_TRANSITIONS_ENABLED`؛ برای خاموش‌کردن کامل shared element کافی است مقدار آن `false` شود. کامنت هزینهٔ Lookahead روی گرید masonry گوشی ضعیف را توضیح می‌دهد.
- `SharedTransitionLayout` فقط برای مسیرهای Home، Profile، Board و Pin Detail و فقط در حالت Special Effects روشن و Reduce Motion خاموش ساخته می‌شود؛ نوار شناور و Overlay آپلود خارج آن قرار دارند.
- شناسه‌های shared bounds تنها برای آیتم‌های قابل‌مشاهده ثبت می‌شوند و در زمان scroll، از مجموعه حذف می‌شوند تا هنگام اسکرول سریع یا خارج از viewport ساخت bounds تازه انجام نشود.

این مسیر از نظر کد محدود شده است، اما افت فریم/پرش transition هنوز باید روی دستگاه واقعی Profiler شود.

### A3. شعاع Squircle

`ui/theme/Shape.kt` شعاع را بر پایهٔ `fraction × minDimension` محاسبه و سپس در `[minRadius, maxRadius]` محدود می‌کند؛ شعاع نهایی از نصف کوچک‌ترین ضلع هم بزرگ‌تر نمی‌شود. Material shapes به شعاع‌های ثابت ۸، ۱۲، ۱۶، ۲۰ و ۲۴dp بازگشتند.

نمونه‌های محاسبه‌شده:

| اندازه | توکن | شعاع نهایی |
|---|---|---:|
| چیپ ۳۲×۳۲dp | `PinBomShape.pin` | ۱۲dp |
| پین ۱۸۰×۲۶۰dp | `PinBomShape.pin` | ۲۴dp |
| کاور برد ۳۶۰×۲۱۲dp | `PinBomShape.boardCover` | ۳۲dp |
| Hero مربع ۳۶۰×۳۶۰dp | `PinBomShape.hero` | ۳۶dp |

این‌ها شعاعِ محاسبه‌شده از توکن‌ها هستند؛ اندازه‌گیری نهایی در layout واقعی، به ابعاد اندازه‌گیری‌شدهٔ کامپوننت وابسته است.

### A4. Particle burst تصادفی و محدود

`ui/components/fx/Particles.kt` برای هر trigger یک seed تازه دارد و مشخصات ذرات (زاویه، سرعت، شعاع، چرخش، عمر، drift و رنگ) را فقط یک بار می‌سازد. سقف تعداد همچنان ۶۰ است و هر burst یک clock دارد. قلب‌ها عمدتاً در نیم‌کرهٔ بالا حرکت می‌کنند و ease-out/pop/fade دارند؛ confetti دو گروه سرعت، افتادن گرانشی، چرخش و نوسان افقی دارد. انیمیشن در Reduce Motion، Lite mode یا lifecycle غیرقابل‌مشاهده شروع/ادامه پیدا نمی‌کند.

## B — صفحه‌های ورود

| مورد | وضعیت دور دوم |
|---|---|
| B1 — Mesh background | اضافه و متصل شد به Splash، Auth، Initial Setup و Interests. سه radial blob با دوره‌های حدود ۱۹/۲۴/۲۹ ثانیه از رنگ‌های توکن موجود ساخته می‌شود؛ Dark ملایم‌تر است و Lite/Reduce Motion پس‌زمینهٔ ثابت می‌دهد. |
| B2 — Bokeh/dust | ۱۴ نقطهٔ خیلی کم‌رنگ در حالت ویژه، فقط روی Boot/Splash؛ چون صفحهٔ مستقل Welcome در سورس پیدا نشد، به آن صفحه‌ای اضافه نشده است. فرم‌های Auth/Setup/Interests dust دائمی ندارند. |
| B3 — Splash | scale-in لوگو، glow پالس محدود و خروج fade هماهنگ با scale سیستم اضافه/تقویت شد. نبود فلش سفید از نظر کد رعایت شده؛ نبود آن باید روی دستگاه واقعی تأیید شود. |
| B4 — Stagger | ورود مرحله‌ای برای اجزای Auth و Initial Setup و حداکثر ۸ کاشی اول Interests اضافه شد؛ در ورود مجدد/اسکرول، گرید نباید هر بار دوباره stagger شود. |
| B5 — انتخاب علاقه | کاشی انتخاب‌شده کمی pop می‌کند، تیک با Canvas draw-on نمایش داده می‌شود، sparkle کوچک می‌آید و دکمهٔ ادامه در گذار disabled→enabled bounce می‌گیرد. |
| B6 — فیلدها | `Fields.kt` از label خودکار Material برای حالت تک‌خطی، فوکوس/درخشش draw-based و shake حدود ۲۴۰ms برای خطا استفاده می‌کند؛ در Reduce Motion/Lite، shake و glow حرکت‌دار حذف می‌شوند و رنگ خطا باقی می‌ماند. |

## C — ناوبری و سطوح شناور

| مورد | وضعیت دور دوم |
|---|---|
| C1 — Bottom navigation | glide فنری و stretch نرمِ marker، crossfade آیکون outline/filled، label fade/expand روی تب فعال در همهٔ سبک‌ها (با اسلات ثابت برای جلوگیری از پرش)، و مخفی‌شدن/بازگشت نوار با nested scroll سبک اضافه/تقویت شد. ضربهٔ دوم روی تب فعال، در بازهٔ ۳۲۰ms رویداد موجود scroll-to-top را ارسال می‌کند؛ ضربهٔ اول دیگر لیست را ناخواسته به بالا نمی‌برد. |
| C1 — blur واقعی پشت نوار | **پیاده‌سازی نشد.** `Modifier.blur` محتوای همان لایه را blur می‌کند و پس‌زمینهٔ sibling را نمونه‌برداری نمی‌کند؛ بنابراین استفاده از آن روی صفحهٔ تخت، backdrop blur واقعی نمی‌سازد. نوار در این نسخه نیمه‌شفاف، گرد و دارای سایهٔ tintشده است. برای blur واقعی باید مسیر جداگانه و قابل‌آزمون با snapshot/رندرِ لایهٔ زیرین طراحی شود؛ عمداً به‌عنوان blur واقعی ادعا نشده است. |
| C2 — Badge | wiggle کوتاه برای badge تازه و rolling number اضافه شد؛ در Reduce Motion/Lite حرکت به حالت ثابت تبدیل می‌شود. |
| C3 — شمارنده | `AnimatedCounter.kt` اضافه و برای شمارنده‌های پین/اکشن، Badge و آمار follower/pin به‌کار برده شد. |
| C4 — Follow | `FollowMorphButton.kt` متن و عرض را تغییر می‌دهد، پس‌زمینه با رنگ تم crossfade می‌شود، تیک draw-on و sparkle محدود دارد؛ از state موجود صفحه استفاده می‌کند و منطق follow تغییر نکرده است. |
| C5 — Toast | `ActionToast.kt` ورود فنری از پایین، خروج، نوار پیشرفت مدت نمایش و swipe افقی برای dismiss دارد. |
| C6 — Header/Parallax | `CollapsingHeader` آلفا/scale و تینت پس‌زمینه را با scroll ترکیب می‌کند؛ کاور برد و پروفایل parallax اندک از طریق graphicsLayer می‌گیرند. |

## فهرست فایل‌های مرتبطِ دور دوم

- `MainActivity.kt`
- `ui/components/ActionToast.kt`, `AnimatedCounter.kt`, `Brand.kt`, `Chips.kt`, `CollapsingHeader.kt`, `Fields.kt`, `FollowMorphButton.kt`, `LanguageToggle.kt`, `Navigation.kt`, `PinBomSwitch.kt`, `Primitives.kt`, `UploadProgressCard.kt`
- `ui/components/fx/MeshBackground.kt`, `ui/components/fx/Particles.kt`
- `ui/nav/AppNavGraph.kt`, `ui/nav/SharedTransition.kt`
- `ui/screens/ActiveSessionsScreen.kt`, `AuthScreen.kt`, `BoardScreen.kt`, `HomeScreen.kt`, `InitialSetupScreen.kt`, `InterestsScreen.kt`, `NotificationsScreen.kt`, `ProfileScreen.kt`, `SettingsScreen.kt`
- `ui/theme/Motion.kt`, `ui/theme/Shape.kt`

فایل جدید: `ui/components/AnimatedCounter.kt` و `ui/components/fx/MeshBackground.kt`. پارتیکل‌ها در این دور در `ui/components/fx/Particles.kt` تقویت/بازنویسی شدند.

## بررسی ایستا و وضعیت Build

- اسکن UI برای `Color(0x...)`, `Color.White`, `Color.Black` و `Color.Transparent` خارج از `ui/theme/Color.kt`: نتیجه‌ای پیدا نشد؛ توقف‌های شفاف به alpha صفرِ رنگ مشتق‌شده از توکن تبدیل شده‌اند.
- اسکن نحوی با `kotlinc` روی سورس انجام شد؛ چون classpath اندروید/Compose حاضر نیست، بررسی type-check کاملاً ممکن نبود. در خروجیِ فیلترشده خطاهای parser مانند `expecting`, `unexpected tokens`, `missing }` مشاهده نشد؛ این **جایگزین کامپایل نیست**.
- Build مجدداً با `./gradlew --offline :app:compileDebugKotlin` اجرا شد، اما Wrapper برای Gradle `9.6.0` تلاش کرد فایل توزیع را دانلود کند و با `UnknownHostException: services.gradle.org` متوقف شد. پس بیلد واقعی **تأیید نشده است**؛ هیچ ادعای build موفقی وجود ندارد.
- وابستگی جدیدی به Gradle اضافه نشده است. منطق ViewModel، شبکه و دیتابیس در این دور تغییر نکرده‌اند.
- پس‌زمینهٔ ویژه و particles از توکن‌های تم استفاده می‌کنند و به هر دو متغیر Reduce Motion و Lite mode احترام می‌گذارند. افکت‌های پایه/حرکت‌های معمول UI به زیرساخت موجود تکیه دارند.

## کارهای عمداً باقی‌مانده

- predictive back ویژهٔ Android 14+ و circular/reveal انتقال تم: در این دور پیاده‌سازی نشدند.
- backdrop blur واقعی برای bottom bar: نیازمند راه‌حل جداگانهٔ sampling/rendering است؛ blur روی خود پنل به‌عنوان پیاده‌سازی جا زده نشد.
- Welcome مستقل در این پروژه وجود ندارد؛ dust فقط روی Boot/Splash فعال شده است.
- اعتبارسنجی ۶۰fps، frame-time و حافظه روی دستگاه واقعی اجرا نشده است.
- تمام مسیرهای تمام Dialog/DropdownMenu/Progress و تمامی صفحه‌ها از نظر بصری دستی در همهٔ حالت‌ها تک‌تک تست نشده‌اند؛ این نسخه یک مجموعهٔ هدفمند از موارد A و گروه‌های B/C است، نه ادعای تکمیل همهٔ خواسته‌های اولیه.

## چک‌لیست تست دستگاه

- [ ] Android 12 (API 31) و Android 14+؛ ورود، خروج، Splash و predictive system back.
- [ ] گوشی ضعیف: scroll سریع grid، تغییر تب و ورود/بازگشت از Pin Detail؛ در صورت افت فریم، `PIN_SHARED_TRANSITIONS_ENABLED = false` را آزمایش کنید.
- [ ] Light و Dark؛ بررسی contrast متن/تیک/Badge در روی indicator و روی تصویر.
- [ ] فارسی RTL و انگلیسی LTR؛ خصوصاً موقعیت خاموش/روشن PinBomSwitch و جهت marker، Toast progress و parallax.
- [ ] Animator duration scale روشن و صفر (Reduce Motion)؛ mesh، dust، particle، focus shake، check draw-on و navigation باید snap/خاموش شوند.
- [ ] Lite mode روشن/خاموش؛ مطمئن شوید پارتیکل، mesh متحرک و shared element خاموش می‌شوند و ترجیح بعد از بستن/بازکردن اپ باقی می‌ماند.
- [ ] لایک، ذخیره در برد، follow/unfollow، انتخاب علاقه، Badge جدید و Toast؛ هر burst را در چند trigger پشت سرهم ببینید.
- [ ] Android Studio Profiler / Perfetto: اندازه‌گیری frame time و jank در گریدهای Home/Profile/Board و هنگام انتقال shared element؛ ثبت مقدار واقعی قبل از ادعای 60fps.
- [ ] ثبت/ورود ناموفق و فوکوس فیلد؛ بررسی shake فقط با motion روشن و border error ثابت با motion خاموش.
