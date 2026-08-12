package com.example.m3uplayer

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applySettings()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            navigateToNextScreen()
        }, 2000)
    }

    private fun applySettings() {
        val prefs = getSharedPreferences("m3uplayer_settings", Context.MODE_PRIVATE)
        
        // Apply Theme
        val isDarkMode = prefs.getBoolean("dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        // Apply Language
        val lang = prefs.getString("language", "ar") ?: "ar"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun navigateToNextScreen() {
        val profileManager = ProfileManager(this)
        val lastProfile = profileManager.getLastUsedProfile()

        val intent = if (lastProfile != null) {
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_PROFILE_ID, lastProfile.id)
                putExtra(MainActivity.EXTRA_SERVER,     lastProfile.serverUrl)
                putExtra(MainActivity.EXTRA_USERNAME,   lastProfile.username)
                putExtra(MainActivity.EXTRA_PASSWORD,   lastProfile.password)
            }
        } else {
            Intent(this, LoginActivity::class.java)
        }

        startActivity(intent)
        finish()
    }
}
