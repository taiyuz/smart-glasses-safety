package com.smartglasses.safety.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Optional TFLite adapter for a future EfficientDet-Lite0 (or similar) placed in assets.
 *
 * No trained weights are in this repo. [detect] returns an empty list until a concrete
 * input/output signature is wired. Prefer [MlKitVehicleDetector] as the runtime default.
 * GPU / NNAPI delegates are intentionally not enabled here.
 */
class TFLiteVehicleDetector(
    private val modelBufferProvider: (Context) -> ByteBuffer
) : VehicleDetector {
    private var interpreter: Interpreter? = null
    @Volatile private var initError: String? = null

    override val diagnosticMessage: String?
        get() = initError

    override fun initialize(context: Context) {
        try {
            interpreter = Interpreter(modelBufferProvider(context))
            initError = null
        } catch (t: Throwable) {
            interpreter = null
            val message = "TFLite init failed: ${t.message ?: t.javaClass.simpleName}"
            initError = message
            Log.e(TAG, message, t)
        }
    }

    override fun detect(frame: Bitmap): List<VehicleDetection> {
        // Model-specific preprocessing/postprocessing is not implemented.
        // Returning empty list until a concrete model signature is wired.
        if (interpreter == null) return emptyList()
        return emptyList()
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }

    companion object {
        private const val TAG = "TFLiteVehicleDetector"
        const val DEFAULT_ASSET_NAME = "efficientdet-lite0.tflite"

        fun fromAssets(assetName: String = DEFAULT_ASSET_NAME): TFLiteVehicleDetector {
            return TFLiteVehicleDetector { context ->
                context.assets.open(assetName).use { input ->
                    val bytes = input.readBytes()
                    ByteBuffer.allocateDirect(bytes.size).apply {
                        order(ByteOrder.nativeOrder())
                        put(bytes)
                        rewind()
                    }
                }
            }
        }
    }
}
