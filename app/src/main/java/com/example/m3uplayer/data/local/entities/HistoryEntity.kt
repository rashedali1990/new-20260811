package com.example.m3uplayer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class HistoryEntity(
    @PrimaryKey val mediaId: String,
    val name: String,
    val lastWatched: Long = System.currentTimeMillis(),
    val position: Long = 0L
)
