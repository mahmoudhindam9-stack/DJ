package com.example.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioItemDao {
    @Query("SELECT * FROM audio_items")
    suspend fun getAllAudioItems(): List<AudioItemEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAudioItems(items: List<AudioItemEntity>)

    @Query("DELETE FROM audio_items WHERE id = :id")
    suspend fun deleteAudioItem(id: String)
    
    @Query("SELECT COUNT(*) FROM audio_items")
    suspend fun getCount(): Int
}
