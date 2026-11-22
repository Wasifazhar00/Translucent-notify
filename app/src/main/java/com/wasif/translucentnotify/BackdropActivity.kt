package com.wasif.translucentnotify

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager

/**
 * Translucent, non-touchable, non-focusable NORMAL activity window. It exists ONLY while the shade is
 * open: ShadeBlurService starts it when the shade opens and finishes it when the shade closes.
 * App windows are always drawn below System UI, so the shade stays sharp and fully usable.
 */
class BackdropActivity : Activity() {
    private lateinit var content: View

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
        if (Build.VERSION.SDK_INT >= 31) {
            a.flags = a.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            a.blurBehindRadius = 0
        }
        window.attributes = a

        content = View(this)
        content.setBackgroundColor(Color.TRANSPARENT)
        setContentView(content)
        overridePendingTransition(0, 0)

        if (ShadeBlurService.instance?.active == true) setActive(true)
    }

    override fun onResume() {
        super.onResume()
        ShadeBlurService.instance?.onBackdropResumed()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (ShadeBlurService.instance?.active == true) setActive(true)
    }

    /** Turn the frosted look on/off on this window. */
    fun setActive(on: Boolean) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            val tint = Prefs.tint(this)
            val blur = Prefs.blur(this)
            content.setBackgroundColor(
                if (on) Color.argb(255 * tint / 100, 128, 128, 128) else Color.TRANSPARENT
            )
            if (Build.VERSION.SDK_INT >= 31) {
                val a = window.attributes
                a.blurBehindRadius =
                    if (on && blur > 0 && windowManager.isCrossWindowBlurEnabled)
                        (blur * resources.displayMetrics.density).toInt()
                    else 0
                window.attributes = a
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: BackdropActivity? = null
    }
}
