package com.niccher.chege_photos_app.models

import kotlinx.serialization.Serializable

@Serializable
data class FaceBbox(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val w: Double = 0.0,
    val h: Double = 0.0
)

@Serializable
data class FaceData(
    val face_id: Int = 0,
    val photo_id: Int = 0,
    val person_id: Int? = null,
    val person_name: String? = null,
    val bbox: FaceBbox = FaceBbox(),
    val detection_score: Double = 0.0,
    val age: Int? = null,
    val gender: String? = null
)

@Serializable
data class FacesListResponse(
    val status: String = "",
    val faces: List<FaceData> = emptyList()
)

@Serializable
data class FaceSearchResult(
    val face_id: Int = 0,
    val photo_id: Int = 0,
    val person_id: Int? = null,
    val person_name: String? = null,
    val score: Double = 0.0,
    val bbox: FaceBbox? = null
)

@Serializable
data class FaceSearchResponse(
    val status: String = "",
    val data: FaceSearchData? = null
)

@Serializable
data class FaceSearchData(
    val results: List<FaceSearchResult> = emptyList()
)

@Serializable
data class PersonData(
    val id: Int = 0,
    val name: String? = null,
    val cluster_label: Int? = null,
    val face_count: Int = 0,
    val thumbnail_face_id: Int? = null,
    val thumbnail: PersonThumbnail? = null
)

@Serializable
data class PersonThumbnail(
    val path: String = "",
    val thumbnail_path: String? = null,
    val bbox_x: Double = 0.0,
    val bbox_y: Double = 0.0,
    val bbox_w: Double = 0.0,
    val bbox_h: Double = 0.0,
    val photo_width: Double = 800.0,
    val photo_height: Double = 600.0
)

@Serializable
data class PersonsListResponse(
    val status: String = "",
    val persons: List<PersonData> = emptyList()
)

@Serializable
data class PersonPhoto(
    val id: Int = 0,
    val path: String = "",
    val thumbnail_path: String? = null,
    val filename: String = "",
    val width: Int? = null,
    val height: Int? = null
)

@Serializable
data class PersonPhotosResponse(
    val status: String = "",
    val photos: List<PersonPhoto> = emptyList()
)
