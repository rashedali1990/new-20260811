package com.example.m3uplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
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

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SERVER     = "extra_server"
        const val EXTRA_USERNAME   = "extra_username"
        const val EXTRA_PASSWORD   = "extra_password"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        const val GRID_SPAN_COUNT  = 3
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

    private var previewPlayer: ExoPlayer? = null

    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.getOrNull(0)
            spokenText?.let {
                binding.searchView.setQuery(it, true)
                binding.categorySearchView.setQuery(it, true)
                filterItems(it)
            }
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

        binding.buttonVoiceSearch.setOnClickListener {
            launchVoiceSearch()
        }

        binding.buttonCategoryStt.setOnClickListener {
            launchVoiceSearch()
        }

        val globalSearchListener = object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean { filterItems(query); return true }
            override fun onQueryTextChange(newText: String?): Boolean { filterItems(newText); return true }
        }
        binding.searchView.setOnQueryTextListener(globalSearchListener)
        binding.categorySearchView.setOnQueryTextListener(globalSearchListener)

        previewPlayer = ExoPlayer.Builder(this).build().also {
            binding.previewPlayerView.player = it
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

    private fun launchVoiceSearch() {
        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "تحدث للبحث الفوري أو الترجمة...")
        }
        voiceSearchLauncher.launch(intent)
    }

    private var currentTab = 0

    private fun showTab(position: Int) {
        currentTab = position
        binding.dashboardLayout.visibility = View.GONE
        binding.splitScreenLayout.visibility = View.GONE

        when (position) {
            0 -> {
                binding.dashboardLayout.visibility = View.VISIBLE
                loadDashboard()
                stopPreviewPlayer()
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

    private fun stopPreviewPlayer() {
        previewPlayer?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        previewPlayer?.release()
    }

    private fun filterItems(query: String?) {
        val queryText = query ?: ""
        val filtered = allMediaItems.filter { item ->
            val matchesQuery = queryText.isBlank() || item.title.contains(queryText, ignoreCase = true)
            val matchesCategory = selectedCategory == "الكل" || item.groupTitle.equals(selectedCategory, ignoreCase = true)
            val matchesFavorite = currentTab != 4 || favoritesManager.isFavorite(item.id)
            matchesQuery && matchesCategory && matchesFavorite
        }
        updateAdapter(filtered)
    }

    private fun updateCategoriesChips(items: List<MediaEntry>) {
        binding.chipGroupCategories.removeAllViews()
        val categories = mutableSetOf("الكل")
        items.forEach { if (!it.groupTitle.isNullOrBlank()) categories.add(it.groupTitle!!) }
        currentCategories = categories.toList()

        for (cat in currentCategories) {
            val chip = Chip(this).apply {
                text = cat
                isCheckable = true
                isChecked = (cat == selectedCategory)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedCategory = cat
                        filterItems(binding.categorySearchView.query.toString())
                    }
                }
            }
            binding.chipGroupCategories.addView(chip)
        }
    }

    private fun updateAdapter(items: List<MediaEntry>) {
        if (currentTab == 2 || currentTab == 3) {
            binding.recyclerContent.layoutManager = GridLayoutManager(this, 2)
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
                    else filterItems(binding.categorySearchView.query.toString())
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
                binding.previewTitleText.text = "يعمل الآن: ${entry.title}"
                previewPlayer?.setMediaItem(MediaItem.fromUri(entry.playUrl!!))
                previewPlayer?.prepare()
                previewPlayer?.play()
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
                    XtreamClient.fetchVod(creds.first, creds.second, creds.third)
                }
                val series = withContext(Dispatchers.IO) {
                    XtreamClient.fetchSeriesList(creds.first, creds.second, creds.third)
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
                    XtreamClient.fetchLive(creds.first, creds.second, creds.third)
                }
                displayMedia(items)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, getString(R.string.load_error, e.message), Toast.LENGTH_LONG).show()
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
                    XtreamClient.fetchVod(creds.first, creds.second, creds.third)
                }
                displayMedia(items)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, getString(R.string.load_error, e.message), Toast.LENGTH_LONG).show()
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
                    XtreamClient.fetchSeriesList(creds.first, creds.second, creds.third)
                }
                displayMedia(items)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, getString(R.string.load_error, e.message), Toast.LENGTH_LONG).show()
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
                val live = withContext(Dispatchers.IO) { XtreamClient.fetchLive(creds.first, creds.second, creds.third) }
                val vod = withContext(Dispatchers.IO) { XtreamClient.fetchVod(creds.first, creds.second, creds.third) }
                val series = withContext(Dispatchers.IO) { XtreamClient.fetchSeriesList(creds.first, creds.second, creds.third) }
                
                val favoriteIds = favoritesManager.getFavoriteIds()
                val allItems = live + vod + series
                val favorites = allItems.filter { favoriteIds.contains(it.id) }
                
                displayMedia(favorites)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "خطأ في تحميل المفضلة: ${e.message}", Toast.LENGTH_LONG).show()
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
