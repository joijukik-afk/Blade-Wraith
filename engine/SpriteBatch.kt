package com.bladewraith.engine

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.sin

class SpriteBatch(maxSprites: Int = 4096) {

    private companion object {
        const val FPV = 8
        const val STRIDE = FPV * 4
    }

    private val maxSprites = maxSprites
    private val vertexData = FloatArray(maxSprites * 4 * FPV)
    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(vertexData.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private val indexData = ShortArray(maxSprites * 6)
    private val indexBuffer: ShortBuffer = ByteBuffer
        .allocateDirect(indexData.size * 2)
        .order(ByteOrder.nativeOrder())
        .asShortBuffer()

    private var count = 0
    private var currentTex = -1
    private var drawing = false

    init {
        var i = 0
        while (i < maxSprites) {
            val v = (i * 4).toShort()
            val o = i * 6
            indexData[o] = v
            indexData[o + 1] = (v + 1).toShort()
            indexData[o + 2] = (v + 2).toShort()
            indexData[o + 3] = v
            indexData[o + 4] = (v + 2).toShort()
            indexData[o + 5] = (v + 3).toShort()
            i++
        }
        indexBuffer.put(indexData)
        indexBuffer.position(0)
    }

    fun begin(mvp: FloatArray) {
        count = 0
        currentTex = -1
        drawing = true
        ShaderCache.sprite.use()
        ShaderCache.sprite.setMVP(mvp)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    fun end() {
        flush()
        drawing = false
    }

    fun draw(
        x: Float, y: Float, w: Float, h: Float,
        color: Int, tex: Int = 0,
        u0: Float = 0f, v0: Float = 0f, u1: Float = 1f, v1: Float = 1f,
        rotationDeg: Float = 0f
    ) {
        if (!drawing) return
        if (tex != currentTex) { flush(); currentTex = tex }
        if (count >= maxSprites) flush()

        val a = ((color ushr 24) and 0xFF) / 255f
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f

        var blx = x;       var bly = y
        var brx = x + w;   var bry = y
        var trx = x + w;   var try_ = y + h
        var tlx = x;       var tly = y + h

        if (rotationDeg != 0f) {
            val rad = Math.toRadians(rotationDeg.toDouble()).toFloat()
            val cs = cos(rad)
            val sn = sin(rad)
            val cx = x + w * 0.5f
            val cy = y + h * 0.5f

            fun rotX(px: Float, py: Float) = cx + (px - cx) * cs - (py - cy) * sn
            fun rotY(px: Float, py: Float) = cy + (px - cx) * sn + (py - cy) * cs

            val nblx = rotX(blx, bly); val nbly = rotY(blx, bly)
            val nbrx = rotX(brx, bry); val nbry = rotY(brx, bry)
            val ntrx = rotX(trx, try_); val ntry = rotY(trx, try_)
            val ntlx = rotX(tlx, tly); val ntly = rotY(tlx, tly)
            blx = nblx; bly = nbly
            brx = nbrx; bry = nbry
            trx = ntrx; try_ = ntry
            tlx = ntlx; tly = ntly
        }

        var i = count * 4 * FPV

        vertexData[i++] = blx; vertexData[i++] = bly
        vertexData[i++] = u0;  vertexData[i++] = v1
        vertexData[i++] = r; vertexData[i++] = g; vertexData[i++] = b; vertexData[i++] = a

        vertexData[i++] = brx; vertexData[i++] = bry
        vertexData[i++] = u1;  vertexData[i++] = v1
        vertexData[i++] = r; vertexData[i++] = g; vertexData[i++] = b; vertexData[i++] = a

        vertexData[i++] = trx; vertexData[i++] = try_
        vertexData[i++] = u1;  vertexData[i++] = v0
        vertexData[i++] = r; vertexData[i++] = g; vertexData[i++] = b; vertexData[i++] = a

        vertexData[i++] = tlx; vertexData[i++] = tly
        vertexData[i++] = u0;  vertexData[i++] = v0
        vertexData[i++] = r; vertexData[i++] = g; vertexData[i++] = b; vertexData[i++] = a

        count++
    }

    fun flush() {
        if (count == 0) return

        val prog = ShaderCache.sprite
        prog.bindTexture(currentTex)

        vertexBuffer.clear()
        vertexBuffer.put(vertexData, 0, count * 4 * FPV)
        vertexBuffer.flip()

        GLES20.glEnableVertexAttribArray(prog.posLoc)
        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(prog.posLoc, 2, GLES20.GL_FLOAT, false, STRIDE, vertexBuffer)

        GLES20.glEnableVertexAttribArray(prog.uvLoc)
        vertexBuffer.position(2)
        GLES20.glVertexAttribPointer(prog.uvLoc, 2, GLES20.GL_FLOAT, false, STRIDE, vertexBuffer)

        GLES20.glEnableVertexAttribArray(prog.colorLoc)
        vertexBuffer.position(4)
        GLES20.glVertexAttribPointer(prog.colorLoc, 4, GLES20.GL_FLOAT, false, STRIDE, vertexBuffer)

        indexBuffer.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, count * 6, GLES20.GL_UNSIGNED_SHORT, indexBuffer)

        GLES20.glDisableVertexAttribArray(prog.posLoc)
        GLES20.glDisableVertexAttribArray(prog.uvLoc)
        GLES20.glDisableVertexAttribArray(prog.colorLoc)

        count = 0
    }
}
