package com.niccher.chege_photos_app.utils

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.RemoteViews
import com.niccher.chege_photos_app.MainActivity
import com.niccher.chege_photos_app.R
import com.niccher.chege_photos_app.data.AppDatabase
import com.niccher.chege_photos_app.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class MemoriesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CYCLE_IMAGE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, MemoriesWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.memories_widget)

        // Set click intent to open main activity
        val pendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

        // Set up click intent to cycle the image
        val cycleIntent = Intent(context, MemoriesWidgetProvider::class.java).apply {
            action = ACTION_CYCLE_IMAGE
        }
        val cyclePendingIntent = PendingIntent.getBroadcast(
            context, 1, cycleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_next_memory, cyclePendingIntent)

        // Fetch image asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val serverPrefs = context.getSharedPreferences("chege_photos_prefs", Context.MODE_PRIVATE)
                val baseUrl = serverPrefs.getString("server_url", "https://photos.chegecache.co.ke/") ?: "https://photos.chegecache.co.ke/"
                
                var pickedPhoto: com.niccher.chege_photos_app.models.Photo? = null
                
                // Try fetching memories first
                try {
                    val service = ApiClient.getPhotoService(context)
                    val response = service.getMemories()
                    if (response.isSuccessful) {
                        val memories = response.body()?.photos
                        if (!memories.isNullOrEmpty()) {
                            pickedPhoto = memories.random()
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MemoriesWidget", "Failed to fetch remote memories: ${e.message}")
                }
                
                // If no memory was found, try fetching favorites
                if (pickedPhoto == null) {
                    try {
                        val service = ApiClient.getPhotoService(context)
                        val response = service.getFavorites()
                        if (response.isSuccessful) {
                            val favorites = response.body()?.photos
                            if (!favorites.isNullOrEmpty()) {
                                pickedPhoto = favorites.random()
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("MemoriesWidget", "Failed to fetch remote favorites: ${e.message}")
                    }
                }
                
                if (pickedPhoto != null) {
                    val imageUrl = baseUrl.trimEnd('/') + "/" + pickedPhoto.path.trimStart('/')
                    val bitmap = downloadBitmap(imageUrl, context)
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widget_image, bitmap)
                        views.setTextViewText(R.id.widget_title, pickedPhoto.filename)
                    } else {
                        views.setImageViewResource(R.id.widget_image, R.drawable.ic_nav_gallery)
                        views.setTextViewText(R.id.widget_title, "No server connection")
                    }
                } else {
                    // Local fallback
                    val db = AppDatabase.getDatabase(context)
                    val localPhotos = db.photoDao().getAllPhotosOnce()
                    if (localPhotos.isNotEmpty()) {
                        val randomPhoto = localPhotos.random()
                        val imageUrl = baseUrl.trimEnd('/') + "/" + randomPhoto.path.trimStart('/')
                        val bitmap = downloadBitmap(imageUrl, context)
                        if (bitmap != null) {
                            views.setImageViewBitmap(R.id.widget_image, bitmap)
                            views.setTextViewText(R.id.widget_title, randomPhoto.filename)
                        } else {
                            views.setImageViewResource(R.id.widget_image, R.drawable.ic_nav_gallery)
                            views.setTextViewText(R.id.widget_title, "No server connection")
                        }
                    } else {
                        views.setImageViewResource(R.id.widget_image, R.drawable.ic_nav_gallery)
                        views.setTextViewText(R.id.widget_title, "Sync app to load memories")
                    }
                }
            } catch (e: Exception) {
                Log.e("MemoriesWidget", "Widget update error: ${e.message}")
                views.setImageViewResource(R.id.widget_image, R.drawable.ic_nav_gallery)
                views.setTextViewText(R.id.widget_title, "Sync app to load memories")
            } finally {
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    private fun downloadBitmap(urlString: String, context: Context): Bitmap? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            
            // Add Authorization header if token exists
            val token = SessionManager(context).getAuthToken()
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            
            connection.connect()
            val input: InputStream = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        const val ACTION_CYCLE_IMAGE = "com.niccher.chege_photos_app.CYCLE_IMAGE"
    }
}
