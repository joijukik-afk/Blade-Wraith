package com.bladewraith.engine

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.bladewraith.core.Time
import com.bladewraith.game.Game
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class Renderer : GLSurfaceView.Renderer {

    lateinit var game: Game
        private set

    private lateinit var batch: SpriteBatch

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.02f, 0.02f, 0.05f, 1f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        ShaderCache.init()
        batch = SpriteBatch()
        game = Game()
        game.input.clear()
        game.onSurfaceCreated()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Camera2D.setSize(width, height)
        game.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        Time.tick()
        game.input.update()
        Camera2D.update(Time.dt)
        game.update(Time.dt)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        game.render(batch)
    }
}
