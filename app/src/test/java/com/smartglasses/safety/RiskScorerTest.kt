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
        assertEquals("No approaching vehicles detected", result.message)
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

    @Test
    fun sameVehicleMapsToDifferentLevelsAcrossProfiles() {
        val candidate = vehicle(
            areaGrowth = 0.1f,
            centerDriftToMiddle = 0.2f,
            confidencePersistence = 0.5f
        )
        val conservative = RiskScorer(RiskProfile.CONSERVATIVE).score(listOf(candidate))
        val balanced = RiskScorer(RiskProfile.BALANCED).score(listOf(candidate))
        val sensitive = RiskScorer(RiskProfile.SENSITIVE).score(listOf(candidate))
        assertEquals(AlertLevel.ADVISORY, conservative.level)
        assertEquals(AlertLevel.IDLE, balanced.level)
        assertEquals(AlertLevel.ADVISORY, sensitive.level)
        assertTrue(sensitive.score > balanced.score)
    }

    @Test
    fun balancedWarningBand() {
        val result = RiskScorer(RiskProfile.BALANCED).score(
            listOf(
                vehicle(
                    areaGrowth = 0.6f,
                    centerDriftToMiddle = 0.2f,
                    confidencePersistence = 0.8f
                )
            )
        )
        assertEquals(AlertLevel.WARNING, result.level)
        assertTrue(result.score >= 0.65f)
        assertTrue(result.score < 0.82f)
    }

    @Test
    fun reportsHighestRiskAmongSeveralVehicles() {
        val quiet = vehicle(
            areaGrowth = 0f,
            centerDriftToMiddle = 0.9f,
            confidencePersistence = 0.2f
        )
        val loud = vehicle(areaGrowth = 1.2f, centerDriftToMiddle = 0.05f)
        val mixed = RiskScorer(RiskProfile.BALANCED).score(listOf(quiet, loud))
        val onlyQuiet = RiskScorer(RiskProfile.BALANCED).score(listOf(quiet))
        assertEquals(AlertLevel.CRITICAL, mixed.level)
        assertTrue(mixed.score > onlyQuiet.score)
    }

    @Test
    fun clampsScoreToOnePointTwo() {
        val result = RiskScorer(RiskProfile.BALANCED).score(
            listOf(vehicle(areaGrowth = 3f, centerDriftToMiddle = 0f, confidencePersistence = 1f))
        )
        assertEquals(1.2f, result.score, 0.0001f)
        assertEquals(AlertLevel.CRITICAL, result.level)
    }

    @Test
    fun negativeGrowthDoesNotProduceANegativeScore() {
        val result = RiskScorer(RiskProfile.BALANCED).score(
            listOf(vehicle(areaGrowth = -1f, centerDriftToMiddle = 1f, confidencePersistence = 0f))
        )
        assertEquals(0f, result.score, 0.0001f)
        assertEquals(AlertLevel.IDLE, result.level)
        assertEquals("Monitoring for approaching vehicles...", result.message)
    }

    @Test
    fun balancedWeightedFormula() {
        // growth 0.4 * 0.5 + centerThreat 0.5 * 0.3 + conf 1.0 * 0.2 = 0.55
        val result = RiskScorer(RiskProfile.BALANCED).score(
            listOf(
                vehicle(
                    areaGrowth = 0.4f,
                    centerDriftToMiddle = 0.5f,
                    confidencePersistence = 1f
                )
            )
        )
        assertEquals(0.55f, result.score, 0.0001f)
        assertEquals(AlertLevel.ADVISORY, result.level)
        assertEquals("Advisory: Vehicle nearby. Stay alert.", result.message)
    }

    @Test
    fun sensitiveMapsTheSameInputsToWarning() {
        val result = RiskScorer(RiskProfile.SENSITIVE).score(
            listOf(
                vehicle(
                    areaGrowth = 0.4f,
                    centerDriftToMiddle = 0.5f,
                    confidencePersistence = 1f
                )
            )
        )
        // 0.4*0.45 + 0.5*0.35 + 1.0*0.2 = 0.555
        assertEquals(0.555f, result.score, 0.0001f)
        assertEquals(AlertLevel.WARNING, result.level)
        assertEquals("Warning: Vehicle approaching. Wait before crossing.", result.message)
    }

    @Test
    fun criticalMessageMatchesLevel() {
        val result = RiskScorer(RiskProfile.BALANCED).score(
            listOf(vehicle(areaGrowth = 1.2f, centerDriftToMiddle = 0.05f))
        )
        assertEquals(AlertLevel.CRITICAL, result.level)
        assertEquals("Critical: Vehicle approaching fast. Stop and verify.", result.message)
    }
}
