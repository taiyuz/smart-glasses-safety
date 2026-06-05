package com.smartglasses.safety.pipeline

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer

class TFLiteVehicleDetector(
    private val modelBufferProvider: (Context) -> ByteBuffer
) : VehicleDetector {
    private var interpreter: Interpreter? = null

    override fun initialize(context: Context) {
        interpreter = Interpreter(modelBufferProvider(context))
    }

    override fun detect(frame: Bitmap): List<VehicleDetection> {
        // Model-specific preprocessing/postprocessing should be implemented here.
        // Returning empty list until a concrete model signature is wired.
        if (interpreter == null) return emptyList()
        return emptyList()
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }
}
