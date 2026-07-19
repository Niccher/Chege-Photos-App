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
    private var baseUrl = "https://photos.chegecache.co.ke/"

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

    /**
     * Normalise a user-typed server address into a valid Retrofit base URL.
     *
     * Rules (applied in order):
     *  1. Strip whitespace.
     *  2. If already has a scheme (contains "://") → just ensure trailing slash.
     *  3. Otherwise auto-detect scheme:
     *       - Private IPv4 ranges, loopback, *.local hostnames  → http://
     *       - Anything else                                       → https://
     *  4. Ensure trailing slash.
     *
     * Examples:
     *   "192.168.1.50:2283"  → "http://192.168.1.50:2283/"
     *   "10.0.0.5"           → "http://10.0.0.5/"
     *   "mynas.local:8080"   → "http://mynas.local:8080/"
     *   "photos.example.com" → "https://photos.example.com/"
     *   "http://192.168.1.5" → "http://192.168.1.5/"   (scheme kept)
     *   "https://mysite.com" → "https://mysite.com/"   (scheme kept)
     */
    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return baseUrl

        // Already has a scheme — preserve it, just guarantee trailing slash
        if ("://" in trimmed) {
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }

        // Extract host (before the first ":" which could be a port separator)
        val host = trimmed.substringBefore(":")

        val isPrivateOrLocal =
            host.equals("localhost", ignoreCase = true) ||
            host == "127.0.0.1" ||
            host == "::1" ||
            host.endsWith(".local", ignoreCase = true) ||
            // 10.0.0.0/8
            host.matches(Regex("""^10\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")) ||
            // 172.16.0.0/12  (172.16.x.x – 172.31.x.x)
            host.matches(Regex("""^172\.(1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3}$""")) ||
            // 192.168.0.0/16
            host.matches(Regex("""^192\.168\.\d{1,3}\.\d{1,3}$""")) ||
            // 169.254.x.x link-local
            host.matches(Regex("""^169\.254\.\d{1,3}\.\d{1,3}$"""))

        val scheme = if (isPrivateOrLocal) "http" else "https"
        val full = "$scheme://$trimmed"
        return if (full.endsWith("/")) full else "$full/"
    }

    // ── OkHttp client ────────────────────────────────────────────────────────

    /**
     * Build a trust-all X509TrustManager so connections to local servers with
     * self-signed certificates succeed (e.g. your NAS or Raspberry Pi running
     * an HTTPS server with a self-signed cert).
     *
     * This is intentionally permissive — it is only used here for a
     * self-hosted media server client where the user controls both ends.
     */
    private fun buildTrustAllClient(context: Context): OkHttpClient {
        val sessionManager = SessionManager(context)

        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, java.security.SecureRandom())
        }

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }          // accept any hostname for local IPs
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
                chain.proceed(requestBuilder.build())
            }
            .build()
    }

    // ── Service management ───────────────────────────────────────────────────

    private var _photoService: PhotoService? = null

    fun getPhotoService(context: Context): PhotoService {
        if (_photoService == null) {
            try {
                _photoService = createService(baseUrl, context)
            } catch (e: Exception) {
                Log.e("ApiClient", "Invalid base URL, falling back to default", e)
                baseUrl = "https://photos.chegecache.co.ke/"
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
            if (baseUrl != normalized) {
                baseUrl = normalized
                _photoService = createService(baseUrl, context)
            }
        } catch (e: Exception) {
            Log.e("ApiClient", "Failed to update base URL to $newUrl", e)
        }
    }

    private fun createService(url: String, context: Context): PhotoService {
        return Retrofit.Builder()
            .baseUrl(url)
            .client(buildTrustAllClient(context))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PhotoService::class.java)
    }
}
