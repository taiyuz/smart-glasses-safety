package com.smartglasses.safety.pipeline

import android.util.Log

class LocalEventLogger {
    fun logRisk(result: RiskResult, perfSnapshot: PerfSnapshot) {
        Log.i(
            TAG,
            "risk=${result.score},level=${result.level},avgLatencyMs=${perfSnapshot.avgLatencyMs},fps=${perfSnapshot.fps}"
        )
    }

    companion object {
        private const val TAG = "SmartGlassesSafety"
    }
}
