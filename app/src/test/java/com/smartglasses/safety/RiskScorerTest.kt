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
    private val box = RectBox(100f, 50f, 250f, 300f)

    @Test
    fun returnsCriticalForHighRiskVehicle() {
        val scorer = RiskScorer(RiskProfile.BALANCED)
        val vehicle = tracked(
            areaGrowth = 1.2f,
            centerDriftToMiddle = 0.05f,
            confidencePersistence = 1f
        )

        val result = scorer.score(listOf(vehicle))
        assertEquals(AlertLevel.CRITICAL, result.level)
    }

    @Test
    fun emptyDetectionsAreIdle() {
        val result = RiskScorer(RiskProfile.BALANCED).score(emptyList())
        assertEquals(AlertLevel.IDLE, result.level)
        assertEquals(0f, result.score, 0f)
    }

    @Test
    fun growingBoxScoresHigherThanStaticBox() {
        val scorer = RiskScorer(RiskProfile.BALANCED)
        val growing = tracked(areaGrowth = 1.0f, centerDriftToMiddle = 0.2f, confidencePersistence = 0.9f)
        val staticBox = tracked(areaGrowth = 0.0f, centerDriftToMiddle = 0.2f, confidencePersistence = 0.9f)

        val growScore = scorer.score(listOf(growing)).score
        val staticScore = scorer.score(listOf(staticBox)).score
        assertTrue("growing=$growScore static=$staticScore", growScore > staticScore)
    }

    @Test
    fun centerThreatScoresHigherThanPeripheral() {
        val scorer = RiskScorer(RiskProfile.BALANCED)
        val center = tracked(areaGrowth = 0.6f, centerDriftToMiddle = 0.05f, confidencePersistence = 0.9f)
        val peripheral = tracked(areaGrowth = 0.6f, centerDriftToMiddle = 0.9f, confidencePersistence = 0.9f)

        val centerScore = scorer.score(listOf(center)).score
        val peripheralScore = scorer.score(listOf(peripheral)).score
        assertTrue("center=$centerScore peripheral=$peripheralScore", centerScore > peripheralScore)
    }

    @Test
    fun sensitiveProfileAlertsAtLowerScoreThanConservative() {
        val moderate = tracked(
            areaGrowth = 0.55f,
            centerDriftToMiddle = 0.25f,
            confidencePersistence = 0.7f
        )

        val sensitive = RiskScorer(RiskProfile.SENSITIVE).score(listOf(moderate))
        val conservative = RiskScorer(RiskProfile.CONSERVATIVE).score(listOf(moderate))

        assertTrue(
            "sensitive=${sensitive.level} conservative=${conservative.level}",
            sensitive.level.ordinal >= conservative.level.ordinal
        )
        assertTrue("sensitive should alert", sensitive.level != AlertLevel.IDLE)
    }

    @Test
    fun balancedThresholdsMapAdvisoryWarningCritical() {
        val scorer = RiskScorer(RiskProfile.BALANCED)

        val advisory = tracked(areaGrowth = 0.4f, centerDriftToMiddle = 0.3f, confidencePersistence = 0.8f)
        val warning = tracked(areaGrowth = 0.7f, centerDriftToMiddle = 0.15f, confidencePersistence = 0.85f)
        val critical = tracked(areaGrowth = 1.1f, centerDriftToMiddle = 0.05f, confidencePersistence = 1f)

        assertEquals(AlertLevel.ADVISORY, scorer.score(listOf(advisory)).level)
        assertEquals(AlertLevel.WARNING, scorer.score(listOf(warning)).level)
        assertEquals(AlertLevel.CRITICAL, scorer.score(listOf(critical)).level)
    }

    private fun tracked(
        areaGrowth: Float,
        centerDriftToMiddle: Float,
        confidencePersistence: Float
    ): TrackedVehicle {
        return TrackedVehicle(
            id = 1L,
            detection = VehicleDetection("car", 0.95f, box),
            areaGrowth = areaGrowth,
            centerDriftToMiddle = centerDriftToMiddle,
            confidencePersistence = confidencePersistence
        )
    }
}
