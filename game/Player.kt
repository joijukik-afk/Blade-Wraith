package com.bladewraith.game

import com.bladewraith.core.Rng

class Player {

    enum class State { IDLE, RUN, JUMP, ATTACK, DASH, HURT, DEAD }

    var x = 0f
    var y = 0f
    var w = 160f
    var h = 160f
    var vx = 0f
    var vy = 0f
    var facing = 1f
    var onGround = true

    var hp = 100f
    val maxHp = 100f

    var state = State.IDLE
    var stateTime = 0f

    var attackCooldown = 0f
    var dashCooldown = 0f
    var invuln = 0f

    var attackTimer = 0f
    var attackHit = false

    val attackDuration = 0.28f
    val attackActiveStart = 0.08f
    val attackActiveEnd = 0.20f
    val attackRange = 130f
    val attackDamage = 12f

    val dashDuration = 0.22f
    val dashSpeed = 1400f
    val dashCooldownMax = 0.65f

    val moveSpeed = 480f
    val jumpSpeed = 900f
    val gravity = -2400f

    fun update(dt: Float, moveAxis: Float, attackPressed: Boolean, jumpPressed: Boolean, dashPressed: Boolean) {
        if (state == State.DEAD) { stateTime += dt; return }

        attackCooldown = (attackCooldown - dt).coerceAtLeast(0f)
        dashCooldown = (dashCooldown - dt).coerceAtLeast(0f)
        invuln = (invuln - dt).coerceAtLeast(0f)

        // اعمال ورودی
        when (state) {
            State.ATTACK -> {
                attackTimer += dt
                if (attackTimer in attackActiveStart..attackActiveEnd && !attackHit) {
                    // hitbox فعاله — Game چک می‌کنه
                }
                if (attackTimer >= attackDuration) {
                    state = if (onGround) State.IDLE else State.JUMP
                }
                vx = 0f
            }
            State.DASH -> {
                vx = facing * dashSpeed
                vy = 0f
                if (stateTime >= dashDuration) {
                    state = if (onGround) State.IDLE else State.JUMP
                }
            }
            else -> {
                // حرکت
                if (moveAxis != 0f) {
                    vx = moveAxis * moveSpeed
                    facing = if (moveAxis > 0f) 1f else -1f
                } else {
                    vx *= 0.6f
                }

                // پرش
                if (jumpPressed && onGround) {
                    vy = jumpSpeed
                    onGround = false
                    state = State.JUMP
                }

                // حمله
                if (attackPressed && attackCooldown <= 0f && onGround) {
                    state = State.ATTACK
                    attackTimer = 0f
                    attackHit = false
                    attackCooldown = 0.35f
                }

                // دَش
                if (dashPressed && dashCooldown <= 0f) {
                    state = State.DASH
                    stateTime = 0f
                    dashCooldown = dashCooldownMax
                    invuln = dashDuration
                }

                if (state != State.ATTACK && state != State.DASH) {
                    state = if (!onGround) State.JUMP
                    else if (kotlin.math.abs(vx) > 30f) State.RUN
                    else State.IDLE
                }
            }
        }

        stateTime += dt

        // فیزیک
        vy += gravity * dt
        x += vx * dt
        y += vy * dt

        if (y <= 0f) {
            y = 0f
            vy = 0f
            if (!onGround) onGround = true
        }
    }

    fun isAttacking(): Boolean =
        state == State.ATTACK && attackTimer in attackActiveStart..attackActiveEnd

    fun attackHitboxX(): Float = if (facing > 0f) x + w else x - attackRange
    fun attackHitboxY(): Float = y + h * 0.25f

    fun takeDamage(dmg: Float): Boolean {
        if (invuln > 0f || state == State.DEAD) return false
        hp -= dmg
        invuln = 0.9f
        if (hp <= 0f) {
            hp = 0f
            state = State.DEAD
            stateTime = 0f
        } else {
            state = State.HURT
            stateTime = 0f
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
        attackCooldown = 0f
        dashCooldown = 0f
        invuln = 0f
        onGround = true
        facing = 1f
    }
}
