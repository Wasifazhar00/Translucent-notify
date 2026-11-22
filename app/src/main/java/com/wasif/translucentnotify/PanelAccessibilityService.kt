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
 * Adds a non-interactive tint layer while the native notification shade is detected.
 * The layer does not read notification content or handle touch input.
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
                ) return@any false

                val root = window.root
                val packageName = root?.packageName?.toString()
                packageName == systemUiPackage ||
                    matches(root?.className) || matches(window.title)
            }
        }.getOrDefault(false)
    }

    private fun showLayer() {
        if (layer != null) return
        val manager = wm ?: return

        val tintPercent = Prefs.tintPercent(this).coerceIn(0, 100)
        val maxAlpha = (tintPercent * 180 / 100).coerceIn(0, 180)
        val tintRgb = when (Prefs.tintColor(this)) {
            0 -> intArrayOf(158, 158, 166) // soft grey
            1 -> intArrayOf(43, 46, 54)    // charcoal
            2 -> intArrayOf(12, 14, 19)    // deep black
            else -> intArrayOf(43, 55, 72) // blue-grey
        }
        val blurEnabled = Prefs.blurEnabled(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val blurRadiusPx = (Prefs.blurDp(this) * resources.displayMetrics.density).toInt()

        val view = object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                if (w <= 0f || h <= 0f || maxAlpha <= 0) return

                val centerX = w / 2f
                val centerY = h * 0.46f
                val radius = maxOf(w, h) * 0.68f
                val r = tintRgb[0]
                val g = tintRgb[1]
                val b = tintRgb[2]
                val colors = intArrayOf(
                    Color.argb((maxAlpha * 0.48f).toInt(), r, g, b),
                    Color.argb((maxAlpha * 0.68f).toInt(), r, g, b),
                    Color.argb((maxAlpha * 0.86f).toInt(), r, g, b),
                    Color.argb(maxAlpha, r, g, b)
                )
                val stops = floatArrayOf(0.0f, 0.48f, 0.78f, 1.0f)

                paint.shader = RadialGradient(
                    centerX, centerY, radius, colors, stops, Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h, paint)
                paint.shader = null

                val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, h * 0.13f,
                        Color.argb((maxAlpha * 0.35f).toInt(), r, g, b),
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, w, h * 0.13f, topPaint)

                val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, h * 0.88f, 0f, h,
                        Color.TRANSPARENT,
                        Color.argb((maxAlpha * 0.30f).toInt(), r, g, b),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, h * 0.88f, w, h, bottomPaint)
            }
        }.apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
        }

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        // Blur-behind is deliberately opt-in: it blurs whatever is underneath
        // this overlay, which may include notification text/icons on this OEM.
        if (blurEnabled && blurRadiusPx > 0) {
            flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 1f
            if (blurEnabled && blurRadiusPx > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setBlurBehindRadius(blurRadiusPx)
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