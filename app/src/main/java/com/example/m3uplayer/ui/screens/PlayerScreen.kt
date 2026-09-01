package com.example.m3uplayer.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.common.MediaItem
import com.example.m3uplayer.data.local.entities.ChannelEntity
import com.example.m3uplayer.ui.viewmodels.PlayerViewModel

@Composable
fun PlayerScreen(channel: ChannelEntity, viewModel: PlayerViewModel) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val program by viewModel.currentProgram.collectAsState()

    LaunchedEffect(channel) {
        val exoPlayer = PlayerComponent.createOptimizedPlayer(context).apply {
            setMediaItem(MediaItem.fromUri(channel.url))
            prepare()
            play()
        }
        player = exoPlayer
        viewModel.loadEpg(channel.url)
    }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.player = player
            }
        )

        // EPG Overlay
        program?.let {
            Surface(
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(16.dp),
                color = Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = it.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(text = it.description, color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
