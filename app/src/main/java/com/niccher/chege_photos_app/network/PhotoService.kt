package com.niccher.chege_photos_app.network

import com.niccher.chege_photos_app.models.AuthResponse
import com.niccher.chege_photos_app.models.PhotoListResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface PhotoService {

    @GET("api/v1/config")
    suspend fun getServerConfig(): Response<com.niccher.chege_photos_app.models.ServerConfigResponse>

    @GET("api/v1/test")
    suspend fun ping(): Response<ResponseBody>

    @POST("api/v1/login")
    @FormUrlEncoded
    suspend fun login(
        @Field("email") email: String,
        @Field("password") password: String,
        @Field("device_name") deviceName: String = "Android Device",
        @Field("device_id") deviceId: String = "",
        @Field("device_fingerprint") deviceFingerprint: String = "",
        @Field("device_uuid") deviceUuid: String = "",
        @Field("os_version") osVersion: String = "",
        @Field("screen_metrics") screenMetrics: String = "",
        @Field("locale") locale: String = "",
        @Field("timezone") timezone: String = "",
        @Field("kernel_version") kernelVersion: String = ""
    ): Response<AuthResponse>

    @POST("api/v1/auth-with-token")
    @FormUrlEncoded
    suspend fun authWithToken(
        @Field("token") token: String,
        @Field("device_id") deviceId: String,
        @Field("device_fingerprint") deviceFingerprint: String,
        @Field("device_name") deviceName: String = "Android Device",
        @Field("device_uuid") deviceUuid: String,
        @Field("os_version") osVersion: String,
        @Field("screen_metrics") screenMetrics: String,
        @Field("locale") locale: String,
        @Field("timezone") timezone: String,
        @Field("kernel_version") kernelVersion: String
    ): Response<AuthResponse>

    @GET("api/v1/photos")
    suspend fun getRemotePhotos(
        @Query("q") query: String? = null
    ): Response<PhotoListResponse>

    @GET("api/v1/albums")
    suspend fun getAlbums(): Response<com.niccher.chege_photos_app.models.AlbumListResponse>

    @GET("api/v1/albums/{albumId}/photos")
    suspend fun getAlbumPhotos(@Path("albumId") albumId: String): Response<PhotoListResponse>

    @GET("api/v1/memories")
    suspend fun getMemories(): Response<PhotoListResponse>

    @GET("api/v1/favorites")
    suspend fun getFavorites(): Response<PhotoListResponse>

    @GET("api/v1/archive")
    suspend fun getArchived(): Response<PhotoListResponse>

    @GET("api/v1/trash")
    suspend fun getTrash(): Response<PhotoListResponse>

    @GET("api/v1/explore")
    suspend fun getExplore(): Response<PhotoListResponse>

    @POST("api/v1/photos/exists-by-hash")
    @FormUrlEncoded
    suspend fun checkPhotoExistsByHash(
        @Field("sha256") sha256: String
    ): Response<AuthResponse>

    @Multipart
    @POST("api/v1/upload")
    suspend fun uploadPhoto(
        @Part file: MultipartBody.Part,
        @Part deviceId: MultipartBody.Part,
        @Part deviceFingerprint: MultipartBody.Part? = null,
        @Part albumId: MultipartBody.Part? = null
    ): Response<AuthResponse>

    @POST("api/v1/photos/delete/{id}")
    suspend fun deletePhoto(@Path("id") id: String): Response<AuthResponse>

    @POST("api/v1/photos/restore/{id}")
    suspend fun restorePhoto(@Path("id") id: String): Response<AuthResponse>

    @POST("api/v1/photos/empty-trash")
    suspend fun emptyTrash(): Response<AuthResponse>

    @POST("api/v1/photos/archive/{id}")
    suspend fun archivePhoto(@Path("id") id: String): Response<AuthResponse>

    @POST("api/v1/photos/favorite/{id}")
    suspend fun favoritePhoto(@Path("id") id: String): Response<AuthResponse>

    @FormUrlEncoded
    @POST("api/v1/albums")
    suspend fun createAlbum(
        @Field("name") name: String,
        @Field("description") description: String? = null
    ): Response<com.niccher.chege_photos_app.models.SingleAlbumResponse>

    @FormUrlEncoded
    @PUT("api/v1/albums/{id}")
    suspend fun updateAlbum(
        @Path("id") id: String,
        @Field("name") name: String,
        @Field("description") description: String? = null
    ): Response<com.niccher.chege_photos_app.models.SingleAlbumResponse>

    @DELETE("api/v1/albums/{id}")
    suspend fun deleteAlbum(
        @Path("id") id: String
    ): Response<AuthResponse>

    @FormUrlEncoded
    @POST("api/v1/albums/add-photo")
    suspend fun addPhotoToAlbum(
        @Field("album_id") albumId: String,
        @Field("photo_id") photoId: String
    ): Response<AuthResponse>

    // ── Face endpoints ─────────────────────────────────────────────

    @GET("api/v1/faces/{photoId}")
    suspend fun getFacesByPhoto(
        @Path("photoId") photoId: Int
    ): Response<com.niccher.chege_photos_app.models.FacesListResponse>

    @Multipart
    @POST("api/v1/faces/search")
    suspend fun searchFacesByPhoto(
        @Part file: okhttp3.MultipartBody.Part,
        @Part limit: okhttp3.MultipartBody.Part
    ): Response<com.niccher.chege_photos_app.models.FaceSearchResponse>

    @GET("api/v1/faces/persons")
    suspend fun getPersons(): Response<com.niccher.chege_photos_app.models.PersonsListResponse>

    @GET("api/v1/faces/by-person/{personId}")
    suspend fun getPersonPhotos(
        @Path("personId") personId: Int
    ): Response<com.niccher.chege_photos_app.models.PersonPhotosResponse>
}
