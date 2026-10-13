package com.bladewraith.game

import com.bladewraith.core.Input
import com.bladewraith.engine.Camera2D
import com.bladewraith.engine.SpriteBatch

class TouchControls {

    // joystick چپ
    var stickCenterX = 0f
    var stickCenterY = 0f
    var stickRadius = 140f
    var knobRadius = 60f
    var knobX = 0f
    var knobY = 0f
    var stickActiveId = -1

    // دکمه‌ها
    var attackX = 0f; var attackY = 0f; var attackR = 80f
    var jumpX = 0f; var jumpY = 0f; var jumpR = 80f
    var dashX = 0f; var dashY = 0f; var dashR = 80f

    var attackId = -1
    var jumpId = -1
    var dashId = -1

    var moveAxis = 0f
    var attackPressed = false
    var jumpPressed = false
    var dashPressed = false

    fun layout(W: Float, H: Float) {
        stickRadius = (W * 0.11f).coerceIn(90f, 180f)
        knobRadius = stickRadius * 0.45f
        stickCenterX = stickRadius + 60f
        stickCenterY = stickRadius + 60f
        knobX = stickCenterX
        knobY = stickCenterY

        val btnR = (W * 0.065f).coerceIn(55f, 100f)
        attackR = btnR
        jumpR = btnR
        dashR = btnR

        attackX = W - btnR - 60f
        attackY = btnR + 60f

        jumpX = W - btnR * 4f - 100f
        jumpY = btnR + 80f

        dashX = W - btnR * 2.6f - 80f
        dashY = btnR * 2.6f + 60f
    }

    fun update(input: Input) {
        moveAxis = 0f
        attackPressed = false
        jumpPressed = false
        dashPressed = false

        // یافتن پوینترهای فعال
        stickActiveId = -1
        attackId = -1
        jumpId = -1
        dashId = -1

        for (p in input.all()) {
            val px = p.x
            val py = Camera2D.height - p.y // تبدیل به مختصات دنیا

            // joystick
            if (stickActiveId == -1) {
                val dx = px - stickCenterX
                val dy = py - stickCenterY
                val d = kotlin.math.sqrt(dx * dx + dy * dy)
                if (d < stickRadius * 1.4f) {
                    stickActiveId = System.identityHashCode(p)
                    val maxD = stickRadius * 0.9f
                    if (d > maxD) {
                        val k = maxD / d
                        knobX = stickCenterX + dx * k
                        knobY = stickCenterY + dy * k
                    } else {
                        knobX = px
                        knobY = py
                    }
                    val nx = (knobX - stickCenterX) / maxD
                    moveAxis = nx.coerceIn(-1f, 1f)
                    continue
                }
            }

            // دکمه حمله
            if (dist2(px, py, attackX, attackY) < attackR * attackR) {
                attackId = System.identityHashCode(p)
                attackPressed = true
                continue
            }
            if (dist2(px, py, jumpX, jumpY) < jumpR * jumpR) {
                jumpId = System.identityHashCode(p)
                jumpPressed = true
                continue
            }
            if (dist2(px, py, dashX, dashY) < dashR * dashR) {
                dashId = System.identityHashCode(p)
                dashPressed = true
                continue
            }
        }

        // اگر joystick رها شد
        if (stickActiveId == -1) {
            knobX += (stickCenterX - knobX) * 0.35f
            knobY += (stickCenterY - knobY) * 0.35f
        }
    }

    private fun dist2(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return dx * dx + dy * dy
    }

    fun render(batch: SpriteBatch, texWhite: Int, texSoft: Int) {
        // joystick — بیرونی
        batch.draw(
            stickCenterX - stickRadius, stickCenterY - stickRadius,
            stickRadius * 2f, stickRadius * 2f,
            0x33FFFFFF, texSoft
        )
        // حلقه
        batch.draw(stickCenterX - stickRadius, stickCenterY - 3f, stickRadius * 2f, 6f, 0x66A0B0FF.toInt(), texWhite)
        batch.draw(stickCenterX - 3f, stickCenterY - stickRadius, 6f, stickRadius * 2f, 0x66A0B0FF.toInt(), texWhite)

        // knob
        batch.draw(
            knobX - knobRadius, knobY - knobRadius,
            knobRadius * 2f, knobRadius * 2f,
            0xCC64A0FF.toInt(), texSoft
        )
        batch.draw(
            knobX - knobRadius * 0.5f, knobY - knobRadius * 0.5f,
            knobRadius, knobRadius, 0xFFE8F4FF.toInt(), texWhite
        )

        // دکمه حمله
        drawButton(batch, texSoft, texWhite, attackX, attackY, attackR, 0xCCFF4060.toInt(), 0xFFFFD0D8.toInt())
        // دکمه پرش
        drawButton(batch, texSoft, texWhite, jumpX, jumpY, jumpR, 0xCC44B0FF.toInt(), 0xFFD0E8FF.toInt())
        // دکمه دَش
        drawButton(batch, texSoft, texWhite, dashX, dashY, dashR, 0xCCB060FF.toInt(), 0xFFE8D0FF.toInt())

        // آیکون‌های ساده با مستطیل
        // حمله: خط افقی
        batch.draw(attackX - attackR * 0.5f, attackY - 5f, attackR, 10f, 0xFFFFFFFF.toInt(), texWhite)
        // پرش: مثلث ساده — سه مستطیل پله‌ای
        batch.draw(jumpX - 8f, jumpY - jumpR * 0.5f, 16f, jumpR * 0.6f, 0xFFFFFFFF.toInt(), texWhite)
        batch.draw(jumpX - 18f, jumpY - jumpR * 0.15f, 36f, 12f, 0xFFFFFFFF.toInt(), texWhite)
        // دَش: دو خط مورب — با مستطیل افقی
        batch.draw(dashX - dashR * 0.5f, dashY + 8f, dashR * 0.5f, 8f, 0xFFFFFFFF.toInt(), texWhite)
        batch.draw(dashX - dashR * 0.2f, dashY - 16f, dashR * 0.5f, 8f, 0xFFFFFFFF.toInt(), texWhite)
    }

    private fun drawButton(
        batch: SpriteBatch, texSoft: Int, texWhite: Int,
        cx: Float, cy: Float, r: Float, fill: Int, ring: Int
    ) {
        batch.draw(cx - r * 1.2f, cy - r * 1.2f, r * 2.4f, r * 2.4f, 0x22000000, texSoft)
        batch.draw(cx - r, cy - r, r * 2f, r * 2f, fill, texSoft)
        batch.draw(cx - r, cy - r, r * 2f, 4f, ring, texWhite)
        batch.draw(cx - r, cy + r - 4f, r * 2f, 4f, ring, texWhite)
        batch.draw(cx - r, cy - r, 4f, r * 2f, ring, texWhite)
        batch.draw(cx + r - 4f, cy - r, 4f, r * 2f, ring, texWhite)
    }
}
