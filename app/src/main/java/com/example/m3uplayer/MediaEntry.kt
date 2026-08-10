package com.example.m3uplayer

data class MediaEntry(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val playUrl: String? = null,
    val isSeries: Boolean = false,
    val groupTitle: String? = null,
    val imageUrl: String? = null
)
