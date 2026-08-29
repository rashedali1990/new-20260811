package com.example.m3uplayer

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.m3uplayer.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var profileManager: ProfileManager
    private lateinit var backupManager: BackupManager
    private val httpClient = OkHttpClient()

    companion object {
        private const val UPDATE_REPO = "rashedali1990/new-20260811"
    }

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

        binding.textCurrentVersion.text = "الإصدار الحالي: ${MainActivity.BUILD_TAG}"
        binding.buttonCheckUpdate.setOnClickListener {
            checkForUpdate()
        }
    }

    /**
     * يتحقق من إصدار GitHub Release المنشور تحت العلامة الثابتة "latest" (يُحدَّثها
     * سير عمل CI تلقائيًا مع كل دفع ناجح لـ main)، ويقارن رقم الإصدار المُضمَّن في
     * اسم/وصف الإصدار بالإصدار المحلي الحالي (MainActivity.BUILD_TAG).
     */
    private fun checkForUpdate() {
        binding.buttonCheckUpdate.isEnabled = false
        binding.buttonCheckUpdate.text = "جاري التحقق..."

        lifecycleScope.launch {
            try {
                val (remoteVersion, downloadUrl) = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("https://api.github.com/repos/$UPDATE_REPO/releases/tags/latest")
                        .header("User-Agent", "M3UPlayer-UpdateChecker")
                        .header("Accept", "application/vnd.github+json")
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("تعذّر الاتصال بخادم التحديثات (رمز: ${response.code})")
                        }
                        val json = JSONObject(response.body?.string().orEmpty())
                        val body = json.optString("body")
                        val version = Regex("APP_VERSION=(\\S+)").find(body)?.groupValues?.getOrNull(1)
                            ?: json.optString("name").removePrefix("الإصدار ").trim()

                        val assets = json.optJSONArray("assets")
                        var apkUrl: String? = null
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                if (asset.optString("name").endsWith(".apk")) {
                                    apkUrl = asset.optString("browser_download_url")
                                    break
                                }
                            }
                        }
                        version to apkUrl
                    }
                }

                if (downloadUrl.isNullOrBlank()) {
                    Toast.makeText(this@SettingsActivity, "لم يُعثر على ملف تحديث في الإصدار الأخير", Toast.LENGTH_SHORT).show()
                } else if (remoteVersion.isNotBlank() && remoteVersion != MainActivity.BUILD_TAG) {
                    showUpdateAvailableDialog(remoteVersion, downloadUrl)
                } else {
                    Toast.makeText(this@SettingsActivity, "أنت تستخدم أحدث إصدار بالفعل", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "تعذّر التحقق من التحديث: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.buttonCheckUpdate.isEnabled = true
                binding.buttonCheckUpdate.text = "التحقق من وجود تحديث"
            }
        }
    }

    private fun showUpdateAvailableDialog(remoteVersion: String, downloadUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("يتوفر تحديث جديد")
            .setMessage(
                "الإصدار الحالي: ${MainActivity.BUILD_TAG}\n" +
                "الإصدار الجديد: $remoteVersion\n\n" +
                "سيُفتح رابط التنزيل في المتصفح؛ بعد اكتمال التنزيل اضغط على الملف لتثبيت التحديث " +
                "(قد يطلب الجهاز تفعيل \"السماح بالتثبيت من مصادر غير معروفة\")."
            )
            .setPositiveButton("تنزيل الآن") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
            }
            .setNegativeButton("لاحقًا", null)
            .show()
    }

    private fun setLocale(lang: String) {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        baseContext.resources.updateConfiguration(config, baseContext.resources.displayMetrics)
    }
}
