package com.bladewraith.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.opengl.GLES20
import android.opengl.GLUtils

object TextureFactory {

    fun upload(bmp: Bitmap): Int {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
        return tex[0]
    }

    fun white(): Int {
        val bmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        return upload(bmp)
    }

    fun softCircle(size: Int, inner: Int, outer: Int): Int {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            size / 2f, size / 2f, size / 2f,
            intArrayOf(inner, outer),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        return upload(bmp)
    }

    fun background(w: Int, h: Int): Int {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val bg = Paint()
        bg.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFF0A0A18.toInt(), 0xFF1A1030.toInt(), 0xFF060610.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

        val moon = Paint(Paint.ANTI_ALIAS_FLAG)
        moon.shader = RadialGradient(
            w * 0.75f, h * 0.30f, h * 0.60f,
            intArrayOf(0x55A080FF.toInt(), 0x00000000),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(w * 0.75f, h * 0.30f, h * 0.60f, moon)

        val pillar = Paint(Paint.ANTI_ALIAS_FLAG)
        pillar.color = 0xFF05030C.toInt()
        val rnd = java.util.Random(42)
        var x = 20f
        while (x < w) {
            val pw = 30f + rnd.nextFloat() * 70f
            val ph = h * (0.35f + rnd.nextFloat() * 0.40f)
            c.drawRect(x, h - ph, x + pw, h.toFloat(), pillar)
            x += pw + 40f + rnd.nextFloat() * 80f
        }

        val ground = Paint()
        ground.shader = LinearGradient(
            0f, h * 0.82f, 0f, h.toFloat(),
            intArrayOf(0xFF0A0510.toInt(), 0xFF000000.toInt()),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, h * 0.82f, w.toFloat(), h.toFloat(), ground)

        return upload(bmp)
    }

    fun knight(): Int {
        val s = 128
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // شمشیر
        val sword = Paint(Paint.ANTI_ALIAS_FLAG)
        sword.shader = LinearGradient(
            80f, 0f, 96f, 0f,
            intArrayOf(0xFFE8F4FF.toInt(), 0xFF9AB6FF.toInt()),
            null, Shader.TileMode.CLAMP
        )
        val sp = Path()
        sp.moveTo(84f, 4f)
        sp.lineTo(94f, 18f)
        sp.lineTo(94f, 62f)
        sp.lineTo(84f, 62f)
        sp.close()
        c.drawPath(sp, sword)

        val hilt = Paint(Paint.ANTI_ALIAS_FLAG)
        hilt.color = 0xFF8B6A2E.toInt()
        c.drawRect(76f, 62f, 100f, 68f, hilt)
        c.drawRect(84f, 68f, 92f, 78f, hilt)

        // شنل
        val body = Paint(Paint.ANTI_ALIAS_FLAG)
        body.shader = LinearGradient(
            40f, 40f, 90f, 122f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFC8D4FF.toInt()),
            null, Shader.TileMode.CLAMP
        )
        val bp = Path()
        bp.moveTo(48f, 46f)
        bp.lineTo(82f, 46f)
        bp.lineTo(92f, 88f)
        bp.lineTo(70f, 122f)
        bp.lineTo(58f, 122f)
        bp.lineTo(36f, 88f)
        bp.close()
        c.drawPath(bp, body)

        // کلاه‌خود
        val helm = Paint(Paint.ANTI_ALIAS_FLAG)
        helm.shader = LinearGradient(
            50f, 20f, 80f, 50f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFB0C0E8.toInt()),
            null, Shader.TileMode.CLAMP
        )
        c.drawCircle(64f, 34f, 18f, helm)

        val visor = Paint(Paint.ANTI_ALIAS_FLAG)
        visor.color = 0xFF1A1020.toInt()
        c.drawRect(52f, 30f, 76f, 36f, visor)

        val eye = Paint(Paint.ANTI_ALIAS_FLAG)
        eye.color = 0xFF64D0FF.toInt()
        c.drawCircle(58f, 33f, 2.2f, eye)
        c.drawCircle(70f, 33f, 2.2f, eye)

        return upload(bmp)
    }

    fun boss(): Int {
        val s = 192
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val body = Paint(Paint.ANTI_ALIAS_FLAG)
        body.shader = LinearGradient(
            60f, 60f, 130f, 190f,
            intArrayOf(0xFF2A1038.toInt(), 0xFF0A0510.toInt()),
            null, Shader.TileMode.CLAMP
        )
        val bp = Path()
        bp.moveTo(96f, 20f)
        bp.lineTo(150f, 90f)
        bp.lineTo(140f, 176f)
        bp.lineTo(52f, 176f)
        bp.lineTo(42f, 90f)
        bp.close()
        c.drawPath(bp, body)

        val horn = Paint(Paint.ANTI_ALIAS_FLAG)
        horn.color = 0xFF6A1A2A.toInt()
        val h1 = Path()
        h1.moveTo(60f, 40f); h1.lineTo(20f, 4f); h1.lineTo(36f, 34f); h1.close()
        c.drawPath(h1, horn)
        val h2 = Path()
        h2.moveTo(132f, 40f); h2.lineTo(172f, 4f); h2.lineTo(156f, 34f); h2.close()
        c.drawPath(h2, horn)

        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        glow.shader = RadialGradient(
            96f, 90f, 62f,
            intArrayOf(0xAAFF2030.toInt(), 0x00FF2030),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(96f, 90f, 62f, glow)

        val eye = Paint(Paint.ANTI_ALIAS_FLAG)
        eye.color = 0xFFFF3040.toInt()
        c.drawCircle(78f, 88f, 5f, eye)
        c.drawCircle(114f, 88f, 5f, eye)

        return upload(bmp)
    }

    fun orb(): Int {
        val s = 64
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            s / 2f, s / 2f, s / 2f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFFF3050.toInt(), 0x00FF2030),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(s / 2f, s / 2f, s / 2f, p)
        return upload(bmp)
    }
}
