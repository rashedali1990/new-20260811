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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SERVER     = "extra_server"
        const val EXTRA_USERNAME   = "extra_username"
        const val EXTRA_PASSWORD   = "extra_password"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        const val GRID_SPAN_COUNT  = 4
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

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadPlaylistFromUri(it) }
    }

    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.getOrNull(0)
            spokenText?.let {
                binding.searchView.setQuery(it, true)
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

        val welcomeName = currentProfile?.profileName ?: "Guest"
        binding.textWelcome.text        = "مرحباً بك، $welcomeName"
        binding.textCurrentProfile.text = welcomeName

        binding.buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.buttonVoiceSearch.setOnClickListener {
            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "تحدث الآن للبحث...")
            }
            voiceSearchLauncher.launch(intent)
        }

        binding.searchView.setOnQueryTextListener(
            object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    filterItems(query); return true
                }
                override fun onQueryTextChange(newText: String?): Boolean {
                    filterItems(newText); return true
                }
            }
        )

        binding.recyclerContent.layoutManager = LinearLayoutManager(this)

        binding.buttonLoadUrl.setOnClickListener {
            val url = binding.editPlaylistUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, R.string.enter_url_hint, Toast.LENGTH_SHORT).show()
            } else {
                loadManualPlaylist(url)
            }
        }

        binding.buttonPickFile.setOnClickListener {
            filePickerLauncher.launch(
                arrayOf("audio/x-mpegurl", "application/x-mpegURL", "*/*")
            )
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home   -> { showTab(0); true }
                R.id.nav_live   -> { showTab(1); true }
                R.id.nav_movies -> { showTab(2); true }
                R.id.nav_series -> { showTab(3); true }
                R.id.nav_manual -> { showTab(4); true }
                else -> false
            }
        }

        showTab(0)
    }

    // ─── عرض التبويبات ───────────────────────────────────────────────────────

    private var currentTab = 0

    private fun showTab(position: Int) {
        currentTab = position
        // إخفاء كل المناطق أولاً
        binding.dashboardLayout.visibility = View.GONE
        binding.recyclerContent.visibility = View.GONE
        binding.manualLayout.visibility    = View.GONE

        when (position) {
            0 -> { binding.dashboardLayout.visibility = View.VISIBLE; loadDashboard() }
            1 -> { binding.recyclerContent.visibility = View.VISIBLE; loadLive() }
            2 -> { binding.recyclerContent.visibility = View.VISIBLE; loadVod() }
            3 -> { binding.recyclerContent.visibility = View.VISIBLE; loadSeries() }
            4 -> { binding.manualLayout.visibility    = View.VISIBLE }
        }
    }

    // ─── تصفية ───────────────────────────────────────────────────────────────

    private fun filterItems(query: String?) {
        val filtered = if (query.isNullOrBlank()) allMediaItems
        else allMediaItems.filter { it.title.contains(query, ignoreCase = true) }
        updateAdapter(filtered)
    }

    private fun updateAdapter(items: List<MediaEntry>) {
        if (currentTab == 2 || currentTab == 3) {
            // الأفلام والمسلسلات: شبكة بوسترات (4 أعمدة أفقيًا وعموديًا)
            binding.recyclerContent.layoutManager = GridLayoutManager(this, GRID_SPAN_COUNT)
            binding.recyclerContent.adapter = PosterAdapter(
                items       = items,
                onClick     = { entry -> handleMediaClick(entry) },
                onLongClick = { entry ->
                    favoritesManager.toggleFavorite(entry.id)
                    val messageRes = if (favoritesManager.isFavorite(entry.id))
                        R.string.added_to_favorites else R.string.removed_from_favorites
                    Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            // البث المباشر: قائمة نصية (تدعم زر المفضلة المباشر)
            binding.recyclerContent.layoutManager = LinearLayoutManager(this)
            binding.recyclerContent.adapter = MediaAdapter(
                items          = items,
                onClick        = { entry -> handleMediaClick(entry) },
                onFavoriteClick = { entry ->
                    favoritesManager.toggleFavorite(entry.id)
                    if (favoritesManager.isFavorite(entry.id)) {
                        notificationHelper.sendReminderNotification(
                            title     = entry.title,
                            message   = "تم إضافة ${entry.title} إلى المفضلة",
                            streamUrl = entry.playUrl ?: ""
                        )
                    }
                    filterItems(binding.searchView.query.toString())
                },
                isFavorite = { entry -> favoritesManager.isFavorite(entry.id) }
            )
        }
    }

    // ─── معالجة النقر على عنصر ────────────────────────────────────────────────

    private fun handleMediaClick(entry: MediaEntry) {
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
            } else if (entry.playUrl != null) {
                openPlayer(entry.title, entry.playUrl, entry.id)
            }
        }
    }

    // ─── الرقابة الأبوية ──────────────────────────────────────────────────────

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

    // ─── تحميل المحتوى ────────────────────────────────────────────────────────

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

                // استكمال المشاهدة
                val history = watchHistoryManager.getHistory().take(3)
                binding.recyclerContinueWatching.adapter = ContinueWatchingAdapter(history) { item ->
                    if (!item.url.isNullOrEmpty()) {
                        openPlayer(item.title, item.url, item.id)
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "رابط التشغيل غير متوفر لهذا العنصر",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                // البطل المميز
                if (movies.isNotEmpty()) {
                    val featured = movies[0]
                    binding.featuredTitle.text = featured.title
                    com.bumptech.glide.Glide.with(this@MainActivity)
                        .load(featured.imageUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .into(binding.featuredImage)
                }

                // أحدث الأفلام
                binding.recyclerLatestMovies.layoutManager = GridLayoutManager(this@MainActivity, GRID_SPAN_COUNT)
                binding.recyclerLatestMovies.adapter = PosterAdapter(movies.take(12)) { entry ->
                    handleMediaClick(entry)
                }

                // أحدث المسلسلات
                binding.recyclerLatestSeries.layoutManager = GridLayoutManager(this@MainActivity, GRID_SPAN_COUNT)
                binding.recyclerLatestSeries.adapter = PosterAdapter(series.take(12)) { entry ->
                    handleMediaClick(entry)
                }

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "خطأ في تحميل لوحة التحكم: ${e.message}", Toast.LENGTH_LONG).show()
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

    private fun displayMedia(items: List<MediaEntry>) {
        allMediaItems.clear()
        allMediaItems.addAll(items)
        if (items.isEmpty()) {
            Toast.makeText(this, R.string.no_channels_found, Toast.LENGTH_SHORT).show()
        }
        updateAdapter(items)
    }

    // ─── أدوات مساعدة ────────────────────────────────────────────────────────

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
        // استكمال المشاهدة تلقائيًا: إن وُجد تقدّم محفوظ لنفس العنصر، نبدأ منه مباشرة
        val savedPosition = watchHistoryManager.getHistory().find { it.id == streamId }?.position ?: 0L
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STREAM_URL,      url)
            putExtra(PlayerActivity.EXTRA_STREAM_NAME,     name)
            putExtra(PlayerActivity.EXTRA_STREAM_ID,       streamId)
            putExtra(PlayerActivity.EXTRA_START_POSITION,  savedPosition)
        }
        startActivity(intent)
    }

    // ─── تحميل قوائم يدوية ───────────────────────────────────────────────────

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
        binding.manualLayout.visibility    = View.GONE
        binding.recyclerContent.visibility = View.VISIBLE
        binding.recyclerContent.adapter = ChannelAdapter(channels) { channel ->
            checkParentalLock(channel.groupTitle) {
                openPlayer(channel.name, channel.url)
            }
        }
    }
}
