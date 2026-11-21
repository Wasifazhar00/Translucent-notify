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
 * Draws a non-touchable translucent background as an application overlay.
 * The native SystemUI shade is above this window, so its icons, notifications,
 * menus and gestures remain in front and interactive.
 *
 * Accessibility window metadata is used only to detect the visible shade.
 * Notification text/content is never traversed, read, or stored.
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
        // Window metadata can lag the event by a frame; always evaluate the
        // current visible windows rather than trusting a stale class-name event.
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
            "quick_settings", "qscontainer", "qsfragment", "shade"
        )
        fun matches(value: CharSequence?): Boolean {
            val name = value?.toString()?.lowercase().orEmpty()
            return name.isNotEmpty() && tokens.any(name::contains)
        }

        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        if (screenWidth <= 0 || screenHeight <= 0) return false

        return runCatching {
            windows.any { window ->
                val bounds = Rect()
                window.getBoundsInScreen(bounds)
                if (bounds.width() < screenWidth * 0.60f ||
                    bounds.height() < screenHeight * 0.18f ||
                    bounds.top > screenHeight * 0.15f
                ) return@any false

                val root = window.root
                val packageName = root?.packageName?.toString()
                val identifiedByPackage = packageName == systemUiPackage
                val identifiedByWindowName =
                    matches(root?.className) || matches(window.title)

                // OxygenOS can expose generic ViewRoot classes instead of
                // AOSP shade class names. A large, top-anchored SystemUI
                // accessibility window is therefore also accepted.
                identifiedByPackage || identifiedByWindowName
            }
        }.getOrDefault(false)
    }

    private fun showLayer() {
        if (layer != null || !Settings.canDrawOverlays(this)) return
        val manager = wm ?: return
        val density = resources.displayMetrics.density
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
                    (Prefs.blurDp(this@PanelAccessibilityService) * density)
                        .toInt().coerceIn(0, (80 * density).toInt())
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