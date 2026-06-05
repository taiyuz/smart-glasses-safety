package com.smartglasses.safety.pipeline

data class RiskResult(
    val level: AlertLevel,
    val message: String,
    val score: Float
)

class RiskScorer(
    private val profile: RiskProfile = RiskProfile.BALANCED
) {
    fun score(trackedVehicles: List<TrackedVehicle>): RiskResult {
        if (trackedVehicles.isEmpty()) {
            return RiskResult(AlertLevel.IDLE, "No approaching vehicles detected", 0f)
        }

        val maxRisk = trackedVehicles.maxOf { vehicle ->
            val growthWeight = profile.growthWeight
            val centerWeight = profile.centerWeight
            val confidenceWeight = profile.confidenceWeight

            val centerThreat = 1f - vehicle.centerDriftToMiddle
            val raw = (vehicle.areaGrowth * growthWeight) +
                (centerThreat * centerWeight) +
                (vehicle.confidencePersistence * confidenceWeight)
            raw.coerceIn(0f, 1.2f)
        }

        val level = when {
            maxRisk >= profile.criticalThreshold -> AlertLevel.CRITICAL
            maxRisk >= profile.warningThreshold -> AlertLevel.WARNING
            maxRisk >= profile.advisoryThreshold -> AlertLevel.ADVISORY
            else -> AlertLevel.IDLE
        }

        val message = when (level) {
            AlertLevel.CRITICAL -> "Critical: Vehicle approaching fast. Stop and verify."
            AlertLevel.WARNING -> "Warning: Vehicle approaching. Wait before crossing."
            AlertLevel.ADVISORY -> "Advisory: Vehicle nearby. Stay alert."
            AlertLevel.IDLE -> "Monitoring for approaching vehicles..."
        }

        return RiskResult(level = level, message = message, score = maxRisk)
    }
}

enum class RiskProfile(
    val advisoryThreshold: Float,
    val warningThreshold: Float,
    val criticalThreshold: Float,
    val growthWeight: Float,
    val centerWeight: Float,
    val confidenceWeight: Float
) {
    CONSERVATIVE(
        advisoryThreshold = 0.35f,
        warningThreshold = 0.55f,
        criticalThreshold = 0.75f,
        growthWeight = 0.45f,
        centerWeight = 0.3f,
        confidenceWeight = 0.25f
    ),
    BALANCED(
        advisoryThreshold = 0.45f,
        warningThreshold = 0.65f,
        criticalThreshold = 0.82f,
        growthWeight = 0.5f,
        centerWeight = 0.3f,
        confidenceWeight = 0.2f
    ),
    SENSITIVE(
        advisoryThreshold = 0.25f,
        warningThreshold = 0.45f,
        criticalThreshold = 0.65f,
        growthWeight = 0.45f,
        centerWeight = 0.35f,
        confidenceWeight = 0.2f
    )
}
