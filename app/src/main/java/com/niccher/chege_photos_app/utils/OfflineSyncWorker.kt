package com.niccher.chege_photos_app.utils

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.niccher.chege_photos_app.data.AppDatabase
import com.niccher.chege_photos_app.network.ApiClient

class OfflineSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("OfflineSyncWorker", "Starting offline actions sync...")
        val database = AppDatabase.getDatabase(context)
        val offlineActionDao = database.offlineActionDao()
        val sessionManager = SessionManager(context)

        if (!sessionManager.isLoggedIn()) {
            Log.d("OfflineSyncWorker", "User not logged in. Skipping actions sync.")
            return Result.success()
        }

        try {
            val pendingActions = offlineActionDao.getAllPendingActions()
            Log.d("OfflineSyncWorker", "Found ${pendingActions.size} pending actions.")

            val service = ApiClient.getPhotoService(context)
            for (action in pendingActions) {
                var success = false
                try {
                    val response = when (action.actionType) {
                        "FAVORITE", "UNFAVORITE" -> service.favoritePhoto(action.photoId)
                        "ARCHIVE" -> service.archivePhoto(action.photoId)
                        "DELETE" -> service.deletePhoto(action.photoId)
                        "RESTORE" -> service.restorePhoto(action.photoId)
                        else -> null
                    }
                    if (response != null && response.isSuccessful) {
                        success = true
                    }
                } catch (e: Exception) {
                    Log.e("OfflineSyncWorker", "Error syncing action ${action.localId}: ${e.localizedMessage}")
                }

                if (success) {
                    offlineActionDao.deleteActionById(action.localId)
                    Log.d("OfflineSyncWorker", "Successfully synced and deleted action ${action.localId}")
                }
            }
            return Result.success()
        } catch (e: Exception) {
            Log.e("OfflineSyncWorker", "Offline actions sync failed: ${e.localizedMessage}", e)
            return Result.retry()
        }
    }
}
