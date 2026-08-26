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
    val centerY: Float = (top + bottom) / 2f
    val area: Float = width * height

    fun iou(other: RectBox): Float {
        val ix1 = maxOf(left, other.left)
        val iy1 = maxOf(top, other.top)
        val ix2 = minOf(right, other.right)
        val iy2 = minOf(bottom, other.bottom)
        val inter = maxOf(0f, ix2 - ix1) * maxOf(0f, iy2 - iy1)
        val union = area + other.area - inter
        return if (union <= 0f) 0f else inter / union
    }
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
