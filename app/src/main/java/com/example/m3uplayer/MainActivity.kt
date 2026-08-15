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
    }

    private lateinit var binding: ActivityMainBinding
    private val httpClient = OkHttpClient()
    private lateinit var profileManager: ProfileManager
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var parentalControlManager: ParentalControlManager
    private lateinit var watchHistoryManager: WatchHistoryManager
    private lateinit var notificationHelper: NotificationHelper

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

    // Speech recognizer moved to player as requested

    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.getOrNull(0)
            spokenText?.let {
                filterItems(it)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "يلزم السماح بالوصول للميكروفون لاستخدام البحث الصوتي", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        binding.buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Voice search button removed from home toolbar

        // Category STT button removed

        // Search and continuous speech recognizer removed as requested

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

    private fun launchVoiceSearch() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            return
        }
        try {
            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "تحدث للبحث الفوري أو الترجمة...")
            }
            voiceSearchLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "التعرف الصوتي غير متوفر على هذا الجهاز", Toast.LENGTH_SHORT).show()
        }
    }

    private var currentTab = 0

    private fun showTab(position: Int) {
        currentTab = position
        selectedCategory = "الكل" // إعادة ضبط الفلتر عند كل تبديل تبويب (كان يبقى من التبويب السابق ويُخفي أغلب المحتوى)
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
            val matchesCategory = selectedCategory == "الكل" || item.groupTitle.equals(selectedCategory, ignoreCase = true)
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
        binding.textContentCount.text = "$count $label"
    }

    private fun updateCategoriesChips(items: List<MediaEntry>) {
        binding.chipGroupCategories.removeAllViews()

        // عدد العناصر داخل كل تصنيف (لعرضه بجانب اسمه في الـ Chip)
        val countPerCategory = items
            .filter { !it.groupTitle.isNullOrBlank() }
            .groupingBy { it.groupTitle!! }
            .eachCount()

        val categories = mutableSetOf("الكل")
        items.forEach { if (!it.groupTitle.isNullOrBlank()) categories.add(it.groupTitle!!) }
        currentCategories = categories.toList()

        for (cat in currentCategories) {
            val count = if (cat == "الكل") items.size else (countPerCategory[cat] ?: 0)
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
            binding.recyclerContent.layoutManager = GridLayoutManager(this, 3).apply {
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

                binding.recyclerLatestMovies.layoutManager = GridLayoutManager(this@MainActivity, 4)
                binding.recyclerLatestMovies.adapter = PosterAdapter(items = movies.take(12), onClick = { entry ->
                    handleMediaClickAutoPlay(entry)
                })

                binding.recyclerLatestSeries.layoutManager = GridLayoutManager(this@MainActivity, 4)
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

    private fun loadLive() {
        val creds = requireCreds() ?: return
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    val categories = XtreamClient.fetchLiveCategories(creds.first, creds.second, creds.third)
                    XtreamClient.fetchLive(creds.first, creds.second, creds.third, categories)
                }
                displayMedia(items)
            } catch (e: Exception) {
                showLoadErrorDialog(e)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun loadVod() {
        val creds = requireCreds() ?: return
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    val categories = XtreamClient.fetchVodCategories(creds.first, creds.second, creds.third)
                    XtreamClient.fetchVod(creds.first, creds.second, creds.third, categories)
                }
                displayMedia(items)
            } catch (e: Exception) {
                showLoadErrorDialog(e)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun loadSeries() {
        val creds = requireCreds() ?: return
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    val categories = XtreamClient.fetchSeriesCategories(creds.first, creds.second, creds.third)
                    XtreamClient.fetchSeriesList(creds.first, creds.second, creds.third, categories)
                }
                displayMedia(items)
            } catch (e: Exception) {
                showLoadErrorDialog(e)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
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
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("تعذّر تحميل المحتوى")
            .setMessage(getString(R.string.load_error, e.message ?: "خطأ غير معروف"))
            .setPositiveButton("حسنًا", null)
            .show()
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
