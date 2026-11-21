package com.wasif.translucentnotify

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout

/**
 * Background-only accessibility layer. It does not intercept touch or read
 * notification contents. Activation requires an explicitly named shade/QS
 * window from System UI; generic touch/content events are not sufficient.
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
        if (open) showLayer() else hideLayer()
    }

    private fun isShadeOpen(event: AccessibilityEvent? = null): Boolean {
        val systemUi = "com.android.systemui"
        val tokens = listOf(
            "notificationshade", "notificationpanel", "notification_panel",
            "expandedstatusbar", "expanded_status_bar", "quicksettings",
            "quick_settings", "qscontainer", "qsfragment"
        )
        fun matches(value: CharSequence?): Boolean {
            val name = value?.toString()?.lowercase().orEmpty()
            return name.isNotEmpty() && tokens.any(name::contains)
        }

        // Only explicit shade/QS identifiers qualify. No screen-size heuristics,
        // generic SystemUI events, status-bar names, or touch events.
        if (event?.packageName?.toString() == systemUi && matches(event.className)) {
            return true
        }
        return runCatching {
            windows.any { window ->
                val root = window.root ?: return@any false
                root.packageName?.toString() == systemUi &&
                    (matches(root.className) || matches(window.title))
            }
        }.getOrDefault(false)
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
