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
            val localPhotos = repository.getLocalPhotos()
            val total = localPhotos.size
            Log.d("SyncWorker", "Found $total local photos to sync.")
            
            if (total == 0) {
                return Result.success()
            }

            showUploadNotification(context, 0, total)
            
            var successCount = 0
            for ((index, photo) in localPhotos.withIndex()) {
                if (isStopped) {
                    Log.d("SyncWorker", "Sync worker was cancelled.")
                    showUploadNotification(context, successCount, total, isFinished = true)
                    return Result.failure()
                }

                showUploadNotification(context, index + 1, total, isFinished = false, currentFileName = photo.name)

                // SyncPhoto already calculates and checks the SHA-256 hash before uploading
                val syncResult = repository.syncPhoto(photo)
                if (syncResult is PhotoSyncResult.Success) {
                    successCount++
                    sessionManager.updateLastUpload()
                } else if (syncResult is PhotoSyncResult.Error && syncResult.message.contains("Server unreachable", ignoreCase = true)) {
                    Log.w("SyncWorker", "Connection lost mid-sync. Pausing batch.")
                    showUploadNotification(context, successCount, total, isFinished = true)
                    return Result.retry()
                }
            }
            
            showUploadNotification(context, successCount, total, isFinished = true)
            Log.d("SyncWorker", "Successfully synced $successCount photos.")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error during auto-sync: ${e.localizedMessage}", e)
            Result.retry()
        }
    }
}
