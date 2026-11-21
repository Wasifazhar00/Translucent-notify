package com.wasif.translucentnotify

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
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
            "Notification shade appearance",
            17,
            0xFFB8D5FF.toInt(),
            true
        ).apply { setPadding(0, dp(12), 0, dp(24)) })

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
            setBackgroundColor(0xFF1C2637.toInt())
        }
        page.addView(panel)

        panel.addView(label("Transparency control unavailable", 19, Color.WHITE, true))
        panel.addView(label(
            "Android 16 on this device does not expose a supported, permission-free way for an ordinary app to change the real system notification shade transparency.",
            15,
            0xFFCBD4E2.toInt(),
            false
        ).apply { setPadding(0, dp(12), 0, dp(16)) })
        panel.addView(label(
            "This app does not use Shizuku, root, ADB commands, overlays, notification access, or hidden system commands. It will not pretend to apply a change that the system does not allow.",
            14,
            0xFF9EACC0.toInt(),
            false
        ))

        page.addView(label(
            "Why there is no slider",
            18,
            Color.WHITE,
            true
        ).apply { setPadding(0, dp(28), 0, dp(8)) })
        page.addView(label(
            "The notification shade is drawn by Android System UI. Its transparency must be supported by the phone's operating system. A normal third-party app cannot set it directly.",
            14,
            0xFFB7C1D2.toInt(),
            false
        ))
        page.addView(label(
            "No background services • No ads • No tracking • No special permissions",
            12,
            0xFF7FCEA9.toInt(),
            true
        ).apply { setPadding(0, dp(26), 0, 0) })
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

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density).toInt()
}
