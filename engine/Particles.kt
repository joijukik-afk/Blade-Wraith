package com.bladewraith.engine

import com.bladewraith.core.Rng

class Particles(private val max: Int = 800) {

    private val x = FloatArray(max)
    private val y = FloatArray(max)
    private val vx = FloatArray(max)
    private val vy = FloatArray(max)
    private val life = FloatArray(max)
    private val maxLife = FloatArray(max)
    private val size = FloatArray(max)
    private val color = IntArray(max)
    private val gravity = FloatArray(max)
    private val drag = FloatArray(max)
    private var count = 0

    fun spawn(
        px: Float, py: Float, pvx: Float, pvy: Float,
        pLife: Float, pSize: Float, pColor: Int,
        pGravity: Float = 0f, pDrag: Float = 0.9f
    ) {
        if (count >= max) return
        x[count] = px
        y[count] = py
        vx[count] = pvx
        vy[count] = pvy
        life[count] = pLife
        maxLife[count] = pLife
        size[count] = pSize
        color[count] = pColor
        gravity[count] = pGravity
        drag[count] = pDrag
        count++
    }

    fun burst(
        px: Float, py: Float, n: Int,
        speed: Float, pLife: Float, pSize: Float, pColor: Int,
        pGravity: Float = 0f
    ) {
        for (i in 0 until n) {
            val ang = Rng.range(0f, 6.283185f)
            val sp = Rng.range(speed * 0.3f, speed)
            spawn(
                px, py,
                kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp,
                pLife * Rng.range(0.6f, 1.2f),
                pSize * Rng.range(0.6f, 1.3f),
                pColor,
                pGravity,
                0.9f
            )
        }
    }

    fun update(dt: Float) {
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f) {
                val last = count - 1
                x[i] = x[last]; y[i] = y[last]
                vx[i] = vx[last]; vy[i] = vy[last]
                life[i] = life[last]; maxLife[i] = maxLife[last]
                size[i] = size[last]; color[i] = color[last]
                gravity[i] = gravity[last]; drag[i] = drag[last]
                count--
                continue
            }
            vy[i] += gravity[i] * dt
            vx[i] *= drag[i]
            vy[i] *= drag[i]
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            i++
        }
    }

    fun render(batch: SpriteBatch, tex: Int) {
        for (i in 0 until count) {
            val t = life[i] / maxLife[i]
            val a = (t * 255f).toInt().coerceIn(0, 255)
            val baseColor = color[i]
            val r = (baseColor shr 16) and 0xFF
            val g = (baseColor shr 8) and 0xFF
            val b = baseColor and 0xFF
            val c = (a shl 24) or (r shl 16) or (g shl 8) or b
            val s = size[i] * (0.4f + 0.6f * t)
            batch.draw(x[i] - s / 2f, y[i] - s / 2f, s, s, c, tex)
        }
    }

    fun clear() { count = 0 }
}
