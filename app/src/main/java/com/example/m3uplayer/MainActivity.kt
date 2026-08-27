package com.example.m3uplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.m3uplayer.databinding.ActivityMainBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SERVER     = "extra_server"
        const val EXTRA_USERNAME   = "extra_username"
        const val EXTRA_PASSWORD   = "extra_password"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        const val GRID_SPAN_COUNT  = 3
        const val HERO_BANNER_INTERVAL_MS = 4500L
        const val OTHER_CATEGORY = "أخرى"
        // بصمة إصدار بسيطة (تُحدَّث يدويًا مع كل تعديل) لتأكيد أن الـ APK المُثبَّت هو الأحدث فعليًا
        const val BUILD_TAG = "1.4.0"
    }

    private lateinit var binding: ActivityMainBinding
    private val httpClient = OkHttpClient()
    private lateinit var profileManager: ProfileManager
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var parentalControlManager: ParentalControlManager
    private lateinit var watchHistoryManager: WatchHistoryManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var cacheManager: MediaCacheManager

    private var currentProfile: Profile? = null
    private var server: String?   = null
    private var username: String? = null
    private var password: String? = null
    private var allMediaItems = mutableListOf<MediaEntry>()
    private var currentCategories = listOf<String>()
    private var selectedCategory: String = "الكل"

    // البانر المميز: تمرير تلقائي كل 4.5 ثانية
    private val heroBannerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var heroBannerRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.textBuildInfo.text = "v$BUILD_TAG"

        binding.editSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.buttonClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                filterItems(s?.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        binding.buttonClearSearch.setOnClickListener {
            binding.editSearch.setText("")
        }

        profileManager         = ProfileManager(this)
        favoritesManager       = FavoritesManager(this)
        parentalControlManager = ParentalControlManager(this)
        watchHistoryManager    = WatchHistoryManager(this)
        notificationHelper     = NotificationHelper(this)

        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
        server   = intent.getStringExtra(EXTRA_SERVER)
        username = intent.getStringExtra(EXTRA_USERNAME)
        password = intent.getStringExtra(EXTRA_PASSWORD)

        currentProfile = if (profileId != null) {
            profileManager.getAllProfiles().find { it.id == profileId }
        } else {
            profileManager.getLastUsedProfile()
        }

        if (server.isNullOrEmpty() && currentProfile != null) {
            server = currentProfile?.serverUrl
            username = currentProfile?.username
            password = currentProfile?.password
        }

        val welcomeName = currentProfile?.profileName ?: "Guest"
        binding.textWelcome.text        = "مرحباً بك، $welcomeName"
        binding.textCurrentProfile.text = welcomeName

        val cacheId = currentProfile?.id ?: "${server}_$username".hashCode().toString()
        cacheManager = MediaCacheManager(this, cacheId)

        binding.buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        intent.getStringExtra("extra_manual_url")?.let { url ->
            loadManualPlaylist(url)
        }
        intent.getStringExtra("extra_manual_uri")?.let { uriStr ->
            loadPlaylistFromUri(Uri.parse(uriStr))
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home      -> { showTab(0); true }
                R.id.nav_live      -> { showTab(1); true }
                R.id.nav_movies    -> { showTab(2); true }
                R.id.nav_series    -> { showTab(3); true }
                R.id.nav_favorites -> { showTab(4); true }
                else -> false
            }
        }

        showTab(0)
    }

    private var currentTab = 0

    private fun showTab(position: Int) {
        currentTab = position
        selectedCategory = "الكل" // إعادة ضبط الفلتر عند كل تبديل تبويب (كان يبقى من التبويب السابق ويُخفي أغلب المحتوى)
        if (binding.editSearch.text.isNotEmpty()) {
            binding.editSearch.setText("") // نفس السبب: نص بحث عالق من تبويب سابق كان سيُخفي أغلب المحتوى هنا أيضًا
        }
        binding.dashboardLayout.visibility = View.GONE
        binding.splitScreenLayout.visibility = View.GONE

        when (position) {
            0 -> {
                binding.dashboardLayout.visibility = View.VISIBLE
                loadDashboard()
            }
            1 -> {
                binding.splitScreenLayout.visibility = View.VISIBLE
                loadLive()
            }
            2 -> {
                binding.splitScreenLayout.visibility = View.VISIBLE
                loadVod()
            }
            3 -> {
                binding.splitScreenLayout.visibility = View.VISIBLE
                loadSeries()
            }
            4 -> {
                binding.splitScreenLayout.visibility = View.VISIBLE
                loadFavorites()
            }
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        heroBannerRunnable?.let { heroBannerHandler.removeCallbacks(it) }
    }

    private fun filterItems(query: String?) {
        val queryText = query ?: ""
        val filtered = allMediaItems.filter { item ->
            val matchesQuery = queryText.isBlank() || item.title.contains(queryText, ignoreCase = true)
            val matchesCategory = when (selectedCategory) {
                "الكل" -> true
                OTHER_CATEGORY -> item.groupTitle.isNullOrBlank()
                else -> item.groupTitle.equals(selectedCategory, ignoreCase = true)
            }
            val matchesFavorite = currentTab != 4 || favoritesManager.isFavorite(item.id)
            matchesQuery && matchesCategory && matchesFavorite
        }
        updateContentCountText(filtered.size)
        updateAdapter(filtered)
    }

    private fun updateContentCountText(count: Int) {
        val label = when (currentTab) {
            1 -> "قناة"
            2 -> "فيلم"
            3 -> "مسلسل"
            4 -> "عنصر مفضّل"
            else -> "عنصر"
        }
        val categoryCountSuffix = if (selectedCategory == "الكل" && currentCategories.size > 1) {
            " من ${currentCategories.size - 1} تصنيف"
        } else ""
        binding.textContentCount.text = "$count $label$categoryCountSuffix"
    }

    private fun updateCategoriesChips(items: List<MediaEntry>) {
        binding.chipGroupCategories.removeAllViews()

        // عدد العناصر داخل كل تصنيف (لعرضه بجانب اسمه في الـ Chip)
        val countPerCategory = items
            .filter { !it.groupTitle.isNullOrBlank() }
            .groupingBy { it.groupTitle!! }
            .eachCount()
        val uncategorizedCount = items.count { it.groupTitle.isNullOrBlank() }

        val categories = mutableSetOf("الكل")
        items.forEach { if (!it.groupTitle.isNullOrBlank()) categories.add(it.groupTitle!!) }
        if (uncategorizedCount > 0) categories.add(OTHER_CATEGORY)
        currentCategories = categories.toList()

        for (cat in currentCategories) {
            val count = when (cat) {
                "الكل" -> items.size
                OTHER_CATEGORY -> uncategorizedCount
                else -> countPerCategory[cat] ?: 0
            }
            val chip = Chip(this).apply {
                text = "$cat ($count)"
                isCheckable = true
                isChecked = (cat == selectedCategory)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedCategory = cat
                        filterItems("")
                    }
                }
            }
            binding.chipGroupCategories.addView(chip)
        }
    }

    private fun updateAdapter(items: List<MediaEntry>) {
        // Movies (2) and Series (3) use top-aligned grid view
        if (currentTab == 2 || currentTab == 3) {
            binding.recyclerContent.layoutManager = GridLayoutManager(this, calculateGridSpanCount()).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int = 1
                }
            }
            binding.recyclerContent.adapter = PosterAdapter(
                items       = items,
                onClick     = { entry -> handleMediaClickAutoPlay(entry) },
                onLongClick = { entry ->
                    favoritesManager.toggleFavorite(entry.id)
                    val messageRes = if (favoritesManager.isFavorite(entry.id))
                        R.string.added_to_favorites else R.string.removed_from_favorites
                    Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
                    if (currentTab == 4) loadFavorites()
                }
            )
        } else {
            // Live TV (1) and Favorites (4) use detailed server-categorized list view
            binding.recyclerContent.layoutManager = LinearLayoutManager(this)
            binding.recyclerContent.adapter = MediaAdapter(
                items          = items,
                onClick        = { entry -> handleMediaClickAutoPlay(entry) },
                onFavoriteClick = { entry ->
                    favoritesManager.toggleFavorite(entry.id)
                    if (favoritesManager.isFavorite(entry.id)) {
                        notificationHelper.sendReminderNotification(
                            title     = entry.title,
                            message   = "تم إضافة ${entry.title} إلى المفضلة",
                            streamUrl = entry.playUrl ?: ""
                        )
                    }
                    if (currentTab == 4) loadFavorites()
                    else filterItems("")
                },
                isFavorite = { entry -> favoritesManager.isFavorite(entry.id) }
            )
        }
    }

    private fun handleMediaClickAutoPlay(entry: MediaEntry) {
        checkParentalLock(entry.groupTitle) {
            if (entry.isSeries) {
                val creds = requireCreds() ?: return@checkParentalLock
                val intent = Intent(this, SeriesDetailActivity::class.java).apply {
                    putExtra(SeriesDetailActivity.EXTRA_SERVER,      creds.first)
                    putExtra(SeriesDetailActivity.EXTRA_USERNAME,    creds.second)
                    putExtra(SeriesDetailActivity.EXTRA_PASSWORD,    creds.third)
                    putExtra(SeriesDetailActivity.EXTRA_SERIES_ID,   entry.id)
                    putExtra(SeriesDetailActivity.EXTRA_SERIES_NAME, entry.title)
                }
                startActivity(intent)
            } else if (!entry.playUrl.isNullOrEmpty()) {
                openPlayer(entry.title, entry.playUrl!!, entry.id)
            }
        }
    }

    private fun checkParentalLock(group: String?, onSuccess: () -> Unit) {
        if (parentalControlManager.isGroupBlocked(group)) {
            val pinInput = android.widget.EditText(this).apply {
                hint      = "أدخل رمز PIN"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                            android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("محتوى مقيّد")
                .setMessage("هذه الفئة محمية. أدخل رمز PIN للمتابعة.")
                .setView(pinInput)
                .setPositiveButton("فتح") { _, _ ->
                    if (pinInput.text.toString() == parentalControlManager.pin) {
                        onSuccess()
                    } else {
                        Toast.makeText(this, "رمز PIN غير صحيح!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("إلغاء", null)
                .show()
        } else {
            onSuccess()
        }
    }

    private fun loadDashboard() {
        val creds = requireCreds() ?: return
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val movies = withContext(Dispatchers.IO) {
                    val categories = XtreamClient.fetchVodCategories(creds.first, creds.second, creds.third)
                    XtreamClient.fetchVod(creds.first, creds.second, creds.third, categories)
                }
                val series = withContext(Dispatchers.IO) {
                    val categories = XtreamClient.fetchSeriesCategories(creds.first, creds.second, creds.third)
                    XtreamClient.fetchSeriesList(creds.first, creds.second, creds.third, categories)
                }

                val history = watchHistoryManager.getHistory().take(3)
                binding.recyclerContinueWatching.adapter = ContinueWatchingAdapter(history) { item ->
                    if (!item.url.isNullOrEmpty()) {
                        openPlayer(item.title, item.url, item.id)
                    } else {
                        Toast.makeText(this@MainActivity, "رابط التشغيل غير متوفر", Toast.LENGTH_SHORT).show()
                    }
                }

                binding.recyclerLatestMovies.layoutManager = GridLayoutManager(this@MainActivity, calculateGridSpanCount())
                binding.recyclerLatestMovies.adapter = PosterAdapter(items = movies.take(12), onClick = { entry ->
                    handleMediaClickAutoPlay(entry)
                })

                binding.recyclerLatestSeries.layoutManager = GridLayoutManager(this@MainActivity, calculateGridSpanCount())
                binding.recyclerLatestSeries.adapter = PosterAdapter(items = series.take(12), onClick = { entry ->
                    handleMediaClickAutoPlay(entry)
                })

                // البانر المميز: أفضل 5 أعمال لها صورة (نخلط أفلامًا ومسلسلات)
                val heroCandidates = (movies + series).filter { !it.imageUrl.isNullOrBlank() }.take(5)
                setupHeroBanner(heroCandidates)

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "خطأ في التحميل: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    /**
     * نمط "اعرض القديم فورًا، وحدّث بالخلفية": إن وُجدت نسخة محفوظة محليًا،
     * تُعرض فورًا دون انتظار الشبكة، ثم يجري تحديث حقيقي من السيرفر في الخلفية
     * ويستبدل المعروض عند اكتماله (فقط إن كان المستخدم لا يزال في نفس التبويب).
     * إن فشل التحديث ولدينا نسخة محفوظة، نتجاهل الخطأ بصمت (تبقى البيانات القديمة
     * ظاهرة بدل نافذة خطأ مزعجة)؛ أما إن لم تكن هناك نسخة محفوظة أصلاً فنعرض الخطأ كالمعتاد.
     */
    private fun loadWithCache(
        cacheKey: String,
        targetTab: Int,
        fetchFresh: suspend () -> List<MediaEntry>
    ) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) { cacheManager.load(cacheKey) }
            if (cached != null && currentTab == targetTab) {
                displayMedia(cached)
                binding.progressBar.visibility = View.GONE
            }

            try {
                val fresh = withContext(Dispatchers.IO) { fetchFresh() }
                withContext(Dispatchers.IO) { cacheManager.save(cacheKey, fresh) }
                if (currentTab == targetTab) {
                    displayMedia(fresh)
                }
            } catch (e: Exception) {
                if (cached == null) {
                    showLoadErrorDialog(e)
                } else if (currentTab == targetTab) {
                    Toast.makeText(this@MainActivity, "تعذّر التحديث، تُعرض آخر بيانات محفوظة", Toast.LENGTH_SHORT).show()
                }
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun loadLive() {
        val creds = requireCreds() ?: return
        loadWithCache(MediaCacheManager.KEY_LIVE, 1) {
            val categories = XtreamClient.fetchLiveCategories(creds.first, creds.second, creds.third)
            XtreamClient.fetchLive(creds.first, creds.second, creds.third, categories)
        }
    }

    private fun loadVod() {
        val creds = requireCreds() ?: return
        loadWithCache(MediaCacheManager.KEY_VOD, 2) {
            val categories = XtreamClient.fetchVodCategories(creds.first, creds.second, creds.third)
            XtreamClient.fetchVod(creds.first, creds.second, creds.third, categories)
        }
    }

    private fun loadSeries() {
        val creds = requireCreds() ?: return
        loadWithCache(MediaCacheManager.KEY_SERIES, 3) {
            val categories = XtreamClient.fetchSeriesCategories(creds.first, creds.second, creds.third)
            XtreamClient.fetchSeriesList(creds.first, creds.second, creds.third, categories)
        }
    }

    private fun loadFavorites() {
        val creds = requireCreds() ?: return
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val live = withContext(Dispatchers.IO) {
                    val categories = XtreamClient.fetchLiveCategories(creds.first, creds.second, creds.third)
                    XtreamClient.fetchLive(creds.first, creds.second, creds.third, categories)
                }
                val vod = withContext(Dispatchers.IO) {
                    val categories = XtreamClient.fetchVodCategories(creds.first, creds.second, creds.third)
                    XtreamClient.fetchVod(creds.first, creds.second, creds.third, categories)
                }
                val series = withContext(Dispatchers.IO) {
                    val categories = XtreamClient.fetchSeriesCategories(creds.first, creds.second, creds.third)
                    XtreamClient.fetchSeriesList(creds.first, creds.second, creds.third, categories)
                }
                
                val favoriteIds = favoritesManager.getFavoriteIds()
                val allItems = live + vod + series
                val favorites = allItems.filter { favoriteIds.contains(it.id) }
                
                displayMedia(favorites)
            } catch (e: Exception) {
                showLoadErrorDialog(e)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun displayMedia(items: List<MediaEntry>) {
        allMediaItems.clear()
        allMediaItems.addAll(items)
        updateCategoriesChips(items)
        filterItems("")
    }

    private fun showLoadErrorDialog(e: Exception) {
        if (e is XtreamClient.CloudflareChallengeException) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("السيرفر يطلب تحقق أمان (Cloudflare)")
                .setMessage(
                    "سيرفر مزوّد الخدمة محمي بواسطة Cloudflare، وقد رصد الطلب كحركة آلية بدل متصفح حقيقي.\n\n" +
                    "هذا ليس عطلًا في التطبيق، وغالبًا ما يكون مؤقتًا. جرّب:\n" +
                    "• الانتظار قليلًا ثم إعادة المحاولة\n" +
                    "• تغيير الشبكة (بيانات الجوال بدل واي فاي أو العكس)\n" +
                    "• التواصل مع مزوّد الاشتراك إن استمرت المشكلة" +
                    (e.rayId?.let { "\n\nRay ID: $it" } ?: "")
                )
                .setPositiveButton("حسنًا", null)
                .show()
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("تعذّر تحميل المحتوى")
            .setMessage(getString(R.string.load_error, e.message ?: "خطأ غير معروف"))
            .setPositiveButton("حسنًا", null)
            .show()
    }

    /**
     * عدد أعمدة الشبكة يتكيّف مع عرض الشاشة الفعلي (بالـ dp) بدل رقم ثابت،
     * حتى تُستغل الشاشات الكبيرة جدًا (مثل Galaxy S25 Ultra) جيدًا بدل ترك
     * مساحات فارغة، وتبقى الشاشات الأصغر مناسبة أيضًا بعدد أعمدة أقل.
     * حجم البطاقة المستهدف ~105dp (يطابق عرض item_media_poster.xml).
     */
    private fun calculateGridSpanCount(): Int {
        val screenWidthDp = resources.configuration.screenWidthDp
        val targetCardWidthDp = 105
        val spanCount = (screenWidthDp / targetCardWidthDp).coerceIn(3, 7)
        return spanCount
    }

    private fun requireCreds(): Triple<String, String, String>? {
        val s = server; val u = username; val p = password
        if (s.isNullOrEmpty() || u.isNullOrEmpty() || p.isNullOrEmpty()) {
            Toast.makeText(this, R.string.login_required, Toast.LENGTH_SHORT).show()
            return null
        }
        return Triple(s, u, p)
    }

    private fun openPlayer(name: String, url: String, id: String? = null) {
        val streamId = id ?: url
        val savedPosition = watchHistoryManager.getHistory().find { it.id == streamId }?.position ?: 0L
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STREAM_URL,      url)
            putExtra(PlayerActivity.EXTRA_STREAM_NAME,     name)
            putExtra(PlayerActivity.EXTRA_STREAM_ID,       streamId)
            putExtra(PlayerActivity.EXTRA_START_POSITION,  savedPosition)
        }
        startActivity(intent)
    }

    private fun setupHeroBanner(items: List<MediaEntry>) {
        heroBannerRunnable?.let { heroBannerHandler.removeCallbacks(it) }

        if (items.isEmpty()) {
            binding.heroBannerPager.visibility = View.GONE
            binding.heroBannerIndicators.visibility = View.GONE
            return
        }
        binding.heroBannerPager.visibility = View.VISIBLE
        binding.heroBannerIndicators.visibility = View.VISIBLE

        binding.heroBannerPager.adapter = HeroBannerAdapter(items) { entry ->
            handleMediaClickAutoPlay(entry)
        }

        // البدء من منتصف المدى الكبير حتى يمكن التمرير بلا "قفزة" مرئية عند إعادة اللف
        val startPosition = (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % items.size)
        binding.heroBannerPager.setCurrentItem(startPosition, false)

        // مؤشرات النقاط أسفل البانر
        binding.heroBannerIndicators.removeAllViews()
        val dotSize   = (resources.displayMetrics.density * 6).toInt()
        val dotMargin = (resources.displayMetrics.density * 3).toInt()
        val dots = items.indices.map { index ->
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    marginStart = dotMargin
                    marginEnd   = dotMargin
                }
                setBackgroundResource(R.drawable.dot_indicator)
                alpha = if (index == 0) 1f else 0.35f
            }
        }
        dots.forEach { binding.heroBannerIndicators.addView(it) }

        binding.heroBannerPager.registerOnPageChangeCallback(object :
            androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val realIndex = position % items.size
                dots.forEachIndexed { i, dot -> dot.alpha = if (i == realIndex) 1f else 0.35f }
            }
        })

        startHeroBannerAutoScroll()
    }

    private fun startHeroBannerAutoScroll() {
        heroBannerRunnable = Runnable {
            val next = binding.heroBannerPager.currentItem + 1
            binding.heroBannerPager.setCurrentItem(next, true)
            heroBannerHandler.postDelayed(heroBannerRunnable!!, HERO_BANNER_INTERVAL_MS)
        }
        heroBannerHandler.postDelayed(heroBannerRunnable!!, HERO_BANNER_INTERVAL_MS)
    }

    override fun onPause() {
        super.onPause()
        heroBannerRunnable?.let { heroBannerHandler.removeCallbacks(it) }
    }

    override fun onResume() {
        super.onResume()
        if (binding.heroBannerPager.adapter != null && heroBannerRunnable != null) {
            startHeroBannerAutoScroll()
        }
    }

    private fun loadManualPlaylist(url: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(url).build()
                    httpClient.newCall(request).execute().use { response ->
                        response.body?.string() ?: ""
                    }
                }
                displayManualChannels(M3uParser.parse(text))
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, getString(R.string.load_error, e.message), Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun loadPlaylistFromUri(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    } ?: ""
                }
                displayManualChannels(M3uParser.parse(text))
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, getString(R.string.load_error, e.message), Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun displayManualChannels(channels: List<Channel>) {
        if (channels.isEmpty()) {
            Toast.makeText(this, R.string.no_channels_found, Toast.LENGTH_SHORT).show()
            return
        }
        val entries = channels.map {
            MediaEntry(id = it.url, title = it.name, imageUrl = it.logoUrl, playUrl = it.url, groupTitle = it.groupTitle, isSeries = false)
        }
        binding.splitScreenLayout.visibility = View.VISIBLE
        displayMedia(entries)
    }
}
