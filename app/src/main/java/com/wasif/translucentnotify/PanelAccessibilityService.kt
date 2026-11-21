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
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Smart translucent overlay for the notification shade.
 *
 * Platform limitation: the overlay always sits ON TOP of the SystemUI shade.
 * To reduce the impact on icons and tiles we use a non-uniform tint:
 * - Stronger on the outer edges
 * - Lighter / more transparent in the center (where notifications + QS live)
 *
 * This is not perfect hole-punching, but it keeps the main content more readable.
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
        val maxAlpha = (tintPercent * 255 / 100).coerceIn(0, 220)

        // Custom view that draws a soft center (lighter) and stronger edges
        val view = object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                if (w <= 0f || h <= 0f) return

                // Radial gradient: transparent-ish in center, stronger toward edges
                // This keeps the main notification + QS area more readable
                val centerX = w / 2f
                val centerY = h * 0.42f          // slightly above center (QS + notifications)
                val radius = maxOf(w, h) * 0.75f

                val colors = intArrayOf(
                    Color.argb((maxAlpha * 0.25f).toInt(), 175, 175, 180), // center - light
                    Color.argb((maxAlpha * 0.55f).toInt(), 175, 175, 180), // mid
                    Color.argb(maxAlpha, 170, 170, 175)                    // edges - stronger
                )
                val stops = floatArrayOf(0f, 0.55f, 1f)

                paint.shader = RadialGradient(
                    centerX, centerY, radius,
                    colors, stops, Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h, paint)

                // Extra soft top bar tint (status area)
                val topPaint = Paint()
                topPaint.shader = LinearGradient(
                    0f, 0f, 0f, h * 0.12f,
                    Color.argb((maxAlpha * 0.7f).toInt(), 160, 160, 165),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h * 0.14f, topPaint)
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
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
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
