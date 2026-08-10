# Add project specific ProGuard rules here.

# Keep ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# Keep data classes
-keep class com.example.m3uplayer.** { *; }
