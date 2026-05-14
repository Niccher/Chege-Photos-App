package com.niccher.prjphotos.data

import androidx.room.*
import com.niccher.prjphotos.models.CachedPhoto
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM cached_photos ORDER BY taken_at DESC")
    fun getAllPhotos(): Flow<List<CachedPhoto>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<CachedPhoto>)

    @Query("DELETE FROM cached_photos")
    suspend fun clearAll()
    
    @Query("SELECT * FROM cached_photos ORDER BY taken_at DESC")
    suspend fun getAllPhotosOnce(): List<CachedPhoto>
    
    @Query("SELECT * FROM cached_photos WHERE id = :id")
    suspend fun getPhotoById(id: Int): CachedPhoto?
}

@Database(entities = [CachedPhoto::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "photo_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
