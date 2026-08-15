plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// معرّف Git الحالي + وقت البناء، يُستخدمان لعرض بصمة إصدار دقيقة داخل التطبيق
// (لحل التباس "هل أنا فعليًا أستخدم آخر نسخة APK؟" نهائيًا)
val gitCommitHash: String = try {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.get().trim()
} catch (e: Exception) {
    "unknown"
}

val buildTimestamp: String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    .format(java.util.Date())

android {
    namespace = "com.example.m3uplayer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.m3uplayer"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "GIT_COMMIT", "\"$gitCommitHash\"")
        buildConfigField("String", "BUILD_TIME", "\"$buildTimestamp\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    implementation("androidx.media3:media3-exoplayer:1.4.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.0")
    implementation("androidx.media3:media3-ui:1.4.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Glide for image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Activity KTX for registerForActivityResult
    implementation("androidx.activity:activity-ktx:1.9.1")

    // ViewPager2 للبانر المميز المتحرك (Hero Banner)
    implementation("androidx.viewpager2:viewpager2:1.1.0")
}
