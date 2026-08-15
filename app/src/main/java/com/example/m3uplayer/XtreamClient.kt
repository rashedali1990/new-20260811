package com.example.m3uplayer

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

object XtreamClient {

    private var httpClient = createDefaultClient()

    private fun createDefaultClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .dns(AdGuardDns())
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    class AdGuardDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return InetAddress.getAllByName(hostname).toList()
        }
    }

    data class LoginResult(val success: Boolean, val message: String)

    /**
     * تحديث إعدادات الشبكة (بروكسي اختياري).
     * يُستدعى من SettingsActivity عند تغيير إعدادات الملف التعريفي.
     */
    fun updateNetworkSettings(proxyHost: String?, proxyPort: Int) {
        val builder = OkHttpClient.Builder()
            .dns(AdGuardDns())
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)

        if (!proxyHost.isNullOrBlank() && proxyPort > 0) {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort))
            builder.proxy(proxy)
        }

        httpClient = builder.build()
    }

    fun updateProxy(host: String?, port: Int) {
        updateNetworkSettings(host, port)
    }

    private fun clean(url: String) = url.trim().trimEnd('/')

    private fun apiUrl(
        server: String,
        username: String,
        password: String,
        action: String? = null
    ): String {
        val base = "${clean(server)}/player_api.php?username=$username&password=$password"
        return if (action != null) "$base&action=$action" else base
    }

    private fun fetchJson(url: String): String {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("رمز الاستجابة: ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    fun login(serverUrl: String, username: String, password: String): LoginResult {
        return try {
            val body = fetchJson(apiUrl(serverUrl, username, password))
            val json = JSONObject(body)
            val userInfo = json.optJSONObject("user_info")
            val auth = userInfo?.optInt("auth", 0) ?: 0
            if (auth == 1) {
                LoginResult(true, "تم تسجيل الدخول بنجاح")
            } else {
                LoginResult(false, "بيانات الدخول غير صحيحة")
            }
        } catch (e: Exception) {
            LoginResult(false, "خطأ في الاتصال: ${e.message}")
        }
    }

    fun liveStreamUrl(server: String, username: String, password: String, streamId: String): String =
        "${clean(server)}/live/$username/$password/$streamId.m3u8"

    fun vodStreamUrl(server: String, username: String, password: String, streamId: String, ext: String): String =
        "${clean(server)}/movie/$username/$password/$streamId.$ext"

    fun episodeStreamUrl(server: String, username: String, password: String, episodeId: String, ext: String): String =
        "${clean(server)}/series/$username/$password/$episodeId.$ext"

    // ─── التصنيفات (Categories) ──────────────────────────────────────────────
    // أغلب سيرفرات Xtream Codes لا تُرجع "category_name" داخل قوائم البث نفسها،
    // بل فقط "category_id"؛ يجب طلب قوائم التصنيفات بشكل منفصل ومطابقتها يدويًا.

    private fun fetchCategoryMap(server: String, username: String, password: String, action: String): Map<String, String> {
        return try {
            val body = fetchJson(apiUrl(server, username, password, action))
            val array = JSONArray(body)
            (0 until array.length()).mapNotNull { i ->
                try {
                    val item = array.getJSONObject(i)
                    val id = item.optString("category_id")
                    val name = item.optString("category_name")
                    if (id.isNotEmpty() && name.isNotEmpty()) id to name else null
                } catch (e: Exception) {
                    null
                }
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun fetchLiveCategories(server: String, username: String, password: String): Map<String, String> =
        fetchCategoryMap(server, username, password, "get_live_categories")

    fun fetchVodCategories(server: String, username: String, password: String): Map<String, String> =
        fetchCategoryMap(server, username, password, "get_vod_categories")

    fun fetchSeriesCategories(server: String, username: String, password: String): Map<String, String> =
        fetchCategoryMap(server, username, password, "get_series_categories")

    // ─── قوائم المحتوى ───────────────────────────────────────────────────────
    // نستخدم mapNotNull + try/catch لكل عنصر بمفرده: عنصر واحد تالف في استجابة
    // الخادم لن يُسقط القائمة بأكملها بعد الآن (كان JSONException في عنصر واحد
    // يوقف array.map{} فيُفرَّغ الكتالوج كله ويظهر خطأ عام بدل نتائج جزئية).

    fun fetchLive(
        server: String,
        username: String,
        password: String,
        categories: Map<String, String> = emptyMap()
    ): List<MediaEntry> {
        val body = fetchJson(apiUrl(server, username, password, "get_live_streams"))
        val array = JSONArray(body)
        return (0 until array.length()).mapNotNull { i ->
            try {
                val item = array.getJSONObject(i)
                val id = item.optString("stream_id")
                val categoryId = item.optString("category_id")
                MediaEntry(
                    id         = id,
                    title      = item.optString("name", "بدون اسم"),
                    subtitle   = categoryId,
                    playUrl    = liveStreamUrl(server, username, password, id),
                    groupTitle = categories[categoryId]
                        ?: item.optString("category_name").takeIf { it.isNotEmpty() },
                    imageUrl   = item.optString("stream_icon").takeIf { it.isNotEmpty() }
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun fetchVod(
        server: String,
        username: String,
        password: String,
        categories: Map<String, String> = emptyMap()
    ): List<MediaEntry> {
        val body = fetchJson(apiUrl(server, username, password, "get_vod_streams"))
        val array = JSONArray(body)
        return (0 until array.length()).mapNotNull { i ->
            try {
                val item = array.getJSONObject(i)
                val id  = item.optString("stream_id")
                val ext = item.optString("container_extension", "mp4")
                val categoryId = item.optString("category_id")
                MediaEntry(
                    id         = id,
                    title      = item.optString("name", "بدون اسم"),
                    playUrl    = vodStreamUrl(server, username, password, id, ext),
                    groupTitle = categories[categoryId]
                        ?: item.optString("category_name").takeIf { it.isNotEmpty() },
                    imageUrl   = item.optString("stream_icon").takeIf { it.isNotEmpty() }
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun fetchSeriesList(
        server: String,
        username: String,
        password: String,
        categories: Map<String, String> = emptyMap()
    ): List<MediaEntry> {
        val body = fetchJson(apiUrl(server, username, password, "get_series"))
        val array = JSONArray(body)
        return (0 until array.length()).mapNotNull { i ->
            try {
                val item = array.getJSONObject(i)
                val categoryId = item.optString("category_id")
                MediaEntry(
                    id         = item.optString("series_id"),
                    title      = item.optString("name", "بدون اسم"),
                    isSeries   = true,
                    groupTitle = categories[categoryId]
                        ?: item.optString("category_name").takeIf { it.isNotEmpty() },
                    imageUrl   = item.optString("stream_icon").takeIf { it.isNotEmpty() }
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun fetchSeriesEpisodes(
        server: String,
        username: String,
        password: String,
        seriesId: String
    ): List<MediaEntry> {
        val body = fetchJson(
            apiUrl(server, username, password, "get_series_info") + "&series_id=$seriesId"
        )
        val json = JSONObject(body)
        val episodesObj = json.optJSONObject("episodes") ?: return emptyList()
        val result = mutableListOf<MediaEntry>()

        val seasonKeys = episodesObj.keys()
        while (seasonKeys.hasNext()) {
            val season = seasonKeys.next()
            val seasonArray = episodesObj.optJSONArray(season) ?: continue
            for (i in 0 until seasonArray.length()) {
                val ep    = seasonArray.getJSONObject(i)
                val id    = ep.optString("id")
                val epNum = ep.optString("episode_num", "?")
                val title = ep.optString("title", "حلقة $epNum")
                val ext   = ep.optJSONObject("info")?.optString("container_extension")
                    ?: ep.optString("container_extension", "mp4")
                result.add(
                    MediaEntry(
                        id       = id,
                        title    = "الموسم $season - الحلقة $epNum",
                        subtitle = title,
                        playUrl  = episodeStreamUrl(server, username, password, id, ext)
                    )
                )
            }
        }
        return result
    }
}
