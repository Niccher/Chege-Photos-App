package com.niccher.prjphotos.network

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import android.content.Context
import com.niccher.prjphotos.utils.SessionManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {
    private var baseUrl = "https://photos.chegecache.co.ke/"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private fun getClient(context: Context): OkHttpClient {
        val sessionManager = SessionManager(context)
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(300, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                
                // Add token if available
                sessionManager.getAuthToken()?.let { token ->
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }
                
                chain.proceed(requestBuilder.build())
            }
            .build()
    }

    private var _photoService: PhotoService? = null

    fun getPhotoService(context: Context): PhotoService {
        if (_photoService == null) {
            _photoService = createService(baseUrl, context)
        }
        return _photoService!!
    }

    fun updateBaseUrl(newUrl: String, context: Context) {
        val sanitizedUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        if (baseUrl != sanitizedUrl) {
            baseUrl = sanitizedUrl
            _photoService = createService(baseUrl, context)
        }
    }

    private fun createService(url: String, context: Context): PhotoService {
        return Retrofit.Builder()
            .baseUrl(url)
            .client(getClient(context))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PhotoService::class.java)
    }
}
