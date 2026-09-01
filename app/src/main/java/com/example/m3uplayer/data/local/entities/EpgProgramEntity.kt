package com.example.m3uplayer.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "epg_programs",
    indices = [Index(value = ["channelId", "startTime"])]
)
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val channelId: String,
    val title: String,
    val description: String,
    val startTime: String,
    val endTime: String,
    val date: String
)
