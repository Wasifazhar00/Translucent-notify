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
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(42), dp(24), dp(32))
            setBackgroundColor(BG)
        }
        setContentView(ScrollView(this).apply { addView(page) })

        page.addView(label("Translucent Notify", 28, Color.WHITE, true))
        page.addView(label(
            "Frosted grey layer behind the original notification shade. Idle = nothing running. It appears only when the shade opens and fades out when it closes.",
            14, MUTED, false
        ).apply { setPadding(0, dp(8), 0, dp(18)) })

        page.addView(label("1. Display over other apps", 16, Color.WHITE, true))
        overlayStatus = label("", 13, MUTED, false)
        page.addView(overlayStatus)
        page.addView(button("Allow display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })

        page.addView(label("2. Shade detector (accessibility)", 16, Color.WHITE, true)
            .apply { setPadding(0, dp(18), 0, 0) })
        a11yStatus = label("", 13, MUTED, false)
        page.addView(a11yStatus)
        page.addView(button("Open accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        page.addView(label(
            "If a switch is greyed out: Settings > Apps > Translucent Notify > three-dot menu > Allow restricted settings.",
            12, HINT, false
        ).apply { setPadding(0, dp(8), 0, 0) })

        page.addView(label("Grey tint", 16, Color.WHITE, true).apply { setPadding(0, dp(22), 0, 0) })
        page.addView(slider(80, Prefs.tint(this), "%") { Prefs.setTint(this, it) })

        page.addView(label("Blur", 16, Color.WHITE, true).apply { setPadding(0, dp(12), 0, 0) })
        page.addView(slider(100, Prefs.blur(this), "") { Prefs.setBlur(this, it) })
        blurStatus = label("", 12, HINT, false)
        page.addView(blurStatus)

        page.addView(label("Animation duration", 16, Color.WHITE, true).apply { setPadding(0, dp(12), 0, 0) })
        page.addView(label("Lower = faster. 0 = instant. Applies to fade in and fade out.", 12, HINT, false))
        page.addView(slider(600, Prefs.animMs(this), " ms") { Prefs.setAnimMs(this, it) })

        page.addView(button("Test layer (5 seconds)") {
            val s = ShadeBlurService.instance
            if (s == null) toast("Turn on the shade detector first.")
            else if (!Settings.canDrawOverlays(this)) toast("Allow display over other apps first.")
            else s.preview()
        })

        page.addView(label("Calibration", 16, Color.WHITE, true).apply { setPadding(0, dp(22), 0, 0) })
        page.addView(label(
            "If the layer does not appear (or stays on) with the real shade, tap this, then pull the shade down within 4 seconds and keep it open. Come back here and send me the text.",
            12, HINT, false
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
        val a11y = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty().contains("$packageName/")
        status(overlayStatus, Settings.canDrawOverlays(this), "ON", "OFF - allow it")
        status(a11yStatus, a11y, "ON", "OFF - turn on \"Translucent Notify shade detector\"")
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        blurStatus.text = when {
            Build.VERSION.SDK_INT < 31 -> "Blur: not supported (Android 12+ needed). Tint only."
            wm.isCrossWindowBlurEnabled -> "Blur: supported on this device."
            else -> "Blur: switched off by the system right now (battery saver, developer option or OEM setting). Tint only."
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun status(v: TextView, ok: Boolean, on: String, off: String) {
        v.text = if (ok) on else off
        v.setTextColor(if (ok) GOOD else BAD)
    }

    private fun slider(max: Int, start: Int, unit: String, onChange: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val value = label("$start$unit", 14, ACCENT, true)
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

    private companion object {
        val BG = Color.rgb(12, 17, 27)
        const val MUTED = 0xFFB7C1D2.toInt()
        const val HINT = 0xFF9AA8BC.toInt()
        const val ACCENT = 0xFFB8D5FF.toInt()
        const val GOOD = 0xFF7FCEA9.toInt()
        const val BAD = 0xFFFFB4A8.toInt()
    }
}
