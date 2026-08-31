package com.example.m3uplayer

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
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
    private var dataSourceFactory: DataSource.Factory? = null

    private var streamUrl: String = ""
    private var streamName: String = ""
    private var streamId: String = ""

    private val subtitleFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) { /* ليست كل مزوّدات الملفات تدعم صلاحية دائمة، لا بأس بتجاهل الخطأ */ }
            applyExternalSubtitle(it, guessSubtitleMimeType(it.toString()))
        }
    }

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
            getString(R.string.subtitle_track),
            getString(R.string.add_external_subtitle)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.player_settings)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showTrackOptions(C.TRACK_TYPE_VIDEO, getString(R.string.quality_selection))
                    1 -> showTrackOptions(C.TRACK_TYPE_AUDIO, getString(R.string.audio_track))
                    2 -> showTrackOptions(C.TRACK_TYPE_TEXT, getString(R.string.subtitle_track))
                    3 -> showAddSubtitleDialog()
                }
            }
            .show()
    }

    // ─── إضافة ترجمة خارجية (Caption) ──────────────────────────────────────────

    private fun showAddSubtitleDialog() {
        val options = arrayOf(
            getString(R.string.subtitle_from_url),
            getString(R.string.subtitle_from_device)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.add_external_subtitle)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSubtitleUrlInputDialog()
                    1 -> subtitleFilePickerLauncher.launch(arrayOf("text/*", "application/*", "application/x-subrip"))
                }
            }
            .show()
    }

    private fun showSubtitleUrlInputDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.subtitle_url_hint)
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.subtitle_from_url)
            .setView(input)
            .setPositiveButton(R.string.add_subtitle_confirm) { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    applyExternalSubtitle(Uri.parse(url), guessSubtitleMimeType(url))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun guessSubtitleMimeType(pathOrUrl: String): String = when {
        pathOrUrl.contains(".vtt", ignoreCase = true)  -> MimeTypes.TEXT_VTT
        pathOrUrl.contains(".ttml", ignoreCase = true) ||
        pathOrUrl.contains(".xml", ignoreCase = true)  -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP // SRT هو الأشيع، ونعتمده افتراضيًا
    }

    /**
     * يدمج ملف ترجمة خارجي (SRT/VTT، من رابط أو من الجهاز) مع مصدر الفيديو الحالي
     * عبر MergingMediaSource، مع الحفاظ على موضع التشغيل الحالي دون انقطاع محسوس.
     */
    private fun applyExternalSubtitle(uri: Uri, mimeType: String) {
        val exoPlayer = player ?: return
        val baseFactory = dataSourceFactory ?: return
        // مصدر بيانات يدعم كلاً من الروابط (http/https) والملفات المحلية (content://)،
        // بعكس مصدر الفيديو الذي يفترض رابطًا شبكيًا دائمًا
        val subtitleDataSourceFactory = DefaultDataSource.Factory(this, baseFactory)

        val resumePosition = exoPlayer.currentPosition

        val videoMediaItem = MediaItem.fromUri(streamUrl)
        val videoSource: MediaSource = when {
            streamUrl.contains(".m3u8", ignoreCase = true) ||
            streamUrl.contains("/live/",  ignoreCase = true) ->
                HlsMediaSource.Factory(baseFactory).createMediaSource(videoMediaItem)
            else ->
                ProgressiveMediaSource.Factory(baseFactory).createMediaSource(videoMediaItem)
        }

        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mimeType)
            .setLanguage("ar")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val subtitleSource = SingleSampleMediaSource.Factory(subtitleDataSourceFactory)
            .createMediaSource(subtitleConfig, C.TIME_UNSET)

        val mergedSource = MergingMediaSource(videoSource, subtitleSource)

        exoPlayer.setMediaSource(mergedSource)
        exoPlayer.prepare()
        exoPlayer.seekTo(resumePosition)
        exoPlayer.playWhenReady = true

        Toast.makeText(this, R.string.subtitle_added, Toast.LENGTH_SHORT).show()
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
        // بعض سيرفرات Xtream ترفض أو "تُعلّق" الطلبات ذات User-Agent غير معتاد
        // (خصوصًا للبث المباشر/المباريات، وهي أكثر مراقبة من الأفلام)، لذا نستخدم
        // نفس القيمة التي يرسلها VLC — الأكثر قبولًا عالميًا لدى هذه السيرفرات.
        val playerUserAgent = "VLC/3.0.18 LibVLC/3.0.18"

        val dataSourceFactory: DataSource.Factory = if (!proxyHost.isNullOrBlank() && proxyPort > 0) {
            // استخدام OkHttpDataSource مع بروكسي عبر OkHttpClient (HTTP أو SOCKS5)
            val type = if (proxyType.equals("SOCKS5", ignoreCase = true)) Proxy.Type.SOCKS else Proxy.Type.HTTP
            val proxy = Proxy(type, InetSocketAddress(proxyHost, proxyPort))
            val okHttpClient = okhttp3.OkHttpClient.Builder().proxy(proxy).build()
            androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent(playerUserAgent)
        } else {
            DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent(playerUserAgent)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
        }

        setupPlayer(streamUrl, startPosition, dataSourceFactory)
    }

    private fun setupPlayer(
        streamUrl: String,
        startPosition: Long,
        dataSourceFactory: DataSource.Factory
    ) {
        this.dataSourceFactory = dataSourceFactory
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
            exoPlayer.addListener(playerListener)
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.seekTo(startPosition)
            exoPlayer.playWhenReady = true
        }

        startBufferingWatchdog()
    }

    // ─── معالجة تعليق التحميل والأخطاء ─────────────────────────────────────────
    // بدون هذا، فشل صامت في الاتصال (مثال: السيرفر يرفض الطلب بصمت أو يُعلّقه)
    // يترك المستخدم أمام شاشة تحميل دائمة بلا أي مؤشر خطأ أو خيار لإعادة المحاولة.

    private val bufferingWatchdogHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var bufferingWatchdogRunnable: Runnable? = null

    private val playerListener = object : androidx.media3.common.Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                androidx.media3.common.Player.STATE_READY -> cancelBufferingWatchdog()
                androidx.media3.common.Player.STATE_BUFFERING -> startBufferingWatchdog()
                androidx.media3.common.Player.STATE_ENDED -> cancelBufferingWatchdog()
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            cancelBufferingWatchdog()
            showPlaybackErrorDialog(error.message ?: "خطأ غير معروف")
        }
    }

    /** إن بقي المشغل في حالة "تحميل" لأكثر من 18 ثانية متواصلة، نعتبره عالقًا ونعرض خيار إعادة المحاولة. */
    private fun startBufferingWatchdog() {
        cancelBufferingWatchdog()
        bufferingWatchdogRunnable = Runnable {
            if (player?.playbackState == androidx.media3.common.Player.STATE_BUFFERING) {
                showPlaybackErrorDialog("لم يستجب البث خلال وقت كافٍ (قد يكون الخادم بطيئًا أو القناة غير متاحة حاليًا)")
            }
        }
        bufferingWatchdogHandler.postDelayed(bufferingWatchdogRunnable!!, 18000)
    }

    private fun cancelBufferingWatchdog() {
        bufferingWatchdogRunnable?.let { bufferingWatchdogHandler.removeCallbacks(it) }
    }

    private fun showPlaybackErrorDialog(message: String) {
        if (isFinishing) return
        AlertDialog.Builder(this)
            .setTitle("تعذّر تشغيل البث")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("إعادة المحاولة") { _, _ ->
                retryPlayback()
            }
            .setNegativeButton("رجوع") { _, _ -> finish() }
            .show()
    }

    private fun retryPlayback() {
        val factory = dataSourceFactory ?: return
        player?.removeListener(playerListener)
        player?.release()
        setupPlayer(streamUrl, 0L, factory)
    }

    override fun onStop() {
        super.onStop()
        cancelBufferingWatchdog()
        saveCurrentPosition()
        player?.removeListener(playerListener)
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
        cancelBufferingWatchdog()
        player?.removeListener(playerListener)
        player?.release()
        player = null
    }
}
