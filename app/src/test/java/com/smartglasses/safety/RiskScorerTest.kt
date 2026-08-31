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
    }
}
