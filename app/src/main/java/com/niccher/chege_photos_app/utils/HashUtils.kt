package com.niccher.chege_photos_app.utils

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.security.MessageDigest

object HashUtils {
    fun calculateSha256(context: Context, uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(65536)
                var bytesRead = inputStream.read(buffer)
                while (bytesRead != -1) {
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = inputStream.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
