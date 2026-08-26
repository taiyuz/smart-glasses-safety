package com.smartglasses.safety.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.min

class TFLiteVehicleDetector(
    private val assetPath: String = MODEL_ASSET,
    private val scoreThreshold: Float = 0.3f
) : VehicleDetector {
    private var interpreter: Interpreter? = null
    private var inputWidth = 320
    private var inputHeight = 320
    private var inputIsUInt8 = true
    private var boxesIndex = 0
    private var classesIndex = 1
    private var scoresIndex = 2
    private var countIndex: Int? = 3
    private var outputBuffers: MutableMap<Int, Any> = mutableMapOf()
    private var swappedClassScore = false

    override var backendName: String = "TFLite EfficientDet-Lite0"
        private set
    override var isReady: Boolean = false
        private set
    override var statusMessage: String = "TFLite not initialized"
        private set

    override fun initialize(context: Context) {
        val buffer = loadModelFile(context, assetPath)
        val options = Interpreter.Options().apply {
            setNumThreads(2)
            // GPU / NNAPI delegates are a later step: they need per-device
            // validation (accuracy, warmup, fallback) before claiming a speedup.
        }
        val interp = Interpreter(buffer, options)
        bindTensors(interp)
        interpreter = interp
        isReady = true
        statusMessage =
            "TFLite EfficientDet-Lite0 (${inputWidth}x$inputHeight, CPU). " +
                "COCO filter: bicycle/car/motorcycle/bus/truck."
        Log.i(TAG, statusMessage)
    }

    override fun detect(frame: Bitmap): List<VehicleDetection> {
        val interp = interpreter ?: return emptyList()
        if (!isReady) return emptyList()
        return try {
            val input = preprocess(frame)
            interp.runForMultipleInputsOutputs(arrayOf(input), outputBuffers)
            postprocess(frame.width, frame.height)
        } catch (e: Exception) {
            Log.e(TAG, "TFLite detect failed", e)
            emptyList()
        }
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        isReady = false
        outputBuffers.clear()
    }

    private fun bindTensors(interp: Interpreter) {
        val inShape = interp.getInputTensor(0).shape()
        inputHeight = inShape[1]
        inputWidth = inShape[2]
        inputIsUInt8 = interp.getInputTensor(0).dataType() == DataType.UINT8

        var boxes = -1
        var classes = -1
        var scores = -1
        var count = -1
        for (i in 0 until interp.outputTensorCount) {
            val tensor = interp.getOutputTensor(i)
            val shape = tensor.shape()
            val name = tensor.name().lowercase()
            when {
                shape.size == 3 && shape.last() == 4 -> boxes = i
                "class" in name -> classes = i
                "score" in name -> scores = i
                "num" in name || "count" in name -> count = i
                shape.size == 1 -> count = i
                shape.size == 2 && shape[1] == 1 -> count = i
            }
        }
        val rank2 = (0 until interp.outputTensorCount).filter { i ->
            val s = interp.getOutputTensor(i).shape()
            s.size == 2 && i != count && i != boxes
        }.sorted()
        if (classes < 0 && rank2.isNotEmpty()) classes = rank2[0]
        if (scores < 0 && rank2.size >= 2) scores = rank2[1]
        if (boxes < 0) boxes = 0
        if (classes < 0) classes = 1
        if (scores < 0) scores = 2

        boxesIndex = boxes
        classesIndex = classes
        scoresIndex = scores
        countIndex = count.takeIf { it >= 0 }

        outputBuffers = mutableMapOf()
        for (i in 0 until interp.outputTensorCount) {
            outputBuffers[i] = allocateOutput(interp.getOutputTensor(i).shape())
        }
    }

    private fun allocateOutput(shape: IntArray): Any {
        return when (shape.size) {
            1 -> FloatArray(shape[0])
            2 -> Array(shape[0]) { FloatArray(shape[1]) }
            3 -> Array(shape[0]) { Array(shape[1]) { FloatArray(shape[2]) } }
            else -> error("Unsupported output rank ${shape.size}")
        }
    }

    private fun preprocess(frame: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(frame, inputWidth, inputHeight, true)
        val pixelCount = inputWidth * inputHeight
        val pixels = IntArray(pixelCount)
        scaled.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        if (scaled !== frame) scaled.recycle()

        return if (inputIsUInt8) {
            val buf = ByteBuffer.allocateDirect(pixelCount * 3).order(ByteOrder.nativeOrder())
            for (p in pixels) {
                buf.put(((p shr 16) and 0xFF).toByte())
                buf.put(((p shr 8) and 0xFF).toByte())
                buf.put((p and 0xFF).toByte())
            }
            buf.rewind()
            buf
        } else {
            val buf = ByteBuffer.allocateDirect(pixelCount * 3 * 4).order(ByteOrder.nativeOrder())
            for (p in pixels) {
                buf.putFloat(((p shr 16) and 0xFF) / 255f)
                buf.putFloat(((p shr 8) and 0xFF) / 255f)
                buf.putFloat((p and 0xFF) / 255f)
            }
            buf.rewind()
            buf
        }
    }

    private fun postprocess(frameWidth: Int, frameHeight: Int): List<VehicleDetection> {
        val boxesArr = flattenBoxes(outputBuffers[boxesIndex]!!)
        var classes = flatten1d(outputBuffers[classesIndex]!!)
        var scores = flatten1d(outputBuffers[scoresIndex]!!)
        if (!swappedClassScore && looksSwapped(classes, scores)) {
            swappedClassScore = true
            val tmp = classesIndex
            classesIndex = scoresIndex
            scoresIndex = tmp
            val swap = classes
            classes = scores
            scores = swap
        } else if (swappedClassScore) {
            val swap = classes
            classes = scores
            scores = swap
        }

        val reported = countIndex?.let { flatten1d(outputBuffers[it]!!).firstOrNull()?.toInt() }
        val n = min(
            reported ?: min(boxesArr.size, min(classes.size, scores.size)),
            min(boxesArr.size, min(classes.size, scores.size))
        )

        val out = ArrayList<VehicleDetection>(n)
        for (i in 0 until n) {
            val score = scores[i]
            if (score < scoreThreshold) continue
            val label = CocoLabels.nameFor(classes[i].toInt())
            if (!CocoLabels.isVehicle(label)) continue
            val ymin = boxesArr[i][0]
            val xmin = boxesArr[i][1]
            val ymax = boxesArr[i][2]
            val xmax = boxesArr[i][3]
            val box = RectBox(
                left = (xmin * frameWidth).coerceIn(0f, frameWidth.toFloat()),
                top = (ymin * frameHeight).coerceIn(0f, frameHeight.toFloat()),
                right = (xmax * frameWidth).coerceIn(0f, frameWidth.toFloat()),
                bottom = (ymax * frameHeight).coerceIn(0f, frameHeight.toFloat())
            )
            if (box.width <= 1f || box.height <= 1f) continue
            out += VehicleDetection(label = label, confidence = score, box = box)
        }
        return out
    }

    private fun looksSwapped(classes: FloatArray, scores: FloatArray): Boolean {
        if (classes.isEmpty() || scores.isEmpty()) return false
        val classLooksLikeScore = classes.take(8).all { it in 0f..1.0001f }
        val scoresLookLikeIds = scores.take(8).any { it > 1.5f }
        return classLooksLikeScore && scoresLookLikeIds
    }

    @Suppress("UNCHECKED_CAST")
    private fun flattenBoxes(raw: Any): Array<FloatArray> {
        return when (raw) {
            is Array<*> -> {
                val first = raw.firstOrNull()
                if (first is Array<*>) first as Array<FloatArray>
                else raw as Array<FloatArray>
            }
            else -> emptyArray()
        }
    }

    private fun flatten1d(raw: Any): FloatArray {
        return when (raw) {
            is FloatArray -> raw
            is Array<*> -> {
                val first = raw.firstOrNull()
                if (first is FloatArray) first
                else FloatArray(raw.size) { idx -> (raw[idx] as Number).toFloat() }
            }
            else -> floatArrayOf()
        }
    }

    companion object {
        const val MODEL_ASSET = "efficientdet-lite0.tflite"
        private const val TAG = "SmartGlassesSafety"

        fun modelAvailable(context: Context): Boolean {
            return try {
                context.assets.open(MODEL_ASSET).close()
                true
            } catch (_: IOException) {
                false
            }
        }

        private fun loadModelFile(context: Context, path: String): MappedByteBuffer {
            val fd = context.assets.openFd(path)
            FileInputStream(fd.fileDescriptor).use { input ->
                return input.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fd.startOffset,
                    fd.declaredLength
                )
            }
        }
    }
}
