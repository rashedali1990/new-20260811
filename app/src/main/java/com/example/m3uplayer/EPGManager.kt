package com.example.m3uplayer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class EpgProgram(
    val channelId: String,
    val title: String,
    val description: String,
    val startTime: String,
    val endTime: String
)

class EPGManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("epg_data", Context.MODE_PRIVATE)

    fun getProgramForChannel(channelId: String): EpgProgram? {
        val jsonString = prefs.getString("epg_$channelId", null) ?: return null
        val obj = JSONObject(jsonString)
        return EpgProgram(
            channelId = obj.getString("channelId"),
            title = obj.getString("title"),
            description = obj.getString("description"),
            startTime = obj.getString("startTime"),
            endTime = obj.getString("endTime")
        )
    }

    fun saveProgram(program: EpgProgram) {
        val obj = JSONObject().apply {
            put("channelId", program.channelId)
            put("title", program.title)
            put("description", program.description)
            put("startTime", program.startTime)
            put("endTime", program.endTime)
        }
        prefs.edit().putString("epg_${program.channelId}", obj.toString()).apply()
    }

    // Simulate fetching EPG for testing
    fun fetchMockEpg(channelId: String) {
        val mock = EpgProgram(channelId, "برنامج تجريبي", "هذا وصف تجريبي للبرنامج الحالي المعروض على القناة", "20:00", "21:00")
        saveProgram(mock)
    }
}
