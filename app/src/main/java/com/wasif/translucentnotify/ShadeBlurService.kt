package com.wasif.translucentnotify

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Keeps BackdropActivity (a normal translucent app window, always below the shade) running invisibly
 * behind the foreground app, and switches its blur/tint on only while the shade is open.
 * Nothing is launched while the shade is open, because starting an activity collapses the shade.
 * A 1x1 invisible overlay is kept only so Android allows the background launch.
 */
class ShadeBlurService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var anchor: View? = null
    private var previewUntil = 0L
    private var lastLaunch = 0L
    private var armedFor: String? = null
    private val check = Runnable { evaluate() }

    /** True while the frosted look should be on. */
    @Volatile var active = false
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addAnchor()
        main.post { evaluate() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        main.removeCallbacks(check)
        main.postDelayed(check, 40)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        main.removeCallbacksAndMessages(null)
        active = false
        BackdropActivity.instance?.finish()
        anchor?.let { runCatching { wm.removeView(it) } }
        anchor = null
        armedFor = null
        if (instance === this) instance = null
    }

    private fun addAnchor() {
        if (anchor != null || !Settings.canDrawOverlays(this)) return
        val v = View(this)
        val lp = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        runCatching {
            wm.addView(v, lp)
            anchor = v
        }
    }

    private fun evaluate() {
        addAnchor()
        if (SystemClock.uptimeMillis() < previewUntil) return
        if (isShadeOpen()) {
            setActive(true)          // only flips blur/tint on the existing window
        } else {
            setActive(false)
            ensureArmed()            // launch only while the shade is closed
        }
    }

    private fun setActive(on: Boolean) {
        active = on
        BackdropActivity.instance?.setActive(on)
    }

    /** Topmost normal app window's package, or null. */
    private fun topAppPackage(): String? =
        windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .maxByOrNull { it.layer }
            ?.root?.packageName?.toString()

    /**
     * Make sure the (invisible) backdrop window sits right above the current foreground app.
     * Re-launched whenever the foreground app changes. Never called while the shade is open.
     */
    private fun ensureArmed() {
        if (!Settings.canDrawOverlays(this)) return
        val top = topAppPackage() ?: return
        if (top == packageName) return                      // our backdrop or our own screen is on top
        if (BackdropActivity.instance != null && top == armedFor) return
        val now = SystemClock.uptimeMillis()
        if (now - lastLaunch < 600) return
        lastLaunch = now
        armedFor = top
        launchBackdrop()
    }

    private fun launchBackdrop() {
        runCatching {
            startActivity(
                Intent(this, BackdropActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            )
        }
    }

    /** Heuristic: a large, focused/active System UI window of type SYSTEM = shade or Quick Settings open. */
    private fun isShadeOpen(): Boolean {
        val h = resources.displayMetrics.heightPixels
        val r = Rect()
        return windows.any { w ->
            if (w.type != AccessibilityWindowInfo.TYPE_SYSTEM) return@any false
            if (!(w.isActive || w.isFocused)) return@any false
            w.getBoundsInScreen(r)
            if (r.height() < h * 0.3f) return@any false
            w.root?.packageName?.toString() == "com.android.systemui"
        }
    }

    /** Settings-screen test: frosted look for 5 s without needing shade detection. */
    fun preview() {
        previewUntil = SystemClock.uptimeMillis() + 5000
        if (BackdropActivity.instance == null) launchBackdrop()
        active = true
        main.postDelayed({ BackdropActivity.instance?.setActive(true) }, 700)
        main.postDelayed({
            previewUntil = 0L
            evaluate()
        }, 5100)
    }

    /** Text dump of all windows, used to calibrate shade detection on the real phone. */
    fun dumpWindows(): String {
        val r = Rect()
        val sb = StringBuilder(
            "open=${isShadeOpen()} backdrop=${BackdropActivity.instance != null} armedFor=$armedFor top=${topAppPackage()}\n"
        )
        windows.sortedByDescending { it.layer }.forEach { w ->
            w.getBoundsInScreen(r)
            sb.append("L${w.layer} type=${w.type} pkg=${w.root?.packageName} ")
                .append("act=${w.isActive} foc=${w.isFocused} ")
                .append("h=${r.height()} y=${r.top}\n")
        }
        return sb.toString()
    }

    companion object {
        @Volatile var instance: ShadeBlurService? = null
    }
}
