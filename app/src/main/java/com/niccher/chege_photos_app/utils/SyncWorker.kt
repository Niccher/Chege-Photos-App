package com.niccher.chege_photos_app.utils

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.niccher.chege_photos_app.repository.PhotoRepository
import com.niccher.chege_photos_app.repository.PhotoSyncResult

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
            Log.d("SyncWorker", "Found ${localPhotos.size} local photos to sync.")
            
            var successCount = 0
            for (photo in localPhotos) {
                // SyncPhoto already calculates and checks the SHA-256 hash before uploading
                val syncResult = repository.syncPhoto(photo)
                if (syncResult is PhotoSyncResult.Success) {
                    successCount++
                    sessionManager.updateLastUpload()
                } else if (syncResult is PhotoSyncResult.Error && syncResult.message.contains("Server unreachable", ignoreCase = true)) {
                    Log.w("SyncWorker", "Connection lost mid-sync. Pausing batch.")
                    return Result.retry()
                }
            }
            
            Log.d("SyncWorker", "Successfully synced $successCount photos.")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error during auto-sync: ${e.localizedMessage}", e)
            Result.retry()
        }
    }
}
