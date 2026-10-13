package com.bladewraith.core

import android.util.Log

/**
 * ============================================================
 *  Blade Wraith — Central Time System
 *
 *  Manages delta time (dt), total elapsed time, pause state,
 *  time scaling, and hit-stop — the brief freeze-frame effect
 *  used in action games to emphasize impactful hits.
 *
 *  Design principles:
 *    1. **Single source of truth.** All game systems read time
 *       from this object. No system uses [System.nanoTime] or
 *       [System.currentTimeMillis] directly.
 *
 *    2. **Frame-rate independence.** The game loop calls
 *       [tick] once per rendered frame, and every system
 *       multiplies its velocities by [dt].
 *
 *    3. **Spike protection.** If a frame takes too long (GC
 *       pause, device stall), dt is clamped to [MAX_DT] to
 *       prevent tunneling (objects passing through walls) and
 *       numeric instability.
 *
 *    4. **Hit-stop.** When a heavy attack lands, [hitStop]
 *       freezes the game for a few milliseconds. Time keeps
 *       advancing internally so that the pause has a real
 *       duration, but [dt] is reported as zero to all game
 *       systems during the freeze.
 *
 *    5. **Time scaling.** For slow-motion effects (e.g., a
 *       boss's phase transition), [timeScale] can be reduced
 *       from 1.0 to 0.3 temporarily without affecting the
 *       real-world clock.
 *
 *  Threading:
 *    All methods are called from the GL thread. No locking is
 *    needed. [tick] is called exactly once per frame, at the
 *    very start of the frame, before any other system reads
 *    [dt].
 *
 *  References:
 *    • Gaffer On Games — "Fix Your Timestep!"
 *      https://gafferongames.com/post/fix_your_timestep/
 *    • "Game Programming Patterns" — Game Loop chapter
 *      https://gameprogrammingpatterns.com/game-loop.html
 *    • Android Developers — "System.nanoTime"
 *      https://developer.android.com/reference/java/lang/System#nanoTime()
 * ============================================================
 */
object Time {

    // ========================================================
    //  Companion — configuration constants
    // ========================================================
    private const val TAG = "Time"

    /**
     * Maximum allowed delta time per frame, in seconds.
     *
     * A value of 0.05s corresponds to 20 FPS. If a frame takes
     * longer than this (e.g., a 200 ms GC pause, or the app
     * coming back from the background), we clamp dt to prevent
     * objects from moving too far in a single step.
     *
     * Without this clamp, a projectile moving at 2000 px/s
     * would jump 400 px in a 200 ms frame — potentially
     * passing straight through a wall, and making the game
     * appear to teleport on resume.
     */
    private const val MAX_DT = 0.05f

    /**
     * Minimum allowed delta time per frame, in seconds.
     *
     * On some platforms (particularly emulators and devices
     * with variable refresh), back-to-back frame callbacks can
     * fire with near-zero dt, which can cause division-by-zero
     * or state flip-flopping. Clamping to a minimum of 1 ms
     * avoids these edge cases.
     */
    private const val MIN_DT = 0.001f

    /**
     * Smoothing factor for dt.
     *
     * To prevent micro-jitter from variable frame times, we
     * apply a light exponential moving average to dt. A value
     * of 0.9 means 90% of the previous frame's dt is retained
     * and 10% of the new value is blended in. This makes
     * animations and physics feel smoother at the cost of a
     * very slight input latency (imperceptible at 60 Hz).
     *
     * Set to 1.0 to disable smoothing (raw dt).
     */
    private const val DT_SMOOTHING = 0.9f

    // ========================================================
    //  Public State
    // ========================================================

    /**
     * Delta time for the current frame, in seconds.
     *
     * This is the value that all game systems should multiply
     * their per-second velocities by. It is 0.0 during hit-stop,
     * and 0.0 while the game is paused.
     */
    @JvmField
    var dt: Float = 0f
        private set

    /**
     * Raw (unsmoothed) delta time for the current frame.
     *
     * Useful for UI animations that should not be affected by
     * game-time scaling.
     */
    @JvmField
    var rawDt: Float = 0f
        private set

    /**
     * Total elapsed game time, in seconds.
     *
     * Does NOT advance during hit-stop or while paused. This
     * is the clock that in-game timers (attack cooldowns, boss
     * pattern timing) should use.
     */
    @JvmField
    var gameTime: Float = 0f
        private set

    /**
     * Total elapsed real time since the first [tick], in
     * seconds. Unlike [gameTime], this continues to advance
     * while paused or in hit-stop. Use for UI, menus, and
     * diagnostic overlays.
     */
    @JvmField
    var realTime: Float = 0f
        private set

    /**
     * Frame counter. Increments once per [tick] call,
     * regardless of pause or hit-stop.
     */
    @JvmField
    var frame: Long = 0L
        private set

    /**
     * Global time scale. Multiplies dt.
     *
     * 1.0 = normal speed
     * 0.5 = half speed (slow-motion)
     * 0.0 = frozen (equivalent to a pause, but does not affect
     *       the pause counter)
     *
     * Values above 1.0 accelerate time, which is rarely useful
     * but supported.
     */
    @JvmField
    var timeScale: Float = 1f

    /**
     * Current FPS (frames per second), computed as a smoothed
     * inverse of dt. Updated once per frame.
     */
    @JvmField
    var fps: Float = 60f
        private set

    /**
     * True while the game is paused. When true, [dt] is 0.0
     * and [gameTime] does not advance. [realTime], [fps], and
     * [frame] continue to advance so that menus and diagnostic
     * overlays animate normally.
     */
    @JvmField
    var isPaused: Boolean = false
        private set

    // ========================================================
    //  Internal State
    // ========================================================

    /**
     * Timestamp (nanoTime) of the previous [tick].
     * 0L means "not yet initialized".
     */
    private var lastNano: Long = 0L

    /**
     * Timestamp (nanoTime) of the very first [tick], used to
     * compute [realTime].
     */
    private var startNano: Long = 0L

    /**
     * Remaining hit-stop duration, in seconds.
     */
    private var hitStopRemaining: Float = 0f

    /**
     * Total hit-stop duration of the current effect, for
     * interpolation/diagnostics.
     */
    private var hitStopTotal: Float = 0f

    /**
     * Accumulator for FPS smoothing.
     */
    private var fpsAccum: Float = 0f
    private var fpsFrames: Int = 0

    /**
     * Temporary time scale override (e.g., for phase transitions).
     * Restored to [baseTimeScale] when the override expires.
     */
    private var timeScaleOverride: Float = 1f
    private var timeScaleOverrideRemaining: Float = 0f

    /**
     * The user-configured base time scale (normally 1.0).
     */
    private var baseTimeScale: Float = 1f

    // ========================================================
    //  Core Tick
    // ========================================================

    /**
     * Advances the clock by one frame.
     *
     * MUST be called exactly once per frame, at the very
     * beginning, before any system reads [dt].
     *
     * Behavior:
     *    1. Compute the elapsed time since the previous call.
     *    2. Clamp it to [MIN_DT]..[MAX_DT] to protect against
     *       spikes and zero-dt frames.
     *    3. Apply dt smoothing.
     *    4. Handle hit-stop: if active, decrement its timer
     *       and set dt to 0.
     *    5. Apply pause: if paused, dt is 0 (but realTime
     *       still advances).
     *    6. Apply [timeScale] and any temporary override.
     *    7. Advance [gameTime], [realTime], [frame], and [fps].
     */
    fun tick() {
        val now = System.nanoTime()

        // ----------------------------------------------------
        //  First-frame initialization
        // ----------------------------------------------------
        if (lastNano == 0L) {
            lastNano = now
            startNano = now
            // Report a nominal dt for the first frame so that
            // systems that rely on dt do not divide by zero.
            rawDt = 1f / 60f
            dt = rawDt
            return
        }

        // ----------------------------------------------------
        //  Compute raw delta time (seconds since last frame)
        // ----------------------------------------------------
        val elapsedNanos = now - lastNano
        lastNano = now

        var newDt = elapsedNanos / 1_000_000_000f

        // ----------------------------------------------------
        //  Clamp dt to protect against spikes
        // ----------------------------------------------------
        if (newDt < MIN_DT) newDt = MIN_DT
        if (newDt > MAX_DT) newDt = MAX_DT

        // ----------------------------------------------------
        //  Store raw dt (unsmoothed)
        // ----------------------------------------------------
        rawDt = newDt

        // ----------------------------------------------------
        //  Smooth dt (EMA)
        // ----------------------------------------------------
        val smoothedDt = if (dt == 0f) {
            newDt
        } else {
            dt * DT_SMOOTHING + newDt * (1f - DT_SMOOTHING)
        }

        // ----------------------------------------------------
        //  Advance real time unconditionally
        // ----------------------------------------------------
        realTime += newDt
        frame++

        // ----------------------------------------------------
        //  FPS measurement (update once per second)
        // ----------------------------------------------------
        fpsAccum += newDt
        fpsFrames++
        if (fpsAccum >= 0.5f) {
            fps = fpsFrames / fpsAccum
            fpsAccum = 0f
            fpsFrames = 0
        }

        // ----------------------------------------------------
        //  Early-out if paused: dt is 0, game time does not
        //  advance, but real time already has.
        // ----------------------------------------------------
        if (isPaused) {
            dt = 0f
            return
        }

        // ----------------------------------------------------
        //  Handle hit-stop
        // ----------------------------------------------------
        if (hitStopRemaining > 0f) {
            hitStopRemaining -= newDt
            if (hitStopRemaining <= 0f) {
                hitStopRemaining = 0f
                hitStopTotal = 0f
            }
            // During hit-stop, game time is frozen.
            dt = 0f
            return
        }

        // ----------------------------------------------------
        //  Handle time-scale override
        // ----------------------------------------------------
        var effectiveScale = baseTimeScale

        if (timeScaleOverrideRemaining > 0f) {
            timeScaleOverrideRemaining -= newDt
            if (timeScaleOverrideRemaining <= 0f) {
                timeScaleOverrideRemaining = 0f
                timeScaleOverride = 1f
            } else {
                effectiveScale = timeScaleOverride
            }
        }

        // ----------------------------------------------------
        //  Apply global time scale
        // ----------------------------------------------------
        effectiveScale *= timeScale

        // ----------------------------------------------------
        //  Compute final dt and advance game time
        // ----------------------------------------------------
        dt = smoothedDt * effectiveScale
        gameTime += dt
    }

    // ========================================================
    //  Hit-Stop API
    // ========================================================

    /**
     * Triggers a hit-stop freeze-frame.
     *
     * The game logic is frozen for the given duration while
     * the real-world clock continues to advance. This creates
     * the classic action-game "impact" feel.
     *
     * If a hit-stop is already active, the longer of the two
     * durations is used. This prevents rapid attacks from
     * accumulating into a long freeze.
     *
     * @param duration Length of the freeze, in real seconds.
     *                 Typical values: 0.03–0.10 for light hits,
     *                 0.10–0.20 for heavy hits.
     */
    fun hitStop(duration: Float) {
        if (duration <= 0f) return
        if (duration > hitStopRemaining) {
            hitStopRemaining = duration
            hitStopTotal = duration
        }
    }

    /**
     * Returns true if hit-stop is currently active.
     */
    fun isInHitStop(): Boolean = hitStopRemaining > 0f

    /**
     * Returns the remaining hit-stop duration, in seconds.
     */
    fun hitStopRemaining(): Float = hitStopRemaining

    /**
     * Returns a normalized progress value of the current
     * hit-stop, from 1.0 (just started) to 0.0 (about to end).
     * Returns 0 if no hit-stop is active.
     */
    fun hitStopProgress(): Float {
        if (hitStopTotal <= 0f) return 0f
        return (hitStopRemaining / hitStopTotal).coerceIn(0f, 1f)
    }

    /**
     * Cancels any active hit-stop.
     */
    fun cancelHitStop() {
        hitStopRemaining = 0f
        hitStopTotal = 0f
    }

    // ========================================================
    //  Pause API
    // ========================================================

    /**
     * Pauses the game. While paused, [dt] is 0.0 and
     * [gameTime] does not advance. [realTime], [fps], and
     * [frame] continue to advance.
     *
     * Calling [pause] on an already-paused clock is a no-op.
     */
    fun pause() {
        if (!isPaused) {
            isPaused = true
            Log.d(TAG, "Game paused at realTime=${"%.2f".format(realTime)}s")
        }
    }

    /**
     * Resumes the game from a paused state.
     *
     * Calling [resume] on a running clock is a no-op.
     */
    fun resume() {
        if (isPaused) {
            isPaused = false
            Log.d(TAG, "Game resumed at realTime=${"%.2f".format(realTime)}s")
        }
    }

    /**
     * Toggles the paused state.
     */
    fun togglePause() {
        if (isPaused) resume() else pause()
    }

    // ========================================================
    //  Time-Scale API
    // ========================================================

    /**
     * Sets the persistent (base) time scale.
     *
     * This is the scale that applies when no override is
     * active. Typical values:
     *    1.0  normal
     *    0.5  half speed
     *    0.25 quarter speed
     *
     * Values are clamped to [0.0, 4.0] to prevent runaway
     * behavior from user-provided values.
     *
     * @param scale The new base time scale.
     */
    fun setTimeScale(scale: Float) {
        baseTimeScale = scale.coerceIn(0f, 4f)
    }

    /**
     * Temporarily overrides the time scale for the given
     * duration, then returns to the base scale.
     *
     * Useful for dramatic slow-motion moments (e.g., a boss
     * reaching its final phase).
     *
     * @param scale    The time scale to apply.
     * @param duration Duration, in real seconds.
     */
    fun timeScaleOverride(scale: Float, duration: Float) {
        if (duration <= 0f) return
        timeScaleOverride = scale.coerceIn(0f, 4f)
        timeScaleOverrideRemaining = duration
    }

    /**
     * Cancels any active time-scale override, immediately
     * returning to the base scale.
     */
    fun cancelTimeScaleOverride() {
        timeScaleOverrideRemaining = 0f
        timeScaleOverride = 1f
    }

    // ========================================================
    //  Reset API
    // ========================================================

    /**
     * Resets the clock to its initial state.
     *
     * Should be called when restarting the game (e.g., after
     * a victory or defeat). After calling [reset], the next
     * [tick] will reinitialize the internal timestamps.
     */
    fun reset() {
        lastNano = 0L
        startNano = 0L
        dt = 0f
        rawDt = 0f
        gameTime = 0f
        realTime = 0f
        frame = 0L
        timeScale = 1f
        baseTimeScale = 1f
        timeScaleOverride = 1f
        timeScaleOverrideRemaining = 0f
        hitStopRemaining = 0f
        hitStopTotal = 0f
        fps = 60f
        fpsAccum = 0f
        fpsFrames = 0
        isPaused = false
        Log.d(TAG, "Time reset")
    }

    // ========================================================
    //  Diagnostics
    // ========================================================

    /**
     * Returns a human-readable snapshot of the current time
     * state, useful for debug overlays.
     */
    fun debugString(): String {
        return "Time[" +
                "dt=${"%.4f".format(dt)}, " +
                "raw=${"%.4f".format(rawDt)}, " +
                "game=${"%.2f".format(gameTime)}, " +
                "real=${"%.2f".format(realTime)}, " +
                "fps=${"%.1f".format(fps)}, " +
                "frame=$frame, " +
                "paused=$isPaused, " +
                "hitStop=${"%.3f".format(hitStopRemaining)}" +
                "]"
    }
}
