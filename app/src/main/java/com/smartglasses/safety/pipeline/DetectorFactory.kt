package com.smartglasses.safety.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.smartglasses.safety.BuildConfig

object DetectorFactory {
    fun create(): VehicleDetector = OnDeviceVehicleDetector()
}

/**
 * Default detector: ML Kit (no weights) or TFLite EfficientDet-Lite0 when the
 * Gradle-downloaded model is in assets. Mock is debug-flag only.
 */
class OnDeviceVehicleDetector : VehicleDetector {
    private var impl: VehicleDetector = UnavailableDetector("not initialized")

    override val backendName: String get() = impl.backendName
    override val isReady: Boolean get() = impl.isReady
    override val statusMessage: String get() = impl.statusMessage

    override fun initialize(context: Context) {
        if (BuildConfig.DEBUG && BuildConfig.USE_MOCK_DETECTOR) {
            impl = MockVehicleDetector()
            impl.initialize(context)
            Log.w(TAG, impl.statusMessage)
            return
        }

        if (TFLiteVehicleDetector.modelAvailable(context)) {
            val tflite = TFLiteVehicleDetector()
            try {
                tflite.initialize(context)
                if (tflite.isReady) {
                    impl = tflite
                    Log.i(TAG, tflite.statusMessage)
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "TFLite EfficientDet-Lite0 failed to load", e)
                tflite.close()
            }
        }

        val mlKit = MlKitVehicleDetector()
        try {
            mlKit.initialize(context)
            impl = mlKit
            Log.i(TAG, mlKit.statusMessage)
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit object detector failed to load", e)
            impl = UnavailableDetector(e.message ?: "ML Kit failed to initialize")
        }
    }

    override fun detect(frame: Bitmap): List<VehicleDetection> {
        if (!impl.isReady) return emptyList()
        return impl.detect(frame)
    }

    override fun close() {
        impl.close()
    }

    companion object {
        private const val TAG = "SmartGlassesSafety"
    }
}
