package com.smartglasses.safety.pipeline

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import java.util.Locale

class AlertManager(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context, this)
    private var ready = false
    private var lastLevel: AlertLevel = AlertLevel.IDLE
    private var lastSpokenAt = 0L

    private val cooldownMsByLevel = mapOf(
        AlertLevel.ADVISORY to 4500L,
        AlertLevel.WARNING to 2500L,
        AlertLevel.CRITICAL to 1200L,
        AlertLevel.IDLE to 0L
    )

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.US
            tts.setSpeechRate(0.95f)
        }
    }

    fun shouldAnnounce(next: AlertLevel): Boolean {
        if (next == AlertLevel.IDLE) return false
        val now = SystemClock.elapsedRealtime()
        val cooldown = cooldownMsByLevel[next] ?: 3000L
        val levelChanged = next != lastLevel
        val cooldownPassed = (now - lastSpokenAt) >= cooldown
        return levelChanged || cooldownPassed
    }

    fun announce(message: String, level: AlertLevel) {
        if (!ready) return
        if (!shouldAnnounce(level)) return
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "safety_alert")
        lastSpokenAt = SystemClock.elapsedRealtime()
        lastLevel = level
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
