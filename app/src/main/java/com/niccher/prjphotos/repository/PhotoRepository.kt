package com.niccher.prjphotos.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.niccher.prjphotos.models.Photo
import com.niccher.prjphotos.network.ApiClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class PhotoRepository(private val context: Context) {

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
                photos.add(File(path))
            }
        }
        return photos
    }

    suspend fun syncPhoto(file: File): Boolean {
        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        
        return try {
            val response = ApiClient.getPhotoService(context).uploadPhoto(body)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
