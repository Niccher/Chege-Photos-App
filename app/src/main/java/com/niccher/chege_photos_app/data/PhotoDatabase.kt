package com.niccher.chege_photos_app.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.niccher.chege_photos_app.models.CachedPhoto
import com.niccher.chege_photos_app.models.OfflineAction
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

    @Query("SELECT * FROM cached_photos WHERE sha256 = :sha256 LIMIT 1")
    suspend fun getPhotoBySha256(sha256: String): CachedPhoto?

    @Query("DELETE FROM cached_photos WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface OfflineActionDao {
    @Query("SELECT * FROM pending_actions ORDER BY timestamp ASC")
    suspend fun getAllPendingActions(): List<OfflineAction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: OfflineAction)

    @Query("DELETE FROM pending_actions WHERE localId = :localId")
    suspend fun deleteActionById(localId: Long)
}

@Database(entities = [CachedPhoto::class, OfflineAction::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun offlineActionDao(): OfflineActionDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_photos ADD COLUMN sha256 TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pending_actions (" +
                        "localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "photoId TEXT NOT NULL, " +
                        "actionType TEXT NOT NULL, " +
                        "timestamp INTEGER NOT NULL)"
                )
            }
        }

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "photo_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
