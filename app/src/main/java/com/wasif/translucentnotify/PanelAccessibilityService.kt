package com.wasif.translucentnotify

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout

/**
 * Detects the expanded System UI shade from accessibility window metadata.
 * It deliberately reads only package/class/title/window geometry, never
 * notification text or other notification content.
 */
class PanelAccessibilityService : AccessibilityService() {
    private var wm: WindowManager? = null
    private var layer: View? = null
    private var lastShadeState = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        refreshShadeState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        refreshShadeState(event)
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

    private fun refreshShadeState(event: AccessibilityEvent? = null) {
        val open = isShadeOpen(event)
        if (open != lastShadeState) {
            lastShadeState = open
            if (open) showLayer() else hideLayer()
        } else if (open && layer == null) {
            // Recover if adding the window previously failed.
            showLayer()
        }
    }

    private fun isShadeOpen(event: AccessibilityEvent? = null): Boolean {
        val systemUi = "com.android.systemui"
        val tokens = listOf(
            "notificationshade", "notificationpanel", "notification_panel",
            "expandedstatusbar", "expanded_status_bar", "statusbar",
            "quicksettings", "quick_settings", "qscontainer", "qsfragment",
            "shade", "brightnessmirror"
        )

        fun matches(value: CharSequence?): Boolean {
            val text = value?.toString()?.lowercase().orEmpty()
            return text.isNotEmpty() && tokens.any(text::contains)
        }

        // Event metadata is useful on builds that expose the shade as a state
        // transition but omit it from the interactive-window list.
        if (event?.packageName?.toString() == systemUi &&
            matches(event.className)) return true

        val screenHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            resources.displayMetrics.heightPixels
        } else {
            resources.displayMetrics.heightPixels
        }
        var expandedSystemUiWindow = false

        runCatching {
            windows.forEach { window ->
                val root = window.root
                val packageName = root?.packageName?.toString()
                val className = root?.className
                val title = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) window.title else null
                val belongsToSystemUi = packageName == systemUi ||
                    event?.packageName?.toString() == systemUi

                if (!belongsToSystemUi) return@forEach
                if (matches(className) || matches(title)) {
                    expandedSystemUiWindow = true
                    return@forEach
                }

                // OxygenOS versions differ in internal class names. An expanded
                // SystemUI window is also distinguishable from the thin status
                // bar by its on-screen height. Do not treat small SystemUI
                // windows (status bar, heads-up notifications) as the shade.
                val bounds = Rect()
                window.getBoundsInScreen(bounds)
                if (bounds.height() >= (screenHeight * 0.38f).toInt() &&
                    bounds.width() >= (resources.displayMetrics.widthPixels * 0.75f).toInt()) {
                    expandedSystemUiWindow = true
                }
            }
        }

        return expandedSystemUiWindow
    }

    private fun showLayer() {
        if (layer != null) return
        val manager = wm ?: return
        val tint = Prefs.tintPercent(this).coerceIn(0, 100)
        val view = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(tint * 255 / 100, 184, 184, 188))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
        }
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 1f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setBlurBehindRadius(
                    (Prefs.blurDp(this@PanelAccessibilityService) *
                        resources.displayMetrics.density).toInt().coerceAtLeast(0)
                )
            }
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
