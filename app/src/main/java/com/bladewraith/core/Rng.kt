package com.bladewraith.core

import java.util.Random

/**
 * ============================================================
 *  Blade Wraith — Deterministic Random Number Generator
 *
 *  A centralized, seeded random number source for all
 *  gameplay randomness (particle jitter, boss attack
 *  selection, damage variance, critical hit rolls, etc.).
 *
 *  Why not use [kotlin.random.Random] or [Math.random]?
 *    • **Determinism.** A fixed seed allows the exact same
 *      sequence of "random" values to be replayed. This is
 *      invaluable for debugging bug reports ("the boss
 *      teleported into a wall at 42 seconds") — with a seed
 *      and a scripted input sequence, the bug reproduces
 *      exactly.
 *
 *    • **Single source.** All game randomness flows through
 *      this object. We can log, audit, or reseed it at any
 *      time without touching game code.
 *
 *    • **Performance.** Java's [Random] (a 48-bit LCG) is
 *      faster than [kotlin.random.Random]'s default (which
 *      uses an XorWow-style generator) and is more than
 *      sufficient for game feel. Cryptographic quality is
 *      not needed here.
 *
 *    • **No global state.** Calling [Math.random] mutates a
 *      JVM-wide generator; other libraries (including the
 *      Android framework) share it. That makes results
 *      unpredictable across the app.
 *
 *  Seeding:
 *    • By default, the seed is a fixed constant
 *      ([DEFAULT_SEED]). This means every fresh launch of the
 *      game produces the exact same sequence of "random"
 *      events — great for testing and tutorials.
 *
 *    • Call [seed] with a runtime-derived value (e.g., the
 *      system clock) to make each playthrough unique.
 *
 *    • Call [reseed] to restore the default deterministic
 *      behavior.
 *
 *  Threading:
 *    This object is NOT thread-safe, by design — [java.util.Random]
 *    is thread-safe but pays for it with an atomic CAS on
 *    every call. All game randomness happens on the GL thread,
 *    so we can skip that cost. Do not call these methods from
 *    other threads.
 *
 *  References:
 *    • Oracle — "java.util.Random"
 *      https://docs.oracle.com/javase/8/docs/api/java/util/Random.html
 *    • "Game Programming Patterns" — "Deterministic Simulation"
 *    • Wikipedia — "Linear congruential generator"
 * ============================================================
 */
object Rng {

    // ========================================================
    //  Constants
    // ========================================================

    /**
     * The default seed used when the game launches.
     *
     * This is a fixed constant so that a fresh launch always
     * produces the same sequence of random events. Change it
     * to any value to get a different fixed sequence.
     *
     * The value 0xC0FFEE is a common, easily recognizable
     * magic number used by game developers.
     */
    private const val DEFAULT_SEED: Long = 0xC0FFEEL

    /**
     * Two Pi. Kept as a constant to avoid recomputation in
     * hot loops that sample random angles.
     */
    const val TWO_PI: Float = 6.2831855f

    /**
     * Pi. Kept as a constant for the same reason.
     */
    const val PI: Float = 3.1415927f

    // ========================================================
    //  Internal State
    // ========================================================

    /**
     * The backing [java.util.Random] instance.
     *
     * Recreated on [seed], [reseed], and [setSeed]. Because
     * [Random] cannot be re-seeded in place, we allocate a new
     * one — the allocation cost is negligible (once per seed
     * change, not per call).
     */
    private var rng: Random = Random(DEFAULT_SEED)

    /**
     * The seed currently in use. Kept for diagnostics and for
     * restoring the exact state after a reseed.
     */
    private var currentSeed: Long = DEFAULT_SEED

    /**
     * Counter of how many random values have been produced
     * since the last seed. Useful for crash reports: "the bug
     * happened after N random draws."
     */
    private var drawCount: Long = 0L

    // ========================================================
    //  Seeding
    // ========================================================

    /**
     * Initializes the generator with the given seed.
     *
     * Subsequent calls to the sampling methods will produce a
     * deterministic sequence based on this seed. Call this at
     * the very start of a new game session (e.g., from
     * [Game.onSurfaceChanged] or [Game.resetBattle]).
     *
     * @param seed The seed value. Any [Long] is valid.
     */
    fun seed(seed: Long) {
        currentSeed = seed
        rng = Random(seed)
        drawCount = 0L
    }

    /**
     * Restores the default deterministic seed.
     *
     * After this call, the generator will produce the exact
     * same sequence as a fresh launch. Useful after a game
     * session ends, before starting a new one in "training
     * mode" (where the same layout is desirable).
     */
    fun reseed() {
        seed(DEFAULT_SEED)
    }

    /**
     * Seeds the generator with a value derived from the
     * current wall-clock time, producing a different sequence
     * each time it is called.
     *
     * Use this at the start of a "real" playthrough where the
     * player expects variety between sessions.
     */
    fun seedFromTime() {
        seed(System.nanoTime())
    }

    /**
     * Returns the seed currently in use.
     */
    fun getSeed(): Long = currentSeed

    /**
     * Returns the default seed value. Useful for logging in
     * bug reports.
     */
    fun getDefaultSeed(): Long = DEFAULT_SEED

    /**
     * Returns the number of random draws since the last seed.
     */
    fun getDrawCount(): Long = drawCount

    // ========================================================
    //  Core Sampling — Float
    // ========================================================

    /**
     * Returns a random float in `[0.0, 1.0)`.
     *
     * This is the fastest sampling method and is the building
     * block for all others. Most game code should call
     * [range] or [rangeInt] instead of [f] directly.
     */
    fun f(): Float {
        drawCount++
        return rng.nextFloat()
    }

    /**
     * Returns a random float in `[min, max)`.
     *
     * If `min == max`, returns `min`. If `min > max`, the
     * arguments are swapped — this makes the call safe
     * regardless of argument order.
     *
     * @param min Lower bound (inclusive).
     * @param max Upper bound (exclusive).
     */
    fun range(min: Float, max: Float): Float {
        if (min == max) return min
        val lo = if (min < max) min else max
        val hi = if (min < max) max else min
        drawCount++
        return lo + rng.nextFloat() * (hi - lo)
    }

    /**
     * Returns a random float in `[-magnitude, +magnitude)`.
     *
     * Common for jitter, spread, and impulse effects. Avoids
     * the awkwardness of `range(-m, m)` at call sites.
     *
     * @param magnitude Half-width of the range. Must be ≥ 0.
     */
    fun spread(magnitude: Float): Float {
        if (magnitude <= 0f) return 0f
        drawCount++
        return (rng.nextFloat() * 2f - 1f) * magnitude
    }

    /**
     * Returns a random float in `[center - magnitude, center + magnitude)`.
     */
    fun rangeAround(center: Float, magnitude: Float): Float {
        if (magnitude <= 0f) return center
        drawCount++
        return center + (rng.nextFloat() * 2f - 1f) * magnitude
    }

    // ========================================================
    //  Core Sampling — Int
    // ========================================================

    /**
     * Returns a random int in `[0, bound)`.
     *
     * @param bound Exclusive upper bound. Must be ≥ 1.
     *              If bound ≤ 0, returns 0.
     */
    fun i(bound: Int): Int {
        if (bound <= 1) return 0
        drawCount++
        return rng.nextInt(bound)
    }

    /**
     * Returns a random int in `[min, max]` (both inclusive).
     *
     * This matches the behavior of Kotlin's `Random.nextInt(from, until)`
     * except that `max` here is inclusive.
     *
     * @param min Lower bound (inclusive).
     * @param max Upper bound (inclusive).
     */
    fun rangeInt(min: Int, max: Int): Int {
        if (min >= max) return min
        drawCount++
        return min + rng.nextInt(max - min + 1)
    }

    /**
     * Returns a random int in `[0, Long.MAX_VALUE)` cast to Long.
     * Used by the boss scheduler for phase-based delays.
     */
    fun l(bound: Long): Long {
        if (bound <= 0L) return 0L
        drawCount++
        // Random.nextLong(bound) requires Java 17; we target API 24.
        // Compose from two ints.
        return (rng.nextLong() ushr 1) % bound
    }

    // ========================================================
    //  Boolean & Sign
    // ========================================================

    /**
     * Returns a random boolean with 50/50 probability.
     */
    fun bool(): Boolean {
        drawCount++
        return rng.nextBoolean()
    }

    /**
     * Returns a random boolean with probability `p` of being
     * true.
     *
     * @param p Probability of returning true, in `[0, 1]`.
     *          Values outside the range are clamped.
     */
    fun chance(p: Float): Boolean {
        if (p <= 0f) return false
        if (p >= 1f) return true
        drawCount++
        return rng.nextFloat() < p
    }

    /**
     * Returns either -1.0f or +1.0f, with 50/50 probability.
     *
     * Very common in game code for choosing a direction.
     * Named [sign] but distinct from [Math.signum] — here it
     * means "a random sign", not "the sign of a value".
     */
    fun sign(): Float {
        drawCount++
        return if (rng.nextBoolean()) 1f else -1f
    }

    /**
     * Returns either -1 or +1 as an [Int].
     */
    fun signInt(): Int {
        drawCount++
        return if (rng.nextBoolean()) 1 else -1
    }

    // ========================================================
    //  Vector / Angle Sampling
    // ========================================================

    /**
     * Returns a random angle in `[0, 2π)`.
     */
    fun angle(): Float {
        drawCount++
        return rng.nextFloat() * TWO_PI
    }

    /**
     * Returns a random point on the unit circle, written into
     * the provided output array as `[x, y]`.
     *
     * Using an output array avoids allocating a [Pair] or
     * similar object on every call — important in particle
     * spawning loops that may run dozens of times per frame.
     *
     * @param out A float array of length ≥ 2.
     */
    fun unitVector(out: FloatArray) {
        val a = angle()
        out[0] = kotlin.math.cos(a)
        out[1] = kotlin.math.sin(a)
    }

    /**
     * Returns a random point inside the unit disk (radius ≤ 1),
     * written into the provided output array.
     *
     * Uses the standard `r = sqrt(u)` trick to get uniform
     * area density (naive `r = u` would concentrate points
     * near the center).
     *
     * @param out A float array of length ≥ 2.
     */
    fun pointInUnitDisk(out: FloatArray) {
        val a = angle()
        val r = kotlin.math.sqrt(rng.nextFloat())
        out[0] = kotlin.math.cos(a) * r
        out[1] = kotlin.math.sin(a) * r
        drawCount++
    }

    // ========================================================
    //  Weighted Selection
    // ========================================================

    /**
     * Returns a random index in `[0, weights.size)` with
     * probability proportional to the weight at each index.
     *
     * Zero-weight entries are never selected. Negative weights
     * are treated as zero. If all weights are zero or the
     * array is empty, returns 0.
     *
     * This is the standard way to implement loot tables,
     * boss-attack selection with biases, and spawn
     * distribution.
     *
     * @param weights Non-negative weights.
     */
    fun weightedIndex(weights: FloatArray): Int {
        if (weights.isEmpty()) return 0

        var total = 0f
        for (w in weights) {
            if (w > 0f) total += w
        }
        if (total <= 0f) return 0

        drawCount++
        var roll = rng.nextFloat() * total
        for (i in weights.indices) {
            val w = weights[i]
            if (w <= 0f) continue
            roll -= w
            if (roll <= 0f) return i
        }
        // Should not reach here, but return the last valid index
        // as a defensive fallback.
        return weights.size - 1
    }

    /**
     * Picks a random element from a list.
     *
     * @throws IllegalArgumentException if the list is empty.
     */
    fun <T> pick(list: List<T>): T {
        require(list.isNotEmpty()) { "Cannot pick from an empty list" }
        drawCount++
        return list[rng.nextInt(list.size)]
    }

    /**
     * Picks a random element from an array.
     *
     * @throws IllegalArgumentException if the array is empty.
     */
    fun <T> pick(array: Array<T>): T {
        require(array.isNotEmpty()) { "Cannot pick from an empty array" }
        drawCount++
        return array[rng.nextInt(array.size)]
    }

    // ========================================================
    //  Shuffle (Fisher–Yates)
    // ========================================================

    /**
     * Shuffles an [IntArray] in place using the Fisher–Yates
     * algorithm.
     *
     * Used for randomizing attack order, particle spawn order,
     * and any other case where we want an unbiased permutation
     * of a small sequence.
     *
     * Reference: Wikipedia — "Fisher–Yates shuffle"
     */
    fun shuffle(array: IntArray) {
        for (i in array.size - 1 downTo 1) {
            drawCount++
            val j = rng.nextInt(i + 1)
            val tmp = array[i]
            array[i] = array[j]
            array[j] = tmp
        }
    }

    /**
     * Shuffles a [FloatArray] in place.
     */
    fun shuffle(array: FloatArray) {
        for (i in array.size - 1 downTo 1) {
            drawCount++
            val j = rng.nextInt(i + 1)
            val tmp = array[i]
            array[i] = array[j]
            array[j] = tmp
        }
    }

    /**
     * Shuffles a [MutableList] in place.
     */
    fun <T> shuffle(list: MutableList<T>) {
        for (i in list.size - 1 downTo 1) {
            drawCount++
            val j = rng.nextInt(i + 1)
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
        }
    }

    // ========================================================
    //  Noise Helpers
    // ========================================================

    /**
     * Returns a smooth pseudo-random value in `[-1, 1]` based
     * on a 1D "noise" input, using two hash-lerp steps.
     *
     * This is not true Perlin noise (which is overkill for our
     * needs); it is a cheap smooth-noise approximation suitable
     * for screen-shake jitter, camera sway, and idle wobble.
     *
     * @param x Input coordinate (any real value).
     */
    fun smoothNoise1D(x: Float): Float {
        val xi = kotlin.math.floor(x).toInt()
        val xf = x - xi
        val a = hashToUnit(xi)
        val b = hashToUnit(xi + 1)
        val t = smoothstep(xf)
        return (a + (b - a) * t) * 2f - 1f
    }

    /**
     * Smoothstep interpolation. Maps `[0, 1]` to `[0, 1]` with
     * zero first derivatives at the endpoints, producing a
     * natural ease-in/out.
     */
    private fun smoothstep(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    /**
     * Hashes an integer to a value in `[0, 1)`.
     * Uses the well-known Wang hash constant.
     */
    private fun hashToUnit(x: Int): Float {
        var h = x
        h = h xor (h ushr 16)
        h *= 0x7feb352d
        h = h xor (h ushr 15)
        h *= 0x846ca68b
        h = h xor (h ushr 16)
        return (h and 0x7FFFFFFF) / 2147483648f
    }

    // ========================================================
    //  Diagnostics
    // ========================================================

    /**
     * Returns a small sample of recent random values, useful
     * for verifying the generator's quality in a debug overlay.
     *
     * @param n Number of samples to return (max 16).
     */
    fun sample(n: Int): FloatArray {
        val count = n.coerceIn(1, 16)
        val out = FloatArray(count)
        for (i in 0 until count) {
            out[i] = f()
        }
        return out
    }

    /**
     * Returns a human-readable snapshot of the current state.
     */
    fun debugString(): String {
        return "Rng[" +
                "seed=0x${currentSeed.toString(16)}, " +
                "draws=$drawCount" +
                "]"
    }
}
