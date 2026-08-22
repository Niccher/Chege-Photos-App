package com.niccher.chege_photos_app.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "pending_actions")
@Serializable
data class OfflineAction(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val photoId: String,
    val actionType: String, // "FAVORITE", "UNFAVORITE", "ARCHIVE", "DELETE", "RESTORE"
    val timestamp: Long = System.currentTimeMillis()
)
