package com.example.m3uplayer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class CustomGroup(
    val id: String,
    val name: String,
    val channelIds: MutableList<String>
)

class CustomGroupManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("custom_groups", Context.MODE_PRIVATE)

    fun getGroups(): List<CustomGroup> {
        val jsonString = prefs.getString("groups", null) ?: return emptyList()
        val jsonArray = JSONArray(jsonString)
        val list = mutableListOf<CustomGroup>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val idsArray = obj.optJSONArray("channelIds")
            val ids = mutableListOf<String>()
            if (idsArray != null) {
                for (j in 0 until idsArray.length()) {
                    ids.add(idsArray.getString(j))
                }
            }
            list.add(CustomGroup(
                id = obj.getString("id"),
                name = obj.getString("name"),
                channelIds = ids
            ))
        }
        return list
    }

    fun saveGroups(groups: List<CustomGroup>) {
        val jsonArray = JSONArray()
        groups.forEach { group ->
            val obj = JSONObject()
            obj.put("id", group.id)
            obj.put("name", group.name)
            val idsArray = JSONArray(group.channelIds)
            obj.put("channelIds", idsArray)
            jsonArray.put(obj)
        }
        prefs.edit().putString("groups", jsonArray.toString()).apply()
    }

    fun addChannelToGroup(groupId: String, channelId: String) {
        val groups = getGroups().toMutableList()
        val group = groups.find { it.id == groupId }
        if (group != null && !group.channelIds.contains(channelId)) {
            group.channelIds.add(channelId)
            saveGroups(groups)
        }
    }

    fun removeChannelFromGroup(groupId: String, channelId: String) {
        val groups = getGroups().toMutableList()
        val group = groups.find { it.id == groupId }
        group?.channelIds?.remove(channelId)
        saveGroups(groups)
    }

    fun createGroup(name: String): CustomGroup {
        val id = java.util.UUID.randomUUID().toString()
        val newGroup = CustomGroup(id, name, mutableListOf())
        val groups = getGroups().toMutableList()
        groups.add(newGroup)
        saveGroups(groups)
        return newGroup
    }

    fun deleteGroup(groupId: String) {
        val groups = getGroups().filter { it.id != groupId }
        saveGroups(groups)
    }
}
