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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class PhotoSyncResult {
    data object Success : PhotoSyncResult()
    data class Error(val message: String) : PhotoSyncResult()
}

data class LocalPhoto(
    val uri: Uri,
    val file: File?,
    val name: String,
    val size: Long,
    val folderName: String = "Other"
)

class PhotoRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val photoDao = database.photoDao()
    private val offlineActionDao = database.offlineActionDao()

    val photosRefreshTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    suspend fun getLocalPhotos(): List<LocalPhoto> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<LocalPhoto>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            } else {
                MediaStore.Images.Media.DATA // Fallback to parse folder name from path
            }
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
            val bucketCol = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                it.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            } else {
                it.getColumnIndex(MediaStore.Images.Media.DATA)
            }
            while (it.moveToNext()) {
                val id = if (idCol >= 0) it.getLong(idCol) else -1L
                val path = if (dataCol >= 0) it.getString(dataCol) else null
                val displayName = if (nameCol >= 0) it.getString(nameCol) else "photo_$id.jpg"
                val fileSize = if (sizeCol >= 0) it.getLong(sizeCol) else 0L
                
                var folder = "Other"
                if (bucketCol >= 0) {
                    val rawFolder = it.getString(bucketCol)
                    if (rawFolder != null) {
                        folder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            rawFolder
                        } else {
                            // Extract parent directory name from path
                            val file = File(rawFolder)
                            file.parentFile?.name ?: "Other"
                        }
                    }
                }

                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val file = if (path != null) File(path) else null
                if (file?.exists() == true || path != null) {
                    photos.add(LocalPhoto(uri, file, displayName, fileSize, folder))
                }
            }
        }

        photos
    }

    suspend fun clearLocalData() {
        try {
            photoDao.clearAll()
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Failed to clear database cache", e)
        }
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

    suspend fun syncPhoto(photo: LocalPhoto, albumId: String? = null, onProgress: ((Float) -> Unit)? = null): PhotoSyncResult = withContext(Dispatchers.IO) {
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

        try {
            val sha256 = com.niccher.chege_photos_app.utils.HashUtils.calculateSha256(context, photo.uri)
            if (sha256 != null) {
                // Check local database cache
                val cached = photoDao.getPhotoBySha256(sha256)
                if (cached != null) {
                    Log.i(tag, "Photo ${photo.name} matches local cached hash, skipping upload.")
                    return@withContext PhotoSyncResult.Success
                }

                // Check server
                try {
                    val checkResponse = ApiClient.getPhotoService(context).checkPhotoExistsByHash(sha256)
                    if (checkResponse.isSuccessful && checkResponse.body()?.status == "success") {
                        Log.i(tag, "Photo ${photo.name} matches server hash, skipping upload.")
                        // Cache it locally so we don't request the server next time
                        return@withContext PhotoSyncResult.Success
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Failed to check hash on server: ${e.message}")
                }
            }

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
                        return@withContext PhotoSyncResult.Error("Failed to read input stream for ${photo.uri}")
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
            val fingerprint = DeviceFingerprint.getFingerprint()
            val fingerprintPart = MultipartBody.Part.createFormData("device_fingerprint", fingerprint)
            Log.v(tag, "Device ID: $deviceId, Fingerprint: $fingerprint")

            val albumPart = albumId?.let { MultipartBody.Part.createFormData("album_id", it) }

            val service = ApiClient.getPhotoService(context)
            Log.d(tag, "Sending POST api/upload for ${photo.name}...")
            val response = service.uploadPhoto(body, deviceIdPart, fingerprintPart, albumPart)
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
                    
                    // Save uploaded metadata to local DB cache to prevent duplicate scan
                    if (sha256 != null) {
                        try {
                            val cachedPhoto = com.niccher.chege_photos_app.models.CachedPhoto(
                                id = authResponse?.id?.toString() ?: authResponse?.user?.id?.toString() ?: java.util.UUID.randomUUID().toString(),
                                filename = photo.name,
                                path = photo.file?.absolutePath ?: photo.uri.toString(),
                                thumbnail_path = null,
                                size = photo.size.toString(),
                                taken_at = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                                width = null,
                                height = null,
                                latitude = null,
                                longitude = null,
                                exif_data = null,
                                mime_type = mime,
                                is_favorite = 0,
                                album_id = albumId?.toIntOrNull(),
                                created_at = null,
                                updated_at = null,
                                sha256 = sha256
                            )
                            photoDao.insertPhotos(listOf(cachedPhoto))
                        } catch (e: Exception) {
                            Log.w(tag, "Failed to cache uploaded photo locally: ${e.message}")
                        }
                    }
                    
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

    private suspend fun saveOfflineAction(photoId: String, type: String) {
        try {
            offlineActionDao.insertAction(
                com.niccher.chege_photos_app.models.OfflineAction(
                    photoId = photoId,
                    actionType = type
                )
            )
            Log.d("PhotoRepository", "Saved offline action: $type for photo $photoId")
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Failed to save offline action: ${e.localizedMessage}")
        }
    }

    suspend fun deletePhoto(id: String): Boolean {
        // Immediate local delete
        photoDao.deleteById(id)
        return try {
            val response = ApiClient.getPhotoService(context).deletePhoto(id)
            if (response.isSuccessful) {
                true
            } else {
                saveOfflineAction(id, "DELETE")
                false
            }
        } catch (e: Exception) {
            saveOfflineAction(id, "DELETE")
            false
        }
    }

    suspend fun restorePhoto(id: String): Boolean {
        return try {
            val response = ApiClient.getPhotoService(context).restorePhoto(id)
            if (response.isSuccessful) {
                true
            } else {
                saveOfflineAction(id, "RESTORE")
                false
            }
        } catch (e: Exception) {
            saveOfflineAction(id, "RESTORE")
            false
        }
    }

    suspend fun archivePhoto(id: String): Boolean {
        // Immediate local archive (delete from main gallery feed cache)
        photoDao.deleteById(id)
        return try {
            val response = ApiClient.getPhotoService(context).archivePhoto(id)
            if (response.isSuccessful) {
                true
            } else {
                saveOfflineAction(id, "ARCHIVE")
                false
            }
        } catch (e: Exception) {
            saveOfflineAction(id, "ARCHIVE")
            false
        }
    }

    suspend fun favoritePhoto(id: String): Boolean {
        // Immediate local favorite toggle
        photoDao.getPhotoById(id)?.let { cached ->
            val newFav = if (cached.is_favorite == 1) 0 else 1
            photoDao.insertPhotos(listOf(cached.copy(is_favorite = newFav)))
        }
        return try {
            val response = ApiClient.getPhotoService(context).favoritePhoto(id)
            if (response.isSuccessful) {
                true
            } else {
                val isNowFav = photoDao.getPhotoById(id)?.is_favorite ?: 1
                val type = if (isNowFav == 1) "FAVORITE" else "UNFAVORITE"
                saveOfflineAction(id, type)
                false
            }
        } catch (e: Exception) {
            val isNowFav = photoDao.getPhotoById(id)?.is_favorite ?: 1
            val type = if (isNowFav == 1) "FAVORITE" else "UNFAVORITE"
            saveOfflineAction(id, type)
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
