package com.example.model

import android.net.Uri

data class AudioItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri,
    val sizeBytes: Long = 0L
)

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<String> = emptyList()
)
