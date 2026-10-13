package com.bladewraith

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.bladewraith.engine.Renderer

class GameSurfaceView(context: Context) : GLSurfaceView(context) {

    private val renderer = Renderer()

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                renderer.game.input.onDown(event.getPointerId(i), event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    renderer.game.input.onMove(event.getPointerId(i), event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                val i = event.actionIndex
                renderer.game.input.onUp(event.getPointerId(i))
            }
            MotionEvent.ACTION_CANCEL -> {
                renderer.game.input.clear()
            }
        }
        return true
    }
}
