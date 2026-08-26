package com.smartglasses.safety.pipeline

import android.content.Context
import android.graphics.Bitmap

interface VehicleDetector {
    val backendName: String
    val isReady: Boolean
    val statusMessage: String

    fun initialize(context: Context)
    fun detect(frame: Bitmap): List<VehicleDetection>
    fun close()
}

class MockVehicleDetector : VehicleDetector {
    private val labels = setOf("car", "truck", "bus", "motorcycle", "bicycle")

    override val backendName: String = "MockVehicleDetector"
    override val isReady: Boolean = true
    override val statusMessage: String =
        "MOCK detector — synthetic centered car box. Debug-only (USE_MOCK_DETECTOR)."

    override fun initialize(context: Context) = Unit

    override fun detect(frame: Bitmap): List<VehicleDetection> {
        val center = RectBox(
            left = frame.width * 0.35f,
            top = frame.height * 0.35f,
            right = frame.width * 0.65f,
            bottom = frame.height * 0.8f
        )
        val detection = VehicleDetection(label = "car", confidence = 0.85f, box = center)
        return listOf(detection).filter { it.label in labels }
    }

    override fun close() = Unit
}

class UnavailableDetector(reason: String) : VehicleDetector {
    override val backendName: String = "unavailable"
    override val isReady: Boolean = false
    override val statusMessage: String = "DETECTOR FAILED: $reason"

    override fun initialize(context: Context) = Unit
    override fun detect(frame: Bitmap): List<VehicleDetection> = emptyList()
    override fun close() = Unit
}
