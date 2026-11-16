package com.example.room

import kotlinx.coroutines.flow.Flow

class PlaylistRepository(private val playlistDao: PlaylistDao) {
    val allPlaylists: Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    suspend fun insert(playlist: PlaylistEntity) {
        playlistDao.insertPlaylist(playlist)
    }

    suspend fun delete(playlistId: String) {
        playlistDao.deletePlaylistByPlaylistId(playlistId)
    }

    suspend fun updateSongs(playlistId: String, songIdsJson: String) {
        playlistDao.updatePlaylistSongs(playlistId, songIdsJson)
    }
}
