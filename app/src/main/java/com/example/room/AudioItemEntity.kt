package com.example.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_items")
data class AudioItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uriString: String,
    val sizeBytes: Long
)
