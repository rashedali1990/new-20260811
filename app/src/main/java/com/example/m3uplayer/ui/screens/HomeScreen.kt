package com.example.m3uplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.example.m3uplayer.data.local.entities.ChannelEntity
import com.example.m3uplayer.ui.viewmodels.ChannelListViewModel

@Composable
fun HomeScreen(viewModel: ChannelListViewModel, onChannelClick: (ChannelEntity) -> Unit) {
    val channels by viewModel.channels.collectAsState()
    val profile by viewModel.activeProfile.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Channels",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (profile == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Please login first")
            }
        } else {
            LazyColumn {
                items(channels) { channel ->
                    ChannelItem(channel, onFavoriteClick = {
                        viewModel.toggleFavorite(channel)
                    }, onClick = {
                        onChannelClick(channel)
                    })
                }
            }
        }
    }
}

@Composable
fun ChannelItem(channel: ChannelEntity, onFavoriteClick: () -> Unit, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = channel.name, style = MaterialTheme.typography.bodyLarge)
                Text(text = channel.groupTitle ?: "Default", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            IconButton(onClick = onFavoriteClick) {
                Text("⭐") // Simplified icon
            }
        }
    }
}
