package com.example.m3uplayer

import android.content.Context
import android.content.SharedPreferences

class ParentalControlManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("parental_prefs", Context.MODE_PRIVATE)

    var pin: String
        get() = prefs.getString("parental_pin", "") ?: ""
        set(value) = prefs.edit().putString("parental_pin", value).apply()

    var isEnabled: Boolean
        get() = prefs.getBoolean("parental_enabled", false)
        set(value) = prefs.edit().putBoolean("parental_enabled", value).apply()

    fun addBlockedGroup(group: String) {
        val blocked = getBlockedGroups().toMutableSet()
        blocked.add(group)
        saveBlockedGroups(blocked)
    }

    fun removeBlockedGroup(group: String) {
        val blocked = getBlockedGroups().toMutableSet()
        blocked.remove(group)
        saveBlockedGroups(blocked)
    }

    fun getBlockedGroups(): Set<String> {
        val groups = prefs.getString("blocked_groups", "") ?: ""
        return if (groups.isEmpty()) emptySet() else groups.split(",").toSet()
    }

    private fun saveBlockedGroups(groups: Set<String>) {
        prefs.edit().putString("blocked_groups", groups.joinToString(",")).apply()
    }

    fun isGroupBlocked(group: String?): Boolean {
        if (group == null || !isEnabled) return false
        return getBlockedGroups().contains(group)
    }
}
