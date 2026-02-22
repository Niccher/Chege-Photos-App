package com.niccher.prjphotos.models

import kotlinx.serialization.Serializable

@Serializable
data class AlbumListResponse(
    val status: String,
    val albums: List<Album>
)

@Serializable
data class Album(
    val id: String? = null,
    val user_id: String? = null,
    val name: String,
    val description: String? = null,
    val cover_photo: String? = null,
    val photo_count: String? = "0"
)
