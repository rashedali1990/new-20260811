package com.example.m3uplayer

import android.content.Context
import org.json.JSONArray
import java.util.UUID

/**
 * Stores Xtream login profiles locally on-device using SharedPreferences.
 * Nothing is sent anywhere except the Xtream server itself during login.
 */
class ProfileManager(context: Context) {

    private val prefs = context.getSharedPreferences("m3uplayer_profiles", Context.MODE_PRIVATE)
    private val KEY_PROFILES = "profiles"
    private val KEY_LAST_USED = "last_used_profile_id"

    fun getAllProfiles(): List<Profile> {
        val raw = prefs.getString(KEY_PROFILES, "[]") ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).map { Profile.fromJson(array.getJSONObject(it)) }
    }

    fun saveProfile(
        profileName: String,
        serverUrl: String,
        username: String,
        password: String,
        proxyHost: String = "",
        proxyPort: Int = 0,
        proxyType: String = "HTTP",
        dnsServer: String = ""
    ): Profile {
        val profiles = getAllProfiles().toMutableList()
        val newProfile = Profile(
            id = UUID.randomUUID().toString(),
            profileName = profileName,
            serverUrl = serverUrl.trim().trimEnd('/'),
            username = username.trim(),
            password = password,
            proxyHost = proxyHost,
            proxyPort = proxyPort,
            proxyType = proxyType,
            dnsServer = dnsServer
        )
        profiles.add(newProfile)
        persist(profiles)
        return newProfile
    }

    fun updateProfile(
        id: String,
        profileName: String,
        serverUrl: String,
        username: String,
        password: String,
        proxyHost: String = "",
        proxyPort: Int = 0,
        proxyType: String = "HTTP",
        dnsServer: String = ""
    ) {
        val profiles = getAllProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.id == id }
        if (index != -1) {
            profiles[index] = Profile(
                id = id,
                profileName = profileName,
                serverUrl = serverUrl.trim().trimEnd('/'),
                username = username.trim(),
                password = password,
                proxyHost = proxyHost,
                proxyPort = proxyPort,
                proxyType = proxyType,
                dnsServer = dnsServer
            )
            persist(profiles)
        }
    }

    /** حذف ملف تعريفي بالمعرّف */
    fun deleteProfile(id: String) {
        val profiles = getAllProfiles().toMutableList()
        profiles.removeAll { it.id == id }
        persist(profiles)
        // إذا كان المحذوف هو الأخير المستخدم، نمسح المرجع
        if (prefs.getString(KEY_LAST_USED, null) == id) {
            prefs.edit().remove(KEY_LAST_USED).apply()
        }
    }

    fun setLastUsed(id: String) {
        prefs.edit().putString(KEY_LAST_USED, id).apply()
    }

    fun getLastUsedProfile(): Profile? {
        val id = prefs.getString(KEY_LAST_USED, null) ?: return null
        return getAllProfiles().find { it.id == id }
    }

    private fun persist(profiles: List<Profile>) {
        val array = JSONArray()
        profiles.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }
}
