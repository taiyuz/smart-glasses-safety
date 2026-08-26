package com.smartglasses.safety.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Optional LiteRT path. Requires a `.tflite` in `assets/`. Without one,
 * [initialize] throws and [detect] never invents boxes.
 *
 * Binds InterpreterApi with GPU, then NNAPI, then CPU. Delegates are actually
 * constructed (`GpuDelegate`, `NnApiDelegate`); failures fall through and are logged.
 * Maven: `com.google.ai.edge.litert:litert` / `litert-gpu` / `litert-gpu-api` 1.4.2
 * (Interpreter line; LiteRT 2.x Interpreter is CPU-only). NNAPI is in the core
 * `litert` AAR as `org.tensorflow.lite.nnapi.NnApiDelegate`.
 */
class LiteRtVehicleDetector : VehicleDetector {
    private var interpreter: InterpreterApi? = null
    private var gpuDelegate: GpuDelegate? = null
    private var nnApiDelegate: NnApiDelegate? = null
    private var modelStream: FileInputStream? = null
    private var boundBackend: String = "none"

    override fun initialize(context: Context) {
        close()
        val model = loadFirstTflite(context)
            ?: throw IllegalStateException(
                "LiteRT: no .tflite in assets; refusing to initialize (no fake boxes)"
            )
        interpreter = bindInterpreter(model)
        Log.i(TAG, "LiteRT interpreter bound: $boundBackend")
    }

    override fun detect(frame: Bitmap): List<VehicleDetection> {
        if (interpreter == null) {
            Log.e(TAG, "LiteRT detect skipped: interpreter not initialized")
            return emptyList()
        }
        Log.w(
            TAG,
            "LiteRT model is bound ($boundBackend) but this repo has no output signature; emitting no boxes"
        )
        return emptyList()
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
        nnApiDelegate?.close()
        nnApiDelegate = null
        modelStream?.close()
        modelStream = null
        boundBackend = "none"
    }

    private fun bindInterpreter(model: MappedByteBuffer): InterpreterApi {
        try {
            val gpu = GpuDelegate()
            try {
                val options = InterpreterApi.Options().addDelegate(gpu)
                val interp = InterpreterApi.create(rewind(model), options)
                gpuDelegate = gpu
                boundBackend = "GPU"
                Log.i(TAG, "LiteRT bound to GPU via GpuDelegate")
                return interp
            } catch (t: Throwable) {
                Log.w(TAG, "GpuDelegate failed, falling back to NNAPI", t)
                gpu.close()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "GpuDelegate construct failed, falling back to NNAPI", t)
        }

        try {
            val nnapi = NnApiDelegate()
            try {
                val options = InterpreterApi.Options().addDelegate(nnapi)
                val interp = InterpreterApi.create(rewind(model), options)
                nnApiDelegate = nnapi
                boundBackend = "NNAPI"
                Log.i(TAG, "LiteRT bound to NNAPI via NnApiDelegate")
                return interp
            } catch (t: Throwable) {
                Log.w(TAG, "NnApiDelegate failed, falling back to CPU", t)
                nnapi.close()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "NnApiDelegate construct failed, falling back to CPU", t)
        }

        val options = InterpreterApi.Options()
        val interp = InterpreterApi.create(rewind(model), options)
        boundBackend = "CPU"
        Log.i(TAG, "LiteRT bound to CPU")
        return interp
    }

    private fun rewind(model: MappedByteBuffer): MappedByteBuffer {
        model.rewind()
        return model
    }

    private fun loadFirstTflite(context: Context): MappedByteBuffer? {
        val names = context.assets.list("") ?: emptyArray()
        val modelName = names.firstOrNull { it.endsWith(".tflite") } ?: return null
        val fd = context.assets.openFd(modelName)
        val input = FileInputStream(fd.fileDescriptor)
        modelStream = input
        return input.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    companion object {
        private const val TAG = "LiteRtVehicleDetector"
    }
}
