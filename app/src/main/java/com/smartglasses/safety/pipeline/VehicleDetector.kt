package com.smartglasses.safety.pipeline

import android.content.Context
import android.graphics.Bitmap

interface VehicleDetector {
    fun initialize(context: Context)
    fun detect(frame: Bitmap): List<VehicleDetection>
    fun close()
}

class MockVehicleDetector : VehicleDetector {
    private val labels = setOf("car", "truck", "bus", "motorcycle", "bicycle")

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
