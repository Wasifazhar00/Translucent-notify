package com.wasif.translucentnotify

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var a11yStatus: TextView
    private lateinit var listenerStatus: TextView
    private lateinit var opacityLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 17, 27)
        window.navigationBarColor = Color.rgb(12, 17, 27)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(42), dp(24), dp(32))
            setBackgroundColor(Color.rgb(12, 17, 27))
        }
        setContentView(ScrollView(this).apply { addView(page) })

        page.addView(label("Translucent Notify", 28, Color.WHITE, true))
        page.addView(label(
            "Own notification panel with adjustable opacity. Swipe down from the very top of the screen to open it.",
            14, 0xFFB7C1D2.toInt(), false
        ).apply { setPadding(0, dp(8), 0, dp(20)) })

        page.addView(label("1. Accessibility service", 16, Color.WHITE, true))
        a11yStatus = label("", 13, 0xFFB7C1D2.toInt(), false)
        page.addView(a11yStatus)
        page.addView(button("Open accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        page.addView(label("2. Notification access", 16, Color.WHITE, true)
            .apply { setPadding(0, dp(18), 0, 0) })
        listenerStatus = label("", 13, 0xFFB7C1D2.toInt(), false)
        page.addView(listenerStatus)
        page.addView(button("Open notification access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })

        page.addView(label(
            "If the switch is greyed out (app installed from a file): Settings > Apps > Translucent Notify > three-dot menu > Allow restricted settings, then try again.",
            12, 0xFF9AA8BC.toInt(), false
        ).apply { setPadding(0, dp(10), 0, 0) })

        page.addView(label("Panel opacity", 16, Color.WHITE, true)
            .apply { setPadding(0, dp(24), 0, 0) })
        val current = Prefs.opacity(this)
        opacityLabel = label("$current%", 15, 0xFFB8D5FF.toInt(), true)
        page.addView(opacityLabel)
        page.addView(SeekBar(this).apply {
            max = Prefs.MAX_OPACITY - Prefs.MIN_OPACITY
            progress = current - Prefs.MIN_OPACITY
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = p + Prefs.MIN_OPACITY
                    opacityLabel.text = "$v%"
                    Prefs.setOpacity(this@MainActivity, v)
                }
                override fun onStartTrackingTouch(s: SeekBar?) = Unit
                override fun onStopTrackingTouch(s: SeekBar?) = Unit
            })
        })
        page.addView(button("Preview panel") {
            val svc = PanelAccessibilityService.instance
            if (svc == null) {
                Toast.makeText(this, "Turn on the accessibility service first.", Toast.LENGTH_LONG).show()
            } else svc.showPanel()
        })

        page.addView(label(
            "Note: this replaces the system shade on swipe-down only; it cannot restyle the real Android shade. Turn the accessibility service off to go back to normal.",
            12, 0xFF7FCEA9.toInt(), false
        ).apply { setPadding(0, dp(24), 0, 0) })
    }

    override fun onResume() {
        super.onResume()
        val a11y = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty().contains("$packageName/")
        val listener = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ).orEmpty().contains(packageName)
        a11yStatus.text = if (a11y) "ON" else "OFF - turn on \"Translucent Notify panel\""
        a11yStatus.setTextColor(if (a11y) 0xFF7FCEA9.toInt() else 0xFFFFB4A8.toInt())
        listenerStatus.text = if (listener) "ON" else "OFF - turn on Translucent Notify"
        listenerStatus.setTextColor(if (listener) 0xFF7FCEA9.toInt() else 0xFFFFB4A8.toInt())
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean) =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.START
            setLineSpacing(dp(3).toFloat(), 1f)
        }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
