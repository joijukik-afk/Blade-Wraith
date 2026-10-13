package com.bladewraith.engine

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.bladewraith.core.Time
import com.bladewraith.game.Game
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class Renderer : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "Renderer"
    }

    @Volatile
    var isReady: Boolean = false
        private set

    private var gameInternal: Game? = null
    private var batchInternal: SpriteBatch? = null

    fun getGame(): Game {
        return gameInternal ?: throw IllegalStateException("Game not initialized")
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated")

        GLES20.glClearColor(0.02f, 0.02f, 0.05f, 1f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        try {
            ShaderCache.initialize()

            val batch = SpriteBatch()
            val game = Game()
            game.onSurfaceCreated()

            batchInternal = batch
            gameInternal = game
            isReady = true

            Log.i(TAG, "Renderer initialized OK")
        } catch (t: Throwable) {
            Log.e(TAG, "Renderer init FAILED", t)
            isReady = false
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.i(TAG, "onSurfaceChanged: ${width}x$height")
        GLES20.glViewport(0, 0, width, height)
        Camera2D.setSize(width, height)

        try {
            gameInternal?.onSurfaceChanged(width, height)
        } catch (t: Throwable) {
            Log.e(TAG, "onSurfaceChanged failed", t)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        val game = gameInternal
        val batch = batchInternal

        if (game == null || batch == null) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            return
        }

        try {
            Time.tick()
            game.input.update()
            Camera2D.update(Time.dt)
            game.update(Time.dt)

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            game.render(batch)
        } catch (t: Throwable) {
            Log.e(TAG, "Frame render failed", t)
        }
    }
}
