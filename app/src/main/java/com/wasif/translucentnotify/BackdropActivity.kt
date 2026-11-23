package com.wasif.translucentnotify

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Choreographer
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/**
 * Translucent, non-touchable, non-focusable NORMAL activity window that exists only while the shade is
 * open. App windows are always drawn below System UI, so the shade stays sharp. Blur radius, tint colour
 * and darkening fade together over the user-set duration.
 *
 * The fade is driven by Choreographer (own timer), so it does NOT depend on the system's animator
 * duration scale and the speed setting always applies.
 */
class BackdropActivity : Activity() {
    private lateinit var content: View
    private lateinit var choreographer: Choreographer
    private val frame = Choreographer.FrameCallback { step(it) }

    private var progress = 0f          // 0 = off, 1 = fully frosted
    private var running = false
    private var from = 0f
    private var to = 0f
    private var startNs = 0L
    private var durationNs = 1L
    private var finishWhenHidden = false

    private var blurOk = false
    private var rgb = Color.GRAY       // tint colour
    private var tintAlpha = 0          // 0..255 at full progress
    private var dimAlpha = 0           // 0..255 at full progress
    private var blurPx = 0             // at full progress

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        choreographer = Choreographer.getInstance()

        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        val a = window.attributes
        if (Build.VERSION.SDK_INT >= 30) {
            a.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        blurOk = Build.VERSION.SDK_INT >= 31 && windowManager.isCrossWindowBlurEnabled
        if (blurOk) {
            a.flags = a.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            a.blurBehindRadius = 0
        }
        window.attributes = a

        content = View(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        setContentView(content)
        overridePendingTransition(0, 0)

        if (ShadeBlurService.instance?.active == true) setActive(true)
    }

    override fun onResume() {
        super.onResume()
        ShadeBlurService.instance?.onBackdropResumed()
    }

    /** Fade the frosted look in (true) or out (false). Cancels a pending finish. */
    fun setActive(on: Boolean) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            finishWhenHidden = false
            if (on) {
                rgb = Prefs.rgb(this)
                tintAlpha = 255 * Prefs.tint(this) / 100
                dimAlpha = 255 * Prefs.dim(this) / 100
                blurPx = (Prefs.blur(this) * resources.displayMetrics.density).toInt()
            }
            animateTo(if (on) 1f else 0f)
        }
    }

    /** Fade out, then finish so nothing is left behind. Safe to call repeatedly. */
    fun hideAndFinish() {
        runOnUiThread {
            if (isFinishing || isDestroyed || finishWhenHidden) return@runOnUiThread
            finishWhenHidden = true
            animateTo(0f)
        }
    }

    // ---------- own fade timer ----------

    private fun animateTo(target: Float) {
        if (running && to == target) return          // already heading there: don't restart
        stopAnim()
        val distance = abs(target - progress)
        val ms = Prefs.animMs(this)
        if (ms == 0 || distance == 0f) {
            render(target)
            settled(target)
            return
        }
        from = progress
        to = target
        startNs = 0L
        durationNs = (ms * distance * 1_000_000f).toLong().coerceAtLeast(1L)
        running = true
        choreographer.postFrameCallback(frame)
    }

    private fun step(frameTimeNanos: Long) {
        if (!running) return
        if (startNs == 0L) startNs = frameTimeNanos
        val t = ((frameTimeNanos - startNs).toFloat() / durationNs).coerceIn(0f, 1f)
        val eased = t * t * (3f - 2f * t)            // smoothstep: same style in and out
        render(from + (to - from) * eased)
        if (t < 1f) {
            choreographer.postFrameCallback(frame)
        } else {
            running = false
            settled(to)
        }
    }

    private fun stopAnim() {
        if (running) {
            choreographer.removeFrameCallback(frame)
            running = false
        }
    }

    private fun settled(target: Float) {
        if (target == 0f && finishWhenHidden) finish()
    }

    private fun render(p: Float) {
        progress = p
        content.setBackgroundColor(Frost.color(rgb, tintAlpha, dimAlpha, p))
        if (blurOk) {
            val radius = (blurPx * p).toInt()
            val a = window.attributes
            if (a.blurBehindRadius != radius) {
                a.blurBehindRadius = radius
                window.attributes = a
            }
        }
    }

    override fun finish() {
        stopAnim()
        super.finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        stopAnim()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: BackdropActivity? = null
    }
}
