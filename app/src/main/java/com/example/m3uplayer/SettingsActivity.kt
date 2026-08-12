package com.example.m3uplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.m3uplayer.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader

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
        }

        binding.buttonSaveProfile.setOnClickListener {
            val name = binding.editProfileName.text.toString()
            val server = binding.editServerUrl.text.toString()
            val user = binding.editUsername.text.toString()
            val pass = binding.editPassword.text.toString()
            val dns = binding.editDnsServer.text.toString()

            if (name.isEmpty() || server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "الرجاء تعبئة جميع الحقول", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            currentProfile?.let {
                profileManager.updateProfile(
                    id = it.id,
                    profileName = name,
                    serverUrl = server,
                    username = user,
                    password = pass,
                    proxyHost = it.proxyHost,
                    proxyPort = it.proxyPort,
                    dnsServer = dns
                )
                Toast.makeText(this, "تم حفظ الملف التعريفي بنجاح", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        binding.buttonLoadManualUrl.setOnClickListener {
            val url = binding.editManualUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "الرجاء إدخال رابط M3U صحيح", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "جاري تحميل القائمة اليدوية...", Toast.LENGTH_SHORT).show()
                // Save or open player / pass back
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
                Toast.makeText(this, "تم اختيار الملف بنجاح", Toast.LENGTH_SHORT).show()
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
}
