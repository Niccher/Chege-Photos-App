package com.niccher.prjphotos.network

import com.niccher.prjphotos.models.AuthResponse
import com.niccher.prjphotos.models.PhotoListResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface PhotoService {

    @POST("api/login")
    @FormUrlEncoded
    suspend fun login(
        @Field("email") email: String,
        @Field("password") password: String,
        @Field("device_name") deviceName: String = "Android Device"
    ): Response<AuthResponse>

    @GET("api/photos")
    suspend fun getRemotePhotos(): Response<PhotoListResponse>

    @GET("api/albums")
    suspend fun getAlbums(): Response<com.niccher.prjphotos.models.AlbumListResponse>

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
        @Part("description") description: RequestBody? = null
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
    ): Response<com.niccher.prjphotos.models.SingleAlbumResponse>

    @FormUrlEncoded
    @PUT("api/albums/{id}")
    suspend fun updateAlbum(
        @Path("id") id: String,
        @Field("name") name: String,
        @Field("description") description: String? = null
    ): Response<com.niccher.prjphotos.models.SingleAlbumResponse>

    @DELETE("api/albums/{id}")
    suspend fun deleteAlbum(
        @Path("id") id: String
    ): Response<AuthResponse>
}
