package com.bladewraith.engine

import android.opengl.GLES20

object ShaderCache {

    lateinit var sprite: SpriteProgram
    lateinit var solid: SolidProgram

    fun init() {
        sprite = SpriteProgram()
        solid = SolidProgram()
    }
}

class SpriteProgram {
    private val program: Int
    val posLoc: Int
    val uvLoc: Int
    val colorLoc: Int
    private val mvpLoc: Int
    private val texLoc: Int

    init {
        val vs = """
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

        val fs = """
            precision mediump float;
            uniform sampler2D uTex;
            varying vec2 vUV;
            varying vec4 vColor;
            void main() {
                vec4 t = texture2D(uTex, vUV);
                gl_FragColor = vec4(t.rgb, t.a) * vColor;
            }
        """.trimIndent()

        program = buildProgram(vs, fs)
        posLoc = GLES20.glGetAttribLocation(program, "aPos")
        uvLoc = GLES20.glGetAttribLocation(program, "aUV")
        colorLoc = GLES20.glGetAttribLocation(program, "aColor")
        mvpLoc = GLES20.glGetUniformLocation(program, "uMVP")
        texLoc = GLES20.glGetUniformLocation(program, "uTex")
    }

    fun use() = GLES20.glUseProgram(program)

    fun setMVP(m: FloatArray) = GLES20.glUniformMatrix4fv(mvpLoc, 1, false, m, 0)

    fun bindTexture(tex: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(texLoc, 0)
    }
}

class SolidProgram {
    private val program: Int
    val posLoc: Int
    private val mvpLoc: Int
    private val colorLoc: Int

    init {
        val vs = """
            attribute vec2 aPos;
            uniform mat4 uMVP;
            void main() { gl_Position = uMVP * vec4(aPos, 0.0, 1.0); }
        """.trimIndent()
        val fs = """
            precision mediump float;
            uniform vec4 uColor;
            void main() { gl_FragColor = uColor; }
        """.trimIndent()
        program = buildProgram(vs, fs)
        posLoc = GLES20.glGetAttribLocation(program, "aPos")
        mvpLoc = GLES20.glGetUniformLocation(program, "uMVP")
        colorLoc = GLES20.glGetUniformLocation(program, "uColor")
    }

    fun use() = GLES20.glUseProgram(program)
    fun setMVP(m: FloatArray) = GLES20.glUniformMatrix4fv(mvpLoc, 1, false, m, 0)
    fun setColor(r: Float, g: Float, b: Float, a: Float) =
        GLES20.glUniform4f(colorLoc, r, g, b, a)
}

private fun buildProgram(vs: String, fs: String): Int {
    val v = compile(GLES20.GL_VERTEX_SHADER, vs)
    val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
    val p = GLES20.glCreateProgram()
    GLES20.glAttachShader(p, v)
    GLES20.glAttachShader(p, f)
    GLES20.glLinkProgram(p)
    val st = IntArray(1)
    GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, st, 0)
    if (st[0] == 0) {
        val log = GLES20.glGetProgramInfoLog(p)
        GLES20.glDeleteProgram(p)
        error("Program link failed: $log")
    }
    GLES20.glDeleteShader(v)
    GLES20.glDeleteShader(f)
    return p
}

private fun compile(type: Int, src: String): Int {
    val s = GLES20.glCreateShader(type)
    GLES20.glShaderSource(s, src)
    GLES20.glCompileShader(s)
    val st = IntArray(1)
    GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, st, 0)
    if (st[0] == 0) {
        val log = GLES20.glGetShaderInfoLog(s)
        GLES20.glDeleteShader(s)
        error("Shader compile failed: $log")
    }
    return s
}
