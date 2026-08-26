package com.smartglasses.safety.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Optional LiteRT adapter for a future EfficientDet-Lite0 (or similar) placed in assets.
 *
 * No trained weights are in this repo. [detect] returns an empty list until a concrete
 * input/output signature is wired — it never emits mock boxes. Prefer [MlKitVehicleDetector]
 * as the runtime default.
 *
 * Accelerator order is real, not a comment: try GPU (CompatibilityList), then NNAPI, then CPU.
 * Whichever binds is logged. A failed delegate is closed and skipped.
 */
class TFLiteVehicleDetector(
    private val modelBufferProvider: (Context) -> ByteBuffer
) : VehicleDetector {
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var nnapiDelegate: NnApiDelegate? = null
    @Volatile private var initError: String? = null
    @Volatile var boundAccelerator: String = "none"
        private set

    override val diagnosticMessage: String?
        get() = initError

    override fun initialize(context: Context) {
        close()
        val buffer = try {
            modelBufferProvider(context)
        } catch (t: Throwable) {
            fail("LiteRT model load failed: ${t.message ?: t.javaClass.simpleName}", t)
            return
        }

        val options = Interpreter.Options()
        if (tryGpu(options) || tryNnapi(options)) {
            options.setNumThreads(1)
        } else {
            boundAccelerator = "CPU"
            options.setNumThreads(2)
            Log.i(TAG, "LiteRT using CPU (GPU and NNAPI unavailable)")
        }

        try {
            interpreter = Interpreter(buffer, options)
            initError = null
            Log.i(TAG, "LiteRT interpreter ready on $boundAccelerator")
        } catch (t: Throwable) {
            fail("LiteRT init failed on $boundAccelerator: ${t.message ?: t.javaClass.simpleName}", t)
            closeDelegates()
        }
    }

    override fun detect(frame: Bitmap): List<VehicleDetection> {
        if (interpreter == null) return emptyList()
        // Preprocess/postprocess for a concrete EfficientDet-Lite signature is not wired.
        // Returning empty keeps this path honest until weights exist in assets.
        return emptyList()
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        closeDelegates()
    }

    private fun tryGpu(options: Interpreter.Options): Boolean {
        return try {
            val compat = CompatibilityList()
            if (!compat.isDelegateSupportedOnThisDevice) {
                compat.close()
                Log.i(TAG, "GPU delegate not supported on this device")
                return false
            }
            val delegate = GpuDelegate(compat.bestOptionsForThisDevice)
            compat.close()
            options.addDelegate(delegate)
            gpuDelegate = delegate
            boundAccelerator = "GPU"
            Log.i(TAG, "LiteRT GPU delegate bound")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "LiteRT GPU delegate skipped", t)
            closeGpu()
            false
        }
    }

    private fun tryNnapi(options: Interpreter.Options): Boolean {
        return try {
            val delegate = NnApiDelegate()
            options.addDelegate(delegate)
            nnapiDelegate = delegate
            boundAccelerator = "NNAPI"
            Log.i(TAG, "LiteRT NNAPI delegate bound")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "LiteRT NNAPI delegate skipped", t)
            closeNnapi()
            false
        }
    }

    private fun fail(message: String, t: Throwable) {
        interpreter = null
        initError = message
        boundAccelerator = "none"
        Log.e(TAG, message, t)
    }

    private fun closeDelegates() {
        closeGpu()
        closeNnapi()
    }

    private fun closeGpu() {
        try {
            gpuDelegate?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "GPU delegate close failed", t)
        }
        gpuDelegate = null
    }

    private fun closeNnapi() {
        try {
            nnapiDelegate?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "NNAPI delegate close failed", t)
        }
        nnapiDelegate = null
    }

    companion object {
        private const val TAG = "LiteRtVehicleDetector"
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
