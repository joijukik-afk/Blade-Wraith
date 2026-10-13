package com.bladewraith.engine

import android.opengl.Matrix
import com.bladewraith.core.Rng

/**
 * ============================================================
 *  Blade Wraith — 2D Orthographic Camera
 *
 *  Manages two independent projection matrices:
 *
 *    1. **World MVP** ([mvp])
 *       Used for rendering game entities, background, particles.
 *       Includes screen-shake offset, so every world object
 *       shakes together during heavy impacts.
 *
 *    2. **UI MVP** ([uiMvp])
 *       Used for rendering HUD, touch controls, menus.
 *       Does NOT include screen-shake — the HUD must stay
 *       rock-steady even when the world is shaking.
 *
 *  Coordinate System:
 *    • Origin (0, 0) is the BOTTOM-LEFT corner of the screen.
 *    • X increases to the RIGHT.
 *    • Y increases UPWARD.
 *    • Units are pixels at the current screen resolution.
 *
 *  This matches OpenGL's native convention (Y-up), which
 *  avoids the flip that would otherwise be needed on every
 *  sprite draw call. Touch coordinates from Android, which
 *  use Y-down, must be converted by the caller:
 *
 *      worldY = Camera2D.height - touchY
 *
 *  Screen Shake:
 *    The shake system uses a decaying amplitude. When [shake]
 *    is called, the camera remembers the peak amplitude and
 *    the total duration. Each frame, [update] computes the
 *    current amplitude as `peak * (remaining / duration)`,
 *    then offsets the camera by a random value in
 *    `[-amplitude, +amplitude]`.
 *
 *    Multiple overlapping [shake] calls do NOT stack. The
 *    stronger of the two wins; a weaker call during an
 *    ongoing strong shake is ignored. This prevents the
 *    screen from tearing itself apart when the player lands
 *    three hits in the same frame.
 *
 *  Threading:
 *    All methods are called from the GL thread. No locking
 *    is needed.
 *
 *  References:
 *    • Android Developers — "android.opengl.Matrix"
 *      https://developer.android.com/reference/android/opengl/Matrix
 *    • "Learn OpenGL" — "Coordinate Systems"
 *      https://learnopengl.com/Getting-started/Coordinate-Systems
 *    • GDC Vault — "Juicing Your Cameras With Math"
 *      (Squirrel Eiserloh) — screen-shake design
 * ============================================================
 */
object Camera2D {

    // ========================================================
    //  World Projection Matrix
    // ========================================================

    /**
     * The 4x4 model-view-projection matrix for world rendering.
     *
     * Order: mvp = projection × view
     *
     * The projection is a pure orthographic mapping of
     * `[0, width] × [0, height]` to NDC.
     *
     * The view applies the screen-shake translation.
     *
     * Rebuilt every frame in [update] (only when shake is
     * active) and on [setSize].
     */
    @JvmField
    val mvp: FloatArray = FloatArray(16)

    /**
     * The 4x4 model-view-projection matrix for UI rendering.
     *
     * Identical to [mvp] but WITHOUT the shake translation.
     * Rebuilt only on [setSize] — never during gameplay.
     */
    @JvmField
    val uiMvp: FloatArray = FloatArray(16)

    // ========================================================
    //  Internal Reusable Buffers
    // ========================================================

    /**
     * Reusable projection matrix. Kept as a field so we do not
     * allocate a new FloatArray every frame.
     */
    private val projMatrix = FloatArray(16)

    /**
     * Reusable view matrix.
     */
    private val viewMatrix = FloatArray(16)

    /**
     * Reusable scratch matrix for multiplication.
     * (Matrix.multiplyMM writes into a separate output array,
     * so we cannot pass [mvp] as both input and output.)
     */
    private val tempMatrix = FloatArray(16)

    // ========================================================
    //  Screen Dimensions
    // ========================================================

    /**
     * Current viewport width in pixels.
     */
    @JvmField
    var width: Float = 1280f
        private set

    /**
     * Current viewport height in pixels.
     */
    @JvmField
    var height: Float = 720f
        private set

    /**
     * Width divided by height. Cached because it is used in
     * aspect-ratio-dependent logic (e.g., letterboxing).
     */
    @JvmField
    var aspect: Float = 16f / 9f
        private set

    /**
     * Half the width. Cached for convenience; many systems
     * want "center of screen" and this saves a division.
     */
    @JvmField
    var halfWidth: Float = 640f
        private set

    /**
     * Half the height.
     */
    @JvmField
    var halfHeight: Float = 360f
        private set

    // ========================================================
    //  Screen Shake State
    // ========================================================

    /**
     * The current peak amplitude of the shake, in pixels.
     * When [shakeTime] reaches 0, this is reset to 0.
     */
    private var shakeAmplitude: Float = 0f

    /**
     * Remaining time for the current shake, in seconds.
     */
    private var shakeTime: Float = 0f

    /**
     * Total duration of the current shake, in seconds.
     * Used to compute the decay curve.
     */
    private var shakeDuration: Float = 0f

    /**
     * The X offset applied to the world this frame.
     * Kept public so debug overlays can display it.
     */
    @JvmField
    var shakeX: Float = 0f
        private set

    /**
     * The Y offset applied to the world this frame.
     */
    @JvmField
    var shakeY: Float = 0f
        private set

    /**
     * Non-linear decay exponent. A value of 1.0 gives linear
     * decay; 2.0 gives a slower-then-faster falloff that feels
     * more "impactful" for heavy hits.
     *
     * Configurable via [setShakeDecay].
     */
    private var shakeDecayExponent: Float = 1.6f

    /**
     * When true, the shake offset is only applied along the
     * X axis (good for horizontal slashes) or only Y (good
     * for vertical slams). Set via [setShakeAxis].
     */
    private var shakeAxisX: Boolean = true
    private var shakeAxisY: Boolean = true

    /**
     * Reusable array for vector sampling, avoids allocations
     * in the per-frame shake calculation.
     */
    private val shakeVector: FloatArray = FloatArray(2)

    // ========================================================
    //  Initialization
    // ========================================================

    /**
     * Sets the viewport dimensions. Must be called from
     * [GLSurfaceView.Renderer.onSurfaceChanged] every time the
     * surface changes size (initialization, rotation, or
     * split-screen resize).
     *
     * Rebuilds both [mvp] and [uiMvp].
     *
     * @param w Viewport width in pixels.
     * @param h Viewport height in pixels.
     */
    fun setSize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return

        width = w.toFloat()
        height = h.toFloat()
        aspect = width / height
        halfWidth = width * 0.5f
        halfHeight = height * 0.5f

        // Build the orthographic projection.
        // left=0, right=width, bottom=0, top=height, near=-1, far=1
        Matrix.orthoM(
            projMatrix, 0,
            0f, width,
            0f, height,
            -1f, 1f
        )

        // Rebuild the UI matrix (no shake).
        System.arraycopy(projMatrix, 0, uiMvp, 0, 16)

        // Rebuild the world matrix.
        rebuildWorldMatrix()
    }

    // ========================================================
    //  Per-Frame Update
    // ========================================================

    /**
     * Advances the shake timer and rebuilds the world matrix
     * with the new shake offset.
     *
     * Must be called once per frame from the renderer, after
     * [com.bladewraith.core.Time.tick] and before
     * [com.bladewraith.game.Game.render].
     *
     * @param dt Delta time in seconds.
     */
    fun update(dt: Float) {
        if (shakeTime <= 0f) {
            // No active shake — nothing to do.
            // The matrix is already in its "clean" state.
            return
        }

        // Decrement the shake timer.
        shakeTime -= dt

        if (shakeTime <= 0f) {
            // Shake finished. Reset everything.
            shakeTime = 0f
            shakeAmplitude = 0f
            shakeDuration = 0f
            shakeX = 0f
            shakeY = 0f
            rebuildWorldMatrix()
            return
        }

        // Compute the current amplitude along the decay curve.
        // progress goes from 1.0 (just started) to 0.0 (ending).
        val progress = shakeTime / shakeDuration
        val decayed = powerApprox(progress, shakeDecayExponent)
        val currentAmp = shakeAmplitude * decayed

        // Sample a random offset. Using smooth noise would be
        // better visually, but for a short, sharp shake, a
        // fresh random value per frame reads as "energy" and
        // is the standard approach in action games.
        shakeVector[0] = Rng.spread(currentAmp)
        shakeVector[1] = Rng.spread(currentAmp)

        shakeX = if (shakeAxisX) shakeVector[0] else 0f
        shakeY = if (shakeAxisY) shakeVector[1] else 0f

        rebuildWorldMatrix()
    }

    // ========================================================
    //  Shake Trigger API
    // ========================================================

    /**
     * Triggers a screen shake.
     *
     * If a stronger shake is already active, this call is
     * ignored (the stronger shake wins). If this shake is
     * stronger, it replaces the current one.
     *
     * @param amplitude Peak displacement in pixels.
     *                  Typical values:
     *                    4–8   light hits
     *                    10–18 medium hits
     *                    20–40 heavy hits, boss slams
     *                    50+   game-ending events
     * @param duration  Length of the shake, in seconds.
     *                  Typical values:
     *                    0.10–0.18  light hits
     *                    0.20–0.35  medium hits
     *                    0.35–0.60  heavy hits
     */
    fun shake(amplitude: Float, duration: Float) {
        if (amplitude <= 0f || duration <= 0f) return

        // Only accept the shake if it is stronger than what is
        // already playing.
        if (amplitude <= shakeAmplitude && shakeTime > 0f) {
            return
        }

        shakeAmplitude = amplitude
        shakeDuration = duration
        shakeTime = duration

        // Reset the axis mask to "both axes" for the new shake.
        // Callers who want a directional shake will re-apply
        // [setShakeAxis] immediately after this call.
        shakeAxisX = true
        shakeAxisY = true
    }

    /**
     * Directional shake: only shake along X.
     */
    fun shakeX(amplitude: Float, duration: Float) {
        shake(amplitude, duration)
        shakeAxisX = true
        shakeAxisY = false
    }

    /**
     * Directional shake: only shake along Y.
     */
    fun shakeY(amplitude: Float, duration: Float) {
        shake(amplitude, duration)
        shakeAxisX = false
        shakeAxisY = true
    }

    /**
     * Stops any active shake immediately.
     */
    fun stopShake() {
        shakeAmplitude = 0f
        shakeTime = 0f
        shakeDuration = 0f
        shakeX = 0f
        shakeY = 0f
        rebuildWorldMatrix()
    }

    /**
     * Returns true if a shake is currently active.
     */
    fun isShaking(): Boolean = shakeTime > 0f

    /**
     * Returns the remaining shake duration, in seconds.
     */
    fun shakeRemaining(): Float = shakeTime

    /**
     * Adjusts the shake decay curve.
     *
     * @param exponent Decay exponent.
     *                 1.0 = linear (constant amplitude, then cut)
     *                 2.0 = accelerating falloff (impactful)
     *                 3.0 = very sharp, "punchy" falloff
     *                 0.5 = slow falloff (long rumble)
     *
     * Values are clamped to [0.25, 4.0] to keep the curve sane.
     */
    fun setShakeDecay(exponent: Float) {
        shakeDecayExponent = exponent.coerceIn(0.25f, 4.0f)
    }

    // ========================================================
    //  Matrix Construction
    // ========================================================

    /**
     * Rebuilds [mvp] as `projection × view`, where the view
     * matrix is a pure translation by the current shake offset.
     */
    private fun rebuildWorldMatrix() {
        // Identity view, then translate by shake offset.
        Matrix.setIdentityM(viewMatrix, 0)
        if (shakeX != 0f || shakeY != 0f) {
            Matrix.translateM(viewMatrix, 0, shakeX, shakeY, 0f)
        }

        // mvp = proj × view
        // Matrix.multiplyMM requires distinct input and output
        // arrays, so we write into tempMatrix then copy.
        Matrix.multiplyMM(tempMatrix, 0, projMatrix, 0, viewMatrix, 0)
        System.arraycopy(tempMatrix, 0, mvp, 0, 16)
    }

    // ========================================================
    //  Coordinate Conversion Helpers
    // ========================================================

    /**
     * Converts a Y coordinate from Android's touch space
     * (origin top-left, Y-down) to the game's world space
     * (origin bottom-left, Y-up).
     *
     * @param touchY The Y value from a [android.view.MotionEvent].
     * @return The equivalent world-space Y.
     */
    fun touchYToWorld(touchY: Float): Float {
        return height - touchY
    }

    /**
     * Converts a world-space Y coordinate back to touch space.
     */
    fun worldYToTouch(worldY: Float): Float {
        return height - worldY
    }

    /**
     * Returns true if the given world-space rectangle is at
     * least partially inside the visible viewport.
     *
     * Useful as a cheap frustum cull for entities and
     * particles.
     */
    fun isVisible(x: Float, y: Float, w: Float, h: Float): Boolean {
        return x + w > 0f && x < width && y + h > 0f && y < height
    }

    // ========================================================
    //  Math Helpers
    // ========================================================

    /**
     * Fast approximation of `x^p` for `x` in `[0, 1]` and
     * `p` in `[0.25, 4]`.
     *
     * Uses a mixed approach: exact for small integer
     * exponents (the common case), and a polynomial
     * approximation otherwise. This avoids the cost of
     * `Math.pow` in the per-frame hot path (though the cost
     * would be negligible here anyway — the function is
     * included for the sake of clean, allocation-free code).
     */
    private fun powerApprox(x: Float, p: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f

        // Snap to common integer exponents.
        val roundedP = kotlin.math.round(p)
        if (kotlin.math.abs(p - roundedP) < 0.001f) {
            when (roundedP.toInt()) {
                1 -> return x
                2 -> return x * x
                3 -> return x * x * x
                4 -> {
                    val x2 = x * x
                    return x2 * x2
                }
            }
        }

        // Fallback for non-integer exponents.
        return Math.pow(x.toDouble(), p.toDouble()).toFloat()
    }

    // ========================================================
    //  Diagnostics
    // ========================================================

    /**
     * Returns a human-readable snapshot of the camera state.
     * Suitable for a debug overlay.
     */
    fun debugString(): String {
        return "Camera[" +
                "${width.toInt()}x${height.toInt()}, " +
                "aspect=${"%.3f".format(aspect)}, " +
                "shake=${"%.2f".format(shakeAmplitude)}/" +
                "${"%.2f".format(shakeTime)}s, " +
                "offset=(${"%.1f".format(shakeX)}, ${"%.1f".format(shakeY)})" +
                "]"
    }
}
