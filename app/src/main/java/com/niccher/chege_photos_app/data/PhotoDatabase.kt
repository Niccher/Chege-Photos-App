package com.niccher.chege_photos_app.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.niccher.chege_photos_app.models.CachedPhoto
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

@Database(entities = [CachedPhoto::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_photos ADD COLUMN exif_data TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "photo_database"
                ).addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
