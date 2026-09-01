package com.example.m3uplayer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val mediaId: String,
    val name: String,
    val url: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val timestamp: Long = System.currentTimeMillis()
)
