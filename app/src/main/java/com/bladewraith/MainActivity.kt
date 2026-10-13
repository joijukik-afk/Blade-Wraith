package com.bladewraith

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * ============================================================
 *  Blade Wraith — Main Activity
 *
 *  The single entry point of the game. Responsible for:
 *    • Creating and hosting the GLSurfaceView
 *    • Managing the Activity lifecycle (onResume / onPause)
 *    • Enabling immersive fullscreen mode (hiding system bars)
 *    • Handling configuration changes without recreating the
 *      OpenGL context
 *    • Providing a graceful fallback if OpenGL ES 2.0 is
 *      unavailable on the device
 *
 *  References:
 *    • Android Developers — "Activity lifecycle"
 *      https://developer.android.com/guide/components/activities/activity-lifecycle
 *    • Android Developers — "Hide system bars for immersive mode"
 *      https://developer.android.com/develop/ui/views/layout/immersive
 *    • Android Developers — "GLSurfaceView"
 *      https://developer.android.com/reference/android/opengl/GLSurfaceView
 *    • Android Developers — "Handle configuration changes"
 *      https://developer.android.com/guide/topics/resources/runtime-changes
 * ============================================================
 */
class MainActivity : Activity() {

    // ========================================================
    //  Companion — constants and logging tag
    // ========================================================
    companion object {
        private const val TAG = "BladeWraith"
    }

    // ========================================================
    //  View References
    // ========================================================

    /**
     * The OpenGL surface that hosts the game renderer.
     * Nullable because it is initialized in [onCreate] and
     * destroyed in [onDestroy].
     */
    private var gameView: GameSurfaceView? = null

    /**
     * Root container. Holding a reference to the root lets us
     * swap the content view (e.g., for an error screen) without
     * losing the Activity.
     */
    private var rootLayout: FrameLayout? = null

    // ========================================================
    //  State Flags
    // ========================================================

    /**
     * True once the Activity has been fully created.
     * Guards against accidental calls to [gameView] during
     * the brief window between [onCreate] and [onResume].
     */
    private var isCreated = false

    /**
     * True if OpenGL ES 2.0 is supported on this device.
     * If false, we show an error screen instead of crashing.
     */
    private var glSupported = true

    // ========================================================
    //  Activity Lifecycle — Creation
    // ========================================================

    /**
     * Called when the Activity is first created.
     *
     * Order of operations:
     *  1. Set the window flags (keep screen on, no title)
     *  2. Enable immersive mode
     *  3. Check OpenGL ES 2.0 support
     *  4. Create the GLSurfaceView (if supported)
     *  5. Set the content view
     */
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate() — beginning Activity creation")

        // ----------------------------------------------------
        //  Step 1: Window flags
        //  Keep the screen on while the game is running.
        //  The game has no user input that would otherwise keep
        //  the screen awake, so this is essential.
        // ----------------------------------------------------
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // ----------------------------------------------------
        //  Step 2: Immersive mode
        //  Hide the status bar and navigation bar.
        //  This must be called before setContentView for the
        //  best results, and again in onWindowFocusChanged
        //  to re-apply after the user swipes the bars back.
        // ----------------------------------------------------
        enableImmersiveMode()

        // ----------------------------------------------------
        //  Step 3: Check OpenGL ES 2.0 support
        //  The game requires OpenGL ES 2.0. If the device does
        //  not support it, show a friendly error instead of
        //  crashing with a native error.
        // ----------------------------------------------------
        glSupported = checkOpenGLSupport()

        if (!glSupported) {
            Log.e(TAG, "OpenGL ES 2.0 is not supported on this device")
            showErrorScreen(getString(R.string.error_gl_not_supported))
            isCreated = true
            return
        }

        // ----------------------------------------------------
        //  Step 4: Create the GLSurfaceView
        //  The GameSurfaceView constructor creates the renderer
        //  and configures the EGL context.
        // ----------------------------------------------------
        try {
            gameView = GameSurfaceView(this)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create GameSurfaceView", t)
            showErrorScreen(getString(R.string.error_init_failed))
            isCreated = true
            return
        }

        // ----------------------------------------------------
        //  Step 5: Set the content view
        //  Wrap the GLSurfaceView in a FrameLayout so we can
        //  swap it out later if needed (e.g., for an error
        //  screen without recreating the Activity).
        // ----------------------------------------------------
        rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF000000.toInt())
            addView(gameView)
        }
        setContentView(rootLayout)

        isCreated = true
        Log.i(TAG, "onCreate() — Activity creation complete")
    }

    // ========================================================
    //  Activity Lifecycle — Resume / Pause
    // ========================================================

    /**
     * Called when the Activity becomes visible to the user.
     *
     * The GLSurfaceView must be notified so it can resume its
     * rendering thread. This is the standard lifecycle contract
     * for GLSurfaceView, as documented by Android:
     *
     *   "A GLSurfaceView must be notified when the activity is
     *    paused and resumed. GLSurfaceView clients are required
     *    to call onPause() when the activity pauses and
     *    onResume() when the activity resumes."
     *
     * Reference: Android Developers — "GLSurfaceView"
     */
    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume()")

        // Re-apply immersive mode (system bars may have been
        // revealed while the app was in the background).
        enableImmersiveMode()

        // Resume the GL rendering thread.
        gameView?.onResume()
    }

    /**
     * Called when the Activity is no longer visible.
     *
     * The GLSurfaceView must be notified so it can pause its
     * rendering thread and release the EGL context if needed.
     *
     * IMPORTANT: Do NOT call [gameView.onPause] from [onPause]
     * if you are using [preserveEGLContextOnPause]. The
     * GLSurfaceView handles this internally — calling onPause
     * will still pause the rendering thread, but the EGL
     * context is preserved if the flag is set.
     *
     * Reference: Android Developers — "GLSurfaceView"
     */
    override fun onPause() {
        super.onPause()
        Log.i(TAG, "onPause()")

        // Pause the GL rendering thread.
        // This releases the EGL context unless
        // preserveEGLContextOnPause is true.
        gameView?.onPause()
    }

    /**
     * Called when the Activity is being destroyed.
     *
     * Clean up references to avoid memory leaks. The
     * GLSurfaceView will be garbage-collected once the
     * Activity is gone.
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy()")

        // Remove the view from the hierarchy so the GL thread
        // can be properly shut down.
        rootLayout?.removeAllViews()
        gameView = null
        rootLayout = null
        isCreated = false
    }

    // ========================================================
    //  Window Focus — Re-apply Immersive Mode
    // ========================================================

    /**
     * Called when the window gains or loses focus.
     *
     * When the user swipes to reveal the system bars (a
     * transient gesture), the bars become visible. When the
     * window regains focus, we re-apply immersive mode to
     * hide them again.
     *
     * Reference: Android Developers — "Hide system bars
     * for immersive mode"
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            Log.d(TAG, "onWindowFocusChanged(true) — re-applying immersive mode")
            enableImmersiveMode()
        }
    }

    // ========================================================
    //  Configuration Changes
    // ========================================================

    /**
     * Called when a configuration change occurs that the
     * Activity declared it handles itself (via the
     * [android:configChanges] attribute in AndroidManifest.xml).
     *
     * Because we handle orientation and screenSize changes
     * manually, the Activity is NOT recreated and the OpenGL
     * context is preserved. This is critical for a smooth
     * gaming experience.
     *
     * The GameSurfaceView will receive the new dimensions
     * through its own [GLSurfaceView.Renderer.onSurfaceChanged]
     * callback, so we only need to log here.
     *
     * Reference: Android Developers — "Handle configuration
     * changes"
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.i(TAG, "onConfigurationChanged() — orientation=${newConfig.orientation}, " +
                "screenWidthDp=${newConfig.screenWidthDp}, " +
                "screenHeightDp=${newConfig.screenHeightDp}")

        // Re-apply immersive mode in case the system bars
        // became visible during the configuration change.
        enableImmersiveMode()

        // The GLSurfaceView's onSurfaceChanged callback will
        // handle updating the viewport and camera dimensions.
        // No additional action is needed here.
    }

    // ========================================================
    //  Immersive Mode — Hide System Bars
    // ========================================================

    /**
     * Enables immersive fullscreen mode, hiding both the status
     * bar and the navigation bar.
     *
     * Uses the modern [WindowInsetsController] API on
     * Android 11 (API 30) and above, and falls back to the
     * deprecated [View.SYSTEM_UI_FLAG_*] flags on older
     * devices.
     *
     * The "sticky" behavior means that if the user swipes to
     * reveal the system bars, they will automatically hide
     * again after a short timeout.
     *
     * Reference: Android Developers — "Hide system bars for
     * immersive mode"
     */
    @Suppress("DEPRECATION")
    private fun enableImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // -------------------------------------------------
            //  Android 11+ (API 30+): Use WindowInsetsController
            // -------------------------------------------------
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // -------------------------------------------------
            //  Android 10 and below: Use the legacy flags
            // -------------------------------------------------
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        }
    }

    // ========================================================
    //  OpenGL ES 2.0 Support Check
    // ========================================================

    /**
     * Checks whether the device supports OpenGL ES 2.0.
     *
     * The game requires OpenGL ES 2.0 for its rendering. If
     * the device does not support it, we show a friendly error
     * screen instead of crashing with a native error.
     *
     * The manifest already declares `android:glEsVersion=
     * "0x00020000"` with `required="true"`, so Google Play
     * filters out incompatible devices. However, sideloaded
     * APKs (e.g., from GitHub Actions) can still be installed
     * on unsupported devices, so we check at runtime.
     *
     * @return true if OpenGL ES 2.0 is supported, false otherwise
     */
    private fun checkOpenGLSupport(): Boolean {
        return try {
            val activityManager = getSystemService(ACTIVITY_SERVICE)
                    as android.app.ActivityManager
            val configurationInfo = activityManager.deviceConfigurationInfo
            val reqGlEsVersion = configurationInfo.reqGlEsVersion
            val supported = reqGlEsVersion >= 0x00020000
            Log.i(TAG, "OpenGL ES version required: " +
                    "${(reqGlEsVersion shr 16)}.${(reqGlEsVersion and 0xFFFF)}")
            supported
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to check OpenGL support", t)
            // Assume supported and let the renderer fail gracefully
            // if it is not — this avoids blocking the game on
            // devices where the check itself throws.
            true
        }
    }

    // ========================================================
    //  Error Screen
    // ========================================================

    /**
     * Replaces the game view with a fullscreen error message.
     *
     * This is used when:
     *  • OpenGL ES 2.0 is not supported
     *  • The renderer fails to initialize
     *  • Any other unrecoverable initialization error occurs
     *
     * The error screen is a simple centered TextView with a
     * dark background. It is intentionally minimal so it
     * works even if the rest of the engine is broken.
     *
     * @param message The error message to display
     */
    private fun showErrorScreen(message: String) {
        Log.e(TAG, "showErrorScreen: $message")

        val errorView = TextView(this).apply {
            text = "$message\n\n${getString(R.string.error_contact_support)}"
            setTextColor(0xFFFF4444.toInt())
            setBackgroundColor(0xFF0A0A18.toInt())
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setPadding(64, 64, 64, 64)
        }

        // Wrap in a FrameLayout to match the root layout structure
        val container = FrameLayout(this).apply {
            setBackgroundColor(0xFF0A0A18.toInt())
            addView(errorView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        setContentView(container)

        // Also hide system bars on the error screen
        enableImmersiveMode()
    }

    // ========================================================
    //  Back Button Handling
    // ========================================================

    /**
     * Handles the system back button.
     *
     * For a game, the back button should NOT immediately exit.
     * Instead, it should open the in-game pause menu.
     *
     * Since the pause menu is not yet implemented in this
     * version, we forward the back press to the GameSurfaceView
     * which can decide what to do (e.g., toggle pause, show
     * a confirmation dialog, or ignore it).
     *
     * For now, we simply consume the back press to prevent
     * accidental exits. The player can still exit by pressing
     * the home button or switching apps.
     *
     * TODO: Wire this up to the pause menu in a future version.
     */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        Log.d(TAG, "onBackPressed() — ignoring to prevent accidental exit")

        // ----------------------------------------------------
        //  Option A: Ignore back press entirely
        //  This prevents accidental exits but might frustrate
        //  users who want to leave quickly.
        // ----------------------------------------------------
        // super.onBackPressed() // Do NOT call this!

        // ----------------------------------------------------
        //  Option B: Forward to the game view for handling
        //  The GameSurfaceView can implement a pause menu,
        //  confirmation dialog, or any other behavior.
        // ----------------------------------------------------
        // gameView?.handleBackPress()

        // ----------------------------------------------------
        //  Option C: Show a confirmation dialog (future)
        //  For now, we ignore the back press.
        // ----------------------------------------------------
    }

    // ========================================================
    //  Public API — for the game view to call back
    // ========================================================

    /**
     * Called by the game when it wants to exit the application.
     * This allows a clean shutdown from the pause menu.
     */
    fun exitGame() {
        Log.i(TAG, "exitGame() — finishing Activity")
        finish()
    }

    /**
     * Returns true if the Activity has been fully created and
     * the game view is ready to receive input.
     */
    fun isGameReady(): Boolean = isCreated && gameView != null && glSupported

    // ========================================================
    //  End of MainActivity
    // ========================================================
}
