package com.example.m3uplayer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val url: String,
    val name: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val profileId: Int
)
