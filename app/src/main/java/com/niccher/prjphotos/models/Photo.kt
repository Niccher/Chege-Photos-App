package com.niccher.prjphotos.models

import kotlinx.serialization.Serializable

@Serializable
data class Photo(
    val id: Int? = null,
    val user_id: Int? = null,
    val filename: String,
    val path: String,
    val thumbnail_path: String? = null,
    val taken_at: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val size: Long? = null,
    val mime_type: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val is_archived: Boolean = false,
    val is_favorite: Boolean = false
)

@Serializable
data class PhotoListResponse(
    val status: String,
    val photos: List<Photo>
)
