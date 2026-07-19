package com.niccher.chege_photos_app.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

object DeviceFingerprint {

    private var _deviceId: String? = null
    private var _fingerprint: String? = null

    fun getDeviceId(context: Context): String {
        if (_deviceId == null) {
            _deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "unknown"
        }
        return _deviceId!!
    }

    fun getFingerprint(): String {
        if (_fingerprint == null) {
            val parts = listOf(
                Build.BOARD,
                Build.BOOTLOADER,
                Build.BRAND,
                Build.DEVICE,
                Build.DISPLAY,
                Build.FINGERPRINT,
                Build.HARDWARE,
                Build.HOST,
                Build.ID,
                Build.MANUFACTURER,
                Build.MODEL,
                Build.PRODUCT,
                Build.TAGS,
                Build.TYPE,
                Build.USER,
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT.toString(),
                Build.VERSION.INCREMENTAL,
                Build.VERSION.CODENAME,
                Build.SUPPORTED_ABIS.joinToString(","),
                getSerial()
            )
            val joined = parts.joinToString("|")
            val digest = MessageDigest.getInstance("SHA-256").digest(joined.toByteArray())
            _fingerprint = digest.joinToString("") { "%02x".format(it) }
        }
        return _fingerprint!!
    }

    private fun getSerial(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Build.getSerial()
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL
            }
        } catch (_: Exception) {
            "unknown"
        }
    }

    fun init(context: Context) {
        getDeviceId(context)
        getFingerprint()
    }
}
