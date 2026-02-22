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

    @GET("explore")
    suspend fun getPhotos(): Response<PhotoListResponse>

    @Multipart
    @POST("upload")
    suspend fun uploadPhoto(
        @Part file: MultipartBody.Part,
        @Part("description") description: RequestBody? = null
    ): Response<AuthResponse>
}
