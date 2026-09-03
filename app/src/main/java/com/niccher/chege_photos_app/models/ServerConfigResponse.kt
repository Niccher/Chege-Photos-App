package com.niccher.chege_photos_app.models

import kotlinx.serialization.Serializable

@Serializable
data class ServerConfigResponse(
    val status: String? = null,
    val data: ServerConfigData? = null
)

@Serializable
data class ServerConfigData(
    val app_name: String? = null,
    val support_email: String? = null,
    val max_upload_size_mb: Int = 500,
    val max_batch_upload_count: Int = 50,
    val allowed_extensions: String = "jpg,jpeg,png,webp,heic,tiff,mp4,mov,m4v,webm,mkv,avi",
    val default_storage_limit: Long = 1073741824L,
    val timezone: String? = null,
    val date_format: String? = null,
    val capabilities: ServerCapabilities? = null
)

@Serializable
data class ServerCapabilities(
    val video_upload: Boolean = true,
    val face_recognition: Boolean = true,
    val semantic_search: Boolean = true,
    val object_tagging: Boolean = true,
    val photo_editing: Boolean = false // Android photo editing is explicitly disabled
)
