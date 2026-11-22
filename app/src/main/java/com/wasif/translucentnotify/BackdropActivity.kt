package com.wasif.translucentnotify

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager

/**
 * Translucent, non-touchable, non-focusable NORMAL activity window. App windows are always drawn
 * below the notification shade, so the shade stays sharp. FLAG_BLUR_BEHIND blurs what is behind
 * this window (the app / home screen) and the window background adds the grey tint.
 */
class BackdropActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        val tint = Prefs.tint(this)
        val blur = Prefs.blur(this)

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
        if (Build.VERSION.SDK_INT >= 31 && blur > 0 && windowManager.isCrossWindowBlurEnabled) {
            a.flags = a.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            a.blurBehindRadius = (blur * resources.displayMetrics.density).toInt()
        }
        window.attributes = a

        val v = View(this)
        v.setBackgroundColor(Color.argb(255 * tint / 100, 128, 128, 128))
        setContentView(v)
        overridePendingTransition(0, 0)
    }

    override fun onResume() {
        super.onResume()
        // Shade already closed before this window finished opening.
        if (ShadeBlurService.instance?.wanted != true) finish()
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
