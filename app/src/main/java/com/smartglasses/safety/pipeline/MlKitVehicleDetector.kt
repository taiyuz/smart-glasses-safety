package com.smartglasses.safety.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.TimeUnit

/**
 * Bundled ML Kit object detector. No weights file required.
 *
 * STREAM_MODE is built for video; classification labels are coarse
 * (fashion/food/home/place/plant), not COCO vehicles. Used as the
 * first-run default so Studio users get a real detector without a .tflite.
 */
class MlKitVehicleDetector : VehicleDetector {
    private var client: ObjectDetector? = null

    override val backendName: String = "ML Kit Object Detection"
    override var isReady: Boolean = false
        private set
    override var statusMessage: String = "ML Kit not initialized"
        private set

    override fun initialize(context: Context) {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        client = ObjectDetection.getClient(options)
        isReady = true
        statusMessage =
            "ML Kit bundled object detector (STREAM_MODE). " +
                "Coarse labels, not COCO vehicle classes."
        Log.i(TAG, statusMessage)
    }

    override fun detect(frame: Bitmap): List<VehicleDetection> {
        val detector = client ?: return emptyList()
        if (!isReady) return emptyList()
        return try {
            val image = InputImage.fromBitmap(frame, 0)
            val results = Tasks.await(detector.process(image), 250, TimeUnit.MILLISECONDS)
            results.map { obj ->
                val box = obj.boundingBox
                val best = obj.labels.maxByOrNull { it.confidence }
                VehicleDetection(
                    label = best?.text?.takeIf { it.isNotBlank() } ?: "object",
                    confidence = best?.confidence ?: 0.5f,
                    box = RectBox(
                        left = box.left.toFloat().coerceAtLeast(0f),
                        top = box.top.toFloat().coerceAtLeast(0f),
                        right = box.right.toFloat().coerceAtMost(frame.width.toFloat()),
                        bottom = box.bottom.toFloat().coerceAtMost(frame.height.toFloat())
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit detect failed", e)
            emptyList()
        }
    }

    override fun close() {
        client?.close()
        client = null
        isReady = false
    }

    companion object {
        private const val TAG = "SmartGlassesSafety"
    }
}
