package com.bladewraith.core

import java.util.Random

object Rng {
    private val r = Random(0xC0FFEEL)
    fun f(): Float = r.nextFloat()
    fun range(a: Float, b: Float): Float = a + r.nextFloat() * (b - a)
    fun i(n: Int): Int = r.nextInt(n)
    fun sign(): Float = if (r.nextBoolean()) 1f else -1f
}
