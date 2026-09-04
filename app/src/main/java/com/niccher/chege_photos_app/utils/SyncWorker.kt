package com.niccher.chege_photos_app.utils

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.niccher.chege_photos_app.repository.PhotoRepository
import com.niccher.chege_photos_app.repository.PhotoSyncResult
import com.niccher.chege_photos_app.showUploadNotification

class SyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("SyncWorker", "Starting background auto-sync work...")
        
        val sessionManager = SessionManager(context)
        if (!sessionManager.isLoggedIn()) {
            Log.d("SyncWorker", "User not logged in. Skipping auto-sync.")
            return Result.success()
        }

        val repository = PhotoRepository(context)
        if (!repository.isServerReachable()) {
            Log.w("SyncWorker", "Server is currently unreachable. Pausing auto-sync.")
            return Result.retry()
        }

        return try {
            val allPhotos = repository.getLocalPhotos()
            val allowedFolders = sessionManager.getBackupFolders()
            val localPhotos = allPhotos.filter {
                !it.isUploaded && (allowedFolders.isEmpty() || it.folderName in allowedFolders)
            }
            val total = localPhotos.size
            val totalBytes = localPhotos.sumOf { it.size }
            Log.d("SyncWorker", "Found $total fresh local photos ($totalBytes bytes) to sync (out of ${allPhotos.size} total).")
            
            if (total == 0) {
                Log.d("SyncWorker", "No fresh photos require auto-sync. All photos up to date.")
                return Result.success()
            }

            var successCount = 0
            var uploadedBytes = 0L

            showUploadNotification(
                context = context,
                current = 0,
                total = total,
                uploadedBytes = 0L,
                totalBytes = totalBytes,
                isFinished = false,
                currentFileName = null,
                bucketName = "All"
            )
            
            for ((index, photo) in localPhotos.withIndex()) {
                if (isStopped) {
                    Log.d("SyncWorker", "Sync worker was cancelled.")
                    showUploadNotification(
                        context = context,
                        current = successCount,
                        total = total,
                        uploadedBytes = uploadedBytes,
                        totalBytes = totalBytes,
                        isFinished = true,
                        bucketName = "All"
                    )
                    return Result.failure()
                }

                val photoSize = photo.size
                showUploadNotification(
                    context = context,
                    current = index + 1,
                    total = total,
                    uploadedBytes = uploadedBytes,
                    totalBytes = totalBytes,
                    isFinished = false,
                    currentFileName = photo.name,
                    bucketName = "All"
                )

                // SyncPhoto already calculates and checks the SHA-256 hash before uploading
                val syncResult = repository.syncPhoto(photo) { progress ->
                    val inProgressBytes = (photoSize * progress).toLong()
                    val liveBytes = uploadedBytes + inProgressBytes
                    showUploadNotification(
                        context = context,
                        current = index + 1,
                        total = total,
                        uploadedBytes = liveBytes,
                        totalBytes = totalBytes,
                        isFinished = false,
                        currentFileName = photo.name,
                        bucketName = "All"
                    )
                }
                if (syncResult is PhotoSyncResult.Success) {
                    successCount++
                    uploadedBytes += photoSize
                    sessionManager.updateLastUpload()
                } else if (syncResult is PhotoSyncResult.Error && syncResult.message.contains("Server unreachable", ignoreCase = true)) {
                    Log.w("SyncWorker", "Connection lost mid-sync. Pausing batch.")
                    showUploadNotification(
                        context = context,
                        current = successCount,
                        total = total,
                        uploadedBytes = uploadedBytes,
                        totalBytes = totalBytes,
                        isFinished = true,
                        bucketName = "All"
                    )
                    return Result.retry()
                }
            }
            
            showUploadNotification(
                context = context,
                current = successCount,
                total = total,
                uploadedBytes = uploadedBytes,
                totalBytes = totalBytes,
                isFinished = true,
                bucketName = "All"
            )
            Log.d("SyncWorker", "Successfully synced $successCount photos.")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error during auto-sync: ${e.localizedMessage}", e)
            Result.retry()
        }
    }
}
