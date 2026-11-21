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
 * Shows a non-interactive application overlay only while an expanded
 * notification-shade or Quick Settings window is visible.
 * Reads window metadata only; never reads notification contents.
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
        // Re-evaluate live windows rather than trusting an event name. Some
        // OxygenOS builds emit SystemUI content events while the shade is shut.
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
        if (!Settings.canDrawOverlays(this)) {
            hideLayer()
            return
        }
        if (isShadeOpen()) showLayer() else hideLayer()
    }

    private fun isShadeOpen(): Boolean {
        val systemUi = "com.android.systemui"
        val tokens = listOf(
            "notificationshade",
            "notificationpanel",
            "notification_panel",
            "expandedstatusbar",
            "expanded_status_bar",
            "quicksettings",
            "quick_settings",
            "qscontainer",
            "qsfragment"
        )
        fun matches(value: CharSequence?): Boolean {
            val text = value?.toString()?.lowercase().orEmpty()
            return text.isNotEmpty() && tokens.any(text::contains)
        }

        val screenHeight = resources.displayMetrics.heightPixels
        val screenWidth = resources.displayMetrics.widthPixels
        return runCatching {
            windows.any { window ->
                val root = window.root ?: return@any false
                if (root.packageName?.toString() != systemUi) return@any false
                if (!matches(root.className) && !matches(window.title)) return@any false

                // Require the identified shade window to be expanded. This
                // prevents the always-present collapsed status-bar window
                // from activating the layer during ordinary screen touches.
                val bounds = Rect()
                window.getBoundsInScreen(bounds)
                bounds.width() >= (screenWidth * 0.75f).toInt() &&
                    bounds.height() >= (screenHeight * 0.35f).toInt()
            }
        }.getOrDefault(false)
    }

    private fun showLayer() {
        if (layer != null || !Settings.canDrawOverlays(this)) return
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
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
