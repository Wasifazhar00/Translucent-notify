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
 * Background-only experiment. AOSP places TYPE_ACCESSIBILITY_OVERLAY below
 * TYPE_NOTIFICATION_SHADE, allowing System UI to remain above this layer.
 * Visibility through the shade depends on OxygenOS shade translucency.
 */
class PanelAccessibilityService : AccessibilityService() {
    private var wm: WindowManager? = null
    private var layer: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        update(isShadeOpen())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        update(isShadeOpen(event))
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

    private fun isShadeOpen(event: AccessibilityEvent? = null): Boolean {
        val names = mutableListOf<String>()
        event?.className?.toString()?.let { names.add(it) }
        event?.packageName?.toString()?.let { names.add(it) }
        runCatching {
            rootInActiveWindow?.let {
                names.add(it.packageName?.toString().orEmpty())
                names.add(it.className?.toString().orEmpty())
            }
            windows.forEach { win ->
                val root = win.root ?: return@forEach
                if (root.packageName?.toString() == "com.android.systemui") {
                    names.add(root.className?.toString().orEmpty())
                    if (Build.VERSION.SDK_INT >= 24) names.add(win.title?.toString().orEmpty())
                }
            }
        }
        return names.any { raw ->
            val s = raw.lowercase()
            s.contains("notificationshade") || s.contains("notificationpanel") ||
                s.contains("expandedstatusbar") || s.contains("quicksettings") ||
                s.contains("qscontainer") || s.contains("qsfragment")
        }
    }

    private fun isShadeOpen(): Boolean = isShadeOpen(null)
    private fun update(open: Boolean) { if (open) showLayer() else hideLayer() }

    private fun showLayer() {
        if (layer != null) return
        val manager = wm ?: return
        val tint = Prefs.tintPercent(this)
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
                setBlurBehindRadius((Prefs.blurDp(this@PanelAccessibilityService) *
                    resources.displayMetrics.density).toInt())
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