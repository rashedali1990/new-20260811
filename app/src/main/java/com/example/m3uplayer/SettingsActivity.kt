package com.example.m3uplayer

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.m3uplayer.databinding.ActivitySettingsBinding
import okhttp3.OkHttpClient
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var profileManager: ProfileManager
    private lateinit var backupManager: BackupManager
    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarSettings.setNavigationOnClickListener { finish() }

        profileManager = ProfileManager(this)
        backupManager = BackupManager(this)
        val currentProfile = profileManager.getLastUsedProfile()

        currentProfile?.let {
            binding.editProfileName.setText(it.profileName)
            binding.editServerUrl.setText(it.serverUrl)
            binding.editUsername.setText(it.username)
            binding.editPassword.setText(it.password)
            binding.editDnsServer.setText(it.dnsServer)
            binding.editProxyHost.setText(it.proxyHost)
            if (it.proxyPort > 0) binding.editProxyPort.setText(it.proxyPort.toString())
            if (it.proxyType.equals("SOCKS5", ignoreCase = true)) {
                binding.toggleProxyType.check(R.id.buttonProxySocks5)
            } else {
                binding.toggleProxyType.check(R.id.buttonProxyHttp)
            }
        } ?: binding.toggleProxyType.check(R.id.buttonProxyHttp)

        // Theme Selection
        val prefs = getSharedPreferences("m3uplayer_settings", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", true)
        if (isDarkMode) binding.toggleTheme.check(R.id.buttonDarkTheme)
        else binding.toggleTheme.check(R.id.buttonLightTheme)

        binding.toggleTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val dark = checkedId == R.id.buttonDarkTheme
                prefs.edit().putBoolean("dark_mode", dark).apply()
                AppCompatDelegate.setDefaultNightMode(
                    if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )
            }
        }

        // Language Selection
        val currentLang = prefs.getString("language", "ar")
        if (currentLang == "en") binding.toggleLanguage.check(R.id.buttonLangEn)
        else binding.toggleLanguage.check(R.id.buttonLangAr)

        binding.toggleLanguage.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val lang = if (checkedId == R.id.buttonLangEn) "en" else "ar"
                if (lang != prefs.getString("language", "ar")) {
                    prefs.edit().putString("language", lang).apply()
                    setLocale(lang)
                    recreate()
                }
            }
        }

        binding.buttonSaveProfile.setOnClickListener {
            val name = binding.editProfileName.text.toString()
            val server = binding.editServerUrl.text.toString()
            val user = binding.editUsername.text.toString()
            val pass = binding.editPassword.text.toString()
            val dns = binding.editDnsServer.text.toString()
            val proxyHost = binding.editProxyHost.text.toString().trim()
            val proxyPort = binding.editProxyPort.text.toString().trim().toIntOrNull() ?: 0
            val proxyType = if (binding.toggleProxyType.checkedButtonId == R.id.buttonProxySocks5) "SOCKS5" else "HTTP"

            if (name.isEmpty() || server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "الرجاء تعبئة جميع الحقول", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (proxyHost.isNotEmpty() && proxyPort <= 0) {
                Toast.makeText(this, "الرجاء إدخال منفذ (Port) صحيح للبروكسي", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            currentProfile?.let {
                profileManager.updateProfile(
                    id = it.id,
                    profileName = name,
                    serverUrl = server,
                    username = user,
                    password = pass,
                    proxyHost = proxyHost,
                    proxyPort = proxyPort,
                    proxyType = proxyType,
                    dnsServer = dns
                )
                XtreamClient.updateNetworkSettings(proxyHost, proxyPort, proxyType)
                Toast.makeText(this, "تم حفظ الملف التعريفي بنجاح", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        binding.buttonLoadManualUrl.setOnClickListener {
            val url = binding.editManualUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "الرجاء إدخال رابط M3U صحيح", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("extra_manual_url", url)
                }
                startActivity(intent)
            }
        }

        val filePickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let {
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("extra_manual_uri", it.toString())
                }
                startActivity(intent)
            }
        }

        binding.buttonPickManualFile.setOnClickListener {
            filePickerLauncher.launch(arrayOf("audio/x-mpegurl", "application/x-mpegURL", "*/*"))
        }

        binding.buttonOpenGroups.setOnClickListener {
            startActivity(Intent(this, CustomGroupsActivity::class.java))
        }

        binding.buttonExportBackup.setOnClickListener {
            backupManager.exportBackup()
        }

        val importLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let { backupManager.importBackup(it) }
        }

        binding.buttonImportBackup.setOnClickListener {
            importLauncher.launch(arrayOf("application/json"))
        }
    }

    private fun setLocale(lang: String) {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        baseContext.resources.updateConfiguration(config, baseContext.resources.displayMetrics)
    }
}
