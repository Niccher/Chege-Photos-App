package com.niccher.chege_photos_app.utils

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException

class ContentUriRequestBody(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val contentType: MediaType?
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                pfd.statSize.takeIf { it > 0 } ?: -1L
            } ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    override fun writeTo(sink: BufferedSink) {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open input stream for URI: $uri")

        inputStream.use { stream ->
            stream.source().use { source ->
                sink.writeAll(source)
            }
        }
    }
}
