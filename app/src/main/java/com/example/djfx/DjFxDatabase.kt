package com.example.djfx

import android.content.Context
import androidx.room.*

@Entity(tableName = "dj_fx")
data class DjFxEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val source: String,
    val license: String,
    val sourceUrl: String,
    val localUri: String?,
    val isFavorite: Boolean
)

@Entity(tableName = "dj_fx_pad")
data class DjFxPadEntity(
    @PrimaryKey val padKey: String, // Format: "{Bank}_{Index}", e.g. "A_0"
    val fxId: String
)

@Dao
interface DjFxDao {
    @Query("SELECT * FROM dj_fx")
    suspend fun getAllFx(): List<DjFxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFx(fx: DjFxEntity)

    @Query("SELECT * FROM dj_fx_pad")
    suspend fun getAllPads(): List<DjFxPadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPad(pad: DjFxPadEntity)

    @Query("DELETE FROM dj_fx_pad WHERE padKey = :padKey")
    suspend fun deletePad(padKey: String)
}

@Database(entities = [DjFxEntity::class, DjFxPadEntity::class], version = 1, exportSchema = false)
abstract class DjFxDatabase : RoomDatabase() {
    abstract fun djFxDao(): DjFxDao

    companion object {
        @Volatile
        private var INSTANCE: DjFxDatabase? = null

        fun getDatabase(context: Context): DjFxDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DjFxDatabase::class.java,
                    "dj_fx_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
