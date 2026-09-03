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

    /** BALANCED: 0.6*0.5 + 0.8*0.3 + 0.8*0.2 = 0.70 (WARNING enter). */
    private fun warningEnterVehicle() =
        vehicle(areaGrowth = 0.6f, centerDriftToMiddle = 0.2f, confidencePersistence = 0.8f)

    /** BALANCED: 0.4*0.5 + 0.8*0.3 + 0.8*0.2 = 0.60 (between warning exit 0.57 and enter 0.65). */
    private fun warningExitBandVehicle() =
        vehicle(areaGrowth = 0.4f, centerDriftToMiddle = 0.2f, confidencePersistence = 0.8f)

    /** BALANCED: 0.3*0.5 + 0.8*0.3 + 0.8*0.2 = 0.55 (ADVISORY, below warning exit). */
    private fun advisoryBelowWarningExitVehicle() =
        vehicle(areaGrowth = 0.3f, centerDriftToMiddle = 0.2f, confidencePersistence = 0.8f)

    /** BALANCED: 0.2*0.5 + 0.5*0.3 + 0.8*0.2 = 0.41 (between advisory exit 0.37 and enter 0.45). */
    private fun advisoryExitBandVehicle() =
        vehicle(areaGrowth = 0.2f, centerDriftToMiddle = 0.5f, confidencePersistence = 0.8f)

    /** BALANCED: 0.7*0.5 + 0.9*0.3 + 0.85*0.2 = 0.79 (between critical exit 0.74 and enter 0.82). */
    private fun criticalExitBandVehicle() =
        vehicle(areaGrowth = 0.7f, centerDriftToMiddle = 0.1f, confidencePersistence = 0.85f)

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

    @Test
    fun holdsWarningWhenScoreDipsIntoExitBand() {
        val scorer = RiskScorer(RiskProfile.BALANCED)
        val entered = scorer.score(listOf(warningEnterVehicle()))
        assertEquals(AlertLevel.WARNING, entered.level)
        assertEquals(0.70f, entered.score, 0.0001f)

        val held = scorer.score(listOf(warningExitBandVehicle()))
        assertEquals(0.60f, held.score, 0.0001f)
        assertEquals(AlertLevel.WARNING, held.level)
    }

    @Test
    fun freshScorerMapsExitBandScoreToAdvisory() {
        val result = RiskScorer(RiskProfile.BALANCED).score(listOf(warningExitBandVehicle()))
        assertEquals(0.60f, result.score, 0.0001f)
        assertEquals(AlertLevel.ADVISORY, result.level)
    }

    @Test
    fun dropsWarningOnceScoreFallsBelowExit() {
        val scorer = RiskScorer(RiskProfile.BALANCED)
        scorer.score(listOf(warningEnterVehicle()))
        val dropped = scorer.score(listOf(advisoryBelowWarningExitVehicle()))
        assertEquals(0.55f, dropped.score, 0.0001f)
        assertEquals(AlertLevel.ADVISORY, dropped.level)
    }

    @Test
    fun holdsAdvisoryWhenScoreDipsIntoExitBand() {
        val scorer = RiskScorer(RiskProfile.BALANCED)
        // 0.55 enters ADVISORY on a fresh scorer
        val entered = scorer.score(listOf(advisoryBelowWarningExitVehicle()))
        assertEquals(AlertLevel.ADVISORY, entered.level)

        val held = scorer.score(listOf(advisoryExitBandVehicle()))
        assertEquals(0.41f, held.score, 0.0001f)
        assertEquals(AlertLevel.ADVISORY, held.level)
    }

    @Test
    fun holdsCriticalWhenScoreDipsIntoExitBand() {
        val scorer = RiskScorer(RiskProfile.BALANCED)
        scorer.score(listOf(vehicle(areaGrowth = 1.2f, centerDriftToMiddle = 0.05f)))
        val held = scorer.score(listOf(criticalExitBandVehicle()))
        assertEquals(0.79f, held.score, 0.0001f)
        assertEquals(AlertLevel.CRITICAL, held.level)
    }

    @Test
    fun upgradesImmediatelyFromWarningToCritical() {
        val scorer = RiskScorer(RiskProfile.BALANCED)
        scorer.score(listOf(warningEnterVehicle()))
        val upgraded = scorer.score(
            listOf(vehicle(areaGrowth = 1.2f, centerDriftToMiddle = 0.05f))
        )
        assertEquals(AlertLevel.CRITICAL, upgraded.level)
    }

    @Test
    fun resetClearsHeldWarning() {
        val scorer = RiskScorer(RiskProfile.BALANCED)
        scorer.score(listOf(warningEnterVehicle()))
        scorer.score(listOf(warningExitBandVehicle()))
        scorer.reset()
        val after = scorer.score(listOf(warningExitBandVehicle()))
        assertEquals(AlertLevel.ADVISORY, after.level)
    }

    @Test
    fun emptyListClearsHeldCritical() {
        val scorer = RiskScorer(RiskProfile.BALANCED)
        scorer.score(listOf(vehicle(areaGrowth = 1.2f, centerDriftToMiddle = 0.05f)))
        val cleared = scorer.score(emptyList())
        assertEquals(AlertLevel.IDLE, cleared.level)
        assertEquals(0f, cleared.score, 0.0001f)

        // Same exit-band score must not re-enter CRITICAL without crossing enter.
        val after = scorer.score(listOf(criticalExitBandVehicle()))
        assertEquals(0.79f, after.score, 0.0001f)
        assertEquals(AlertLevel.WARNING, after.level)
    }
}
