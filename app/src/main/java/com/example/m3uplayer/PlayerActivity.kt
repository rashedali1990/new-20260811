package com.example.m3uplayer

import android.os.Bundle
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
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

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STREAM_URL    = "extra_stream_url"
        const val EXTRA_STREAM_NAME   = "extra_stream_name"
        const val EXTRA_STREAM_ID     = "extra_stream_id"
        const val EXTRA_START_POSITION = "extra_start_position"
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private lateinit var historyManager: WatchHistoryManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        historyManager = WatchHistoryManager(this)
        val profileManager  = ProfileManager(this)
        val currentProfile  = profileManager.getLastUsedProfile()

        val streamUrl   = intent.getStringExtra(EXTRA_STREAM_URL) ?: run { finish(); return }
        val streamName  = intent.getStringExtra(EXTRA_STREAM_NAME) ?: getString(R.string.app_name)
        val startPosition = intent.getLongExtra(EXTRA_START_POSITION, 0L)

        title = streamName

        initializePlayer(
            streamUrl     = streamUrl,
            startPosition = startPosition,
            proxyHost     = currentProfile?.proxyHost,
            proxyPort     = currentProfile?.proxyPort ?: 0
        )

        binding.buttonExternalPlayer.setOnClickListener {
            showExternalPlayerDialog(streamUrl)
        }
    }

    private fun showExternalPlayerDialog(url: String) {
        val players = arrayOf("VLC Player", "MX Player", "مشغل خارجي آخر")
        androidx.appcompat.app.AlertDialog.Builder(this)
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
            enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
        }
    }

    private fun initializePlayer(
        streamUrl: String,
        startPosition: Long,
        proxyHost: String?,
        proxyPort: Int
    ) {
        val dataSourceFactory: DataSource.Factory = if (!proxyHost.isNullOrBlank() && proxyPort > 0) {
            // استخدام OkHttpDataSource مع بروكسي عبر OkHttpClient
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort))
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
        val streamUrl  = intent.getStringExtra(EXTRA_STREAM_URL) ?: ""
        val streamName = intent.getStringExtra(EXTRA_STREAM_NAME) ?: ""
        val streamId   = intent.getStringExtra(EXTRA_STREAM_ID) ?: streamUrl

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
        player?.release()
        player = null
    }
}
