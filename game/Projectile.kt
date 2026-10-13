package com.bladewraith.game

class Projectile {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var r = 22f
    var alive = false
    var damage = 10f
    var life = 0f
    var age = 0f

    fun spawn(px: Float, py: Float, pvx: Float, pvy: Float, dmg: Float, lifetime: Float) {
        x = px; y = py
        vx = pvx; vy = pvy
        damage = dmg
        life = lifetime
        age = 0f
        alive = true
    }

    fun update(dt: Float) {
        if (!alive) return
        age += dt
        x += vx * dt
        y += vy * dt
        if (age >= life) alive = false
    }
}
