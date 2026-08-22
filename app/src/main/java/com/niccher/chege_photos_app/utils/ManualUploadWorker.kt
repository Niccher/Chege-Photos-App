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

    override suspend fun doWork(): Result {
        Log.d("ManualUploadWorker", "Starting manual background upload worker...")

        val sessionManager = SessionManager(context)
        if (!sessionManager.isLoggedIn()) {
            Log.d("ManualUploadWorker", "User not logged in. Aborting manual upload.")
            return Result.failure()
        }

        // Retrieve input arrays
        val uris = inputData.getStringArray("uris") ?: emptyArray()
        val filePaths = inputData.getStringArray("file_paths") ?: emptyArray()
        val names = inputData.getStringArray("names") ?: emptyArray()
        val sizes = inputData.getLongArray("sizes") ?: longArrayOf()
        val albumId = inputData.getString("album_id")

        val total = uris.size
        if (total == 0) {
            Log.d("ManualUploadWorker", "No photos provided to upload.")
            return Result.success()
        }

        Log.d("ManualUploadWorker", "Queueing upload of $total manual items.")
        val repository = PhotoRepository(context)
        var successCount = 0
        
        // Show initial notification
        showUploadNotification(context, 0, total)

        for (i in 0 until total) {
            if (isStopped) {
                Log.d("ManualUploadWorker", "Upload worker was stopped/cancelled.")
                showUploadNotification(context, successCount, total, isFinished = true)
                return Result.failure()
            }

            val uriStr = uris[i]
            val pathStr = filePaths.getOrNull(i)
            val name = names.getOrNull(i) ?: "photo_$i.jpg"
            val size = sizes.getOrNull(i) ?: 0L

            val uri = Uri.parse(uriStr)
            val file = if (pathStr != null) File(pathStr) else null

            val localPhoto = LocalPhoto(
                uri = uri,
                file = file,
                name = name,
                size = size
            )

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
                // Report sub-file progress
                val currentFileProgress = progress
                val overallProgress = (i.toFloat() + currentFileProgress) / total
                // We can set progress for UI updates
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
                Log.e("ManualUploadWorker", "Failed to sync photo $name: $errMsg")
            }
        }

        // Show finished notification
        showUploadNotification(context, successCount, total, isFinished = true)
        
        Log.d("ManualUploadWorker", "Background manual upload complete: $successCount / $total items succeeded.")
        
        // Return custom output data
        return Result.success(workDataOf(
            "succeeded" to successCount,
            "total" to total
        ))
    }
}
