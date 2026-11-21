package com.wasif.translucentnotify

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout

/**
 * Draws a non-touchable translucent grey layer.
 *
 * IMPORTANT LIMITATION (OxygenOS / OnePlus 11R):
 * Even TYPE_ACCESSIBILITY_OVERLAY is almost always drawn ABOVE the
 * notification shade. True "under the shade" is not possible without
 * root or SystemUI modification.
 *
 * Therefore we intentionally avoid FLAG_BLUR_BEHIND by default so that
 * notification icons, QS tiles and text are not blurred.
 */
class PanelAccessibilityService : AccessibilityService() {

    private var wm: WindowManager? = null
    private var layer: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        refreshShadeState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Always re-check current windows (event class names can be stale)
        refreshShadeState()
    }

    override fun onInterrupt() = hideLayer()

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        hideLayer()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        hideLayer()
        wm = null
        super.onDestroy()
    }

    private fun refreshShadeState() {
        if (isShadeOpen()) showLayer() else hideLayer()
    }

    private fun isShadeOpen(): Boolean {
        val systemUiPackage = "com.android.systemui"
        val tokens = listOf(
            "notificationshade", "notificationpanel", "notification_panel",
            "expandedstatusbar", "expanded_status_bar", "quicksettings",
            "quick_settings", "qscontainer", "qsfragment", "shade",
            "notificationstackscroller", "status_bar_expanded"
        )

        fun matches(value: CharSequence?): Boolean {
            val name = value?.toString()?.lowercase().orEmpty()
            return name.isNotEmpty() && tokens.any { name.contains(it) }
        }

        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        if (screenWidth <= 0 || screenHeight <= 0) return false

        return runCatching {
            windows.any { window ->
                val bounds = Rect()
                window.getBoundsInScreen(bounds)

                // Must be a large top-anchored window
                if (bounds.width() < screenWidth * 0.55f ||
                    bounds.height() < screenHeight * 0.15f ||
                    bounds.top > screenHeight * 0.18f
                ) {
                    return@any false
                }

                val root = window.root
                val packageName = root?.packageName?.toString()
                val identifiedByPackage = packageName == systemUiPackage
                val identifiedByName =
                    matches(root?.className) || matches(window.title)

                identifiedByPackage || identifiedByName
            }
        }.getOrDefault(false)
    }

    private fun showLayer() {
        if (layer != null) return
        val manager = wm ?: return

        val tint = Prefs.tintPercent(this).coerceIn(0, 100)
        val view = FrameLayout(this).apply {
            // Neutral grey translucent background
            setBackgroundColor(Color.argb(tint * 255 / 100, 180, 180, 185))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
        }

        // Critical flags – NO FLAG_BLUR_BEHIND by default
        // (blur would affect SystemUI icons because the overlay sits above the shade)
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 1f
            // Intentionally do NOT call setBlurBehindRadius.
            // On OxygenOS the overlay ends up above the shade, so blur
            // would frost the icons, notifications and QS tiles.
        }

        runCatching {
            manager.addView(view, params)
            layer = view
        }
    }

    private fun hideLayer() {
        val current = layer ?: return
        runCatching { wm?.removeView(current) }
        layer = null
    }
}
