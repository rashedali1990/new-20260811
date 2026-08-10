package com.example.m3uplayer

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // انتظر ثانيتين ثم انتقل إلى الشاشة المناسبة
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToNextScreen()
        }, 2000)
    }

    private fun navigateToNextScreen() {
        val profileManager = ProfileManager(this)
        val lastProfile = profileManager.getLastUsedProfile()

        val intent = if (lastProfile != null) {
            // يوجد ملف تعريفي محفوظ → انتقل مباشرة للشاشة الرئيسية
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_PROFILE_ID, lastProfile.id)
                putExtra(MainActivity.EXTRA_SERVER,     lastProfile.serverUrl)
                putExtra(MainActivity.EXTRA_USERNAME,   lastProfile.username)
                putExtra(MainActivity.EXTRA_PASSWORD,   lastProfile.password)
            }
        } else {
            // لا يوجد ملف تعريفي → انتقل لشاشة تسجيل الدخول
            Intent(this, LoginActivity::class.java)
        }

        startActivity(intent)
        finish()
    }
}
