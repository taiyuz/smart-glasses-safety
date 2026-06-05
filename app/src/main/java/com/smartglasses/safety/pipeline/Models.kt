package com.smartglasses.safety.pipeline

data class RectBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float = right - left
    val height: Float = bottom - top
    val centerX: Float = (left + right) / 2f
    val area: Float = width * height
}

data class VehicleDetection(
    val label: String,
    val confidence: Float,
    val box: RectBox
)

data class TrackedVehicle(
    val id: Long,
    val detection: VehicleDetection,
    val areaGrowth: Float,
    val centerDriftToMiddle: Float,
    val confidencePersistence: Float
)

enum class AlertLevel {
    IDLE,
    ADVISORY,
    WARNING,
    CRITICAL
}
