package com.niccher.chege_photos_app.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.niccher.chege_photos_app.data.AppDatabase
import com.niccher.chege_photos_app.models.toCachedPhoto
import com.niccher.chege_photos_app.models.Photo
import com.niccher.chege_photos_app.network.ApiClient
import com.niccher.chege_photos_app.models.PhotoListResponse
import com.niccher.chege_photos_app.utils.DeviceFingerprint
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File

sealed class PhotoSyncResult {
    data object Success : PhotoSyncResult()
    data class Error(val message: String) : PhotoSyncResult()
}

data class LocalPhoto(
    val uri: Uri,
    val file: File?,
    val name: String,
    val size: Long
)

class PhotoRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val photoDao = database.photoDao()

    val photosRefreshTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun getLocalPhotos(): List<LocalPhoto> {
        val photos = mutableListOf<LocalPhoto>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE
        )
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )

        cursor?.use {
            val idCol = it.getColumnIndex(MediaStore.Images.Media._ID)
            val dataCol = it.getColumnIndex(MediaStore.Images.Media.DATA)
            val nameCol = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = it.getColumnIndex(MediaStore.Images.Media.SIZE)
            while (it.moveToNext()) {
                val id = if (idCol >= 0) it.getLong(idCol) else -1L
                val path = if (dataCol >= 0) it.getString(dataCol) else null
                val displayName = if (nameCol >= 0) it.getString(nameCol) else "photo_$id.jpg"
                val fileSize = if (sizeCol >= 0) it.getLong(sizeCol) else 0L
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val file = if (path != null) File(path) else null
                if (file?.exists() == true || path != null) {
                    photos.add(LocalPhoto(uri, file, displayName, fileSize))
                }
            }
        }

        return photos
    }

    suspend fun getRemotePhotos(): List<Photo> {
        return try {
            val response = ApiClient.getPhotoService(context).getRemotePhotos()
            if (response.isSuccessful) {
                val remotePhotos = response.body()?.photos ?: emptyList()
                photoDao.clearAll()
                photoDao.insertPhotos(remotePhotos.map { it.toCachedPhoto() })
                remotePhotos
            } else {
                getCachedPhotos()
            }
        } catch (e: Exception) {
            getCachedPhotos()
        }
    }

    private suspend fun getCachedPhotos(): List<Photo> {
        return try {
            photoDao.getAllPhotosOnce().map { it.toPhoto() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun syncPhoto(photo: LocalPhoto, onProgress: ((Float) -> Unit)? = null): PhotoSyncResult {
        val tag = "UPLOAD"
        Log.v(tag, "=== START upload: ${photo.name} (${photo.size} bytes) ===")
        Log.v(tag, "File exists: ${photo.file?.exists()}, URI: ${photo.uri}")

        val mime = photo.name.substringAfterLast('.', "jpg").lowercase().let { ext ->
            when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "bmp" -> "image/bmp"
                "heic", "heif" -> "image/heic"
                "mp4" -> "video/mp4"
                "mov" -> "video/quicktime"
                "3gp" -> "video/3gpp"
                "avi" -> "video/x-msvideo"
                "mkv" -> "video/x-matroska"
                else -> "image/jpeg"
            }
        }
        Log.v(tag, "MIME: $mime")

        return try {
            val body = if (photo.file?.exists() == true) {
                Log.v(tag, "Reading from file: ${photo.file?.absolutePath}")
                val requestBody = photo.file.asRequestBody(mime.toMediaTypeOrNull())
                val monitoredBody = if (onProgress != null) {
                    com.niccher.chege_photos_app.utils.ProgressRequestBody(requestBody, onProgress)
                } else requestBody
                MultipartBody.Part.createFormData("file", photo.name, monitoredBody)
            } else {
                Log.v(tag, "Reading from content URI")
                val bytes = context.contentResolver.openInputStream(photo.uri)?.use { it.readBytes() }
                    ?: run {
                        Log.e(tag, "Failed to read input stream for ${photo.uri}")
                        return PhotoSyncResult.Error("Failed to read input stream for ${photo.uri}")
                    }
                Log.v(tag, "Read ${bytes.size} bytes from URI")
                val requestBody = bytes.toRequestBody(mime.toMediaTypeOrNull())
                val monitoredBody = if (onProgress != null) {
                    com.niccher.chege_photos_app.utils.ProgressRequestBody(requestBody, onProgress)
                } else requestBody
                MultipartBody.Part.createFormData("file", photo.name, monitoredBody)
            }

            val deviceId = DeviceFingerprint.getDeviceId(context)
            val deviceIdPart = MultipartBody.Part.createFormData("device_id", deviceId)
            Log.v(tag, "Device ID: $deviceId")

            val service = ApiClient.getPhotoService(context)
            Log.d(tag, "Sending POST api/upload for ${photo.name}...")
            val response = service.uploadPhoto(body, deviceIdPart)
            Log.d(tag, "Response HTTP ${response.code()}: ${response.message()}")

            val bodyStr = response.body()?.let { 
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true }
                    .encodeToString(kotlinx.serialization.serializer<com.niccher.chege_photos_app.models.AuthResponse>(), it)
            } ?: "null"
            Log.v(tag, "Response body: $bodyStr")

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Log.e(tag, "Upload FAILED: $errorBody")
                PhotoSyncResult.Error(errorBody)
            } else {
                val authResponse = response.body()
                val status = authResponse?.status ?: "unknown"
                Log.d(tag, "Upload result status: $status")
                if (status == "success") {
                    val msg = authResponse?.messageText ?: "OK"
                    Log.i(tag, "Upload SUCCESS: ${photo.name} — $msg")
                    PhotoSyncResult.Success
                } else {
                    val errMsg = authResponse?.messageText ?: "Unknown error"
                    Log.w(tag, "Upload returned error status: $errMsg")
                    PhotoSyncResult.Error(errMsg)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Upload EXCEPTION for ${photo.name}: ${e.localizedMessage}", e)
            PhotoSyncResult.Error(e.localizedMessage ?: "Unknown exception")
        }
    }

    suspend fun deletePhoto(id: String): Boolean {
        return try {
            ApiClient.getPhotoService(context).deletePhoto(id).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun restorePhoto(id: String): Boolean {
        return try {
            ApiClient.getPhotoService(context).restorePhoto(id).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun archivePhoto(id: String): Boolean {
        return try {
            ApiClient.getPhotoService(context).archivePhoto(id).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun favoritePhoto(id: String): Boolean {
        return try {
            ApiClient.getPhotoService(context).favoritePhoto(id).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun createAlbum(name: String, description: String?): Boolean {
        return try {
            ApiClient.getPhotoService(context).createAlbum(name, description).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateAlbum(id: String, name: String, description: String?): Boolean {
        return try {
            ApiClient.getPhotoService(context).updateAlbum(id, name, description).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteAlbum(id: String): Boolean {
        return try {
            ApiClient.getPhotoService(context).deleteAlbum(id).isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun addPhotoToAlbum(albumId: String, photoId: String): Boolean {
        return try {
            ApiClient.getPhotoService(context).addPhotoToAlbum(albumId, photoId).isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
