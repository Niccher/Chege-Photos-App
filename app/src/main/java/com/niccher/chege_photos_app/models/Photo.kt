package com.niccher.chege_photos_app.models

import kotlinx.serialization.Serializable

@Serializable
data class Photo(
    val id: String? = null,
    val user_id: String? = null,
    val album_id: String? = null,
    val filename: String,
    val path: String,
    val thumbnail_path: String? = null,
    val taken_at: String? = null,
    val width: String? = null,
    val height: String? = null,
    val size: String? = null,
    val mime_type: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val is_archived: String? = null,
    val is_favorite: String? = null,
    val is_deleted: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class PhotoListResponse(
    val status: String,
    val photos: List<Photo>
)
