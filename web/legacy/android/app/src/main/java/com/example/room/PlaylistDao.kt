package com.example.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY timestamp DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylistByPlaylistId(playlistId: String)

    @Query("UPDATE playlists SET songIdsJson = :songIdsJson WHERE playlistId = :playlistId")
    suspend fun updatePlaylistSongs(playlistId: String, songIdsJson: String)
}
