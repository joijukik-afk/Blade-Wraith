package com.bladewraith.game

import com.bladewraith.engine.Camera2D
import com.bladewraith.engine.SpriteBatch

class HUD {

    fun render(batch: SpriteBatch, texWhite: Int, player: Player, boss: Boss) {
        val W = Camera2D.width
        val H = Camera2D.height

        // نوار HP بازیکن
        val pBarX = 40f
        val pBarY = H - 70f
        val pBarW = W * 0.32f
        val pBarH = 28f

        // پس‌زمینه
        batch.draw(pBarX - 4f, pBarY - 4f, pBarW + 8f, pBarH + 8f, 0xCC000000.toInt(), texWhite)
        // قاب
        batch.draw(pBarX - 2f, pBarY - 2f, pBarW + 4f, pBarH + 4f, 0xFF2A2540.toInt(), texWhite)
        // پس‌زمینه داخل
        batch.draw(pBarX, pBarY, pBarW, pBarH, 0xFF0A0A18.toInt(), texWhite)
        // پر شدن
        val pRatio = (player.hp / player.maxHp).coerceIn(0f, 1f)
        val hpColor = when {
            pRatio > 0.6f -> 0xFF44E080.toInt()
            pRatio > 0.3f -> 0xFFFFC844.toInt()
            else -> 0xFFFF4455.toInt()
        }
        batch.draw(pBarX, pBarY, pBarW * pRatio, pBarH, hpColor, texWhite)
        // درخشش بالای نوار
        batch.draw(pBarX, pBarY + pBarH - 4f, pBarW * pRatio, 4f, 0x66FFFFFF, texWhite)

        // نوار HP باس
        val bBarW = W * 0.5f
        val bBarH = 32f
        val bBarX = (W - bBarW) / 2f
        val bBarY = H - 80f

        batch.draw(bBarX - 4f, bBarY - 4f, bBarW + 8f, bBarH + 8f, 0xCC000000.toInt(), texWhite)
        batch.draw(bBarX - 2f, bBarY - 2f, bBarW + 4f, bBarH + 4f, 0xFF3A1020.toInt(), texWhite)
        batch.draw(bBarX, bBarY, bBarW, bBarH, 0xFF0A0A18.toInt(), texWhite)

        val bRatio = (boss.hp / boss.maxHp).coerceIn(0f, 1f)
        val bColor = if (boss.phase == 2) 0xFFFF2040.toInt() else 0xFFB03060.toInt()
        // نوار از راست به چپ (نزدیک‌تر به باس)
        val fillW = bBarW * bRatio
        batch.draw(bBarX + (bBarW - fillW), bBarY, fillW, bBarH, bColor, texWhite)
        batch.draw(bBarX + (bBarW - fillW), bBarY + bBarH - 4f, fillW, 4f, 0x88FFFFFF.toInt(), texWhite)

        // حاشیه‌ی فاز ۲
        if (boss.phase == 2) {
            batch.draw(bBarX - 6f, bBarY - 6f, bBarW + 12f, 2f, 0xFFFF3040.toInt(), texWhite)
            batch.draw(bBarX - 6f, bBarY + bBarH + 4f, bBarW + 12f, 2f, 0xFFFF3040.toInt(), texWhite)
        }
    }

    fun renderGameOver(batch: SpriteBatch, texWhite: Int, won: Boolean, alpha: Float) {
        val W = Camera2D.width
        val H = Camera2D.height

        batch.draw(0f, 0f, W, H, ((alpha * 0.75f * 255).toInt().coerceIn(0, 255) shl 24), texWhite)

        // متن با rectangle (بدون فونت)
        // "YOU WIN" یا "YOU DIED" — با مستطیل‌های افقی
        val color = if (won) 0xFF44E080.toInt() else 0xFFFF2040.toInt()
        val barW = W * 0.35f
        val barH = 10f
        val cx = W / 2f
        val cy = H / 2f
        val spacing = 22f
        val text = if (won) "VICTORY" else "DEFEATED"
        val n = text.length
        val totalW = (n - 1) * spacing
        val startX = cx - totalW / 2f
        for (i in 0 until n) {
            val px = startX + i * spacing
            batch.draw(px - barW / 10f, cy - barH / 2f, barW / 5f, barH, color, texWhite)
        }
    }
}
