package com.example.m3uplayer

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.m3uplayer.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var profileManager: ProfileManager
    private lateinit var backupManager: BackupManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        binding.buttonOpenGroups.setOnClickListener {
            startActivity(android.content.Intent(this, CustomGroupsActivity::class.java))
        }

        binding.buttonExportBackup.setOnClickListener {
            backupManager.exportBackup()
        }

        val importLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let { backupManager.importBackup(it) }
        }

        binding.buttonImportBackup.setOnClickListener {
            importLauncher.launch(arrayOf("application/json"))
        }
    }
}
