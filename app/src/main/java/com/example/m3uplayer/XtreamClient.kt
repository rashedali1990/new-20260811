package com.example.m3uplayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
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

    // ─── تشخيص متقدم ──────────────────────────────────────────────────────────
    // يبني تقريرًا نصيًا خامًا يوضح بالضبط كيف يتصرف السيرفر مع كل نوع طلب،
    // لتحديد سبب نقص الكتالوج دون الحاجة لسجلات CI.
    suspend fun diagnoseSeriesFetch(
        server: String,
        username: String,
        password: String
    ): String = withContext(Dispatchers.IO) {
        val report = StringBuilder()

        // 1) استجابة get_series_categories الخام
        try {
            val body = fetchJson(apiUrl(server, username, password, "get_series_categories"))
            val arr = try { JSONArray(body) } catch (e: Exception) { null }
            report.append("① get_series_categories:\n")
            report.append("  حجم الاستجابة: ${body.length} حرف\n")
            report.append("  عدد العناصر: ${arr?.length() ?: "فشل تحليل JSON"}\n")
            report.append("  أول 200 حرف: ${body.take(200)}\n\n")
        } catch (e: Exception) {
            report.append("① get_series_categories: فشل الطلب - ${e.message}\n\n")
        }

        // 2) استجابة get_series الشاملة (بلا category_id)
        var bulkCount = 0
        val bulkCategoryIds = mutableSetOf<String>()
        try {
            val body = fetchJson(apiUrl(server, username, password, "get_series"))
            val arr = JSONArray(body)
            bulkCount = arr.length()
            for (i in 0 until arr.length()) {
                try {
                    val cid = arr.getJSONObject(i).optString("category_id")
                    if (cid.isNotEmpty()) bulkCategoryIds.add(cid)
                } catch (e: Exception) { }
            }
            report.append("② get_series (شامل بلا category_id):\n")
            report.append("  حجم الاستجابة: ${body.length} حرف\n")
            report.append("  عدد العناصر: $bulkCount\n")
            report.append("  عدد التصنيفات المختلفة داخل هذه العناصر: ${bulkCategoryIds.size}\n\n")
        } catch (e: Exception) {
            report.append("② get_series (شامل): فشل الطلب - ${e.message}\n\n")
        }

        // 3) استجابة get_series لأول تصنيف مكتشف تحديدًا (لمعرفة إن كان محدودًا أيضًا)
        val sampleCategoryId = bulkCategoryIds.firstOrNull()
        if (sampleCategoryId != null) {
            try {
                val body = fetchJson(
                    apiUrl(server, username, password, "get_series") + "&category_id=$sampleCategoryId"
                )
                val arr = JSONArray(body)
                report.append("③ get_series&category_id=$sampleCategoryId:\n")
                report.append("  عدد العناصر لهذا التصنيف وحده: ${arr.length()}\n\n")
            } catch (e: Exception) {
                report.append("③ get_series&category_id=$sampleCategoryId: فشل الطلب - ${e.message}\n\n")
            }
        }

        // 4) تجربة معاملات ترقيم شائعة غير رسمية (page/limit) تستخدمها بعض اللوحات
        try {
            val body = fetchJson(apiUrl(server, username, password, "get_series") + "&limit=10000&page=1")
            val arr = JSONArray(body)
            report.append("④ get_series&limit=10000&page=1 (تجربة ترقيم غير رسمي):\n")
            report.append("  عدد العناصر: ${arr.length()}\n")
        } catch (e: Exception) {
            report.append("④ get_series&limit=10000&page=1: فشل الطلب - ${e.message}\n")
        }

        report.toString()
    }

    // ─── قوائم المحتوى ───────────────────────────────────────────────────────
    // نستخدم mapNotNull + try/catch لكل عنصر بمفرده: عنصر واحد تالف في استجابة
    // الخادم لن يُسقط القائمة بأكملها بعد الآن (كان JSONException في عنصر واحد
    // يوقف array.map{} فيُفرَّغ الكتالوج كله ويظهر خطأ عام بدل نتائج جزئية).
    //
    // ملاحظة مهمة: كثير من سيرفرات Xtream (خصوصًا لوحات الطرف الثالث غير الرسمية)
    // لا تُرجع الكتالوج الكامل عند الطلب بلا "category_id" — بل نسخة محدودة فقط.
    // لضمان جلب كل شيء، نطلب كل تصنيف بمعرّفه الخاص بالتوازي، ثم ندمج النتائج
    // مع الطلب الشامل، ونزيل أي تكرار بالاعتماد على المعرّف (id).

    private fun parseVodArray(
        body: String,
        server: String,
        username: String,
        password: String,
        categories: Map<String, String>,
        seenCategoryIds: MutableSet<String>? = null
    ): List<MediaEntry> {
        val array = JSONArray(body)
        return (0 until array.length()).mapNotNull { i ->
            try {
                val item = array.getJSONObject(i)
                val id  = item.optString("stream_id")
                val ext = item.optString("container_extension", "mp4")
                val categoryId = item.optString("category_id")
                if (categoryId.isNotEmpty()) seenCategoryIds?.add(categoryId)
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

    private fun parseSeriesArray(
        body: String,
        categories: Map<String, String>,
        seenCategoryIds: MutableSet<String>? = null
    ): List<MediaEntry> {
        val array = JSONArray(body)
        return (0 until array.length()).mapNotNull { i ->
            try {
                val item = array.getJSONObject(i)
                val categoryId = item.optString("category_id")
                if (categoryId.isNotEmpty()) seenCategoryIds?.add(categoryId)
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

    private fun parseLiveArray(
        body: String,
        server: String,
        username: String,
        password: String,
        categories: Map<String, String>,
        seenCategoryIds: MutableSet<String>? = null
    ): List<MediaEntry> {
        val array = JSONArray(body)
        return (0 until array.length()).mapNotNull { i ->
            try {
                val item = array.getJSONObject(i)
                val id = item.optString("stream_id")
                val categoryId = item.optString("category_id")
                if (categoryId.isNotEmpty()) seenCategoryIds?.add(categoryId)
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

    // ─── ترقيم الصفحات (Pagination) ──────────────────────────────────────────
    // كثير من لوحات Xtream غير الرسمية تفرض حدًا أقصى لعدد العناصر في كل طلب
    // (حتى مع تحديد category_id!) وتتطلب معاملات page/limit غير موجودة في
    // المواصفة الرسمية للحصول على البقية. هذه الدالة تطلب صفحة تلو الأخرى
    // وتتوقف تلقائيًا إن لم تُضِف الصفحة الجديدة أي عنصر جديد (يعني إما انتهت
    // الصفحات فعليًا، أو أن السيرفر يتجاهل الترقيم أصلاً ويُرجع نفس المجموعة).
    private suspend fun fetchAllPages(
        baseUrl: String,
        parse: (String) -> List<MediaEntry>
    ): List<MediaEntry> {
        val merged = LinkedHashMap<String, MediaEntry>()
        var page = 1
        val pageSize = 500
        while (page <= 30) { // سقف أمان لمنع حلقة لا نهائية
            val url = "$baseUrl&page=$page&limit=$pageSize"
            val items = try {
                parse(fetchJson(url))
            } catch (e: Exception) {
                break
            }
            if (items.isEmpty()) break
            val sizeBefore = merged.size
            items.forEach { merged[it.id] = it }
            val addedNew = merged.size - sizeBefore
            if (addedNew == 0) break // لا جديد: إما انتهت الصفحات أو السيرفر يتجاهل الترقيم
            if (items.size < pageSize) break // آخر صفحة على الأغلب
            page++
        }
        return merged.values.toList()
    }

    suspend fun fetchLive(
        server: String,
        username: String,
        password: String,
        categories: Map<String, String> = emptyMap()
    ): List<MediaEntry> = withContext(Dispatchers.IO) {
        val merged = LinkedHashMap<String, MediaEntry>()
        // مصدر معرّفات التصنيفات: قائمة get_live_categories (إن نجحت) + المعرّفات
        // الظاهرة فعليًا داخل عناصر الطلب الشامل (احتياط إن فشلت القائمة المنفصلة)
        val categoryIds = mutableSetOf<String>().apply { addAll(categories.keys) }

        val bulkBase = apiUrl(server, username, password, "get_live_streams")
        fetchAllPages(bulkBase) { body ->
            parseLiveArray(body, server, username, password, categories, categoryIds)
        }.forEach { merged[it.id] = it }

        // طلب كل تصنيف بمعرّفه بالتوازي (مع ترقيم صفحات لكل تصنيف أيضًا): يضمن
        // الكتالوج الكامل حتى مع السيرفرات التي تحدّ كل طلب بمفرده
        if (categoryIds.isNotEmpty()) {
            val perCategory = categoryIds.map { categoryId ->
                async {
                    val base = apiUrl(server, username, password, "get_live_streams") + "&category_id=$categoryId"
                    fetchAllPages(base) { body -> parseLiveArray(body, server, username, password, categories) }
                }
            }
            perCategory.forEach { deferred ->
                deferred.await().forEach { merged[it.id] = it }
            }
        }
        merged.values.toList()
    }

    suspend fun fetchVod(
        server: String,
        username: String,
        password: String,
        categories: Map<String, String> = emptyMap()
    ): List<MediaEntry> = withContext(Dispatchers.IO) {
        val merged = LinkedHashMap<String, MediaEntry>()
        val categoryIds = mutableSetOf<String>().apply { addAll(categories.keys) }

        val bulkBase = apiUrl(server, username, password, "get_vod_streams")
        fetchAllPages(bulkBase) { body ->
            parseVodArray(body, server, username, password, categories, categoryIds)
        }.forEach { merged[it.id] = it }

        if (categoryIds.isNotEmpty()) {
            val perCategory = categoryIds.map { categoryId ->
                async {
                    val base = apiUrl(server, username, password, "get_vod_streams") + "&category_id=$categoryId"
                    fetchAllPages(base) { body -> parseVodArray(body, server, username, password, categories) }
                }
            }
            perCategory.forEach { deferred ->
                deferred.await().forEach { merged[it.id] = it }
            }
        }
        merged.values.toList()
    }

    suspend fun fetchSeriesList(
        server: String,
        username: String,
        password: String,
        categories: Map<String, String> = emptyMap()
    ): List<MediaEntry> = withContext(Dispatchers.IO) {
        val merged = LinkedHashMap<String, MediaEntry>()
        val categoryIds = mutableSetOf<String>().apply { addAll(categories.keys) }

        val bulkBase = apiUrl(server, username, password, "get_series")
        fetchAllPages(bulkBase) { body ->
            parseSeriesArray(body, categories, categoryIds)
        }.forEach { merged[it.id] = it }

        if (categoryIds.isNotEmpty()) {
            val perCategory = categoryIds.map { categoryId ->
                async {
                    val base = apiUrl(server, username, password, "get_series") + "&category_id=$categoryId"
                    fetchAllPages(base) { body -> parseSeriesArray(body, categories) }
                }
            }
            perCategory.forEach { deferred ->
                deferred.await().forEach { merged[it.id] = it }
            }
        }
        merged.values.toList()
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
