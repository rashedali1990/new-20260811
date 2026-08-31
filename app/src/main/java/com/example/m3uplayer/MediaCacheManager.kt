package com.example.m3uplayer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * تخزين مؤقت محلي لقوائم المحتوى (بث مباشر / أفلام / مسلسلات) لكل ملف تعريفي.
 *
 * الفكرة: بما أن جلب كتالوج كبير (آلاف العناصر) عبر عدة صفحات قد يستغرق وقتًا،
 * نعرض آخر نسخة محفوظة فورًا عند فتح التطبيق (بلا انتظار)، ثم نُحدّث البيانات
 * فعليًا من السيرفر في الخلفية ونستبدل القائمة المعروضة عند اكتمال التحديث.
 * هذا يعطي إحساسًا بفتح فوري مع بقاء البيانات محدّثة دائمًا (Stale-While-Revalidate).
 */
class MediaCacheManager(context: Context, profileId: String) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("media_cache_$profileId", Context.MODE_PRIVATE)

    fun save(key: String, items: List<MediaEntry>) {
        val jsonArray = JSONArray()
        items.forEach { entry ->
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("title", entry.title)
            obj.put("subtitle", entry.subtitle)
            obj.put("playUrl", entry.playUrl)
            obj.put("isSeries", entry.isSeries)
            obj.put("groupTitle", entry.groupTitle)
            obj.put("imageUrl", entry.imageUrl)
            jsonArray.put(obj)
        }
        prefs.edit()
            .putString(key, jsonArray.toString())
            .putLong("${key}_timestamp", System.currentTimeMillis())
            .apply()
    }

    fun load(key: String): List<MediaEntry>? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            val jsonArray = JSONArray(raw)
            val items = (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                MediaEntry(
                    id         = obj.optString("id"),
                    title      = obj.optString("title"),
                    subtitle   = obj.optString("subtitle").takeIf { it.isNotEmpty() && it != "null" },
                    playUrl    = obj.optString("playUrl").takeIf { it.isNotEmpty() && it != "null" },
                    isSeries   = obj.optBoolean("isSeries", false),
                    groupTitle = obj.optString("groupTitle").takeIf { it.isNotEmpty() && it != "null" },
                    imageUrl   = obj.optString("imageUrl").takeIf { it.isNotEmpty() && it != "null" }
                )
            }
            // كاش فارغ (ناتج عن تحديث سابق فشل بصمت) يُعامَل كأنه غير موجود إطلاقًا،
            // ليُجبر التطبيق على تحميل كامل جديد بدل عرض قائمة فارغة عالقة إلى الأبد
            items.ifEmpty { null }
        } catch (e: Exception) {
            null // كاش تالف أو بصيغة قديمة: نتجاهله ونتعامل معه كأنه غير موجود
        }
    }

    fun lastUpdatedAt(key: String): Long = prefs.getLong("${key}_timestamp", 0L)

    companion object {
        const val KEY_LIVE   = "live"
        const val KEY_VOD    = "vod"
        const val KEY_SERIES = "series"
    }
}
