package com.niccher.chege_photos_app.network

import com.niccher.chege_photos_app.models.AuthResponse
import com.niccher.chege_photos_app.models.PhotoListResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface PhotoService {

    @GET("api/test")
    suspend fun ping(): Response<ResponseBody>

    @POST("api/login")
    @FormUrlEncoded
    suspend fun login(
        @Field("email") email: String,
        @Field("password") password: String,
        @Field("device_name") deviceName: String = "Android Device",
        @Field("device_id") deviceId: String = ""
    ): Response<AuthResponse>

    @POST("api/auth-with-token")
    @FormUrlEncoded
    suspend fun authWithToken(
        @Field("token") token: String,
        @Field("device_id") deviceId: String,
        @Field("device_fingerprint") deviceFingerprint: String,
        @Field("device_name") deviceName: String = "Android Device"
    ): Response<AuthResponse>

    @GET("api/photos")
    suspend fun getRemotePhotos(): Response<PhotoListResponse>

    @GET("api/albums")
    suspend fun getAlbums(): Response<com.niccher.chege_photos_app.models.AlbumListResponse>

    @GET("api/albums/{albumId}/photos")
    suspend fun getAlbumPhotos(@Path("albumId") albumId: String): Response<PhotoListResponse>

    @GET("api/memories")
    suspend fun getMemories(): Response<PhotoListResponse>

    @GET("api/favorites")
    suspend fun getFavorites(): Response<PhotoListResponse>

    @GET("api/archive")
    suspend fun getArchived(): Response<PhotoListResponse>

    @GET("api/trash")
    suspend fun getTrash(): Response<PhotoListResponse>

    @GET("api/explore")
    suspend fun getExplore(): Response<PhotoListResponse>

    @Multipart
    @POST("api/upload")
    suspend fun uploadPhoto(
        @Part file: MultipartBody.Part,
        @Part deviceId: MultipartBody.Part,
        @Part deviceFingerprint: MultipartBody.Part? = null,
        @Part albumId: MultipartBody.Part? = null
    ): Response<AuthResponse>

    @POST("photos/delete/{id}")
    suspend fun deletePhoto(@Path("id") id: String): Response<AuthResponse>

    @POST("photos/restore/{id}")
    suspend fun restorePhoto(@Path("id") id: String): Response<AuthResponse>

    @POST("photos/archive/{id}")
    suspend fun archivePhoto(@Path("id") id: String): Response<AuthResponse>

    @POST("photos/favorite/{id}")
    suspend fun favoritePhoto(@Path("id") id: String): Response<AuthResponse>

    @FormUrlEncoded
    @POST("api/albums")
    suspend fun createAlbum(
        @Field("name") name: String,
        @Field("description") description: String? = null
    ): Response<com.niccher.chege_photos_app.models.SingleAlbumResponse>

    @FormUrlEncoded
    @PUT("api/albums/{id}")
    suspend fun updateAlbum(
        @Path("id") id: String,
        @Field("name") name: String,
        @Field("description") description: String? = null
    ): Response<com.niccher.chege_photos_app.models.SingleAlbumResponse>

    @DELETE("api/albums/{id}")
    suspend fun deleteAlbum(
        @Path("id") id: String
    ): Response<AuthResponse>

    @FormUrlEncoded
    @POST("albums/add-photo")
    suspend fun addPhotoToAlbum(
        @Field("album_id") albumId: String,
        @Field("photo_id") photoId: String
    ): Response<AuthResponse>

    // ── Face endpoints ─────────────────────────────────────────────

    @GET("api/faces/{photoId}")
    suspend fun getFacesByPhoto(
        @Path("photoId") photoId: Int
    ): Response<com.niccher.chege_photos_app.models.FacesListResponse>

    @Multipart
    @POST("api/faces/search")
    suspend fun searchFacesByPhoto(
        @Part file: okhttp3.MultipartBody.Part,
        @Part limit: okhttp3.MultipartBody.Part
    ): Response<com.niccher.chege_photos_app.models.FaceSearchResponse>

    @GET("api/faces/persons")
    suspend fun getPersons(): Response<com.niccher.chege_photos_app.models.PersonsListResponse>

    @GET("api/faces/by-person/{personId}")
    suspend fun getPersonPhotos(
        @Path("personId") personId: Int
    ): Response<com.niccher.chege_photos_app.models.PersonPhotosResponse>
}
