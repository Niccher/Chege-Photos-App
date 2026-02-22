package com.niccher.prjphotos.network

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

object ApiClient {
    private var baseUrl = "https://photos.chegecache.co.ke/"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    private var _photoService: PhotoService? = null

    val photoService: PhotoService
        get() {
            if (_photoService == null) {
                _photoService = createService(baseUrl)
            }
            return _photoService!!
        }

    fun updateBaseUrl(newUrl: String) {
        val sanitizedUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        if (baseUrl != sanitizedUrl) {
            baseUrl = sanitizedUrl
            _photoService = createService(baseUrl)
        }
    }

    private fun createService(url: String): PhotoService {
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PhotoService::class.java)
    }
}
