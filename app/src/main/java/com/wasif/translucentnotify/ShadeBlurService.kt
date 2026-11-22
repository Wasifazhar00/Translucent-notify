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
 * Detects the notification shade and shows/hides BackdropActivity (a normal translucent app window,
 * always below the shade). Starting an activity makes the system collapse the shade, so after the
 * backdrop is up the service reopens the shade once. A 1x1 invisible overlay is kept only so the
 * system lets this service start the activity from the background. It draws nothing, takes no touches.
 */
class ShadeBlurService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var anchor: View? = null
    private var previewUntil = 0L
    private var holdUntil = 0L
    private var lastLaunch = 0L
    private var reopenPending = false
    private val check = Runnable { evaluate() }

    /** True while the backdrop should be on screen. BackdropActivity reads this. */
    @Volatile var wanted = false
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addAnchor()
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
        hide()
        anchor?.let { runCatching { wm.removeView(it) } }
        anchor = null
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
        val now = SystemClock.uptimeMillis()
        // Ignore the shade-closed signal while the backdrop launches and the shade is reopened.
        if (now < previewUntil || now < holdUntil) return
        if (isShadeOpen()) show(false) else hide()
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

    private fun show(isPreview: Boolean) {
        wanted = true
        if (BackdropActivity.instance != null) return
        val now = SystemClock.uptimeMillis()
        if (now - lastLaunch < 800) return
        lastLaunch = now
        if (!isPreview) {
            reopenPending = true
            holdUntil = now + 2500
        }
        try {
            startActivity(
                Intent(this, BackdropActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            )
        } catch (e: Exception) {
            reopenPending = false
            holdUntil = 0L
            wanted = false
        }
    }

    /** Called by BackdropActivity once it is on screen. Reopens the shade the launch collapsed. */
    fun onBackdropReady() {
        if (!reopenPending) return
        reopenPending = false
        main.postDelayed({
            if (!wanted) return@postDelayed
            if (!isShadeOpen()) performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            holdUntil = SystemClock.uptimeMillis() + 1000
        }, 150)
    }

    fun hide() {
        wanted = false
        reopenPending = false
        BackdropActivity.instance?.finish()
    }

    /** Settings-screen test: show the backdrop for 5 s without needing shade detection. */
    fun preview() {
        previewUntil = SystemClock.uptimeMillis() + 5000
        show(true)
        main.postDelayed({
            previewUntil = 0L
            evaluate()
        }, 5100)
    }

    /** Text dump of all windows, used to calibrate shade detection on the real phone. */
    fun dumpWindows(): String {
        val r = Rect()
        val sb = StringBuilder("open=${isShadeOpen()} backdrop=${BackdropActivity.instance != null}\n")
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
