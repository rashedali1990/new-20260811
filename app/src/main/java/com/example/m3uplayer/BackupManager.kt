package com.example.m3uplayer

import android.content.Context
import android.net.Uri
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class BackupManager(private val context: Context) {

    // أسماء SharedPreferences يجب أن تتطابق مع ما تستخدمه بقية المديرين
    private val PREFS_PROFILES    = "m3uplayer_profiles"
    private val PREFS_GROUPS      = "custom_groups"
    private val PREFS_HISTORY     = "watch_history"

    fun exportBackup() {
        try {
            val profilePrefs = context.getSharedPreferences(PREFS_PROFILES, Context.MODE_PRIVATE)
            val groupPrefs   = context.getSharedPreferences(PREFS_GROUPS,   Context.MODE_PRIVATE)
            val historyPrefs = context.getSharedPreferences(PREFS_HISTORY,  Context.MODE_PRIVATE)

            val backupData = JSONObject().apply {
                put("profiles", JSONObject(profilePrefs.all as Map<*, *>))
                put("groups",   JSONObject(groupPrefs.all   as Map<*, *>))
                put("history",  JSONObject(historyPrefs.all as Map<*, *>))
            }

            val fileName = "rashed_player_backup.json"
            val file = File(context.getExternalFilesDir(null), fileName)

            FileOutputStream(file).use { output ->
                output.write(backupData.toString(2).toByteArray(Charsets.UTF_8))
            }

            Toast.makeText(
                context,
                "تم تصدير النسخة الاحتياطية إلى:\n${file.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, "خطأ في التصدير: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun importBackup(uri: Uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val jsonString = input.bufferedReader().use { it.readText() }
                val backupData = JSONObject(jsonString)

                val profileEditor = context.getSharedPreferences(PREFS_PROFILES, Context.MODE_PRIVATE).edit()
                val groupEditor   = context.getSharedPreferences(PREFS_GROUPS,   Context.MODE_PRIVATE).edit()
                val historyEditor = context.getSharedPreferences(PREFS_HISTORY,  Context.MODE_PRIVATE).edit()

                backupData.optJSONObject("profiles")?.let { obj ->
                    obj.keys().forEach { key -> profileEditor.putString(key, obj.getString(key)) }
                }
                backupData.optJSONObject("groups")?.let { obj ->
                    obj.keys().forEach { key -> groupEditor.putString(key, obj.getString(key)) }
                }
                backupData.optJSONObject("history")?.let { obj ->
                    obj.keys().forEach { key -> historyEditor.putString(key, obj.getString(key)) }
                }

                profileEditor.apply()
                groupEditor.apply()
                historyEditor.apply()

                Toast.makeText(context, "تم استيراد النسخة الاحتياطية بنجاح", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "خطأ في الاستيراد: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
