package com.wasif.translucentnotify

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Shows ONE non-touchable layer (TYPE_APPLICATION_OVERLAY, window layer 12) while the notification
 * shade is open. The shade (layer 17-19) and status bar (15) draw above it, so they stay sharp and
 * fully interactive. The layer blurs whatever is behind it (app / home / wallpaper) and tints it grey.
 * It draws no text, icons or controls and never takes touches.
 */
class ShadeBlurService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var previewUntil = 0L
    private val check = Runnable { evaluate() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        main.removeCallbacks(check)
        main.postDelayed(check, 40)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
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
        if (instance === this) instance = null
    }

    private fun evaluate() {
        if (SystemClock.uptimeMillis() < previewUntil) return
        if (isShadeOpen()) show() else hide()
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

    fun show() {
        if (overlay != null || !Settings.canDrawOverlays(this)) return
        val tint = Prefs.tint(this)
        val blur = Prefs.blur(this)
        val v = View(this).apply {
            setBackgroundColor(Color.argb(255 * tint / 100, 128, 128, 128))
            alpha = 0f
        }
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        if (Build.VERSION.SDK_INT >= 31 && blur > 0 && wm.isCrossWindowBlurEnabled) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = (blur * resources.displayMetrics.density).toInt()
        }
        runCatching {
            wm.addView(v, lp)
            overlay = v
            v.animate().alpha(1f).setDuration(120).start()
        }
    }

    fun hide() {
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
    }

    /** Settings-screen test: show the layer for 5 s without needing shade detection. */
    fun preview() {
        previewUntil = SystemClock.uptimeMillis() + 5000
        hide()
        show()
        main.postDelayed({ evaluate() }, 5100)
    }

    /** Text dump of all windows, used to calibrate shade detection on the real phone. */
    fun dumpWindows(): String {
        val r = Rect()
        val sb = StringBuilder("open=${isShadeOpen()}\n")
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
