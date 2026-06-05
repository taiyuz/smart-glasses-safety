package com.smartglasses.safety.pipeline

import android.os.SystemClock

data class PerfSnapshot(
    val avgLatencyMs: Double,
    val fps: Double
)

class PerformanceMonitor(private val maxSamples: Int = 60) {
    private val latenciesMs = ArrayDeque<Long>()
    private val frameTimes = ArrayDeque<Long>()

    fun markFrame(latencyMs: Long) {
        val now = SystemClock.elapsedRealtime()
        latenciesMs.addLast(latencyMs)
        frameTimes.addLast(now)
        while (latenciesMs.size > maxSamples) latenciesMs.removeFirst()
        while (frameTimes.size > maxSamples) frameTimes.removeFirst()
    }

    fun snapshot(): PerfSnapshot {
        val avgLatency = if (latenciesMs.isEmpty()) 0.0 else latenciesMs.average().toDouble()
        val fps = when {
            frameTimes.size < 2 -> 0.0
            else -> {
                val durationMs = (frameTimes.last() - frameTimes.first()).coerceAtLeast(1L)
                (frameTimes.size - 1) * 1000.0 / durationMs
            }
        }
        return PerfSnapshot(avgLatencyMs = avgLatency, fps = fps)
    }
}
