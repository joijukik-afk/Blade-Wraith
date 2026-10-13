package com.bladewraith.engine

import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * ============================================================
 *  Blade Wraith — Batched Sprite Renderer
 *
 *  Collects sprite draw calls into a single vertex buffer and
 *  flushes them to the GPU in one draw call per texture.
 *
 *  This is the single most important performance optimization
 *  in the entire engine. Without batching, drawing 200 particles
 *  would issue 200 separate glDrawElements calls, each with its
 *  own driver overhead — easily 5+ ms per frame on mid-range
 *  mobile GPUs. With batching, all 200 go out in one call,
 *  typically under 0.5 ms.
 *
 *  Vertex Format:
 *    Each vertex is 8 floats:
 *      [x, y, u, v, r, g, b, a]
 *    where:
 *      x, y   = position in world space (pixels, Y-up)
 *      u, v   = texture coordinate (0..1)
 *      r,g,b,a = per-vertex color and alpha (0..1)
 *
 *    Each sprite uses 4 vertices (BL, BR, TR, TL) and 6
 *    indices (two triangles: BL-BR-TR and BL-TR-TL).
 *
 *  Index Buffer:
 *    A static index buffer is built once in the constructor.
 *    It contains the pattern `[0,1,2, 0,2,3]` repeated for
 *    every sprite slot. It is uploaded once and reused forever.
 *
 *  Flushing:
 *    Flush is triggered automatically when:
 *      • The texture changes between sprites.
 *      • The vertex buffer reaches its maximum capacity.
 *      • [end] is called.
 *
 *    The texture-change trigger means we may issue several
 *    draw calls per frame if the frame uses multiple textures
 *    (background, characters, particles, orbs, UI). In
 *    practice this is 3–5 draw calls per frame total, which
 *    is negligible.
 *
 *  Threading:
 *    All methods MUST be called from the GL thread.
 *
 *  References:
 *    • Android Developers — "Draw shapes with OpenGL ES"
 *      https://developer.android.com/develop/ui/views/graphics/opengl/draw
 *    • Realtime Rendering (4th ed.), ch. 18 — "Acceleration
 *      Algorithms" (batching)
 *    • "Batching and Instancing" — OpenGL Wiki
 * ============================================================
 */
class SpriteBatch(
    /**
     * Maximum number of sprites that can be in-flight before an
     * automatic flush is forced. A value of 4096 is comfortably
     * above the worst-case sprite count in a 60 Hz frame
     * (particles + characters + UI), while keeping the vertex
     * buffer at a sane size:
     *
     *   4096 sprites × 4 vertices × 8 floats × 4 bytes = 512 KB
     *   4096 sprites × 6 indices × 2 bytes           =  48 KB
     *
     * Total GPU-side buffer: ~560 KB — trivial for any device.
     */
    private val maxSprites: Int = 4096
) {

    // ========================================================
    //  Companion
    // ========================================================
    private companion object {
        const val TAG = "SpriteBatch"

        /**
         * Number of floats per vertex:
         *   x, y, u, v, r, g, b, a = 8
         */
        const val FLOATS_PER_VERTEX = 8

        /**
         * Bytes per vertex (8 floats × 4 bytes).
         */
        const val BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4

        /**
         * Vertices per sprite (quad corners).
         */
        const val VERTICES_PER_SPRITE = 4

        /**
         * Indices per sprite (two triangles).
         */
        const val INDICES_PER_SPRITE = 6

        /**
         * Floats per sprite (4 vertices × 8 floats).
         */
        const val FLOATS_PER_SPRITE = VERTICES_PER_SPRITE * FLOATS_PER_VERTEX

        /**
         * Bytes per sprite's vertex data.
         */
        const val BYTES_PER_SPRITE = FLOATS_PER_SPRITE * 4

        /**
         * Default color: opaque white (used when a caller does
         * not specify a color).
         */
        const val DEFAULT_COLOR = 0xFFFFFFFF.toInt()
    }

    // ========================================================
    //  Vertex Buffer (Direct-Allocated)
    // ========================================================

    /**
     * The backing float array. We write into this from the CPU
     * side, then copy a slice into [vertexBuffer] at flush time.
     *
     * Using a plain FloatArray (not a FloatBuffer) for the
     * CPU-side scratch pad is significantly faster because
     * reads/writes avoid the ByteBuffer indirection.
     */
    private val vertexData = FloatArray(maxSprites * FLOATS_PER_SPRITE)

    /**
     * The direct-allocated FloatBuffer that the GPU reads from.
     * Direct allocation is REQUIRED for OpenGL on Android —
     * non-direct buffers will either throw or incur a costly
     * copy on every upload.
     */
    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(vertexData.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    // ========================================================
    //  Index Buffer (Built Once, Reused Forever)
    // ========================================================

    /**
     * The static index data. Built in [init] using the pattern:
     *   sprite i: vertices [4i, 4i+1, 4i+2, 4i+3]
     *   indices:  [4i, 4i+1, 4i+2,  4i, 4i+2, 4i+3]
     */
    private val indexData = ShortArray(maxSprites * INDICES_PER_SPRITE)

    /**
     * Direct-allocated index buffer.
     */
    private val indexBuffer: ShortBuffer = ByteBuffer
        .allocateDirect(indexData.size * 2)
        .order(ByteOrder.nativeOrder())
        .asShortBuffer()

    // ========================================================
    //  Runtime State
    // ========================================================

    /**
     * Number of sprites currently queued in [vertexData].
     * Incremented by [draw], reset to 0 by [flush].
     */
    private var spriteCount = 0

    /**
     * The texture ID currently bound. -1 means "no texture
     * bound yet" — the first [draw] with a real texture will
     * not trigger a flush.
     */
    private var currentTexture: Int = -1

    /**
     * True between [begin] and [end]. Drawing outside this
     * window is a no-op, which protects against accidental
     * use (e.g., calling [draw] before [begin]).
     */
    private var drawing: Boolean = false

    /**
     * Reusable scratch array for pre-computing rotated corner
     * positions. Avoids allocation in the hot path.
     */
    private val rotatedCorners = FloatArray(8)

    // ========================================================
    //  Initialization
    // ========================================================

    init {
        // ----------------------------------------------------
        //  Pre-build the static index buffer.
        //  This runs once, at construction time.
        // ----------------------------------------------------
        var i = 0
        while (i < maxSprites) {
            val v = (i * VERTICES_PER_SPRITE).toShort()
            val o = i * INDICES_PER_SPRITE

            // Triangle 1: BL → BR → TR
            indexData[o]     = v
            indexData[o + 1] = (v + 1).toShort()
            indexData[o + 2] = (v + 2).toShort()

            // Triangle 2: BL → TR → TL
            indexData[o + 3] = v
            indexData[o + 4] = (v + 2).toShort()
            indexData[o + 5] = (v + 3).toShort()

            i++
        }

        indexBuffer.clear()
        indexBuffer.put(indexData)
        indexBuffer.position(0)

        Log.d(TAG, "SpriteBatch created: maxSprites=$maxSprites, " +
                "vertexBuffer=${vertexData.size * 4 / 1024} KB, " +
                "indexBuffer=${indexData.size * 2 / 1024} KB")
    }

    // ========================================================
    //  Begin / End
    // ========================================================

    /**
     * Starts a new batch.
     *
     * Resets the internal counters and binds the sprite shader
     * with the given MVP matrix.
     *
     * Must be paired with exactly one [end] call.
     *
     * @param mvp The model-view-projection matrix to use for
     *            this batch. Typically [Camera2D.mvp] for world
     *            rendering, or [Camera2D.uiMvp] for HUD.
     */
    fun begin(mvp: FloatArray) {
        if (drawing) {
            Log.w(TAG, "begin() called while already drawing — " +
                    "forcing a flush of the previous batch")
            flush()
        }

        spriteCount = 0
        currentTexture = -1
        drawing = true

        val prog = ShaderCache.sprite
        if (!prog.isValid) {
            // Shader failed to compile — disable drawing so
            // the rest of the frame is a harmless no-op.
            drawing = false
            return
        }

        prog.use()
        prog.setMVP(mvp)

        // Ensure blending is enabled. Some drivers reset state
        // unexpectedly; setting it here guarantees consistent
        // alpha blending for every batch.
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
    }

    /**
     * Ends the current batch, flushing any queued sprites.
     */
    fun end() {
        if (!drawing) return
        flush()
        drawing = false
    }

    // ========================================================
    //  Draw — Plain Quad
    // ========================================================

    /**
     * Queues an axis-aligned textured quad.
     *
     * @param x       Left edge, in world pixels.
     * @param y       Bottom edge, in world pixels.
     * @param w       Width, in pixels.
     * @param h       Height, in pixels.
     * @param color   ARGB color (0xAARRGGBB). Use
     *                [DEFAULT_COLOR] for opaque white.
     * @param texture The GL texture ID to sample from. Must be
     *                non-zero; 0 means "no texture" and the
     *                sprite is skipped.
     * @param u0,v0   Top-left UV coordinate (default 0, 0).
     * @param u1,v1   Bottom-right UV coordinate (default 1, 1).
     */
    fun draw(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color: Int = DEFAULT_COLOR,
        texture: Int = 0,
        u0: Float = 0f,
        v0: Float = 0f,
        u1: Float = 1f,
        v1: Float = 1f
    ) {
        if (!drawing) return
        if (texture == 0) return
        if (w <= 0f || h <= 0f) return

        // ----------------------------------------------------
        //  Texture change → flush the previous batch.
        // ----------------------------------------------------
        if (texture != currentTexture) {
            flush()
            currentTexture = texture
        }

        // ----------------------------------------------------
        //  Capacity check → flush if full.
        // ----------------------------------------------------
        if (spriteCount >= maxSprites) {
            flush()
        }

        // ----------------------------------------------------
        //  Unpack the ARGB color into normalized floats.
        // ----------------------------------------------------
        val a = ((color ushr 24) and 0xFF) / 255f
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f

        // ----------------------------------------------------
        //  Compute the four corner positions.
        //
        //  The order matters: we write them in the same order
        //  as the index buffer pattern (BL, BR, TR, TL).
        //
        //  UV mapping:
        //    v0 corresponds to the TOP of the texture in image
        //    space. Because our coordinate system is Y-up,
        //    the bottom vertices use v1 and the top vertices
        //    use v0.
        // ----------------------------------------------------
        val x0 = x
        val y0 = y
        val x1 = x + w
        val y1 = y + h

        // ----------------------------------------------------
        //  Write the four vertices into the scratch array.
        // ----------------------------------------------------
        var i = spriteCount * FLOATS_PER_SPRITE

        // Bottom-left
        vertexData[i++] = x0
        vertexData[i++] = y0
        vertexData[i++] = u0
        vertexData[i++] = v1
        vertexData[i++] = r
        vertexData[i++] = g
        vertexData[i++] = b
        vertexData[i++] = a

        // Bottom-right
        vertexData[i++] = x1
        vertexData[i++] = y0
        vertexData[i++] = u1
        vertexData[i++] = v1
        vertexData[i++] = r
        vertexData[i++] = g
        vertexData[i++] = b
        vertexData[i++] = a

        // Top-right
        vertexData[i++] = x1
        vertexData[i++] = y1
        vertexData[i++] = u1
        vertexData[i++] = v0
        vertexData[i++] = r
        vertexData[i++] = g
        vertexData[i++] = b
        vertexData[i++] = a

        // Top-left
        vertexData[i++] = x0
        vertexData[i++] = y1
        vertexData[i++] = u0
        vertexData[i++] = v0
        vertexData[i++] = r
        vertexData[i++] = g
        vertexData[i++] = b
        vertexData[i]     = a

        spriteCount++
    }

    // ========================================================
    //  Draw — Rotated Quad
    // ========================================================

    /**
     * Queues a textured quad rotated around a pivot point.
     *
     * The pivot is specified in normalized coordinates relative
     * to the quad's bounding box:
     *    (0, 0) = bottom-left
     *    (1, 1) = top-right
     *    (0.5, 0.5) = center
     *
     * This is the version used by:
     *    • Player sword swing (pivot at chest, rotation sweeps)
     *    • Any future weapon trail effect
     *
     * @param pivotU Normalized pivot X in [0, 1].
     * @param pivotV Normalized pivot Y in [0, 1].
     * @param angleDeg Rotation in degrees, clockwise.
     */
    fun drawRotated(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        angleDeg: Float,
        color: Int = DEFAULT_COLOR,
        texture: Int = 0,
        u0: Float = 0f,
        v0: Float = 0f,
        u1: Float = 1f,
        v1: Float = 1f,
        pivotU: Float = 0.5f,
        pivotV: Float = 0.5f
    ) {
        if (!drawing) return
        if (texture == 0) return
        if (w <= 0f || h <= 0f) return

        // ----------------------------------------------------
        //  Fast path: no rotation.
        // ----------------------------------------------------
        if (angleDeg == 0f) {
            draw(x, y, w, h, color, texture, u0, v0, u1, v1)
            return
        }

        // ----------------------------------------------------
        //  Texture change → flush.
        // ----------------------------------------------------
        if (texture != currentTexture) {
            flush()
            currentTexture = texture
        }

        // ----------------------------------------------------
        //  Capacity check.
        // ----------------------------------------------------
        if (spriteCount >= maxSprites) {
            flush()
        }

        // ----------------------------------------------------
        //  Unpack color.
        // ----------------------------------------------------
        val a = ((color ushr 24) and 0xFF) / 255f
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f

        // ----------------------------------------------------
        //  Compute rotation around the pivot.
        // ----------------------------------------------------
        val pivotX = x + w * pivotU
        val pivotY = y + h * pivotV

        val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)

        // Rotate a point around the pivot.
        // Note: we use the clockwise rotation matrix here so
        // that positive angles look "clockwise" on screen,
        // matching the intuition of a sword swing.
        fun rotX(px: Float, py: Float): Float =
            pivotX + (px - pivotX) * cosA - (py - pivotY) * sinA

        fun rotY(px: Float, py: Float): Float =
            pivotY + (px - pivotX) * sinA + (py - pivotY) * cosA

        val x0 = x
        val y0 = y
        val x1 = x + w
        val y1 = y + h

        // Pre-rotate the four corners.
        rotatedCorners[0] = rotX(x0, y0); rotatedCorners[1] = rotY(x0, y0)
        rotatedCorners[2] = rotX(x1, y0); rotatedCorners[3] = rotY(x1, y0)
        rotatedCorners[4] = rotX(x1, y1); rotatedCorners[5] = rotY(x1, y1)
        rotatedCorners[6] = rotX(x0, y1); rotatedCorners[7] = rotY(x0, y1)

        // ----------------------------------------------------
        //  Write the four vertices.
        // ----------------------------------------------------
        var i = spriteCount * FLOATS_PER_SPRITE

        // Bottom-left
        vertexData[i++] = rotatedCorners[0]
        vertexData[i++] = rotatedCorners[1]
        vertexData[i++] = u0
        vertexData[i++] = v1
        vertexData[i++] = r
        vertexData[i++] = g
        vertexData[i++] = b
        vertexData[i++] = a

        // Bottom-right
        vertexData[i++] = rotatedCorners[2]
        vertexData[i++] = rotatedCorners[3]
        vertexData[i++] = u1
        vertexData[i++] = v1
        vertexData[i++] = r
        vertexData[i++] = g
        vertexData[i++] = b
        vertexData[i++] = a

        // Top-right
        vertexData[i++] = rotatedCorners[4]
        vertexData[i++] = rotatedCorners[5]
        vertexData[i++] = u1
        vertexData[i++] = v0
        vertexData[i++] = r
        vertexData[i++] = g
        vertexData[i++] = b
        vertexData[i++] = a

        // Top-left
        vertexData[i++] = rotatedCorners[6]
        vertexData[i++] = rotatedCorners[7]
        vertexData[i++] = u0
        vertexData[i++] = v0
        vertexData[i++] = r
        vertexData[i++] = g
        vertexData[i++] = b
        vertexData[i]     = a

        spriteCount++
    }

    // ========================================================
    //  Draw — Convenience: Center-Aligned
    // ========================================================

    /**
     * Queues a quad centered on `(cx, cy)`.
     *
     * Many game objects (projectiles, particles) are naturally
     * expressed as "at this point, draw a circle of this size".
     * This helper saves the caller from doing the half-width
     * subtraction.
     */
    fun drawCentered(
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        color: Int = DEFAULT_COLOR,
        texture: Int = 0,
        u0: Float = 0f,
        v0: Float = 0f,
        u1: Float = 1f,
        v1: Float = 1f
    ) {
        draw(cx - w * 0.5f, cy - h * 0.5f, w, h, color, texture, u0, v0, u1, v1)
    }

    // ========================================================
    //  Draw — Convenience: Flipped
    // ========================================================

    /**
     * Queues a quad, optionally flipping horizontally and/or
     * vertically.
     *
     * Flipping is achieved by swapping the UV coordinates,
     * which costs nothing — the vertices themselves are
     * unchanged.
     */
    fun drawFlipped(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        flipH: Boolean,
        flipV: Boolean,
        color: Int = DEFAULT_COLOR,
        texture: Int = 0
    ) {
        val u0 = if (flipH) 1f else 0f
        val u1 = if (flipH) 0f else 1f
        val v0 = if (flipV) 1f else 0f
        val v1 = if (flipV) 0f else 1f
        draw(x, y, w, h, color, texture, u0, v0, u1, v1)
    }

    // ========================================================
    //  Draw — Convenience: Colored Rectangle (No Texture)
    // ========================================================

    /**
     * Queues a flat-colored rectangle with no texture.
     *
     * Internally this uses the sprite program with a 1×1 white
     * texture bound. Because the caller must pass a texture ID,
     * this method does NOT auto-bind a white texture — the
     * caller should pass the engine's 1×1 white texture ID.
     *
     * Using the sprite program (rather than [SolidProgram]) for
     * solid rectangles keeps every UI draw in the same batch,
     * which is faster than switching programs mid-frame.
     */
    fun drawRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color: Int,
        whiteTexture: Int
    ) {
        draw(x, y, w, h, color, whiteTexture, 0f, 0f, 1f, 1f)
    }

    // ========================================================
    //  Flush — The Hot Path
    // ========================================================

    /**
     * Uploads all queued sprites to the GPU and issues a single
     * glDrawElements call.
     *
     * Called automatically by [draw] on texture change or
     * buffer overflow, and by [end] at the end of a batch.
     * Calling it manually is legal but rarely necessary.
     */
    fun flush() {
        if (spriteCount == 0) return

        val prog = ShaderCache.sprite
        if (!prog.isValid || prog.programId == 0) {
            // Shader is invalid — discard the batch silently.
            spriteCount = 0
            return
        }

        // ----------------------------------------------------
        //  Upload vertex data to the direct buffer.
        // ----------------------------------------------------
        vertexBuffer.clear()
        vertexBuffer.put(vertexData, 0, spriteCount * FLOATS_PER_SPRITE)
        vertexBuffer.position(0)

        // ----------------------------------------------------
        //  Bind the current texture.
        // ----------------------------------------------------
        prog.bindTexture(currentTexture)

        // ----------------------------------------------------
        //  Enable and configure the vertex attributes.
        //
        //  Layout in the vertex buffer:
        //    offset 0:  aPos   (vec2) — 8 bytes
        //    offset 8:  aUV    (vec2) — 8 bytes
        //    offset 16: aColor (vec4) — 16 bytes
        //    stride:    32 bytes
        // ----------------------------------------------------
        if (prog.posLocation >= 0) {
            GLES20.glEnableVertexAttribArray(prog.posLocation)
            vertexBuffer.position(0)
            GLES20.glVertexAttribPointer(
                prog.posLocation, 2, GLES20.GL_FLOAT, false,
                BYTES_PER_VERTEX, vertexBuffer
            )
        }

        if (prog.uvLocation >= 0) {
            GLES20.glEnableVertexAttribArray(prog.uvLocation)
            vertexBuffer.position(2)
            GLES20.glVertexAttribPointer(
                prog.uvLocation, 2, GLES20.GL_FLOAT, false,
                BYTES_PER_VERTEX, vertexBuffer
            )
        }

        if (prog.colorLocation >= 0) {
            GLES20.glEnableVertexAttribArray(prog.colorLocation)
            vertexBuffer.position(4)
            GLES20.glVertexAttribPointer(
                prog.colorLocation, 4, GLES20.GL_FLOAT, false,
                BYTES_PER_VERTEX, vertexBuffer
            )
        }

        // ----------------------------------------------------
        //  Issue the draw call.
        // ----------------------------------------------------
        indexBuffer.position(0)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            spriteCount * INDICES_PER_SPRITE,
            GLES20.GL_UNSIGNED_SHORT,
            indexBuffer
        )

        // ----------------------------------------------------
        //  Disable the attributes we enabled. This is not
        //  strictly required, but doing so keeps the GL state
        //  clean and prevents subtle bugs if another renderer
        //  runs later in the same frame.
        // ----------------------------------------------------
        if (prog.posLocation >= 0) {
            GLES20.glDisableVertexAttribArray(prog.posLocation)
        }
        if (prog.uvLocation >= 0) {
            GLES20.glDisableVertexAttribArray(prog.uvLocation)
        }
        if (prog.colorLocation >= 0) {
            GLES20.glDisableVertexAttribArray(prog.colorLocation)
        }

        // ----------------------------------------------------
        //  Reset the sprite counter. Note that we do NOT reset
        //  currentTexture — the next batch may reuse the same
        //  texture, and keeping it avoids a redundant flush.
        // ----------------------------------------------------
        spriteCount = 0
    }

    // ========================================================
    //  Diagnostics
    // ========================================================

    /**
     * Returns the number of sprites currently queued but not
     * yet flushed. Useful for debug overlays.
     */
    fun queuedSpriteCount(): Int = spriteCount

    /**
     * Returns the maximum number of sprites this batch supports.
     */
    fun capacity(): Int = maxSprites

    /**
     * Returns a human-readable snapshot of the batch state.
     */
    fun debugString(): String {
        return "SpriteBatch[" +
                "queued=$spriteCount/$maxSprites, " +
                "texture=$currentTexture, " +
                "drawing=$drawing" +
                "]"
    }
}
