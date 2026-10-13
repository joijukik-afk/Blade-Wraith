package com.bladewraith

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.MotionEvent
import com.bladewraith.engine.Renderer

/**
 * ============================================================
 *  Blade Wraith — Game Surface View
 *
 *  A [GLSurfaceView] subclass that hosts the game renderer and
 *  forwards touch input to the engine. This is the bridge
 *  between the Android view system and the OpenGL rendering
 *  pipeline.
 *
 *  Key responsibilities:
 *    • Configure the EGL context for OpenGL ES 2.0
 *    • Attach the [Renderer] to the surface
 *    • Forward multi-touch events to [Renderer.game.input]
 *    • Safely handle touch events that arrive before the
 *      renderer is fully initialized
 *    • Manage the GL thread lifecycle (onResume / onPause)
 *
 *  Thread safety:
 *    Touch events are delivered on the UI thread, while
 *    rendering happens on the GL thread. The [Renderer] exposes
 *    a [Renderer.isReady] flag to guard against premature
 *    access. Touch events are queued internally in the
 *    [com.bladewraith.core.Input] class using a
 *    [java.util.concurrent.ConcurrentLinkedQueue], which is
 *    thread-safe for single-producer / single-consumer use.
 *
 *  References:
 *    • Android Developers — "Respond to touch events"
 *      https://developer.android.com/develop/ui/views/graphics/opengl/touch
 *    • Android Developers — "GLSurfaceView"
 *      https://developer.android.com/reference/android/opengl/GLSurfaceView
 *    • Android Developers — "GLSurfaceView.Renderer"
 *      https://developer.android.com/reference/android/opengl/GLSurfaceView.Renderer
 * ============================================================
 */
class GameSurfaceView(context: Context) : GLSurfaceView(context) {

    // ========================================================
    //  Companion
    // ========================================================
    companion object {
        private const val TAG = "GameSurfaceView"
    }

    // ========================================================
    //  Renderer
    // ========================================================

    /**
     * The OpenGL ES 2.0 renderer that draws the game.
     *
     * Exposed as a public val so [MainActivity] and other
     * components can access the game state if needed.
     *
     * The renderer is created immediately in the constructor
     * but its OpenGL resources are only initialized after
     * [GLSurfaceView.Renderer.onSurfaceCreated] is called.
     */
    val renderer = Renderer()

    // ========================================================
    //  Initialization
    // ========================================================

    init {
        Log.i(TAG, "Initializing GameSurfaceView")

        // ----------------------------------------------------
        //  Step 1: Request an OpenGL ES 2.0 context
        //
        //  This tells the EGL layer that we want an ES 2.0
        //  context. The game's shaders are written for ES 2.0
        //  (GLSL ES 1.00), so this is required.
        //
        //  Reference: Android Developers — "setEGLContextClientVersion"
        // ----------------------------------------------------
        setEGLContextClientVersion(2)

        // ----------------------------------------------------
        //  Step 2: Configure the EGL surface
        //
        //  Request a 16-bit depth buffer (sufficient for a 2D
        //  game) and no stencil buffer (not needed).
        //
        //  Arguments: redSize, greenSize, blueSize, alphaSize,
        //             depthSize, stencilSize
        //
        //  We use 8,8,8,8,16,0 which gives:
        //    • 24-bit color (true color)
        //    • 8-bit alpha
        //    • 16-bit depth
        //    • No stencil
        //
        //  Reference: Android Developers — "setEGLConfigChooser"
        // ----------------------------------------------------
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)

        // ----------------------------------------------------
        //  Step 3: Preserve the EGL context on pause
        //
        //  This is CRITICAL for a smooth experience. When the
        //  user switches away from the game (e.g., a phone call)
        //  and comes back, we want to keep all OpenGL resources
        //  (textures, shaders, VBOs) in GPU memory instead of
        //  recreating them.
        //
        //  If set to false (default), the EGL context is
        //  destroyed on pause, and all textures/shaders must be
        //  recreated on resume — which can cause a visible
        //  stutter and may even fail on some devices.
        //
        //  Reference: Android Developers — "setPreserveEGLContextOnPause"
        // ----------------------------------------------------
        preserveEGLContextOnPause = true

        // ----------------------------------------------------
        //  Step 4: Attach the renderer
        //
        //  This starts the GL thread. The renderer's callbacks
        //  (onSurfaceCreated, onSurfaceChanged, onDrawFrame)
        //  will be invoked on the GL thread.
        //
        //  MUST be called after setEGLContextClientVersion and
        //  setEGLConfigChooser.
        // ----------------------------------------------------
        setRenderer(renderer)

        // ----------------------------------------------------
        //  Step 5: Set the render mode
        //
        //  RENDERMODE_CONTINUOUSLY: the renderer is called
        //  repeatedly to re-render the scene, targeting the
        //  device's display refresh rate (typically 60 Hz).
        //
        //  This is the correct choice for a real-time game
        //  where the world state changes every frame (physics,
        //  animations, particles, enemy AI).
        //
        //  Alternative: RENDERMODE_WHEN_DIRTY only renders when
        //  requestRender() is called — suitable for static
        //  scenes or reactive UIs, but NOT for a game.
        //
        //  Reference: Android Developers — "setRenderMode"
        // ----------------------------------------------------
        renderMode = RENDERMODE_CONTINUOUSLY

        // ----------------------------------------------------
        //  Step 6: Enable focus for keyboard/gamepad input
        //
        //  This allows the view to receive focus, which is
        //  necessary for future gamepad and keyboard support.
        // ----------------------------------------------------
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()

        Log.i(TAG, "GameSurfaceView initialized successfully")
    }

    // ========================================================
    //  Touch Input Handling
    // ========================================================

    /**
     * Forwards touch events to the renderer's input system.
     *
     * Handles the full multi-touch lifecycle:
     *   • ACTION_DOWN / ACTION_POINTER_DOWN → new pointer
     *   • ACTION_MOVE → all active pointers
     *   • ACTION_UP / ACTION_POINTER_UP → pointer released
     *   • ACTION_CANCEL → all pointers cancelled
     *
     * All input is delivered through the thread-safe
     * [com.bladewraith.core.Input] queue, which is drained by
     * the GL thread at the start of each frame. This ensures
     * that input events are processed in a deterministic order
     * relative to rendering, with no risk of concurrent
     * modification.
     *
     * Reference: Android Developers — "Respond to touch events"
     *   https://developer.android.com/develop/ui/views/graphics/opengl/touch
     *
     * @param event The motion event from the Android framework
     * @return Always true, since we consume all touch events
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // ----------------------------------------------------
        //  Guard: Only process if the renderer is ready
        //
        //  The renderer is not ready until onSurfaceCreated
        //  has been called on the GL thread. Touch events can
        //  arrive before this point (especially on slow devices
        //  or during app startup), so we must guard against
        //  accessing uninitialized game state.
        // ----------------------------------------------------
        if (!renderer.isReady) {
            // Renderer not ready yet — ignore the event.
            // We still return true to keep receiving events.
            return true
        }

        // ----------------------------------------------------
        //  Access the game's input queue
        //
        //  This call is wrapped in a try-catch because in rare
        //  cases the game may have been torn down between the
        //  isReady check and this line (e.g., if the Activity
        //  is finishing). We never want to crash the app over
        //  a touch event.
        // ----------------------------------------------------
        val input = try {
            renderer.getGame().input
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to access game input; ignoring touch event", t)
            return true
        }

        // ----------------------------------------------------
        //  Dispatch by action type
        // ----------------------------------------------------
        when (event.actionMasked) {

            // ------------------------------------------------
            //  New pointer pressed
            // ------------------------------------------------
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                val x = event.getX(index)
                val y = event.getY(index)
                input.onDown(id, x, y)
            }

            // ------------------------------------------------
            //  Pointer moved
            //
            //  ACTION_MOVE delivers a batch of pointer
            //  positions. We must iterate over ALL active
            //  pointers, not just the one that "caused" the
            //  event (which is meaningless for MOVE).
            // ------------------------------------------------
            MotionEvent.ACTION_MOVE -> {
                val count = event.pointerCount
                for (i in 0 until count) {
                    val id = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)
                    input.onMove(id, x, y)
                }
            }

            // ------------------------------------------------
            //  Pointer released
            // ------------------------------------------------
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                input.onUp(id)
            }

            // ------------------------------------------------
            //  All pointers cancelled
            //
            //  This happens when a parent view intercepts the
            //  touch stream, or when the system needs to
            //  cancel input (e.g., a phone call arrives).
            //  We clear all active pointers to avoid "stuck"
            //  input states.
            // ------------------------------------------------
            MotionEvent.ACTION_CANCEL -> {
                input.clear()
            }
        }

        // ----------------------------------------------------
        //  Return true to indicate we consumed the event.
        //  Returning false would allow the event to propagate
        //  to parent views, which we do not want.
        // ----------------------------------------------------
        return true
    }

    // ========================================================
    //  Lifecycle — Resume / Pause
    // ========================================================

    /**
     * Called by [MainActivity] when the Activity resumes.
     *
     * This resumes the GL rendering thread, recreating the
     * EGL context if it was released (which should not happen
     * because we set [preserveEGLContextOnPause] to true).
     *
     * The [GLSurfaceView] documentation states that this
     * method must be called from the Activity's onResume:
     *
     *   "Inform the view that the activity is resumed. The
     *    owner of this view must call this method when the
     *    activity is resumed."
     *
     * Reference: Android Developers — "GLSurfaceView.onResume"
     */
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume() — resuming GL rendering thread")

        // ----------------------------------------------------
        //  Clear any pending input state
        //
        //  When the app is paused, the user may have been
        //  holding a button. On resume, we do not want that
        //  button to be considered "still held". Clearing
        //  the input queue prevents phantom inputs.
        // ----------------------------------------------------
        if (renderer.isReady) {
            try {
                renderer.getGame().input.clear()
            } catch (_: Throwable) {
                // Ignore — the game may not be ready yet.
            }
        }
    }

    /**
     * Called by [MainActivity] when the Activity pauses.
     *
     * This pauses the GL rendering thread. Because we set
     * [preserveEGLContextOnPause] to true, the EGL context
     * and all OpenGL resources are preserved.
     *
     * The [GLSurfaceView] documentation states that this
     * method must be called from the Activity's onPause:
     *
     *   "Inform the view that the activity is paused. The
     *    owner of this view must call this method when the
     *    activity is paused."
     *
     * Reference: Android Developers — "GLSurfaceView.onPause"
     */
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause() — pausing GL rendering thread")

        // ----------------------------------------------------
        //  Clear input state
        //
        //  Same reasoning as onResume: avoid phantom inputs
        //  after the user returns to the game.
        // ----------------------------------------------------
        if (renderer.isReady) {
            try {
                renderer.getGame().input.clear()
            } catch (_: Throwable) {
                // Ignore
            }
        }
    }

    // ========================================================
    //  Focus Handling
    // ========================================================

    /**
     * Called when the view gains or loses focus.
     *
     * We use this to log focus changes and, in the future,
     * to pause the game when the user switches away (e.g.,
     * a system dialog appears).
     */
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        Log.d(TAG, "onWindowFocusChanged: $hasWindowFocus")
    }

    // ========================================================
    //  Back Button Handling (optional, for future use)
    // ========================================================

    /**
     * Handles the back button press.
     *
     * This is a placeholder for future pause-menu integration.
     * For now, it simply logs and returns false (meaning the
     * event is not consumed, allowing the default behavior).
     *
     * TODO: Wire this up to the in-game pause menu.
     *
     * @return true if the event was consumed, false otherwise
     */
    fun handleBackPress(): Boolean {
        Log.d(TAG, "handleBackPress() — not yet implemented")
        return false
    }

    // ========================================================
    //  End of GameSurfaceView
    // ========================================================
}
