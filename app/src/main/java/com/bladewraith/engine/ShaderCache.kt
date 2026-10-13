package com.bladewraith.engine

import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer

/**
 * ============================================================
 *  Blade Wraith — Shader Cache & GLSL Program Management
 *
 *  Compiles, links, and caches the GLSL ES 1.00 shader programs
 *  used by the engine. All OpenGL shader handling flows through
 *  this file — game code never touches raw GL calls directly.
 *
 *  Programs provided:
 *
 *    1. **SpriteProgram**
 *       Textured, colored quad rendering with MVP transform.
 *       Used for: background, characters, particles, orbs, HUD,
 *                 touch controls — literally everything drawn.
 *
 *    2. **SolidProgram**
 *       Flat-color quad rendering (no texture). Used for: solid
 *       rectangles in the HUD (health bar fills, borders).
 *
 *  Error Handling Philosophy:
 *    This class NEVER crashes the app on shader failure. If a
 *    shader fails to compile or a program fails to link, we:
 *      1. Log the full GLSL compiler/linker error to Logcat.
 *      2. Mark the program as invalid (program ID = 0).
 *      3. Continue running. Renders using that program become
 *         no-ops, which is far better than a hard crash.
 *
 *    The previous version of this file used `error(...)` in the
 *    compile/link path, which is why the app crashed on launch
 *    on devices with strict GLSL compilers. That has been fixed.
 *
 *  Caching:
 *    Programs are compiled exactly once, at first use. The
 *    [initialize] method builds both programs and stores their
 *    IDs. Subsequent frames reuse the cached IDs.
 *
 *  Threading:
 *    All methods MUST be called from the GL thread. GL calls are
 *    only valid on the thread that owns the EGL context.
 *
 *  References:
 *    • Khronos — "OpenGL ES 2.0 Reference: glCreateProgram"
 *      https://www.khronos.org/registry/OpenGL-Refpages/es2.0/
 *    • Khronos — "GLSL ES 1.00 Specification"
 *      https://www.khronos.org/files/opengles_shading_language.pdf
 *    • Android Developers — "Displaying graphics with OpenGL ES"
 *      https://developer.android.com/develop/ui/views/graphics/opengl
 * ============================================================
 */
object ShaderCache {

    // ========================================================
    //  Companion
    // ========================================================
    private const val TAG = "ShaderCache"

    /**
     * Maximum length of a shader info log, in characters.
     * The OpenGL spec guarantees at least 512 bytes; we use a
     * generous value to capture full error messages on verbose
     * compilers.
     */
    private const val MAX_LOG_LENGTH = 4096

    // ========================================================
    //  Public Programs
    // ========================================================

    /**
     * The sprite program. Valid after [initialize] returns.
     *
     * If shader compilation failed, [SpriteProgram.isValid]
     * returns false and all [SpriteProgram.use] calls become
     * no-ops.
     */
    @JvmField
    var sprite: SpriteProgram = SpriteProgram()

    /**
     * The solid-color program.
     */
    @JvmField
    var solid: SolidProgram = SolidProgram()

    /**
     * True if the cache has been initialized and both programs
     * compiled successfully.
     */
    @JvmField
    var initialized: Boolean = false
        private set

    // ========================================================
    //  Initialization
    // ========================================================

    /**
     * Compiles and links all shader programs.
     *
     * MUST be called from the GL thread, after the EGL context
     * is created — typically from
     * [GLSurfaceView.Renderer.onSurfaceCreated].
     *
     * Safe to call multiple times. If the EGL context is
     * destroyed and recreated (e.g., after [onSurfaceCreated]
     * fires again after a context loss), this method can be
     * called again to rebuild the programs.
     */
    fun initialize() {
        Log.i(TAG, "Initializing shader cache")

        val startNanos = System.nanoTime()

        // ----------------------------------------------------
        //  Compile the sprite program
        // ----------------------------------------------------
        sprite = SpriteProgram()
        sprite.compile()

        // ----------------------------------------------------
        //  Compile the solid-color program
        // ----------------------------------------------------
        solid = SolidProgram()
        solid.compile()

        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000f

        initialized = sprite.isValid && solid.isValid

        if (initialized) {
            Log.i(TAG, "Shader cache ready in ${"%.2f".format(elapsedMs)} ms")
        } else {
            Log.e(
                TAG,
                "Shader cache FAILED to initialize. " +
                        "sprite.valid=${sprite.isValid}, " +
                        "solid.valid=${solid.isValid}"
            )
        }
    }

    /**
     * Releases all GPU resources held by the programs.
     *
     * Should be called when the EGL context is about to be
     * destroyed (e.g., in [Renderer.onSurfaceDestroyed], if
     * you implement it). On most devices this is not strictly
     * necessary because the GL driver reclaims resources when
     * the context dies, but it is good practice.
     */
    fun dispose() {
        Log.i(TAG, "Disposing shader cache")

        sprite.dispose()
        solid.dispose()

        initialized = false
    }

    // ========================================================
    //  Sprite Program
    // ========================================================

    /**
     * The textured sprite shader program.
     *
     * Vertex attributes:
     *    • aPos   (vec2) — position in world space
     *    • aUV    (vec2) — texture coordinate
     *    • aColor (vec4) — per-vertex tint (RGBA, premultiplied
     *                      by alpha via [SpriteBatch])
     *
     * Uniforms:
     *    • uMVP   (mat4)     — model-view-projection matrix
     *    • uTex   (sampler2D) — the texture to sample
     *
     * Output:
     *    gl_FragColor = texture(uTex, vUV) * vColor
     *
     * The multiplication with vColor enables:
     *    • Tinting (multiply by RGB)
     *    • Fading   (multiply by alpha)
     *    • Flash    (multiply by white with full alpha to boost)
     */
    class SpriteProgram internal constructor() {

        /** The GL program ID, or 0 if compilation failed. */
        var programId: Int = 0
            private set

        /** True if the program was compiled and linked successfully. */
        var isValid: Boolean = false
            private set

        // ----------------------------------------------------
        //  Attribute locations
        //  -1 means the attribute was optimized out by the
        //  compiler, which is fine — we just skip enabling it.
        // ----------------------------------------------------
        @JvmField var posLocation: Int = -1
        @JvmField var uvLocation: Int = -1
        @JvmField var colorLocation: Int = -1

        // ----------------------------------------------------
        //  Uniform locations
        // ----------------------------------------------------
        private var mvpLocation: Int = -1
        private var textureLocation: Int = -1

        /**
         * Compiles and links the program.
         */
        internal fun compile() {
            val vsSource = VERTEX_SHADER
            val fsSource = FRAGMENT_SHADER

            val vsId = compileShader(GLES20.GL_VERTEX_SHADER, vsSource, "Sprite.VS")
            val fsId = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSource, "Sprite.FS")

            if (vsId == 0 || fsId == 0) {
                // Free whichever one succeeded, if any.
                if (vsId != 0) GLES20.glDeleteShader(vsId)
                if (fsId != 0) GLES20.glDeleteShader(fsId)
                isValid = false
                programId = 0
                Log.e(TAG, "SpriteProgram: shader compilation failed")
                return
            }

            val pid = linkProgram(vsId, fsId, "Sprite")

            // Shaders can be deleted once the program is linked.
            GLES20.glDeleteShader(vsId)
            GLES20.glDeleteShader(fsId)

            if (pid == 0) {
                isValid = false
                programId = 0
                return
            }

            programId = pid
            isValid = true

            // Cache attribute locations.
            posLocation = GLES20.glGetAttribLocation(pid, "aPos")
            uvLocation = GLES20.glGetAttribLocation(pid, "aUV")
            colorLocation = GLES20.glGetAttribLocation(pid, "aColor")

            // Cache uniform locations.
            mvpLocation = GLES20.glGetUniformLocation(pid, "uMVP")
            textureLocation = GLES20.glGetUniformLocation(pid, "uTex")

            Log.d(
                TAG,
                "SpriteProgram linked OK: " +
                        "id=$pid, " +
                        "aPos=$posLocation, aUV=$uvLocation, aColor=$colorLocation, " +
                        "uMVP=$mvpLocation, uTex=$textureLocation"
            )
        }

        /**
         * Makes this program the active one for subsequent draw
         * calls. No-op if the program is invalid.
         */
        fun use() {
            if (!isValid) return
            GLES20.glUseProgram(programId)
        }

        /**
         * Uploads the MVP matrix to the shader.
         */
        fun setMVP(matrix: FloatArray) {
            if (!isValid || mvpLocation < 0) return
            GLES20.glUniformMatrix4fv(mvpLocation, 1, false, matrix, 0)
        }

        /**
         * Binds a texture to texture unit 0 and tells the shader
         * to sample from it.
         */
        fun bindTexture(textureId: Int) {
            if (!isValid) return

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

            if (textureLocation >= 0) {
                GLES20.glUniform1i(textureLocation, 0)
            }
        }

        /**
         * Deletes the GL program.
         */
        internal fun dispose() {
            if (programId != 0) {
                GLES20.glDeleteProgram(programId)
                programId = 0
            }
            isValid = false
        }

        // ------------------------------------------------
        //  GLSL source
        // ------------------------------------------------

        private companion object {
            /**
             * Vertex shader for sprites.
             *
             * Transforms each vertex by the MVP matrix and passes
             * the UV and color to the fragment stage.
             */
            val VERTEX_SHADER: String = """
                // Blade Wraith — Sprite Vertex Shader
                // GLSL ES 1.00
                
                attribute vec2 aPos;
                attribute vec2 aUV;
                attribute vec4 aColor;
                
                uniform mat4 uMVP;
                
                varying vec2 vUV;
                varying vec4 vColor;
                
                void main() {
                    vUV = aUV;
                    vColor = aColor;
                    gl_Position = uMVP * vec4(aPos, 0.0, 1.0);
                }
            """.trimIndent()

            /**
             * Fragment shader for sprites.
             *
             * Samples the texture and multiplies by the per-vertex
             * color (which carries tint and alpha).
             */
            val FRAGMENT_SHADER: String = """
                // Blade Wraith — Sprite Fragment Shader
                // GLSL ES 1.00
                precision mediump float;
                
                uniform sampler2D uTex;
                
                varying vec2 vUV;
                varying vec4 vColor;
                
                void main() {
                    vec4 texel = texture2D(uTex, vUV);
                    gl_FragColor = texel * vColor;
                }
            """.trimIndent()
        }
    }

    // ========================================================
    //  Solid Program
    // ========================================================

    /**
     * The solid-color shader program.
     *
     * Used for HUD bars, borders, and any rectangular fill that
     * does not need a texture. Avoiding the texture sample makes
     * this program slightly cheaper per fragment, though the
     * difference is small on modern GPUs.
     *
     * Vertex attributes:
     *    • aPos (vec2) — position
     *
     * Uniforms:
     *    • uMVP   (mat4) — MVP matrix
     *    • uColor (vec4) — flat color
     */
    class SolidProgram internal constructor() {

        var programId: Int = 0
            private set

        var isValid: Boolean = false
            private set

        @JvmField var posLocation: Int = -1

        private var mvpLocation: Int = -1
        private var colorLocation: Int = -1

        internal fun compile() {
            val vsId = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER, "Solid.VS")
            val fsId = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER, "Solid.FS")

            if (vsId == 0 || fsId == 0) {
                if (vsId != 0) GLES20.glDeleteShader(vsId)
                if (fsId != 0) GLES20.glDeleteShader(fsId)
                isValid = false
                programId = 0
                Log.e(TAG, "SolidProgram: shader compilation failed")
                return
            }

            val pid = linkProgram(vsId, fsId, "Solid")

            GLES20.glDeleteShader(vsId)
            GLES20.glDeleteShader(fsId)

            if (pid == 0) {
                isValid = false
                programId = 0
                return
            }

            programId = pid
            isValid = true

            posLocation = GLES20.glGetAttribLocation(pid, "aPos")
            mvpLocation = GLES20.glGetUniformLocation(pid, "uMVP")
            colorLocation = GLES20.glGetUniformLocation(pid, "uColor")

            Log.d(
                TAG,
                "SolidProgram linked OK: " +
                        "id=$pid, aPos=$posLocation, " +
                        "uMVP=$mvpLocation, uColor=$colorLocation"
            )
        }

        fun use() {
            if (!isValid) return
            GLES20.glUseProgram(programId)
        }

        fun setMVP(matrix: FloatArray) {
            if (!isValid || mvpLocation < 0) return
            GLES20.glUniformMatrix4fv(mvpLocation, 1, false, matrix, 0)
        }

        fun setColor(r: Float, g: Float, b: Float, a: Float) {
            if (!isValid || colorLocation < 0) return
            GLES20.glUniform4f(colorLocation, r, g, b, a)
        }

        internal fun dispose() {
            if (programId != 0) {
                GLES20.glDeleteProgram(programId)
                programId = 0
            }
            isValid = false
        }

        private companion object {
            val VERTEX_SHADER: String = """
                // Blade Wraith — Solid Vertex Shader
                // GLSL ES 1.00
                
                attribute vec2 aPos;
                
                uniform mat4 uMVP;
                
                void main() {
                    gl_Position = uMVP * vec4(aPos, 0.0, 1.0);
                }
            """.trimIndent()

            val FRAGMENT_SHADER: String = """
                // Blade Wraith — Solid Fragment Shader
                // GLSL ES 1.00
                precision mediump float;
                
                uniform vec4 uColor;
                
                void main() {
                    gl_FragColor = uColor;
                }
            """.trimIndent()
        }
    }

    // ========================================================
    //  Shader Compilation Helpers
    // ========================================================

    /**
     * Compiles a single shader stage.
     *
     * On failure, logs the full GLSL compiler output and
     * returns 0. The caller is responsible for cleaning up any
     * partial state.
     *
     * @param type      GL_VERTEX_SHADER or GL_FRAGMENT_SHADER.
     * @param source    GLSL ES 1.00 source code.
     * @param debugName A human-readable name for logs.
     * @return The shader object ID, or 0 on failure.
     */
    private fun compileShader(type: Int, source: String, debugName: String): Int {
        val shaderId = GLES20.glCreateShader(type)
        if (shaderId == 0) {
            Log.e(TAG, "$debugName: glCreateShader returned 0")
            return 0
        }

        GLES20.glShaderSource(shaderId, source)
        GLES20.glCompileShader(shaderId)

        // Check compile status.
        val status = IntArray(1)
        GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, status, 0)

        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shaderId)
            Log.e(TAG, "===== $debugName compile FAILED =====")
            Log.e(TAG, "Log:\n$log")
            Log.e(TAG, "Source:\n$source")
            Log.e(TAG, "=====================================")
            GLES20.glDeleteShader(shaderId)
            return 0
        }

        // Even on success, log any warnings.
        val infoLog = GLES20.glGetShaderInfoLog(shaderId)
        if (!infoLog.isNullOrEmpty()) {
            Log.d(TAG, "$debugName compile warnings:\n$infoLog")
        }

        return shaderId
    }

    /**
     * Links a vertex and fragment shader into a program.
     *
     * On failure, logs the full linker output and returns 0.
     * The shaders are NOT deleted here — the caller does that
     * regardless of success.
     *
     * @param vsId      Vertex shader ID (non-zero).
     * @param fsId      Fragment shader ID (non-zero).
     * @param debugName A human-readable name for logs.
     * @return The program object ID, or 0 on failure.
     */
    private fun linkProgram(vsId: Int, fsId: Int, debugName: String): Int {
        val programId = GLES20.glCreateProgram()
        if (programId == 0) {
            Log.e(TAG, "$debugName: glCreateProgram returned 0")
            return 0
        }

        GLES20.glAttachShader(programId, vsId)
        GLES20.glAttachShader(programId, fsId)

        // ----------------------------------------------------
        //  Bind attribute locations BEFORE linking.
        //
        //  This is optional but useful: it forces the linker to
        //  use the exact attribute slot indices we specify,
        //  making debugging easier and reducing the chance of
        //  attribute-slot collisions on some drivers.
        //
        //  We use the standard locations:
        //      0 = position
        //      1 = UV
        //      2 = color
        //
        //  Note: We still query the actual locations afterward
        //  via glGetAttribLocation, because some drivers ignore
        //  the binding hint and assign their own slots.
        // ----------------------------------------------------
        if (debugName == "Sprite") {
            GLES20.glBindAttribLocation(programId, 0, "aPos")
            GLES20.glBindAttribLocation(programId, 1, "aUV")
            GLES20.glBindAttribLocation(programId, 2, "aColor")
        } else {
            GLES20.glBindAttribLocation(programId, 0, "aPos")
        }

        GLES20.glLinkProgram(programId)

        // Check link status.
        val status = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, status, 0)

        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(programId)
            Log.e(TAG, "===== $debugName link FAILED =====")
            Log.e(TAG, "Log:\n$log")
            Log.e(TAG, "==================================")
            GLES20.glDeleteProgram(programId)
            return 0
        }

        // Even on success, log warnings.
        val infoLog = GLES20.glGetProgramInfoLog(programId)
        if (!infoLog.isNullOrEmpty()) {
            Log.d(TAG, "$debugName link warnings:\n$infoLog")
        }

        return programId
    }

    // ========================================================
    //  Diagnostics
    // ========================================================

    /**
     * Returns a human-readable snapshot of the shader cache state.
     */
    fun debugString(): String {
        return "ShaderCache[" +
                "initialized=$initialized, " +
                "sprite=${if (sprite.isValid) "OK(${sprite.programId})" else "INVALID"}, " +
                "solid=${if (solid.isValid) "OK(${solid.programId})" else "INVALID"}" +
                "]"
    }
}
