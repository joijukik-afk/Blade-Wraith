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
import android.util.Log
import java.util.Random

/**
 * ============================================================
 *  Blade Wraith — Procedural Texture Factory
 *
 *  Generates all game textures at runtime using Android's
 *  [Canvas] API, then uploads them to the GPU as OpenGL ES
 *  2.0 textures. No PNG or JPG assets are shipped in the APK.
 *
 *  Why procedural textures?
 *    • **Tiny APK.** All artwork lives in code. The APK is
 *      pure Kotlin + shaders — under 1 MB.
 *    • **No asset pipeline.** Change a color constant, rebuild,
 *      done. No Photoshop, no export, no import.
 *    • **Infinite resolution.** Textures are generated at the
 *      exact size needed. No scaling artifacts.
 *    • **Parameterizable.** Colors, sizes, and shapes can be
 *      driven by gameplay state (e.g., a boss tinted red at
 *      low HP).
 *
 *  Tradeoffs:
 *    • **Startup cost.** Generating ~8 textures takes 50–150 ms
 *      on a mid-range device. This happens once, during
 *      [Renderer.onSurfaceCreated], before the first frame.
 *    • **Not artist-friendly.** Procedural art is great for
 *      stylized minimal looks, but for detailed illustration
 *      you would still use a PNG.
 *
 *  Design:
 *    Each public method returns a GL texture ID (Int). The
 *    generated [Bitmap] is recycled immediately after upload
 *    to free heap memory. Textures are uploaded with
 *    `GL_LINEAR` filtering and `GL_CLAMP_TO_EDGE` wrapping,
 *    which are the correct defaults for a 2D game.
 *
 *  Threading:
 *    All methods MUST be called from the GL thread, because
 *    they make GL calls. They also use [Bitmap.createBitmap]
 *    and [Canvas], which are safe to call from any thread.
 *
 *  References:
 *    • Android Developers — "Draw with Canvas"
 *      https://developer.android.com/develop/ui/views/graphics/drawables
 *    • Android Developers — "GLUtils.texImage2D"
 *      https://developer.android.com/reference/android/opengl/GLUtils
 *    • Khronos — "OpenGL ES 2.0: glTexImage2D"
 * ============================================================
 */
object TextureFactory {

    // ========================================================
    //  Companion
    // ========================================================
    private const val TAG = "TextureFactory"

    // ========================================================
    //  Public API — Individual Textures
    // ========================================================

    /**
     * Creates a 1×1 opaque white texture.
     *
     * Used by [SpriteBatch.drawRect] to render solid colored
     * rectangles through the sprite pipeline (rather than the
     * solid-color pipeline). This keeps every UI draw in a
     * single batch, which is faster than switching shaders.
     *
     * @return GL texture ID.
     */
    fun white(): Int {
        val bmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val id = upload(bmp, "white")
        Log.d(TAG, "white() → $id")
        return id
    }

    /**
     * Creates a soft radial-gradient circle.
     *
     * Perfect for:
     *    • Particles (dust, sparks, blood)
     *    • Projectile glow
     *    • Light halos
     *    • Under-entity shadows
     *    • Screen-shake shockwaves
     *
     * @param size        Texture size in pixels (square).
     * @param innerColor  ARGB color at the center.
     * @param outerColor  ARGB color at the edge (usually 0x00…).
     * @return GL texture ID.
     */
    fun softCircle(size: Int = 64, innerColor: Int = 0xFFFFFFFF.toInt(), outerColor: Int = 0x00FFFFFF): Int {
        val s = size.coerceIn(8, 512)
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            s / 2f, s / 2f, s / 2f,
            intArrayOf(innerColor, outerColor),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(s / 2f, s / 2f, s / 2f, paint)

        val id = upload(bmp, "softCircle($s)")
        Log.d(TAG, "softCircle() → $id")
        return id
    }

    /**
     * Creates the game background: a deep gradient sky with a
     * moon halo, ruined pillars along the bottom, and a
     * grounded fog line.
     *
     * The texture is generated at a fixed aspect ratio of 2:1
     * (e.g., 1024×512) and stretched to fill the viewport. This
     * is acceptable because the background has no fine detail —
     * a small amount of stretching is imperceptible.
     *
     * @param width  Texture width in pixels.
     * @param height Texture height in pixels.
     * @return GL texture ID.
     */
    fun background(width: Int = 1024, height: Int = 512): Int {
        val w = width.coerceIn(256, 2048)
        val h = height.coerceIn(128, 1024)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // ----------------------------------------------------
        //  Layer 1: vertical gradient sky
        // ----------------------------------------------------
        val sky = Paint()
        sky.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                0xFF0A0A18.toInt(),   // top: near-black indigo
                0xFF1A1030.toInt(),   // middle: deep violet
                0xFF060610.toInt()    // bottom: almost black
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), sky)

        // ----------------------------------------------------
        //  Layer 2: moon halo (soft radial glow, upper-right)
        // ----------------------------------------------------
        val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        moonPaint.shader = RadialGradient(
            w * 0.75f, h * 0.30f, h * 0.55f,
            intArrayOf(0x55A080FF.toInt(), 0x00000000),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * 0.75f, h * 0.30f, h * 0.55f, moonPaint)

        // Moon disc itself (smaller, brighter)
        val moonDisc = Paint(Paint.ANTI_ALIAS_FLAG)
        moonDisc.color = 0xFFE8E0FF.toInt()
        canvas.drawCircle(w * 0.75f, h * 0.28f, h * 0.055f, moonDisc)

        // ----------------------------------------------------
        //  Layer 3: ruins — jagged pillars along the bottom
        // ----------------------------------------------------
        val pillarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        pillarPaint.color = 0xFF05030C.toInt()

        val rnd = Random(42L)  // fixed seed → same ruins every run
        var x = 20f
        while (x < w) {
            val pillarWidth = 30f + rnd.nextFloat() * 70f
            val pillarHeight = h * (0.35f + rnd.nextFloat() * 0.40f)
            canvas.drawRect(
                x, h - pillarHeight,
                x + pillarWidth, h.toFloat(),
                pillarPaint
            )
            x += pillarWidth + 40f + rnd.nextFloat() * 80f
        }

        // ----------------------------------------------------
        //  Layer 4: ground fog band
        // ----------------------------------------------------
        val fog = Paint()
        fog.shader = LinearGradient(
            0f, h * 0.82f, 0f, h.toFloat(),
            intArrayOf(0x00000000, 0xFF0A0510.toInt(), 0xFF000000.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h * 0.82f, w.toFloat(), h.toFloat(), fog)

        val id = upload(bmp, "background($w×$h)")
        Log.d(TAG, "background() → $id")
        return id
    }

    /**
     * Creates the player character sprite: a silver knight
     * holding a sword, rendered as a flat vector illustration.
     *
     * The sprite is drawn in a 128×128 canvas with the
     * character's feet at the bottom edge, so the caller can
     * position it with `y = groundY`.
     *
     * @return GL texture ID.
     */
    fun knight(): Int {
        val s = 128
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // ================================================
        //  SWORD (behind the body, extends up-right)
        // ================================================
        val blade = Paint(Paint.ANTI_ALIAS_FLAG)
        blade.shader = LinearGradient(
            82f, 0f, 96f, 0f,
            intArrayOf(0xFFF4F8FF.toInt(), 0xFF9AB0D8.toInt()),
            null, Shader.TileMode.CLAMP
        )
        val bladePath = Path().apply {
            moveTo(84f, 6f)      // tip
            lineTo(94f, 20f)     // right shoulder
            lineTo(94f, 64f)     // right base
            lineTo(84f, 64f)     // left base
            lineTo(84f, 20f)     // left shoulder
            close()
        }
        canvas.drawPath(bladePath, blade)

        // Blade center ridge (highlight)
        val ridge = Paint(Paint.ANTI_ALIAS_FLAG)
        ridge.color = 0xFFFFFFFF.toInt()
        canvas.drawRect(88.5f, 8f, 89.5f, 62f, ridge)

        // ================================================
        //  HILT (golden crossbar)
        // ================================================
        val hilt = Paint(Paint.ANTI_ALIAS_FLAG)
        hilt.shader = LinearGradient(
            76f, 62f, 100f, 68f,
            intArrayOf(0xFFE8C060.toInt(), 0xFF806018.toInt()),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(76f, 62f, 102f, 68f, hilt)

        // Handle
        canvas.drawRect(84f, 68f, 94f, 80f, hilt)

        // ================================================
        //  CLOAK (main body, silver gradient)
        // ================================================
        val body = Paint(Paint.ANTI_ALIAS_FLAG)
        body.shader = LinearGradient(
            40f, 40f, 92f, 124f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFC8D4FF.toInt(), 0xFF8A98C0.toInt()),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        val bodyPath = Path().apply {
            moveTo(48f, 46f)     // left shoulder
            lineTo(82f, 46f)     // right shoulder
            lineTo(94f, 88f)     // right hem
            lineTo(72f, 124f)    // bottom-right
            lineTo(56f, 124f)    // bottom-left
            lineTo(34f, 88f)     // left hem
            close()
        }
        canvas.drawPath(bodyPath, body)

        // Cloak fold lines (subtle)
        val fold = Paint(Paint.ANTI_ALIAS_FLAG)
        fold.color = 0x308080A0.toInt()
        fold.strokeWidth = 1.5f
        fold.style = Paint.Style.STROKE
        canvas.drawLine(64f, 50f, 60f, 120f, fold)
        canvas.drawLine(64f, 50f, 70f, 120f, fold)

        // ================================================
        //  HELMET (rounded silver cap)
        // ================================================
        val helm = Paint(Paint.ANTI_ALIAS_FLAG)
        helm.shader = LinearGradient(
            50f, 20f, 78f, 50f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFB0C0E8.toInt()),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(64f, 34f, 18f, helm)

        // Helmet bottom flat edge
        canvas.drawRect(46f, 40f, 82f, 48f, helm)

        // ================================================
        //  VISOR (dark slit)
        // ================================================
        val visor = Paint(Paint.ANTI_ALIAS_FLAG)
        visor.color = 0xFF1A1020.toInt()
        canvas.drawRect(52f, 30f, 76f, 37f, visor)

        // Glowing eyes
        val eye = Paint(Paint.ANTI_ALIAS_FLAG)
        eye.color = 0xFF64D0FF.toInt()
        canvas.drawCircle(58f, 33.5f, 2.3f, eye)
        canvas.drawCircle(70f, 33.5f, 2.3f, eye)

        // ================================================
        //  SHOULDER PAULDRON (small highlight)
        // ================================================
        val pauldron = Paint(Paint.ANTI_ALIAS_FLAG)
        pauldron.color = 0xFFD0D8F8.toInt()
        canvas.drawCircle(48f, 50f, 8f, pauldron)
        canvas.drawCircle(80f, 50f, 8f, pauldron)

        val id = upload(bmp, "knight")
        Log.d(TAG, "knight() → $id")
        return id
    }

    /**
     * Creates the boss sprite: a horned shadow wraith with
     * glowing red eyes, rendered as a flat vector illustration.
     *
     * The sprite is drawn in a 192×192 canvas with the
     * character's feet at the bottom edge.
     *
     * @return GL texture ID.
     */
    fun boss(): Int {
        val s = 192
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // ================================================
        //  BODY (dark violet gradient, tapered silhouette)
        // ================================================
        val body = Paint(Paint.ANTI_ALIAS_FLAG)
        body.shader = LinearGradient(
            60f, 60f, 130f, 190f,
            intArrayOf(0xFF2A1038.toInt(), 0xFF120618.toInt(), 0xFF05030A.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        val bodyPath = Path().apply {
            moveTo(96f, 18f)      // top of head
            lineTo(150f, 90f)     // right shoulder
            lineTo(140f, 178f)    // right foot
            lineTo(52f, 178f)     // left foot
            lineTo(42f, 90f)      // left shoulder
            close()
        }
        canvas.drawPath(bodyPath, body)

        // ================================================
        //  HORNS (blood-red, curving outward and up)
        // ================================================
        val horn = Paint(Paint.ANTI_ALIAS_FLAG)
        horn.shader = LinearGradient(
            20f, 4f, 60f, 40f,
            intArrayOf(0xFF8A2030.toInt(), 0xFF4A0A18.toInt()),
            null, Shader.TileMode.CLAMP
        )

        // Left horn
        val leftHorn = Path().apply {
            moveTo(58f, 42f)
            lineTo(14f, 6f)
            lineTo(22f, 24f)
            lineTo(38f, 36f)
            close()
        }
        canvas.drawPath(leftHorn, horn)

        // Right horn
        val rightHorn = Path().apply {
            moveTo(134f, 42f)
            lineTo(178f, 6f)
            lineTo(170f, 24f)
            lineTo(154f, 36f)
            close()
        }
        canvas.drawPath(rightHorn, horn)

        // ================================================
        //  EYE GLOW (large soft red halo)
        // ================================================
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        glow.shader = RadialGradient(
            96f, 88f, 64f,
            intArrayOf(0xAAFF2030.toInt(), 0x00FF2030),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(96f, 88f, 64f, glow)

        // ================================================
        //  EYES (bright red dots with white centers)
        // ================================================
        val eye = Paint(Paint.ANTI_ALIAS_FLAG)
        eye.color = 0xFFFF3040.toInt()
        canvas.drawCircle(78f, 88f, 5.5f, eye)
        canvas.drawCircle(114f, 88f, 5.5f, eye)

        val eyeCore = Paint(Paint.ANTI_ALIAS_FLAG)
        eyeCore.color = 0xFFFFE0E0.toInt()
        canvas.drawCircle(78f, 88f, 1.8f, eyeCore)
        canvas.drawCircle(114f, 88f, 1.8f, eyeCore)

        // ================================================
        //  CHEST RUNE (glowing red sigil)
        // ================================================
        val rune = Paint(Paint.ANTI_ALIAS_FLAG)
        rune.color = 0xCCFF2040.toInt()
        val runePath = Path().apply {
            moveTo(96f, 120f)
            lineTo(104f, 132f)
            lineTo(96f, 144f)
            lineTo(88f, 132f)
            close()
        }
        canvas.drawPath(runePath, rune)

        val runeCore = Paint(Paint.ANTI_ALIAS_FLAG)
        runeCore.color = 0xFFFFE0E0.toInt()
        val runeCorePath = Path().apply {
            moveTo(96f, 124f)
            lineTo(101f, 132f)
            lineTo(96f, 140f)
            lineTo(91f, 132f)
            close()
        }
        canvas.drawPath(runeCorePath, runeCore)

        val id = upload(bmp, "boss")
        Log.d(TAG, "boss() → $id")
        return id
    }

    /**
     * Creates a glowing projectile orb.
     *
     * The orb has a bright white core, a saturated color ring,
     * and a soft transparent halo. This is a "one texture fits
     * all projectiles" approach — the caller can tint it via
     * [SpriteBatch.draw]'s color parameter.
     *
     * @return GL texture ID.
     */
    fun orb(): Int {
        val s = 64
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            s / 2f, s / 2f, s / 2f,
            intArrayOf(
                0xFFFFFFFF.toInt(),   // core: white
                0xFFFF3050.toInt(),   // inner: red
                0x80FF2030.toInt(),   // mid: dark red
                0x00FF2030            // outer: transparent
            ),
            floatArrayOf(0f, 0.35f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(s / 2f, s / 2f, s / 2f, paint)

        val id = upload(bmp, "orb")
        Log.d(TAG, "orb() → $id")
        return id
    }

    /**
     * Creates a small square "spark" texture with a bright
     * center and fading edges. Used for blade trails, hit
     * sparks, and dash streaks.
     *
     * @return GL texture ID.
     */
    fun spark(): Int {
        val s = 32
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            s / 2f, s / 2f, s / 2f,
            intArrayOf(
                0xFFFFFFFF.toInt(),
                0xC0C0E8FF.toInt(),
                0x0080B0FF
            ),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(s / 2f, s / 2f, s / 2f, paint)

        val id = upload(bmp, "spark")
        Log.d(TAG, "spark() → $id")
        return id
    }

    /**
     * Creates a horizontal "shockwave" ring. When scaled and
     * faded, this reads as a ground-slam impact.
     *
     * @return GL texture ID.
     */
    fun shockwave(): Int {
        val w = 128
        val h = 64
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val ring = Paint(Paint.ANTI_ALIAS_FLAG)
        ring.shader = RadialGradient(
            w / 2f, h / 2f, w / 2f,
            intArrayOf(
                0x00FF8040,
                0x00FF8040,
                0xCCFF6020.toInt(),
                0x80FF4020.toInt(),
                0x00FF2020
            ),
            floatArrayOf(0f, 0.55f, 0.72f, 0.88f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w / 2f, h / 2f, w / 2f, ring)

        val id = upload(bmp, "shockwave")
        Log.d(TAG, "shockwave() → $id")
        return id
    }

    /**
     * Creates a soft vertical light beam used for telegraph
     * indicators (e.g., where the boss is about to slam).
     *
     * @return GL texture ID.
     */
    fun telegraphBeam(): Int {
        val w = 128
        val h = 32
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val beam = Paint()
        beam.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                0x00FF2040,
                0xCCFF2040.toInt(),
                0xCCFF2040.toInt(),
                0x00FF2040
            ),
            floatArrayOf(0f, 0.4f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), beam)

        val id = upload(bmp, "telegraphBeam")
        Log.d(TAG, "telegraphBeam() → $id")
        return id
    }

    // ========================================================
    //  Core Upload
    // ========================================================

    /**
     * Uploads a [Bitmap] to the GPU as an OpenGL ES 2.0 texture.
     *
     * Filtering and wrapping:
     *    • MIN/MAG filter = GL_LINEAR
     *        Smooth scaling — correct for all our textures.
     *        Mipmaps are not used (they add ~33% to texture size
     *        and are only needed when textures are viewed at
     *        large distances, which does not happen in a 2D game).
     *    • WRAP_S/WRAP_T = GL_CLAMP_TO_EDGE
     *        Prevents sampling artifacts at the edges. This is
     *        the correct choice for non-tiling 2D sprites. For
     *        tiling textures (backgrounds), switch to GL_REPEAT.
     *
     * The Bitmap is recycled immediately after upload to free
     * its heap memory. On devices with limited heap, holding
     * dozens of Bitmaps alive simultaneously would risk an
     * OutOfMemoryError.
     *
     * @param bitmap The source bitmap. Will be recycled.
     * @param name   Human-readable name for logs.
     * @return GL texture ID, or 0 on failure.
     */
    private fun upload(bitmap: Bitmap, name: String): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)

        if (ids[0] == 0) {
            Log.e(TAG, "glGenTextures failed for '$name'")
            bitmap.recycle()
            return 0
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])

        // Filtering
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )

        // Wrapping
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )

        // Upload
        try {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        } catch (t: Throwable) {
            Log.e(TAG, "texImage2D failed for '$name'", t)
            GLES20.glDeleteTextures(1, ids, 0)
            bitmap.recycle()
            return 0
        }

        // Check for GL errors.
        val err = GLES20.glGetError()
        if (err != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "GL error after upload of '$name': 0x${err.toString(16)}")
            GLES20.glDeleteTextures(1, ids, 0)
            bitmap.recycle()
            return 0
        }

        // Free CPU-side memory immediately.
        bitmap.recycle()

        return ids[0]
    }

    // ========================================================
    //  Bulk Generation
    // ========================================================

    /**
     * A container holding every texture the game needs.
     * Populated once at startup by [generateAll].
     */
    class TextureSet(
        @JvmField val white: Int,
        @JvmField val soft: Int,
        @JvmField val background: Int,
        @JvmField val knight: Int,
        @JvmField val boss: Int,
        @JvmField val orb: Int,
        @JvmField val spark: Int,
        @JvmField val shockwave: Int,
        @JvmField val telegraphBeam: Int
    ) {
        /**
         * Deletes all textures from the GPU. Called on shutdown.
         */
        fun dispose() {
            val ids = intArrayOf(
                white, soft, background, knight, boss,
                orb, spark, shockwave, telegraphBeam
            )
            val nonZero = ids.filter { it != 0 }
            if (nonZero.isNotEmpty()) {
                val arr = nonZero.toIntArray()
                GLES20.glDeleteTextures(arr.size, arr, 0)
                Log.d(TAG, "Disposed ${arr.size} textures")
            }
        }
    }

    /**
     * Generates every texture the game needs and returns them
     * as a [TextureSet].
     *
     * Must be called from the GL thread.
     *
     * @return A fully-populated [TextureSet].
     */
    fun generateAll(): TextureSet {
        Log.i(TAG, "Generating all textures…")
        val start = System.nanoTime()

        val set = TextureSet(
            white = white(),
            soft = softCircle(64, 0xFFFFFFFF.toInt(), 0x00FFFFFF),
            background = background(1024, 512),
            knight = knight(),
            boss = boss(),
            orb = orb(),
            spark = spark(),
            shockwave = shockwave(),
            telegraphBeam = telegraphBeam()
        )

        val elapsed = (System.nanoTime() - start) / 1_000_000f
        Log.i(TAG, "All textures generated in ${"%.1f".format(elapsed)} ms")

        return set
    }

    // ========================================================
    //  Diagnostics
    // ========================================================

    /**
     * Returns a human-readable description of the given texture
     * ID, suitable for debug overlays.
     */
    fun describe(id: Int): String {
        return when {
            id == 0 -> "invalid(0)"
            id < 0 -> "negative($id)"
            else -> "tex($id)"
        }
    }
}
