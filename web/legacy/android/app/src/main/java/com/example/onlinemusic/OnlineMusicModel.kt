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

data class AlbumatyLink(
    val title: String,
    val url: String
)

data class AlbumatyHomeData(
    val categories: List<AlbumatyLink> = emptyList(),
    val albums: List<AlbumatyLink> = emptyList(),
    val songs: List<AlbumatyLink> = emptyList(),
    val artists: List<AlbumatyLink> = emptyList()
)

data class AlbumatySection(
    val title: String,
    val url: String,
    val content: List<AlbumatyLink> = emptyList()
) {
    val songs: List<AlbumatyLink> get() = content.filter { it.isSongPath() }
}

private fun AlbumatyLink.isSongPath(): Boolean = runCatching {
    java.net.URI(url).path.orEmpty().trim('/').lowercase().split('/').any { it == "song" || it.startsWith("song") }
}.getOrDefault(false)
