package com.example.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val playlistId: String,
    val name: String,
    val songIdsJson: String,
    val timestamp: Long = System.currentTimeMillis()
)
