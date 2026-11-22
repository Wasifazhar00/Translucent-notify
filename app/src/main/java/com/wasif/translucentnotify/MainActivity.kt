package com.wasif.translucentnotify

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var overlayStatus: TextView
    private lateinit var a11yStatus: TextView
    private lateinit var blurStatus: TextView
    private lateinit var dumpView: TextView
    private val main = Handler(Looper.getMainLooper())

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
            "Frosted grey layer behind the original notification shade. Idle = nothing running over your apps. It appears only when the shade opens and disappears when it closes.",
            14, 0xFFB7C1D2.toInt(), false
        ).apply { setPadding(0, dp(8), 0, dp(18)) })

        page.addView(label("1. Display over other apps", 16, Color.WHITE, true))
        overlayStatus = label("", 13, 0xFFB7C1D2.toInt(), false)
        page.addView(overlayStatus)
        page.addView(button("Allow display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })

        page.addView(label("2. Shade detector (accessibility)", 16, Color.WHITE, true)
            .apply { setPadding(0, dp(18), 0, 0) })
        a11yStatus = label("", 13, 0xFFB7C1D2.toInt(), false)
        page.addView(a11yStatus)
        page.addView(button("Open accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        page.addView(label(
            "If a switch is greyed out: Settings > Apps > Translucent Notify > three-dot menu > Allow restricted settings.",
            12, 0xFF9AA8BC.toInt(), false
        ).apply { setPadding(0, dp(8), 0, 0) })

        page.addView(label("Grey tint", 16, Color.WHITE, true).apply { setPadding(0, dp(22), 0, 0) })
        page.addView(slider(80, Prefs.tint(this), "%") { Prefs.setTint(this, it) })

        page.addView(label("Blur", 16, Color.WHITE, true).apply { setPadding(0, dp(12), 0, 0) })
        page.addView(slider(100, Prefs.blur(this), "") { Prefs.setBlur(this, it) })
        blurStatus = label("", 12, 0xFF9AA8BC.toInt(), false)
        page.addView(blurStatus)

        page.addView(button("Test layer (5 seconds)") {
            val s = ShadeBlurService.instance
            if (s == null) toast("Turn on the shade detector first.")
            else if (!Settings.canDrawOverlays(this)) toast("Allow display over other apps first.")
            else s.preview()
        })

        page.addView(label("Calibration", 16, Color.WHITE, true).apply { setPadding(0, dp(22), 0, 0) })
        page.addView(label(
            "If the layer does not appear (or stays on) with the real shade, tap this, then pull the shade down within 4 seconds and keep it open. Come back here and send me the text.",
            12, 0xFF9AA8BC.toInt(), false
        ))
        page.addView(button("Capture windows in 4 s") {
            val s = ShadeBlurService.instance
            if (s == null) toast("Turn on the shade detector first.")
            else main.postDelayed({ dumpView.text = s.dumpWindows() }, 4000)
        })
        dumpView = label("", 11, 0xFFD3DBE8.toInt(), false).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        page.addView(dumpView)
    }

    override fun onResume() {
        super.onResume()
        val overlay = Settings.canDrawOverlays(this)
        val a11y = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty().contains("$packageName/")
        status(overlayStatus, overlay, "ON", "OFF - allow it")
        status(a11yStatus, a11y, "ON", "OFF - turn on \"Translucent Notify shade detector\"")
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        blurStatus.text = when {
            Build.VERSION.SDK_INT < 31 -> "Blur: not supported (Android 12+ needed). Tint only."
            wm.isCrossWindowBlurEnabled -> "Blur: supported on this device."
            else -> "Blur: switched off by the system right now (battery saver, developer option or OEM setting). Tint only."
        }
    }

    private fun status(v: TextView, ok: Boolean, on: String, off: String) {
        v.text = if (ok) on else off
        v.setTextColor(if (ok) 0xFF7FCEA9.toInt() else 0xFFFFB4A8.toInt())
    }

    private fun slider(max: Int, start: Int, unit: String, onChange: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val value = label("$start$unit", 14, 0xFFB8D5FF.toInt(), true)
        row.addView(value)
        row.addView(SeekBar(this).apply {
            this.max = max
            progress = start
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    value.text = "$p$unit"
                    onChange(p)
                }
                override fun onStartTrackingTouch(s: SeekBar?) = Unit
                override fun onStopTrackingTouch(s: SeekBar?) = Unit
            })
        })
        return row
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    private fun label(value: String, size: Int, color: Int, bold: Boolean) =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setLineSpacing(dp(3).toFloat(), 1f)
        }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
