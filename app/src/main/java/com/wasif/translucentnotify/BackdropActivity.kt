package com.wasif.translucentnotify

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/**
 * Translucent, non-touchable, non-focusable NORMAL activity window that exists only while the shade is
 * open. App windows are always drawn below System UI, so the shade stays sharp. Blur radius and grey
 * tint fade in/out together over a user-set duration.
 */
class BackdropActivity : Activity() {
    private lateinit var content: View
    private var animator: ValueAnimator? = null
    private var progress = 0f
    private var finishWhenHidden = false
    private var blurOk = false
    private var tintAlpha = 0      // 0..255 at full progress
    private var blurPx = 0         // at full progress

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

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
                tintAlpha = 255 * Prefs.tint(this) / 100
                blurPx = (Prefs.blur(this) * resources.displayMetrics.density).toInt()
            }
            animateTo(if (on) 1f else 0f)
        }
    }

    /** Fade out, then finish so nothing is left behind. */
    fun hideAndFinish() {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            finishWhenHidden = true
            animateTo(0f)
        }
    }

    private fun animateTo(target: Float) {
        animator?.cancel()
        val distance = abs(target - progress)
        val duration = Prefs.animMs(this)
        if (duration == 0 || distance == 0f) {
            render(target)
            settled(target)
            return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            this.duration = (duration * distance).toLong().coerceAtLeast(1L)
            addUpdateListener { render(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) settled(target)
                }
            })
            start()
        }
    }

    private fun settled(target: Float) {
        if (target == 0f && finishWhenHidden) finish()
    }

    private fun render(p: Float) {
        progress = p
        content.setBackgroundColor(Color.argb((tintAlpha * p).toInt(), 128, 128, 128))
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
        animator?.cancel()
        super.finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        animator?.cancel()
        animator = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: BackdropActivity? = null
    }
}
