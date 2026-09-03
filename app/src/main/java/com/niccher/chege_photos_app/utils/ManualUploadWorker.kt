package com.niccher.chege_photos_app.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.niccher.chege_photos_app.repository.LocalPhoto
import com.niccher.chege_photos_app.repository.PhotoRepository
import com.niccher.chege_photos_app.repository.PhotoSyncResult
import java.io.File
import com.niccher.chege_photos_app.showUploadNotification


class ManualUploadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ManualUploadWorker"

        fun enqueue(
            context: Context,
            photos: List<LocalPhoto>,
            albumId: String? = null
        ) {
            if (photos.isEmpty()) return

            val queueDir = File(context.cacheDir, "upload_queues").apply { mkdirs() }
            val queueFile = File(queueDir, "queue_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}.json")
            val jsonArray = org.json.JSONArray()
            for (photo in photos) {
                val obj = org.json.JSONObject().apply {
                    put("uri", photo.uri.toString())
                    put("filePath", photo.file?.absolutePath ?: "")
                    put("name", photo.name)
                    put("size", photo.size)
                }
                jsonArray.put(obj)
            }
            queueFile.writeText(jsonArray.toString())

            val inputData = workDataOf(
                "queue_file" to queueFile.absolutePath,
                "album_id" to albumId
            )

            val workRequest = androidx.work.OneTimeWorkRequestBuilder<ManualUploadWorker>()
                .setInputData(inputData)
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "ChegePhotosManualUpload",
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d(TAG, "Enqueued ${photos.size} manual upload items via queue file: ${queueFile.name}")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting manual background upload worker...")

        val sessionManager = SessionManager(context)
        if (!sessionManager.isLoggedIn()) {
            Log.d(TAG, "User not logged in. Aborting manual upload.")
            return Result.failure()
        }

        val photosToUpload = mutableListOf<LocalPhoto>()
        val queueFilePath = inputData.getString("queue_file")
        val albumId = inputData.getString("album_id")

        if (queueFilePath != null) {
            val qFile = File(queueFilePath)
            if (qFile.exists()) {
                try {
                    val content = qFile.readText()
                    val jsonArray = org.json.JSONArray(content)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val uriStr = obj.optString("uri")
                        val pathStr = obj.optString("filePath").takeIf { it.isNotBlank() }
                        val name = obj.optString("name", "photo_$i.jpg")
                        val size = obj.optLong("size", 0L)
                        photosToUpload.add(
                            LocalPhoto(
                                uri = Uri.parse(uriStr),
                                file = pathStr?.let { File(it) },
                                name = name,
                                size = size
                            )
                        )
                    }
                    // Clean up temporary queue file
                    qFile.delete()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse queue file: ${e.message}", e)
                }
            }
        }

        // Fallback for direct array parameters (legacy / small payloads)
        if (photosToUpload.isEmpty()) {
            val uris = inputData.getStringArray("uris") ?: emptyArray()
            val filePaths = inputData.getStringArray("file_paths") ?: emptyArray()
            val names = inputData.getStringArray("names") ?: emptyArray()
            val sizes = inputData.getLongArray("sizes") ?: longArrayOf()
            for (i in uris.indices) {
                val uriStr = uris[i]
                val pathStr = filePaths.getOrNull(i)
                val name = names.getOrNull(i) ?: "photo_$i.jpg"
                val size = sizes.getOrNull(i) ?: 0L
                photosToUpload.add(
                    LocalPhoto(
                        uri = Uri.parse(uriStr),
                        file = if (!pathStr.isNullOrBlank()) File(pathStr) else null,
                        name = name,
                        size = size
                    )
                )
            }
        }

        val total = photosToUpload.size
        if (total == 0) {
            Log.d(TAG, "No photos provided to upload.")
            return Result.success()
        }

        Log.d(TAG, "Queueing upload of $total manual items.")
        val repository = PhotoRepository(context)
        if (!repository.isServerReachable()) {
            Log.w(TAG, "Server is currently unreachable. Pausing manual upload.")
            return Result.retry()
        }

        var successCount = 0
        
        // Show initial notification
        showUploadNotification(context, 0, total)

        for ((i, localPhoto) in photosToUpload.withIndex()) {
            if (isStopped) {
                Log.d(TAG, "Upload worker was stopped/cancelled.")
                showUploadNotification(context, successCount, total, isFinished = true)
                return Result.failure()
            }

            val name = localPhoto.name

            // Update progress in database / state
            setProgress(workDataOf(
                "current" to i,
                "total" to total,
                "progress" to 0f,
                "current_name" to name,
                "status" to "uploading"
            ))

            // Show updated notification for current item
            showUploadNotification(context, i + 1, total)

            // Sync the photo
            val syncResult = repository.syncPhoto(localPhoto, albumId = albumId) { progress ->
                val currentFileProgress = progress
                val overallProgress = (i.toFloat() + currentFileProgress) / total
                setProgressAsync(workDataOf(
                    "current" to i,
                    "total" to total,
                    "progress" to overallProgress,
                    "current_name" to name,
                    "status" to "uploading"
                ))
            }

            if (syncResult is PhotoSyncResult.Success) {
                successCount++
                sessionManager.updateLastUpload()
            } else {
                val errMsg = (syncResult as? PhotoSyncResult.Error)?.message ?: "Unknown error"
                Log.e(TAG, "Failed to sync photo $name: $errMsg")
                if (errMsg.contains("Server unreachable", ignoreCase = true)) {
                    Log.w(TAG, "Server became unreachable mid-batch. Retrying later.")
                    return Result.retry()
                }
            }
        }

        // Show finished notification
        showUploadNotification(context, successCount, total, isFinished = true)
        
        Log.d(TAG, "Background manual upload complete: $successCount / $total items succeeded.")
        
        return Result.success(workDataOf(
            "succeeded" to successCount,
            "total" to total
        ))
    }
}
