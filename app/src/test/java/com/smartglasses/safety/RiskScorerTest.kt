package com.smartglasses.safety

import com.smartglasses.safety.pipeline.AlertLevel
import com.smartglasses.safety.pipeline.RectBox
import com.smartglasses.safety.pipeline.RiskProfile
import com.smartglasses.safety.pipeline.RiskScorer
import com.smartglasses.safety.pipeline.TrackedVehicle
import com.smartglasses.safety.pipeline.VehicleDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskScorerTest {
    @Test
    fun returnsCriticalForHighRiskVehicle() {
        val scorer = RiskScorer(RiskProfile.BALANCED)
        val vehicle = TrackedVehicle(
            id = 1L,
            detection = VehicleDetection("car", 0.95f, RectBox(100f, 50f, 250f, 300f)),
            areaGrowth = 1.2f,
            centerDriftToMiddle = 0.05f,
            confidencePersistence = 1f
        )

        val result = scorer.score(listOf(vehicle))
        assertEquals(AlertLevel.CRITICAL, result.level)
    }

    @Test
    fun emptyTracksAreIdle() {
        val result = RiskScorer(RiskProfile.BALANCED).score(emptyList())
        assertEquals(AlertLevel.IDLE, result.level)
        assertEquals(0f, result.score)
    }

    @Test
    fun growingBoxAreaDoesNotLowerScore() {
        val scorer = RiskScorer(RiskProfile.BALANCED)
        val scores = listOf(0.1f, 0.4f, 0.9f).map { growth ->
            scorer.score(
                listOf(
                    vehicle(
                        areaGrowth = growth,
                        centerDriftToMiddle = 0.4f,
                        confidencePersistence = 0.6f
                    )
                )
            ).score
        }
        assertTrue(scores[1] >= scores[0] - 1e-4f)
        assertTrue(scores[2] >= scores[1] - 1e-4f)
    }

    @Test
    fun closerToCenterDoesNotLowerScore() {
        val scorer = RiskScorer(RiskProfile.BALANCED)
        val far = scorer.score(listOf(vehicle(centerDriftToMiddle = 0.9f))).score
        val mid = scorer.score(listOf(vehicle(centerDriftToMiddle = 0.4f))).score
        val near = scorer.score(listOf(vehicle(centerDriftToMiddle = 0.05f))).score
        assertTrue(mid >= far - 1e-4f)
        assertTrue(near >= mid - 1e-4f)
    }

    @Test
    fun sensitiveFiresAtLeastAsEarlyAsConservative() {
        val mild = listOf(
            vehicle(
                areaGrowth = 0.35f,
                centerDriftToMiddle = 0.45f,
                confidencePersistence = 0.5f
            )
        )
        val conservative = RiskScorer(RiskProfile.CONSERVATIVE).score(mild)
        val sensitive = RiskScorer(RiskProfile.SENSITIVE).score(mild)
        assertTrue(
            "sensitive=${sensitive.level} conservative=${conservative.level}",
            sensitive.level.ordinal >= conservative.level.ordinal
        )
    }

    @Test
    fun balancedThresholdsSitBetweenProfiles() {
        assertTrue(RiskProfile.SENSITIVE.advisoryThreshold < RiskProfile.BALANCED.advisoryThreshold)
        assertTrue(RiskProfile.BALANCED.advisoryThreshold < RiskProfile.CONSERVATIVE.advisoryThreshold)
        assertTrue(RiskProfile.SENSITIVE.criticalThreshold < RiskProfile.CONSERVATIVE.criticalThreshold)
    }

    private fun vehicle(
        areaGrowth: Float = 0.4f,
        centerDriftToMiddle: Float = 0.3f,
        confidencePersistence: Float = 0.7f
    ): TrackedVehicle {
        return TrackedVehicle(
            id = 1L,
            detection = VehicleDetection("car", 0.8f, RectBox(40f, 40f, 120f, 160f)),
            areaGrowth = areaGrowth,
            centerDriftToMiddle = centerDriftToMiddle,
            confidencePersistence = confidencePersistence
        )
    }
}
