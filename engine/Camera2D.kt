package com.bladewraith.engine

import android.opengl.Matrix

object Camera2D {
    val mvp = FloatArray(16)
    val uiMvp = FloatArray(16)

    var width = 1280f
        private set
    var height = 720f
        private set

    private var shakeAmp = 0f
    private var shakeTime = 0f
    private var shakeDur = 0f
    private var shakeX = 0f
    private var shakeY = 0f

    fun setSize(w: Int, h: Int) {
        width = w.toFloat()
        height = h.toFloat()
        rebuildUI()
        rebuildWorld()
    }

    fun shake(amp: Float, dur: Float) {
        if (amp > shakeAmp || shakeTime <= 0f) {
            shakeAmp = amp
            shakeDur = dur
            shakeTime = dur
        }
    }

    fun update(dt: Float) {
        if (shakeTime > 0f) {
            shakeTime -= dt
            val t = if (shakeDur > 0f) shakeTime / shakeDur else 0f
            val a = shakeAmp * t
            shakeX = com.bladewraith.core.Rng.range(-a, a)
            shakeY = com.bladewraith.core.Rng.range(-a, a)
            if (shakeTime <= 0f) { shakeX = 0f; shakeY = 0f; shakeAmp = 0f }
        } else {
            shakeX = 0f; shakeY = 0f
        }
        rebuildWorld()
    }

    private fun rebuildUI() {
        val proj = FloatArray(16)
        Matrix.orthoM(proj, 0, 0f, width, 0f, height, -1f, 1f)
        System.arraycopy(proj, 0, uiMvp, 0, 16)
    }

    private fun rebuildWorld() {
        val proj = FloatArray(16)
        val view = FloatArray(16)
        Matrix.orthoM(proj, 0, 0f, width, 0f, height, -1f, 1f)
        Matrix.setIdentityM(view, 0)
        Matrix.translateM(view, 0, shakeX, shakeY, 0f)
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0)
    }
}
