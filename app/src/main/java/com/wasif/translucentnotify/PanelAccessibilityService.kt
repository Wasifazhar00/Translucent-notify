package com.wasif.translucentnotify

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Smart translucent overlay tuned for OnePlus 11R / OxygenOS QS layout.
 *
 * Uses the standard application-overlay layer rather than an accessibility
 * overlay. Android places application overlays below critical system windows;
 * this is intended to let the native notification shade render above the tint.
 * The exact shade ordering remains OEM-dependent and must be verified on-device.
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

        val tintPercent = Prefs.tintPercent(this).coerceIn(0, 100)
        // Keep a clearly visible grey wash through the transparent native panel.
        // The native SystemUI content is in a higher window layer and remains
        // interactive; this view itself never receives touch or focus.
        val maxAlpha = (tintPercent * 180 / 100).coerceIn(0, 180)

        val view = object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                if (w <= 0f || h <= 0f) return

                // Main content area on OnePlus 11R QS is roughly:
                // from ~12% to ~78% of screen height, centered horizontally
                val centerX = w / 2f
                val centerY = h * 0.46f
                val radius = maxOf(w, h) * 0.68f

                // Keep the centre visibly tinted too; the native panel content
                // (icons, notifications, QS tiles and menus) draws above this layer.
                val colors = intArrayOf(
                    Color.argb((maxAlpha * 0.58f).toInt(), 172, 172, 180),
                    Color.argb((maxAlpha * 0.70f).toInt(), 168, 168, 176),
                    Color.argb((maxAlpha * 0.86f).toInt(), 164, 164, 172),
                    Color.argb(maxAlpha, 158, 158, 166)
                )
                val stops = floatArrayOf(0.0f, 0.48f, 0.78f, 1.0f)

                paint.shader = RadialGradient(
                    centerX, centerY, radius,
                    colors, stops, Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h, paint)

                // Soft top strip (status bar area) - slightly stronger
                val topPaint = Paint()
                topPaint.shader = LinearGradient(
                    0f, 0f, 0f, h * 0.11f,
                    Color.argb((maxAlpha * 0.55f).toInt(), 155, 155, 160),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h * 0.13f, topPaint)

                // Soft bottom fade
                val bottomPaint = Paint()
                bottomPaint.shader = LinearGradient(
                    0f, h * 0.88f, 0f, h,
                    Color.TRANSPARENT,
                    Color.argb((maxAlpha * 0.45f).toInt(), 155, 155, 160),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, h * 0.88f, w, h, bottomPaint)
            }
        }.apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 1f
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
