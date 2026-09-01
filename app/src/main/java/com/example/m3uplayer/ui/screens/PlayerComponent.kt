package com.example.m3uplayer.ui.screens

import android.content.Context
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultHttpDataSource

object PlayerComponent {
    fun createOptimizedPlayer(context: Context): ExoPlayer {
        // Custom LoadControl for aggressive buffering
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000, // Min buffer
                60_000, // Max buffer
                2_500,   // Buffer for playback
                5_000    // Buffer for re-buffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
    }
}
