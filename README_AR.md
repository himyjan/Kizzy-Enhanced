<div align="center">
  <img width="2048" height="989" alt="17831909936168749184217530060427" src="https://github.com/user-attachments/assets/7a1b7c4d-3610-4541-b400-8a8c7ef91605" />
<h1><img src="https://github.com/user-attachments/assets/02086040-660d-42d8-8830-78053e1e05a4" height="30" style="vertical-align: middle;"> Kizzy Enhanced</h1>
<h4>نسخة محسنة من <a href="https://github.com/dead8309/Kizzy">Kizzy</a> — مدير حالة حضور ديسكورد الغنية (Rich Presence) لنظام أندرويد، مكتوبة بلغة Kotlin.</h4>
</div>

<div align="center">
<img src="https://img.shields.io/badge/Minimum%20SDK-27-%23?&style=flat-square&color=5b5ef7">
<img src="https://img.shields.io/badge/kotlin-5b5ef7.svg?logo=kotlin&logoColor=white&style=flat-square">
<img src="https://img.shields.io/badge/package-com.my.kizzy.enhanced-5b5ef7?style=flat-square">
<img src="https://img.shields.io/badge/name-Kizzy%20Enhanced-5b5ef7?style=flat-square">
<div align="center">

[![README in English](https://img.shields.io/badge/README-in%20English-blue?style=for-the-badge)](README.md)
[![README بالعربية](https://img.shields.io/badge/README-بالعربية-blue?style=for-the-badge)](README_AR.md)

</div>
</div>
<p>
<img src="https://github.com/user-attachments/assets/f8402300-d628-4104-8f86-dc6021c9fbbe" width=60%/>
</p>
</div>

> [!NOTE]
> **Kizzy Enhanced** هو إصدار معدل شخصي من [Kizzy](https://github.com/dead8309/Kizzy) بواسطة dead8309. الميزات المضافة في هذا الإصدار محددة بـ ⭐ أدناه.

## الميزات

- [x] أزرار قابلة للنقر
- [x] اكتشاف التطبيق الحالي قيد التشغيل
- [x] اكتشاف الوسائط الحالية قيد التشغيل
- [x] طوابع زمنية اختيارية
- [x] حالة مخصصة
- [x] حفظ/تحميل إعدادات الحضور
- [x] سمة Material You
- [x] الترجمات
- [x] إعداد سهل
- [x] محاكاة وحدة التحكم RPC
- [x] إنشاء إعدادات مخصصة مع صورك وروابطك الخاصة
- [x] معاينة RPC داخل التطبيق نفسه
- [x] يعمل في الخلفية حتى عند إيقاف تشغيل الشاشة
- [x] دعم صور GIF
- [x] دعم الروابط الخارجية (مما يعني أنه يمكنك إعطاء رابط يشير إلى صورة على الويب وسيعرضها ديسكورد!)
- [x] استخدام الصور من المعرض
- [x] ⭐ محاكاة المنصة (سطح المكتب / الويب / أندرويد / آي أو إس / إكس بوكس / بلايستيشن / الواقع الافتراضي)
- [x] ⭐ معرف تطبيق مخصص — استخدم أيقونة تطبيق ديسكورد الخاص بك واسمه
- [x] ⭐ عناصر نائب إضافية للنماذج (`{{media_album}}`, `{{battery}}`, `{{playback}}` والمزيد)
- [x] ⭐ محدد وضع الطابع الزمني (افتراضي الوسائط/التطبيق · الوقت الحالي من اليوم · ميللي ثانية مخصصة)
- [x] ⭐ إعادة تصميم الشاشة الرئيسية — بطاقات بعرض كامل مع مفاتيح تشغيل/إيقاف
- [x] ⭐ إعادة الاتصال التلقائي بالبوابة مع تراجع أسي (لن يتوقف بعد ساعة أو ساعتين)
- [x] ⭐ حلقة اكتشاف غير قابلة للكسر — أخطاء الشبكة لم تعد توقف اكتشاف التطبيقات/الألعاب
- [x] ⭐ استطلاع تبديل التطبيقات كل ثانيتين (كان 5 ثوانٍ) لاكتشاف الألعاب بشكل أسرع

## ما الجديد في هذا الإصدار المعدل

جميع الإضافات موجودة في شاشة **Experimental RPC** والشاشة الرئيسية.

### ⭐ محاكاة المنصة (Platform spoofer)
- اختر المنصة التي يتصل منها حضورك — **سطح المكتب، الويب، أندرويد، آي أو إس، إكس بوكس، بلايستيشن، الواقع الافتراضي** (مقتبس من منصات Equicord).
- يعمل على مستوى `IDENTIFY` للبوابة (حالة العميل)، لذلك يتم اكتشافه بشكل صحيح بواسطة الإضافات مثل **platformindicators**.
- يتم التطبيق **مباشرة** — يعيد الخدمة تشغيل نفسها، دون الحاجة للتبديل اليدوي.

### ⭐ معرف تطبيق مخصص (أيقونتك الخاصة)
- محدد بين **مدمج (Built-in) / مخصص (Custom)**. يحتفظ المدمج بالأيقونة الافتراضية؛ يكشف المخصص عن حقل حيث يمكنك لصق **معرف التطبيق (Application ID)** الخاص بك على ديسكورد، بحيث يعرض النشاط أيقونتك واسمك.

### ⭐ عناصر نائب إضافية للنماذج
بالإضافة إلى النماذج الأصلية، مع الإكمال التلقائي:
- `{{media_album}}`, `{{media_album_artist}}`, `{{media_duration}}`, `{{media_writer}}`, `{{media_composer}}`
- `{{battery}}` — نسبة البطارية
- `{{playback}}` — حالة التشغيل (▶ / ⏸)

### ⭐ محدد وضع الطابع الزمني
- **الوسائط / التطبيق (افتراضي)** — طوابع زمنية من المسار/التطبيق الحالي.
- **نفس وقت اليوم الحالي** — يعرض ديسكورد الوقت الحالي كمنقضٍ (مثلاً في 15:30 ← يعرض "منقضٍ 15:30"). نفس منطق customRPC في Vencord.
- **مخصص** — اكتب **طابع البدء / الانتهاء (بالمللي ثانية)** بنفسك.

### ⭐ إعادة تصميم الشاشة الرئيسية
- بطاقات الميزات أصبحت الآن كومة عمودية من البطاقات واسعة النطاق.
- **مفاتيح تبديل التشغيل/الإيقاف على كل بطاقة**، بحيث يمكن بدء وإيقاف Media / Custom / Experimental RPC مباشرة من الشاشة الرئيسية.

### ⭐ مرونة البوابة (مقتبسة من [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy))
- إعادة اتصال تلقائية مع تراجع أسي عند أي انقطاع غير متوقع.
- استعادة الجلسة متى أمكن؛ التراجع إلى التعرف الجديد بعد 3 محاولات فاشلة.
- رموز الإغلاق القاتلة (4004، 4010–4014) لا تزال تتوقف بشكل دائم.
- حد أقصى مدته 60 ثانية على انتظارات تحديث الحضور (كان حلقة لا نهائية).
- حلقة الاكتشاف مغلفة الآن بـ try/catch — خطأ شبكة واحد لم يعد يوقف اكتشاف تبديل التطبيقات حتى إعادة التشغيل اليدوية.
- استطلاع تبديل التطبيقات: **ثانيتان** (كان 5 ثوانٍ).
- إصلاح: التبديل بين تطبيقين مفعلين يثبت تحديث الحضور فوراً.

### الإصلاحات
- تم إصلاح التعطل عند **"مشاركة السجل (Share log)"** (أصبحت صلاحية FileProvider تتبع معرف الحزمة).
- تم إصلاح الأعطال عند تبديل ميزات RPC من الشاشة الرئيسية.

## متطلبات النظام
- نظام التشغيل: أندرويد 8.1 حتى 16
- الذاكرة العشوائية (RAM): 3 جيجابايت كحد أدنى

## التحميل
احصل على أحدث ملف `app-release.apk` موقع من صفحة [**الإصدارات (Releases)**](../../releases/latest).

يستخدم **Kizzy Enhanced** معرف الحزمة **`com.my.kizzy.enhanced`**، لذا فهو يتم تثبيته بجانب تطبيق Kizzy الأصلي إذا كان مثبتًا لديك.

> [!WARNING]
> يستخدم هذا التطبيق اتصال بوابة ديسكورد (Discord Gateway). استخدامه على مسؤوليتك الخاصة.

## البناء والتطوير
> المتطلبات الأساسية: Android Studio، ومعرفة بـ Gradle و Kotlin و Jetpack Compose.

```console
git clone https://github.com/himyjan/Kizzy-Enhanced.git
```
افتح المشروع في Android Studio، قم باستيراده، ثم قم بالبناء والتشغيل (Build and Run). يتم إنشاء إصدارات النشر تلقائيًا بواسطة إجراءات GitHub Actions **Build Release Apk**.

## الشكر والتقدير
✨ [Kizzy](https://github.com/dead8309/Kizzy) لمشروع Kizzy الأصلي.

✨ [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy) لإصلاحات مرونة البوابة.

✨ [Read You](https://github.com/Ashinch/ReadYou) و [Seal](https://github.com/JunkFood02/Seal) لمكونات واجهة المستخدم.

✨ [Material Color Utilities](https://github.com/material-foundation/material-color-utilities)

✨ [Rich-Presence-U](https://github.com/ninstar/Rich-Presence-U) لبيانات ألعاب نينتندو ووي يو.

✨ [Logra](https://github.com/wingio/Logra) واجهة سجلات المستخدم.

✨ [Xbox-Rich-Presence-Discord](https://github.com/MrCoolAndroid/Xbox-Rich-Presence-Discord) لبيانات ألعاب إكس بوكس.

✨ [Monet](https://github.com/Kyant0/Monet) لوحات ألوان Material3.

## الترخيص
**Kizzy** هو مشروع مفتوح المصدر تحت ترخيص GNU GPL 3.0، والذي يتيح لك استخدام الرمز البرمجي والإشارة إليه وتعديله مجانًا، ولكنه لا يسمح بتوزيع أو بيع الكود المعدل والمشتق كبرمجيات تجارية مغلقة المصدر.
