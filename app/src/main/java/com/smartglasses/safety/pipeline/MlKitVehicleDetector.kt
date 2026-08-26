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
 * Default on-device detector.
 *
 * Uses the bundled ML Kit Object Detection artifact (`com.google.mlkit:object-detection`).
 * The model ships in the APK, so a Play Services model download is not required at runtime.
 *
 * Honest limitation: the bundled detector finds generic objects. Its optional coarse
 * classifier is fashion/food/home/place/plant — not vehicle classes — so classification
 * is left off. Every detected object is treated as a vehicle *candidate*. Expect
 * false positives on non-vehicles. This is not a vehicle-class detector and not ADAS.
 */
class MlKitVehicleDetector(
    private val inferTimeoutMs: Long = 400L
) : VehicleDetector {
    private var detector: ObjectDetector? = null
    @Volatile private var initError: String? = null

    override val diagnosticMessage: String?
        get() = initError

    override fun initialize(context: Context) {
        try {
            val options = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .build()
            detector = ObjectDetection.getClient(options)
            initError = null
            Log.i(TAG, "ML Kit Object Detector ready (bundled model, STREAM_MODE, no coarse classifier)")
        } catch (t: Throwable) {
            detector = null
            val message = "ML Kit init failed: ${t.message ?: t.javaClass.simpleName}"
            initError = message
            Log.e(TAG, message, t)
        }
    }

    override fun detect(frame: Bitmap): List<VehicleDetection> {
        val client = detector
        if (client == null) {
            if (initError == null) {
                initError = "ML Kit detector is not initialized"
            }
            Log.w(TAG, diagnosticMessage ?: "ML Kit detector unavailable")
            return emptyList()
        }

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
