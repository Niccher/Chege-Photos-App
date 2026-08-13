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
    suspend fun getPhotoById(id: String): CachedPhoto?
}

@Database(entities = [CachedPhoto::class], version = 3, exportSchema = false)
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

        // Cache-only table: the ID column changed from INTEGER to TEXT to stop
        // non-numeric photo IDs collapsing to 0 (REPLACE collisions). Safe to drop
        // and rebuild since the cache is re-populated from the server on next sync.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS cached_photos")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS cached_photos (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "filename TEXT NOT NULL, " +
                        "path TEXT NOT NULL, " +
                        "thumbnail_path TEXT, " +
                        "size TEXT, " +
                        "taken_at TEXT, " +
                        "width INTEGER, " +
                        "height INTEGER, " +
                        "latitude TEXT, " +
                        "longitude TEXT, " +
                        "exif_data TEXT, " +
                        "mime_type TEXT, " +
                        "is_favorite INTEGER NOT NULL DEFAULT 0, " +
                        "album_id INTEGER, " +
                        "created_at TEXT, " +
                        "updated_at TEXT)"
                )
            }
        }

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "photo_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
