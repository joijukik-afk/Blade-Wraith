package com.bladewraith.core

object Time {
    var dt = 0f
        private set
    var elapsed = 0f
        private set

    private var lastNano = 0L
    private var hitStopRemaining = 0f

    fun tick() {
        val now = System.nanoTime()
        if (lastNano == 0L) { lastNano = now; return }
        var d = (now - lastNano) / 1_000_000_000f
        lastNano = now
        if (d > 0.05f) d = 0.05f

        if (hitStopRemaining > 0f) {
            hitStopRemaining -= d
            dt = 0f
            return
        }

        dt = d
        elapsed += d
    }

    fun hitStop(duration: Float) {
        if (duration > hitStopRemaining) hitStopRemaining = duration
    }

    fun reset() { lastNano = 0L; elapsed = 0f; dt = 0f; hitStopRemaining = 0f }
}
