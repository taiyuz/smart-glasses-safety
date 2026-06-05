package com.smartglasses.safety

import com.smartglasses.safety.pipeline.AlertLevel
import com.smartglasses.safety.pipeline.RectBox
import com.smartglasses.safety.pipeline.RiskProfile
import com.smartglasses.safety.pipeline.RiskScorer
import com.smartglasses.safety.pipeline.TrackedVehicle
import com.smartglasses.safety.pipeline.VehicleDetection
import org.junit.Assert.assertEquals
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
}
