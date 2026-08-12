package com.example.m3uplayer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class WatchHistory(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val position: Long,
    val duration: Long,
    val url: String? = null
)

class WatchHistoryManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("watch_history", Context.MODE_PRIVATE)

    fun saveProgress(id: String, title: String, imageUrl: String?, position: Long, duration: Long, url: String? = null) {
        val history = getHistory().toMutableList()
        history.removeAll { it.id == id }
        history.add(WatchHistory(id, title, imageUrl, position, duration, url))

        val jsonArray = JSONArray()
        history.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("title", it.title)
            obj.put("imageUrl", it.imageUrl)
            obj.put("position", it.position)
            obj.put("duration", it.duration)
            obj.put("url", it.url)
            jsonArray.put(obj)
        }
        prefs.edit().putString("history", jsonArray.toString()).apply()
    }

    fun getHistory(): List<WatchHistory> {
        val jsonString = prefs.getString("history", null) ?: return emptyList()
        val jsonArray = JSONArray(jsonString)
        val list = mutableListOf<WatchHistory>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(WatchHistory(
                id = obj.getString("id"),
                title = obj.getString("title"),
                imageUrl = obj.optString("imageUrl"),
                position = obj.getLong("position"),
                duration = obj.getLong("duration"),
                url = obj.optString("url")
            ))
        }
        return list
    }

    fun removeEntry(id: String) {
        val history = getHistory().toMutableList()
        history.removeAll { it.id == id }
        val jsonArray = JSONArray()
        history.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("title", it.title)
            obj.put("imageUrl", it.imageUrl)
            obj.put("position", it.position)
            obj.put("duration", it.duration)
            obj.put("url", it.url)
            jsonArray.put(obj)
        }
        prefs.edit().putString("history", jsonArray.toString()).apply()
    }
}
