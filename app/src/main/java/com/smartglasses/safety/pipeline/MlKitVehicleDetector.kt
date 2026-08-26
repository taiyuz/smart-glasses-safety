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
 * Default on-device detector using bundled ML Kit Object Detection
 * (`com.google.mlkit:object-detection`). No Play Services model download.
 *
 * Bundled coarse classes are fashion/food/home/place/plant, not vehicles,
 * so classification is off. Boxes are generic object candidates, not ADAS.
 */
class MlKitVehicleDetector(
    private val inferTimeoutMs: Long = 400L
) : VehicleDetector {
    private var detector: ObjectDetector? = null

    override fun initialize(context: Context) {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()
        detector = ObjectDetection.getClient(options)
        Log.i(TAG, "ML Kit Object Detector ready (bundled, STREAM_MODE)")
    }

    override fun detect(frame: Bitmap): List<VehicleDetection> {
        val client = detector ?: return emptyList()
        return try {
            val image = InputImage.fromBitmap(frame, 0)
            val results = Tasks.await(client.process(image), inferTimeoutMs, TimeUnit.MILLISECONDS)
            results.mapNotNull { obj ->
                val box = obj.boundingBox
                if (box.width() <= 0 || box.height() <= 0) return@mapNotNull null
                val label = obj.labels.maxByOrNull { it.confidence }
                VehicleDetection(
                    label = label?.text ?: CANDIDATE_LABEL,
                    confidence = label?.confidence ?: DEFAULT_CANDIDATE_CONFIDENCE,
                    box = RectBox(
                        left = box.left.toFloat(),
                        top = box.top.toFloat(),
                        right = box.right.toFloat(),
                        bottom = box.bottom.toFloat()
                    )
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "ML Kit detect failed; emitting no boxes", t)
            emptyList()
        }
    }

    override fun close() {
        detector?.close()
        detector = null
    }

    companion object {
        private const val TAG = "MlKitVehicleDetector"
        private const val CANDIDATE_LABEL = "object"
        private const val DEFAULT_CANDIDATE_CONFIDENCE = 0.5f
    }
}
