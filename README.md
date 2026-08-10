# M3U Player — Xtream Codes Android App

<p align="center">
  <img src="app/src/main/res/mipmap-xxhdpi/ic_launcher.png" width="80" alt="App Icon"/>
</p>

تطبيق **Android** مكتوب بـ **Kotlin** لتشغيل قوائم M3U/M3U8 والاتصال بسيرفرات **Xtream Codes**.

---

## المميزات

| الميزة | الوصف |
|--------|-------|
| **Xtream Codes** | تسجيل دخول كامل مع دعم Live / VOD / Series |
| **قوائم M3U** | تحميل من رابط URL أو ملف محلي |
| **لوحة تحكم** | عرض أحدث الأفلام والمسلسلات مع قسم "استكمال المشاهدة" |
| **مشغل ExoPlayer** | دعم HLS و Progressive مع دعم PiP |
| **بحث صوتي** | البحث عبر الميكروفون |
| **المفضلة** | إضافة/إزالة المحتوى من المفضلة |
| **الرقابة الأبوية** | قفل فئات بـ PIN |
| **ملفات تعريفية** | حفظ وإدارة حسابات متعددة |
| **نسخ احتياطي** | تصدير/استيراد الإعدادات بصيغة JSON |
| **بروكسي** | دعم HTTP Proxy لكل ملف تعريفي |
| **مشغل خارجي** | فتح المحتوى في VLC أو MX Player |

---

## بنية المشروع

```
app/src/main/java/com/example/m3uplayer/
├── SplashActivity.kt          — شاشة الإطلاق (تتحقق من الملف التعريفي المحفوظ)
├── LoginActivity.kt           — تسجيل الدخول وإدارة الملفات التعريفية
├── MainActivity.kt            — الشاشة الرئيسية (Dashboard + Live + Movies + Series)
├── PlayerActivity.kt          — مشغل الفيديو (ExoPlayer + PiP + External Player)
├── SeriesDetailActivity.kt    — قائمة حلقات المسلسل
├── SettingsActivity.kt        — إعدادات الملف التعريفي والنسخ الاحتياطي
├── CustomGroupsActivity.kt    — إدارة المجموعات المخصصة
├── XtreamClient.kt            — عميل Xtream Codes API
├── M3uParser.kt               — محلل ملفات M3U
├── ProfileManager.kt          — إدارة الملفات التعريفية
├── FavoritesManager.kt        — إدارة المفضلة
├── WatchHistoryManager.kt     — سجل المشاهدة
├── ParentalControlManager.kt  — الرقابة الأبوية
├── BackupManager.kt           — النسخ الاحتياطي
└── NotificationHelper.kt      — الإشعارات
```

---

## المتطلبات

- **Android Studio** Hedgehog أو أحدث
- **JDK 17**
- **Android SDK** API 24 (Android 7.0) كحد أدنى

---

## رفع المشروع إلى GitHub

1. أنشئ مستودعاً جديداً فارغاً على GitHub.
2. من داخل مجلد المشروع نفّذ:
   ```bash
   git remote add origin https://github.com/USERNAME/REPO_NAME.git
   git branch -M main
   git push -u origin main
   ```
3. بمجرد اكتمال الرفع، سيبدأ **GitHub Actions** تلقائياً ببناء ملف APK.
4. بعد انتهاء البناء، ستجد ملف APK جاهزاً تحت تبويب **Actions → Artifacts**.

---

## التبعيات الرئيسية

| المكتبة | الإصدار | الغرض |
|---------|---------|-------|
| AndroidX Media3 (ExoPlayer) | 1.4.0 | تشغيل الفيديو |
| OkHttp | 4.12.0 | طلبات HTTP |
| Glide | 4.16.0 | تحميل الصور |
| Kotlin Coroutines | 1.8.1 | العمليات غير المتزامنة |
| Material Components | 1.12.0 | واجهة المستخدم |

---

## ملاحظات تقنية

- الحد الأدنى المدعوم: **Android 7.0 (API 24)**
- الهدف: **Android 14 (API 34)**
- يستخدم **ViewBinding** بدلاً من `findViewById`
- يدعم **Picture-in-Picture (PiP)** عند الضغط على زر الرجوع أثناء التشغيل
- يتم تخزين جميع البيانات محلياً على الجهاز فقط

---

## الترخيص

هذا المشروع مفتوح المصدر للأغراض التعليمية.
