package com.smartglasses.safety.pipeline

/**
 * COCO 90-slot label map used by EfficientDet-Lite0 TFLite metadata models.
 * Unused TF Object Detection API indices are "???".
 */
object CocoLabels {
    val names: List<String> = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck",
        "boat", "traffic light", "fire hydrant", "???", "stop sign", "parking meter", "bench",
        "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe",
        "???", "backpack", "umbrella", "???", "???", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove",
        "skateboard", "surfboard", "tennis racket", "bottle", "???", "wine glass", "cup",
        "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange", "broccoli",
        "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch", "potted plant", "bed",
        "???", "dining table", "???", "???", "toilet", "???", "tv", "laptop", "mouse", "remote",
        "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator",
        "???", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
    )

    val vehicleNames: Set<String> = setOf("bicycle", "car", "motorcycle", "bus", "truck")

    fun nameFor(classId: Int): String {
        if (classId < 0 || classId >= names.size) return "unknown"
        return names[classId]
    }

    fun isVehicle(label: String): Boolean = label in vehicleNames
}
