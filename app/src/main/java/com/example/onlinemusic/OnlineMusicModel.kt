package com.example.onlinemusic

data class OnlineMusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val streamUrl: String?,
    val downloadUrl: String?
)

data class OnlineMusicAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?
)

data class OnlineMusicArtist(
    val id: String,
    val name: String,
    val artworkUrl: String?
)
