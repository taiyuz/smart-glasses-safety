package com.smartglasses.safety.pipeline

data class RiskResult(
    val level: AlertLevel,
    val message: String,
    val score: Float
)

/**
 * Maps track features to an [AlertLevel].
 *
 * Enter thresholds come from [RiskProfile]. Once a level is active, the score
 * must fall below that level's exit threshold (enter − [exitMargin]) before the
 * alert drops. Upgrades still use the enter thresholds. This stops frame-to-frame
 * flicker when areaGrowth jitters around a cut-off; it is not ByteTrack.
 */
class RiskScorer(
    private val profile: RiskProfile = RiskProfile.BALANCED,
    private val exitMargin: Float = 0.08f
) {
    private var lastLevel: AlertLevel = AlertLevel.IDLE

    fun score(trackedVehicles: List<TrackedVehicle>): RiskResult {
        if (trackedVehicles.isEmpty()) {
            lastLevel = AlertLevel.IDLE
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

        val level = applyHysteresis(maxRisk)
        lastLevel = level

        val message = when (level) {
            AlertLevel.CRITICAL -> "Critical: Vehicle approaching fast. Stop and verify."
            AlertLevel.WARNING -> "Warning: Vehicle approaching. Wait before crossing."
            AlertLevel.ADVISORY -> "Advisory: Vehicle nearby. Stay alert."
            AlertLevel.IDLE -> "Monitoring for approaching vehicles..."
        }

        return RiskResult(level = level, message = message, score = maxRisk)
    }

    /** Clears held alert state (e.g. when [AnalysisPipeline] starts a new camera session). */
    fun reset() {
        lastLevel = AlertLevel.IDLE
    }

    private fun applyHysteresis(score: Float): AlertLevel {
        val criticalEnter = profile.criticalThreshold
        val warningEnter = profile.warningThreshold
        val advisoryEnter = profile.advisoryThreshold
        val criticalExit = (criticalEnter - exitMargin).coerceAtLeast(warningEnter)
        val warningExit = (warningEnter - exitMargin).coerceAtLeast(advisoryEnter)
        val advisoryExit = (advisoryEnter - exitMargin).coerceAtLeast(0f)

        return when (lastLevel) {
            AlertLevel.CRITICAL -> when {
                score >= criticalExit -> AlertLevel.CRITICAL
                score >= warningEnter -> AlertLevel.WARNING
                score >= advisoryEnter -> AlertLevel.ADVISORY
                else -> AlertLevel.IDLE
            }
            AlertLevel.WARNING -> when {
                score >= criticalEnter -> AlertLevel.CRITICAL
                score >= warningExit -> AlertLevel.WARNING
                score >= advisoryEnter -> AlertLevel.ADVISORY
                else -> AlertLevel.IDLE
            }
            AlertLevel.ADVISORY -> when {
                score >= criticalEnter -> AlertLevel.CRITICAL
                score >= warningEnter -> AlertLevel.WARNING
                score >= advisoryExit -> AlertLevel.ADVISORY
                else -> AlertLevel.IDLE
            }
            AlertLevel.IDLE -> when {
                score >= criticalEnter -> AlertLevel.CRITICAL
                score >= warningEnter -> AlertLevel.WARNING
                score >= advisoryEnter -> AlertLevel.ADVISORY
                else -> AlertLevel.IDLE
            }
        }
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
