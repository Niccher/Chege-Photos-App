package com.niccher.prjphotos.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "cached_photos")
@Serializable
data class CachedPhoto(
    @PrimaryKey val id: Int,
    val filename: String,
    val path: String,
    val thumbnail_path: String?,
    val size: String?,
    val taken_at: String?,
    val width: Int?,
    val height: Int?,
    val latitude: String?,
    val longitude: String?,
    val mime_type: String?,
    val is_favorite: Int = 0,
    val album_id: Int? = null,
    val created_at: String? = null,
    val updated_at: String? = null
) {
    fun toPhoto() = Photo(
        id = id.toString(),
        filename = filename,
        path = path,
        thumbnail_path = thumbnail_path,
        size = size,
        taken_at = taken_at,
        width = width?.toString(),
        height = height?.toString(),
        latitude = latitude,
        longitude = longitude,
        mime_type = mime_type,
        is_favorite = is_favorite.toString(),
        album_id = album_id?.toString(),
        created_at = created_at,
        updated_at = updated_at
    )
}

fun Photo.toCachedPhoto() = CachedPhoto(
    id = id?.toIntOrNull() ?: 0,
    filename = filename,
    path = path,
    thumbnail_path = thumbnail_path,
    size = size,
    taken_at = taken_at,
    width = width?.toIntOrNull(),
    height = height?.toIntOrNull(),
    latitude = latitude,
    longitude = longitude,
    mime_type = mime_type,
    is_favorite = is_favorite?.toIntOrNull() ?: 0,
    album_id = album_id?.toIntOrNull(),
    created_at = created_at,
    updated_at = updated_at
)
