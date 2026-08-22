package com.niccher.chege_photos_app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.niccher.chege_photos_app.models.FaceBbox
import com.niccher.chege_photos_app.models.FaceData
import kotlinx.coroutines.tasks.await

object LocalFaceDetector {
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .build()

    private val detector = FaceDetection.getClient(options)

    suspend fun detectFaces(context: Context, uri: Uri): List<FaceData> {
        return try {
            val inputImage = InputImage.fromFilePath(context, uri)
            val mlKitFaces = detector.process(inputImage).await()
            
            // Get original bitmap dimensions for sizing
            var imgWidth = 800
            var imgHeight = 600
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, options)
                if (options.outWidth > 0) imgWidth = options.outWidth
                if (options.outHeight > 0) imgHeight = options.outHeight
            }

            mlKitFaces.mapIndexed { idx, face ->
                val bounds = face.boundingBox
                FaceData(
                    face_id = -1 - idx, // Temporary client-side IDs are negative
                    photo_id = 0,
                    person_id = null,
                    person_name = "Local Face",
                    bbox = FaceBbox(
                        x = bounds.left.toDouble(),
                        y = bounds.top.toDouble(),
                        w = bounds.width().toDouble(),
                        h = bounds.height().toDouble()
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("LocalFaceDetector", "ML Kit Face Detection failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun detectFaces(bitmap: Bitmap): List<FaceData> {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val mlKitFaces = detector.process(inputImage).await()

            mlKitFaces.mapIndexed { idx, face ->
                val bounds = face.boundingBox
                FaceData(
                    face_id = -1 - idx, // Temporary client-side IDs are negative
                    photo_id = 0,
                    person_id = null,
                    person_name = "Local Face",
                    bbox = FaceBbox(
                        x = bounds.left.toDouble(),
                        y = bounds.top.toDouble(),
                        w = bounds.width().toDouble(),
                        h = bounds.height().toDouble()
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("LocalFaceDetector", "ML Kit Face Detection on Bitmap failed: ${e.message}")
            emptyList()
        }
    }
}
