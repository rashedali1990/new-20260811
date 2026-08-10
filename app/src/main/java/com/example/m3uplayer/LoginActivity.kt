package com.example.m3uplayer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.m3uplayer.databinding.ActivityLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var profileManager: ProfileManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profileManager = ProfileManager(this)
        binding.recyclerProfiles.layoutManager = LinearLayoutManager(this)
        refreshProfileList()

        binding.buttonLogin.setOnClickListener { attemptLogin() }
    }

    private fun refreshProfileList() {
        val profiles = profileManager.getAllProfiles()
        binding.recyclerProfiles.adapter = ProfileAdapter(
            profiles,
            onClick = { profile -> openMain(profile.id, profile.serverUrl, profile.username, profile.password) },
            onDelete = { profile ->
                profileManager.deleteProfile(profile.id)
                refreshProfileList()
            }
        )
    }

    private fun attemptLogin() {
        val profileName = binding.editProfileName.text.toString().trim().ifEmpty { "بدون اسم" }
        val serverUrl = binding.editServerUrl.text.toString().trim()
        val username = binding.editUsername.text.toString().trim()
        val password = binding.editPassword.text.toString().trim()

        if (serverUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBarLogin.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                XtreamClient.login(serverUrl, username, password)
            }
            binding.progressBarLogin.visibility = android.view.View.GONE

            if (result.success) {
                val savedProfile = profileManager.saveProfile(profileName, serverUrl, username, password)
                profileManager.setLastUsed(savedProfile.id)
                Toast.makeText(this@LoginActivity, result.message, Toast.LENGTH_SHORT).show()
                openMain(savedProfile.id, serverUrl, username, password)
            } else {
                Toast.makeText(this@LoginActivity, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openMain(profileId: String, server: String, username: String, password: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_PROFILE_ID, profileId)
            putExtra(MainActivity.EXTRA_SERVER, server)
            putExtra(MainActivity.EXTRA_USERNAME, username)
            putExtra(MainActivity.EXTRA_PASSWORD, password)
        }
        startActivity(intent)
        finish()
    }
}
