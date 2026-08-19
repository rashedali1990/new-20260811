package com.example.m3uplayer

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.m3uplayer.databinding.ActivityPlayerBinding
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Locale

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STREAM_URL     = "extra_stream_url"
        const val EXTRA_STREAM_NAME    = "extra_stream_name"
        const val EXTRA_STREAM_ID      = "extra_stream_id"
        const val EXTRA_START_POSITION = "extra_start_position"
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private lateinit var historyManager: WatchHistoryManager

    private var streamUrl: String = ""
    private var streamName: String = ""
    private var streamId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enterImmersiveFullScreen()

        historyManager = WatchHistoryManager(this)
        val profileManager  = ProfileManager(this)
        val currentProfile  = profileManager.getLastUsedProfile()

        streamUrl   = intent.getStringExtra(EXTRA_STREAM_URL) ?: run { finish(); return }
        streamName  = intent.getStringExtra(EXTRA_STREAM_NAME) ?: getString(R.string.app_name)
        streamId    = intent.getStringExtra(EXTRA_STREAM_ID) ?: streamUrl
        val startPosition = intent.getLongExtra(EXTRA_START_POSITION, 0L)

        title = streamName

        initializePlayer(
            streamUrl     = streamUrl,
            startPosition = startPosition,
            proxyHost     = currentProfile?.proxyHost,
            proxyPort     = currentProfile?.proxyPort ?: 0,
            proxyType     = currentProfile?.proxyType ?: "HTTP"
        )

        binding.buttonExternalPlayer.setOnClickListener {
            showExternalPlayerDialog(streamUrl)
        }

        binding.buttonPlayerSettings.setOnClickListener {
            showPlayerSettingsMenu()
        }

        binding.buttonPlayerVoiceStt.setOnClickListener {
            showLanguageSelectionDialog()
        }

        setupPlayerSpeechRecognizer()
    }

    private var speechRecognizer: android.speech.SpeechRecognizer? = null
    private var speechRecognizerIntent: android.content.Intent? = null

    private fun setupPlayerSpeechRecognizer() {
        if (android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizerIntent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "تحدث للترجمة أو البحث الفوري...")
            }

            speechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {}
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val spokenText = matches[0]
                        binding.textPlayerSubtitle.visibility = android.view.View.VISIBLE
                        binding.textPlayerSubtitle.text = spokenText
                        
                        // Hide subtitle after 5 seconds
                        binding.textPlayerSubtitle.postDelayed({
                            binding.textPlayerSubtitle.visibility = android.view.View.GONE
                        }, 5000)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        binding.textPlayerSubtitle.visibility = android.view.View.VISIBLE
                        binding.textPlayerSubtitle.text = matches[0]
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun showLanguageSelectionDialog() {
        val languages = arrayOf(
            "العربية (Arabic)",
            "اللغة الكورية (Korean - 한국어)",
            "اللغة الصينية (Chinese - 中文)",
            "اللغة الإنجليزية (English)"
        )
        val codes = arrayOf("ar-SA", "ko-KR", "zh-CN", "en-US")

        AlertDialog.Builder(this)
            .setTitle("اختر لغة الحديث للترجمة الفورية")
            .setItems(languages) { _, which ->
                val selectedLang = codes[which]
                startPlayerVoiceRecognition(selectedLang, languages[which])
            }
            .show()
    }

    private fun startPlayerVoiceRecognition(languageCode: String, langName: String) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 101)
            return
        }
        try {
            speechRecognizer?.destroy()
            speechRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this)
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "تحدث باللغة المحددة للترجمة...")
            }

            speechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {}
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        binding.textPlayerSubtitle.visibility = android.view.View.VISIBLE
                        binding.textPlayerSubtitle.text = matches[0]
                        binding.textPlayerSubtitle.postDelayed({
                            binding.textPlayerSubtitle.visibility = android.view.View.GONE
                        }, 5000)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        binding.textPlayerSubtitle.visibility = android.view.View.VISIBLE
                        binding.textPlayerSubtitle.text = matches[0]
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer?.startListening(intent)
            Toast.makeText(this, "جاري الاستماع ($langName) للترجمة للعربية...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر تشغيل التعرف الصوتي", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── ملء الشاشة الغامر (Immersive Full Screen) ────────────────────────────

    private fun enterImmersiveFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // فيديو بحجم الشاشة الكاملة: نفضّل الوضع الأفقي، مع السماح بالدوران بين وضعي landscape
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveFullScreen()
    }

    // ─── قائمة إعدادات المشغل (جودة / صوت / ترجمة) ────────────────────────────

    private fun showPlayerSettingsMenu() {
        val options = arrayOf(
            getString(R.string.quality_selection),
            getString(R.string.audio_track),
            getString(R.string.subtitle_track)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.player_settings)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showTrackOptions(C.TRACK_TYPE_VIDEO, getString(R.string.quality_selection))
                    1 -> showTrackOptions(C.TRACK_TYPE_AUDIO, getString(R.string.audio_track))
                    2 -> showTrackOptions(C.TRACK_TYPE_TEXT, getString(R.string.subtitle_track))
                }
            }
            .show()
    }

    private fun showTrackOptions(trackType: Int, title: String) {
        val exoPlayer = player ?: return
        val relevantGroups = exoPlayer.currentTracks.groups.filter { it.type == trackType }

        if (relevantGroups.none { it.length > 0 }) {
            Toast.makeText(this, R.string.no_tracks_available, Toast.LENGTH_SHORT).show()
            return
        }

        // العنصر الأول دائمًا "تلقائي" (يلغي أي تحديد يدوي سابق)
        val labels = mutableListOf(getString(R.string.track_auto))
        val selections = mutableListOf<Pair<TrackGroup, Int>?>(null)

        // بالنسبة للترجمة فقط: خيار إضافي لإيقافها تمامًا
        val offIndex = if (trackType == C.TRACK_TYPE_TEXT) {
            labels.add(getString(R.string.track_off))
            selections.add(null)
            1
        } else -1

        relevantGroups.forEach { group ->
            val mediaGroup = group.mediaTrackGroup
            for (i in 0 until mediaGroup.length) {
                val format = mediaGroup.getFormat(i)
                val label = when (trackType) {
                    C.TRACK_TYPE_VIDEO -> when {
                        format.height > 0 -> "${format.height}p"
                        format.bitrate > 0 -> "${format.bitrate / 1000} kbps"
                        else -> "${getString(R.string.quality_selection)} ${labels.size}"
                    }
                    else -> format.language?.let { languageDisplayName(it) }
                        ?: format.label
                        ?: "$title ${labels.size}"
                }
                labels.add(label)
                selections.add(mediaGroup to i)
            }
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, which ->
                applyTrackSelection(trackType, selections[which], disable = which == offIndex)
            }
            .show()
    }

    private fun languageDisplayName(code: String): String = try {
        Locale(code).displayLanguage.ifBlank { code }
    } catch (e: Exception) {
        code
    }

    private fun applyTrackSelection(trackType: Int, selection: Pair<TrackGroup, Int>?, disable: Boolean) {
        val exoPlayer = player ?: return
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
        builder.clearOverridesOfType(trackType)
        builder.setTrackTypeDisabled(trackType, disable)
        if (!disable && selection != null) {
            val (trackGroup, index) = selection
            builder.addOverride(TrackSelectionOverride(trackGroup, index))
        }
        exoPlayer.trackSelectionParameters = builder.build()
    }

    // ─── مشغل خارجي ──────────────────────────────────────────────────────────

    private fun showExternalPlayerDialog(url: String) {
        val players = arrayOf("VLC Player", "MX Player", "مشغل خارجي آخر")
        AlertDialog.Builder(this)
            .setTitle("اختر مشغل خارجي")
            .setItems(players) { _, which ->
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                intent.setDataAndType(android.net.Uri.parse(url), "video/*")
                when (which) {
                    0 -> intent.setPackage("org.videolan.vlc")
                    1 -> intent.setPackage("com.mxtech.videoplayer.ad")
                    // الخيار 2 بدون package محدد يفتح قائمة التطبيقات
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "المشغل غير مثبت على جهازك", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
            } catch (e: Exception) {
                // Fallback for some devices or build issues
            }
        }
    }

    // ─── إعداد المشغل ────────────────────────────────────────────────────────

    private fun initializePlayer(
        streamUrl: String,
        startPosition: Long,
        proxyHost: String?,
        proxyPort: Int,
        proxyType: String = "HTTP"
    ) {
        val dataSourceFactory: DataSource.Factory = if (!proxyHost.isNullOrBlank() && proxyPort > 0) {
            // استخدام OkHttpDataSource مع بروكسي عبر OkHttpClient (HTTP أو SOCKS5)
            val type = if (proxyType.equals("SOCKS5", ignoreCase = true)) Proxy.Type.SOCKS else Proxy.Type.HTTP
            val proxy = Proxy(type, InetSocketAddress(proxyHost, proxyPort))
            val okHttpClient = okhttp3.OkHttpClient.Builder().proxy(proxy).build()
            androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent("M3UPlayer/1.0")
        } else {
            DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("M3UPlayer/1.0")
        }

        setupPlayer(streamUrl, startPosition, dataSourceFactory)
    }

    private fun setupPlayer(
        streamUrl: String,
        startPosition: Long,
        dataSourceFactory: DataSource.Factory
    ) {
        val mediaItem = MediaItem.fromUri(streamUrl)

        val mediaSource: MediaSource = when {
            streamUrl.contains(".m3u8", ignoreCase = true) ||
            streamUrl.contains("/live/",  ignoreCase = true) ->
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            else ->
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }

        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            binding.playerView.player = exoPlayer
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.seekTo(startPosition)
            exoPlayer.playWhenReady = true
        }
    }

    override fun onStop() {
        super.onStop()
        saveCurrentPosition()
        player?.release()
        player = null
    }

    private fun saveCurrentPosition() {
        val currentPos = player?.currentPosition ?: 0L
        val duration   = player?.duration ?: 0L

        if (duration > 0) {
            historyManager.saveProgress(
                id       = streamId,
                title    = streamName,
                imageUrl = null,
                position = currentPos,
                duration = duration,
                url      = streamUrl
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
        player?.release()
        player = null
    }
}
