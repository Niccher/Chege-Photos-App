package com.niccher.chege_photos_app.network

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import android.content.Context
import android.util.Log
import com.niccher.chege_photos_app.utils.SessionManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.net.InetAddress
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object ApiClient {
    private var baseUrl = "https://chege-photos-webapp-production.up.railway.app/"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val logging = HttpLoggingInterceptor { msg ->
        android.util.Log.v("OKHTTP", msg)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // ── URL normalisation ────────────────────────────────────────────────────

    fun isPrivateOrLocalHost(raw: String): Boolean {
        val trimmed = raw.trim()
        val host = if ("://" in trimmed) {
            trimmed.substringAfter("://").substringBefore("/").substringBefore(":")
        } else {
            trimmed.substringBefore("/").substringBefore(":")
        }
        return host.equals("localhost", ignoreCase = true) ||
            host == "127.0.0.1" ||
            host == "::1" ||
            host.endsWith(".local", ignoreCase = true) ||
            host.matches(Regex("""^10\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")) ||
            host.matches(Regex("""^172\.(1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3}$""")) ||
            host.matches(Regex("""^192\.168\.\d{1,3}\.\d{1,3}$""")) ||
            host.matches(Regex("""^169\.254\.\d{1,3}\.\d{1,3}$"""))
    }

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return baseUrl

        // Already has a scheme — preserve it, just guarantee trailing slash
        if ("://" in trimmed) {
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }

        val isPrivateOrLocal = isPrivateOrLocalHost(trimmed)
        val scheme = if (isPrivateOrLocal) "http" else "https"
        val full = "$scheme://$trimmed"
        return if (full.endsWith("/")) full else "$full/"
    }

    var onUnauthorizedCallback: (() -> Unit)? = null

    // ── OkHttp client ────────────────────────────────────────────────────────

    /**
     * Build an OkHttpClient with smart conditional TLS:
     * - For private/local IPs (e.g. NAS/homelab), trust self-signed certs.
     * - For public domains (e.g. Railway), enforce standard strict Android TLS.
     */
    private fun buildClient(context: Context, url: String): OkHttpClient {
        val sessionManager = SessionManager(context)
        val builder = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                sessionManager.getAuthToken()?.let { token ->
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }
                // Add device UUID to every outgoing request
                val uuid = sessionManager.getDeviceUuid()
                requestBuilder.addHeader("X-Device-UUID", uuid)

                val response = chain.proceed(requestBuilder.build())
                if (response.code == 401) {
                    sessionManager.clearSession()
                    onUnauthorizedCallback?.let { callback ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            callback()
                        }
                    }
                }
                response
            }

        if (isPrivateOrLocalHost(url)) {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, trustAllCerts, java.security.SecureRandom())
            }
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        }

        return builder.build()
    }

    // ── Service management ───────────────────────────────────────────────────

    private var _httpClient: OkHttpClient? = null
    private var _photoService: PhotoService? = null

    fun getHttpClient(context: Context): OkHttpClient {
        if (_httpClient == null) {
            _httpClient = buildClient(context, baseUrl)
        }
        return _httpClient!!
    }

    fun getPhotoService(context: Context): PhotoService {
        if (_photoService == null) {
            val sharedPrefs = context.getSharedPreferences("chege_photos_prefs", Context.MODE_PRIVATE)
            val savedUrl = sharedPrefs.getString("server_url", null)
            if (savedUrl != null) {
                baseUrl = normalizeUrl(savedUrl)
            }
            try {
                _httpClient = buildClient(context, baseUrl)
                _photoService = createService(baseUrl, context)
            } catch (e: Exception) {
                Log.e("ApiClient", "Invalid base URL, falling back to default", e)
                baseUrl = "https://chege-photos-webapp-production.up.railway.app/"
                _httpClient = buildClient(context, baseUrl)
                _photoService = createService(baseUrl, context)
            }
        }
        return _photoService!!
    }

    fun updateBaseUrl(newUrl: String, context: Context) {
        try {
            val normalized = normalizeUrl(newUrl)
            // Ensure the URL is actually valid for OkHttp/Retrofit before applying
            if (normalized.toHttpUrlOrNull() == null) {
                return
            }
            if (baseUrl != normalized || _photoService == null) {
                baseUrl = normalized
                _httpClient = buildClient(context, baseUrl)
                _photoService = createService(baseUrl, context)
            }
        } catch (e: Exception) {
            Log.e("ApiClient", "Failed to update base URL to $newUrl", e)
        }
    }

    private fun createService(url: String, context: Context): PhotoService {
        return Retrofit.Builder()
            .baseUrl(url)
            .client(getHttpClient(context))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PhotoService::class.java)
    }

    private var _imageLoader: coil.ImageLoader? = null

    fun getImageLoader(context: Context): coil.ImageLoader {
        if (_imageLoader == null) {
            val appCtx = context.applicationContext
            _imageLoader = coil.ImageLoader.Builder(appCtx)
                .okHttpClient { getHttpClient(appCtx) }
                .crossfade(true)
                .memoryCache {
                    coil.memory.MemoryCache.Builder(appCtx)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    coil.disk.DiskCache.Builder()
                        .directory(appCtx.cacheDir.resolve("image_cache"))
                        .maxSizeBytes(100 * 1024 * 1024) // 100MB
                        .build()
                }
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .build()
        }
        return _imageLoader!!
    }
}
