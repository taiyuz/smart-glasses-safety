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
    private fun vehicle(
        areaGrowth: Float,
        centerDriftToMiddle: Float,
        confidencePersistence: Float = 1f
    ): TrackedVehicle {
        return TrackedVehicle(
            id = 1L,
            detection = VehicleDetection("car", 0.95f, RectBox(100f, 50f, 250f, 300f)),
            areaGrowth = areaGrowth,
            centerDriftToMiddle = centerDriftToMiddle,
            confidencePersistence = confidencePersistence
        )
    }

    @Test
    fun idleWhenNoVehicles() {
        val result = RiskScorer(RiskProfile.BALANCED).score(emptyList())
        assertEquals(AlertLevel.IDLE, result.level)
        assertEquals(0f, result.score, 0.0001f)
    }

    @Test
    fun returnsCriticalForHighRiskVehicle() {
        val result = RiskScorer(RiskProfile.BALANCED).score(
            listOf(vehicle(areaGrowth = 1.2f, centerDriftToMiddle = 0.05f))
        )
        assertEquals(AlertLevel.CRITICAL, result.level)
    }

    @Test
    fun growingCenteredBoxScoresHigherThanSmallPeripheralBox() {
        val scorer = RiskScorer(RiskProfile.BALANCED)
        val approaching = scorer.score(
            listOf(vehicle(areaGrowth = 1.0f, centerDriftToMiddle = 0.05f))
        )
        val peripheral = scorer.score(
            listOf(vehicle(areaGrowth = 0f, centerDriftToMiddle = 0.9f, confidencePersistence = 0.2f))
        )
        assertTrue(approaching.score > peripheral.score)
    }
}
