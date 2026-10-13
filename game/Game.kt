package com.bladewraith.game

import com.bladewraith.core.Input
import com.bladewraith.core.Rng
import com.bladewraith.core.Time
import com.bladewraith.engine.Camera2D
import com.bladewraith.engine.Particles
import com.bladewraith.engine.SpriteBatch
import com.bladewraith.engine.TextureFactory

class Game {

    val input = Input()

    private var texWhite = 0
    private var texBg = 0
    private var texKnight = 0
    private var texBoss = 0
    private var texSoft = 0
    private var texOrb = 0

    private val player = Player()
    private val boss = Boss()
    private val projectiles = ArrayList<Projectile>()
    private val particles = Particles()
    private val hud = HUD()
    private val controls = TouchControls()

    private var groundY = 0f

    enum class Phase { PLAYING, VICTORY, DEFEAT }
    private var phase = Phase.PLAYING
    private var endAlpha = 0f
    private var endDelay = 0f

    private var bossSlamShock = 0f
    private var bossSlamX = 0f

    fun onSurfaceCreated() {
        texWhite = TextureFactory.white()
        texBg = TextureFactory.background(1024, 512)
        texKnight = TextureFactory.knight()
        texBoss = TextureFactory.boss()
        texSoft = TextureFactory.softCircle(64, 0xFFFFFFFF.toInt(), 0x00FFFFFF)
        texOrb = TextureFactory.orb()
    }

    fun onSurfaceChanged(w: Int, h: Int) {
        groundY = h * 0.14f
        controls.layout(w.toFloat(), h.toFloat())
        resetBattle()
    }

    private fun resetBattle() {
        player.reset(Camera2D.width * 0.22f, groundY)
        boss.reset(Camera2D.width * 0.72f, groundY)
        projectiles.clear()
        particles.clear()
        phase = Phase.PLAYING
        endAlpha = 0f
        endDelay = 0f
        bossSlamShock = 0f
        Time.reset()
    }

    fun update(dt: Float) {
        controls.update(input)

        if (phase != Phase.PLAYING) {
            endDelay += dt
            if (endDelay > 0.8f) endAlpha = (endAlpha + dt * 1.2f).coerceAtMost(1f)
            // دکمه‌ی restart با لمس هر جای صفحه بعد از محو شدن
            if (endAlpha >= 1f && input.count() > 0) {
                resetBattle()
            }
            particles.update(dt)
            return
        }

        // --- بازیکن ---
        player.update(
            dt,
            controls.moveAxis,
            controls.attackPressed,
            controls.jumpPressed,
            controls.dashPressed
        )
        player.x = player.x.coerceIn(0f, Camera2D.width - player.w)

        // --- باس ---
        boss.update(dt, player.x + player.w / 2f, projectiles) { slamX ->
            bossSlamX = slamX
            bossSlamShock = 1f
            Camera2D.shake(28f, 0.35f)
            Time.hitStop(0.06f)
            // موج ضربه
            val gy = groundY
            particles.burst(slamX, gy + 20f, 30, 700f, 0.7f, 40f, 0xFFFF6020.toInt(), -1200f)
            // آسیب اگر بازیکن روی زمین و نزدیک
            val dx = (player.x + player.w / 2f) - slamX
            if (kotlin.math.abs(dx) < 220f && player.onGround) {
                if (player.takeDamage(18f)) {
                    Camera2D.shake(20f, 0.3f)
                    Time.hitStop(0.08f)
                }
            }
        }

        // --- پرتابه‌ها ---
        for (i in projectiles.indices) projectiles[i].update(dt)
        projectiles.removeAll { !it.alive }

        // برخورد پرتابه با بازیکن
        val pcx = player.x + player.w / 2f
        val pcy = player.y + player.h / 2f
        for (p in projectiles) {
            if (!p.alive) continue
            val dx = p.x - pcx
            val dy = p.y - pcy
            val rr = p.r + player.w * 0.3f
            if (dx * dx + dy * dy < rr * rr) {
                if (player.takeDamage(p.damage)) {
                    p.alive = false
                    particles.burst(p.x, p.y, 14, 380f, 0.5f, 26f, 0xFFFF3050.toInt(), -400f)
                    Camera2D.shake(12f, 0.18f)
                    Time.hitStop(0.04f)
                }
            }
        }

        // --- شمشیر بازیکن ---
        if (player.isAttacking()) {
            val hx = player.attackHitboxX()
            val hy = player.attackHitboxY()
            val hw = player.attackRange
            val hh = player.h * 0.5f

            // برخورد با باس
            val bossHit = aabb(hx, hy, hw, hh, boss.x, boss.y, boss.w, boss.h)
            if (bossHit && !player.attackHit) {
                player.attackHit = true
                if (boss.takeDamage(player.attackDamage)) {
                    Camera2D.shake(14f, 0.18f)
                    Time.hitStop(0.05f)
                    val hitX = if (player.facing > 0f) boss.x else boss.x + boss.w
                    particles.burst(hitX, hy + hh / 2f, 20, 520f, 0.5f, 30f, 0xFFFFE0A0.toInt(), -200f)
                    particles.burst(hitX, hy + hh / 2f, 10, 380f, 0.7f, 22f, 0xFFFFFFFF.toInt(), -100f)
                }
            }

            // ذرات رد شمشیر
            if (kotlin.math.abs(player.stateTime - 0.12f) < dt * 0.6f) {
                val trailX = if (player.facing > 0f) hx + hw else hx
                particles.burst(trailX, hy + hh / 2f, 5, 200f, 0.22f, 18f, 0xFFA8D0FF.toInt(), 0f)
            }
        }

        // --- ذرات ---
        particles.update(dt)

        // --- بررسی برد/باخت ---
        if (player.state == Player.State.DEAD && endDelay == 0f) {
            phase = Phase.DEFEAT
            endDelay = 0f
            Camera2D.shake(40f, 0.7f)
            particles.burst(pcx, pcy, 40, 700f, 1.0f, 40f, 0xFFFF8080.toInt(), -800f)
        }
        if (boss.state == Boss.State.DEAD && phase == Phase.PLAYING && endDelay == 0f) {
            phase = Phase.VICTORY
            endDelay = 0f
            Camera2D.shake(35f, 0.6f)
            particles.burst(boss.x + boss.w / 2f, boss.y + boss.h / 2f, 60, 900f, 1.4f, 45f, 0xFFFF3060.toInt(), -900f)
            particles.burst(boss.x + boss.w / 2f, boss.y + boss.h / 2f, 30, 700f, 1.2f, 30f, 0xFFFFFFFF.toInt(), -600f)
        }
    }

    private fun aabb(ax: Float, ay: Float, aw: Float, ah: Float, bx: Float, by: Float, bw: Float, bh: Float): Boolean {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by
    }

    fun render(batch: SpriteBatch) {
        // ---- دنیا ----
        batch.begin(Camera2D.mvp)

        // پس‌زمینه
        batch.draw(0f, 0f, Camera2D.width, Camera2D.height, 0xFFFFFFFF.toInt(), texBg)

        // موج ضربه اسلم
        if (bossSlamShock > 0f) {
            bossSlamShock = (bossSlamShock - Time.dt * 2.5f).coerceAtLeast(0f)
            val r = (1f - bossSlamShock) * 320f
            val a = (bossSlamShock * 200f).toInt().coerceIn(0, 255)
            batch.draw(bossSlamX - r, groundY - 40f, r * 2f, 80f, (a shl 24) or 0xFF8030, texSoft)
        }

        // باس
        val bossAlpha = if (boss.invuln > 0f && boss.state != Boss.State.DEAD) {
            val blink = (kotlin.math.sin(boss.invuln * 60f) * 0.5f + 0.5f) > 0.5f
            if (blink) 0xFFFFFFFF.toInt() else 0x88FFFFFF.toInt()
        } else 0xFFFFFFFF.toInt()
        val bossColor = if (boss.hurtFlash > 0f) {
            val t = (boss.hurtFlash / 0.12f).coerceIn(0f, 1f)
            val r = (255).toInt()
            val g = (255 * (1f - t)).toInt()
            val b = (255 * (1f - t)).toInt()
            (255 shl 24) or (r shl 16) or (g shl 8) or b
        } else bossAlpha

        // هاله‌ی باس
        val glowSize = boss.w * 1.6f
        val glowColor = if (boss.phase == 2) 0x66FF2040.toInt() else 0x443080FF.toInt()
        batch.draw(
            boss.x + boss.w / 2f - glowSize / 2f,
            boss.y + boss.h / 2f - glowSize / 2f,
            glowSize, glowSize, glowColor, texSoft
        )

        // چشمک telegraph
        if (boss.state == Boss.State.TELEGRAPH) {
            val t = boss.stateTime / (if (boss.phase == 2) 0.28f else 0.42f)
            val a = ((t * 200).toInt().coerceIn(0, 200))
            val gc = (a shl 24) or (0xFF2040)
            val gs = boss.w * 1.8f
            batch.draw(
                boss.x + boss.w / 2f - gs / 2f,
                boss.y + boss.h / 2f - gs / 2f,
                gs, gs, gc, texSoft
            )
        }

        batch.draw(boss.x, boss.y, boss.w, boss.h, bossColor, texBoss, 0f, 0f, 1f, 1f)

        // پرتابه‌ها
        for (p in projectiles) {
            if (!p.alive) continue
            val s = p.r * 2.4f
            batch.draw(p.x - s / 2f, p.y - s / 2f, s, s, 0xFFFFFFFF.toInt(), texOrb)
        }

        // بازیکن
        if (player.state != Player.State.DEAD || player.stateTime < 2f) {
            val playerAlpha = if (player.invuln > 0f && player.state != Player.State.DEAD) {
                val blink = (kotlin.math.sin(player.invuln * 50f) * 0.5f + 0.5f) > 0.5f
                if (blink) 0xFFFFFFFF.toInt() else 0x66FFFFFF.toInt()
            } else 0xFFFFFFFF.toInt()

            // رد دَش
            if (player.state == Player.State.DASH) {
                for (i in 1..3) {
                    val off = i * 22f * player.facing
                    batch.draw(player.x - off * 0.5f, player.y, player.w, player.h, 0x33A0D0FF, texKnight,
                        if (player.facing < 0f) 1f else 0f, 0f,
                        if (player.facing < 0f) 0f else 1f, 1f)
                }
            }

            // درخشش زیر بازیکن
            batch.draw(
                player.x + player.w / 2f - player.w * 0.7f,
                player.y - player.h * 0.1f,
                player.w * 1.4f, player.h * 0.3f,
                0x4464A0FF, texSoft
            )

            // چشمک قرمز وقتی آسیب خورده
            val finalPlayerColor = if (player.state == Player.State.HURT && player.stateTime < 0.15f) {
                0xFFFF8080.toInt()
            } else playerAlpha

            val u0 = if (player.facing < 0f) 1f else 0f
            val u1 = if (player.facing < 0f) 0f else 1f

            // چرخش حمله
            val rot = if (player.state == Player.State.ATTACK) {
                val t = player.attackTimer / player.attackDuration
                val swing = kotlin.math.sin(t * Math.PI.toFloat()) * 15f
                swing * player.facing
            } else 0f

            // pivot در مرکز پایین‌تنه
            val cx = player.x + player.w / 2f
            val cy = player.y + player.h * 0.3f
            val cos = kotlin.math.cos(Math.toRadians(rot.toDouble()).toFloat())
            val sin = kotlin.math.sin(Math.toRadians(rot.toDouble()).toFloat())
            val rx = player.x - cx
            val ry = player.y - cy
            val drawX = cx + rx * cos - ry * sin
            val drawY = cy + rx * sin + ry * cos

            batch.draw(
                drawX, drawY, player.w, player.h,
                finalPlayerColor, texKnight,
                u0, 0f, u1, 1f,
                rot
            )
        }

        // ذرات
        particles.render(batch, texSoft)

        batch.end()

        // ---- UI ----
        batch.begin(Camera2D.uiMvp)
        hud.render(batch, texWhite, player, boss)
        if (phase == Phase.PLAYING) {
            controls.render(batch, texWhite, texSoft)
        }
        if (phase != Phase.PLAYING) {
            hud.renderGameOver(batch, texWhite, phase == Phase.VICTORY, endAlpha)
        }
        batch.end()
    }
}
