package com.bladewraith.game

import com.bladewraith.core.Rng

class Boss {

    enum class State { IDLE, APPROACH, TELEGRAPH, SLAM, PROJECTILE, DASH, RECOVER, PHASE, DEAD }

    var x = 0f
    var y = 0f
    var w = 280f
    var h = 280f
    var vx = 0f
    var vy = 0f
    var facing = -1f

    var hp = 300f
    val maxHp = 300f

    var state = State.IDLE
    var stateTime = 0f
    var phase = 1

    var invuln = 0f
    var hurtFlash = 0f

    var attackIndex = 0
    var telegraphColor = 0xFFFF3040.toInt()

    fun update(dt: Float, playerX: Float, projectiles: MutableList<Projectile>, onSlam: (Float) -> Unit) {
        if (state == State.DEAD) {
            stateTime += dt
            vy += -2400f * dt
            y += vy * dt
            if (y < 0f) { y = 0f; vy = 0f }
            return
        }

        invuln = (invuln - dt).coerceAtLeast(0f)
        hurtFlash = (hurtFlash - dt).coerceAtLeast(0f)
        stateTime += dt

        // انتقال فاز
        if (phase == 1 && hp <= maxHp * 0.5f) {
            state = State.PHASE
            stateTime = 0f
            phase = 2
            invuln = 1.5f
            return
        }

        facing = if (playerX > x + w / 2f) 1f else -1f

        when (state) {
            State.IDLE -> {
                if (stateTime > 0.5f) {
                    state = State.APPROACH
                    stateTime = 0f
                }
            }
            State.APPROACH -> {
                val targetX = playerX - w / 2f
                val dx = targetX - x
                val speed = if (phase == 2) 380f else 240f
                vx = (dx.coerceIn(-speed, speed))
                x += vx * dt
                if (stateTime > (if (phase == 2) 0.7f else 1.1f) || kotlin.math.abs(dx) < 40f) {
                    chooseAttack(playerX)
                }
            }
            State.TELEGRAPH -> {
                vx = 0f
                if (stateTime > (if (phase == 2) 0.28f else 0.42f)) {
                    executeAttack(playerX, projectiles, onSlam)
                }
            }
            State.SLAM -> {
                if (stateTime > 0.55f) { state = State.RECOVER; stateTime = 0f }
            }
            State.PROJECTILE -> {
                if (stateTime > 0.6f) { state = State.RECOVER; stateTime = 0f }
            }
            State.DASH -> {
                x += facing * (if (phase == 2) 1300f else 1000f) * dt
                if (stateTime > 0.35f) { state = State.RECOVER; stateTime = 0f }
            }
            State.RECOVER -> {
                if (stateTime > (if (phase == 2) 0.35f else 0.55f)) {
                    state = State.APPROACH
                    stateTime = 0f
                }
            }
            State.PHASE -> {
                vx = 0f
                if (stateTime > 1.5f) {
                    state = State.APPROACH
                    stateTime = 0f
                }
            }
            State.DEAD -> {}
        }

        // مرز
        if (x < 0f) x = 0f
    }

    private fun chooseAttack(playerX: Float) {
        attackIndex = Rng.i(if (phase == 2) 3 else 2)
        state = State.TELEGRAPH
        stateTime = 0f
    }

    private fun executeAttack(playerX: Float, projectiles: MutableList<Projectile>, onSlam: (Float) -> Unit) {
        when (attackIndex) {
            0 -> {
                // اسلم
                state = State.SLAM
                stateTime = 0f
                onSlam(x + w / 2f)
            }
            1 -> {
                // پرتابه
                state = State.PROJECTILE
                stateTime = 0f
                val cx = x + w / 2f
                val cy = y + h * 0.55f
                val count = if (phase == 2) 5 else 3
                val dirX = if (playerX > cx) 1f else -1f
                for (i in 0 until count) {
                    val spread = (i - (count - 1) / 2f) * 0.22f
                    val baseAng = if (dirX > 0f) 0f else Math.PI.toFloat()
                    val ang = baseAng + spread * dirX
                    val sp = 520f
                    val p = projectiles.firstOrNull { !it.alive } ?: Projectile().also { projectiles.add(it) }
                    p.spawn(
                        cx, cy,
                        kotlin.math.cos(ang) * sp,
                        kotlin.math.sin(ang) * sp + 120f,
                        12f, 3.5f
                    )
                }
            }
            else -> {
                // دَش سریع
                state = State.DASH
                stateTime = 0f
            }
        }
    }

    fun takeDamage(dmg: Float): Boolean {
        if (invuln > 0f || state == State.DEAD) return false
        hp -= dmg
        hurtFlash = 0.12f
        if (hp <= 0f) {
            hp = 0f
            state = State.DEAD
            stateTime = 0f
            vy = 400f
        }
        return true
    }

    fun reset(startX: Float, groundY: Float) {
        x = startX
        y = groundY
        vx = 0f; vy = 0f
        hp = maxHp
        state = State.IDLE
        stateTime = 0f
        phase = 1
        invuln = 0f
        hurtFlash = 0f
    }
}
