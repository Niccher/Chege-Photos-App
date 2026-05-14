package com.niccher.prjphotos.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.niccher.prjphotos.data.AppDatabase
import com.niccher.prjphotos.models.toCachedPhoto
import com.niccher.prjphotos.models.Photo
import com.niccher.prjphotos.network.ApiClient
import com.niccher.prjphotos.models.PhotoListResponse
import retrofit2.Response
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

class PhotoRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val photoDao = database.photoDao()

    val photosRefreshTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun getLocalPhotos(): List<File> {
        val photos = mutableListOf<File>()
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )

        cursor?.use {
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            while (it.moveToNext()) {
                val path = it.getString(dataColumn)
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) photos.add(file)
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
                // Update Cache
                photoDao.clearAll()
                photoDao.insertPhotos(remotePhotos.map { it.toCachedPhoto() })
                remotePhotos
            } else {
                // Fallback to Cache
                getCachedPhotos()
            }
        } catch (e: Exception) {
            // Fallback to Cache
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

    suspend fun syncPhoto(file: File, onProgress: ((Float) -> Unit)? = null): Boolean {
        val baseRequestBody = file.asRequestBody("image/*".toMediaTypeOrNull())
        val requestFile = if (onProgress != null) {
            com.niccher.prjphotos.utils.ProgressRequestBody(baseRequestBody, onProgress)
        } else {
            baseRequestBody
        }
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        
        return try {
            val response = ApiClient.getPhotoService(context).uploadPhoto(body)
            response.isSuccessful
        } catch (e: Exception) {
            false
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
}
