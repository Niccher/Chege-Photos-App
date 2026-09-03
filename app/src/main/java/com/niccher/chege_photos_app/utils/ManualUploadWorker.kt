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
            albumId: String? = null,
            bucketName: String? = null
        ) {
            if (photos.isEmpty()) return

            val totalBytes = photos.sumOf { it.size }
            val queueDir = File(context.cacheDir, "upload_queues").apply { mkdirs() }
            val queueFile = File(queueDir, "queue_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}.json")
            val jsonArray = org.json.JSONArray()
            for (photo in photos) {
                val obj = org.json.JSONObject().apply {
                    put("uri", photo.uri.toString())
                    put("filePath", photo.file?.absolutePath ?: "")
                    put("name", photo.name)
                    put("size", photo.size)
                    put("folder", photo.folderName)
                }
                jsonArray.put(obj)
            }
            queueFile.writeText(jsonArray.toString())

            val inputData = workDataOf(
                "queue_file" to queueFile.absolutePath,
                "album_id" to albumId,
                "bucket_name" to bucketName,
                "total_bytes" to totalBytes
            )

            val workRequest = androidx.work.OneTimeWorkRequestBuilder<ManualUploadWorker>()
                .setInputData(inputData)
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "ChegePhotosManualUpload",
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d(TAG, "Enqueued ${photos.size} manual upload items ($totalBytes bytes) for bucket: $bucketName")
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
        val bucketName = inputData.getString("bucket_name") ?: "Photos"

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
                        val folder = obj.optString("folder", "Other")
                        photosToUpload.add(
                            LocalPhoto(
                                uri = Uri.parse(uriStr),
                                file = pathStr?.let { File(it) },
                                name = name,
                                size = size,
                                folderName = folder
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

        val totalBytes = inputData.getLong("total_bytes", photosToUpload.sumOf { it.size }).let {
            if (it <= 0L) photosToUpload.sumOf { p -> p.size } else it
        }

        Log.d(TAG, "Queueing upload of $total manual items ($totalBytes bytes) for bucket: $bucketName.")
        val repository = PhotoRepository(context)
        if (!repository.isServerReachable()) {
            Log.w(TAG, "Server is currently unreachable. Pausing manual upload.")
            return Result.retry()
        }

        var successCount = 0
        var failedCount = 0
        var consecutiveNetworkFailures = 0
        var uploadedBytes = 0L
        
        // Show initial notification
        showUploadNotification(
            context = context,
            current = 0,
            total = total,
            uploadedBytes = 0L,
            totalBytes = totalBytes,
            isFinished = false,
            failedCount = 0,
            currentFileName = null,
            bucketName = bucketName
        )

        for ((i, localPhoto) in photosToUpload.withIndex()) {
            if (isStopped) {
                Log.d(TAG, "Upload worker was stopped/cancelled.")
                val unattempted = (total - (successCount + failedCount)).coerceAtLeast(0)
                showUploadNotification(
                    context = context,
                    current = successCount,
                    total = total,
                    uploadedBytes = uploadedBytes,
                    totalBytes = totalBytes,
                    isFinished = true,
                    failedCount = failedCount + unattempted,
                    bucketName = bucketName
                )
                return Result.failure()
            }

            val name = localPhoto.name
            val photoSize = localPhoto.size

            // Update progress in database / state
            setProgress(workDataOf(
                "current" to (successCount + failedCount),
                "total" to total,
                "progress" to if (totalBytes > 0) (uploadedBytes.toFloat() / totalBytes) else (i.toFloat() / total),
                "current_name" to name,
                "status" to "uploading"
            ))

            // Show updated notification for current item
            showUploadNotification(
                context = context,
                current = i + 1,
                total = total,
                uploadedBytes = uploadedBytes,
                totalBytes = totalBytes,
                isFinished = false,
                failedCount = failedCount,
                currentFileName = name,
                bucketName = bucketName
            )

            // Sync the photo
            var lastProgressNotifyTime = 0L
            val syncResult = repository.syncPhoto(localPhoto, albumId = albumId) { progress ->
                val inProgressBytes = (photoSize * progress).toLong()
                val liveBytes = uploadedBytes + inProgressBytes
                val overallProgress = if (totalBytes > 0) (liveBytes.toFloat() / totalBytes) else (i.toFloat() + progress) / total
                setProgressAsync(workDataOf(
                    "current" to (successCount + failedCount),
                    "total" to total,
                    "progress" to overallProgress,
                    "current_name" to name,
                    "status" to "uploading"
                ))
                // Throttle notification calls during stream to prevent Android rate-limiting (shedding)
                val now = System.currentTimeMillis()
                if (now - lastProgressNotifyTime >= 600L) {
                    lastProgressNotifyTime = now
                    showUploadNotification(
                        context = context,
                        current = i + 1,
                        total = total,
                        uploadedBytes = liveBytes,
                        totalBytes = totalBytes,
                        isFinished = false,
                        failedCount = failedCount,
                        currentFileName = name,
                        bucketName = bucketName
                    )
                }
            }

            if (syncResult is PhotoSyncResult.Success) {
                successCount++
                uploadedBytes += photoSize
                consecutiveNetworkFailures = 0
                sessionManager.updateLastUpload()
            } else {
                failedCount++
                val errMsg = (syncResult as? PhotoSyncResult.Error)?.message ?: "Unknown error"
                Log.e(TAG, "Failed to sync photo $name ($failedCount failed so far): $errMsg")
                
                val isNetworkDown = errMsg.contains("ConnectException", ignoreCase = true) ||
                                   errMsg.contains("UnknownHostException", ignoreCase = true) ||
                                   errMsg.contains("SocketTimeoutException", ignoreCase = true)
                if (isNetworkDown) {
                    consecutiveNetworkFailures++
                    if (consecutiveNetworkFailures >= 3) {
                        Log.w(TAG, "3 consecutive network dropouts. Pausing batch to retry later.")
                        val unattempted = (total - (successCount + failedCount)).coerceAtLeast(0)
                        showUploadNotification(
                            context = context,
                            current = successCount,
                            total = total,
                            uploadedBytes = uploadedBytes,
                            totalBytes = totalBytes,
                            isFinished = true,
                            failedCount = failedCount + unattempted,
                            bucketName = bucketName
                        )
                        return Result.retry()
                    }
                } else {
                    consecutiveNetworkFailures = 0
                }
            }
        }

        // Show finished notification with exact success and failure counts
        showUploadNotification(
            context = context,
            current = successCount,
            total = total,
            uploadedBytes = uploadedBytes,
            totalBytes = totalBytes,
            isFinished = true,
            failedCount = failedCount,
            bucketName = bucketName
        )
        
        Log.d(TAG, "Background upload complete: $successCount succeeded, $failedCount failed out of $total items.")
        
        return Result.success(workDataOf(
            "succeeded" to successCount,
            "failed" to failedCount,
            "total" to total,
            "uploaded_bytes" to uploadedBytes,
            "total_bytes" to totalBytes
        ))
    }
}
