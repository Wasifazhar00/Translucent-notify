package com.wasif.translucentnotify

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private class Slider(val row: LinearLayout, val bar: SeekBar)
    private class Preset(val name: String, val hue: Int, val sat: Int, val bright: Int)

    private lateinit var overlayStatus: TextView
    private lateinit var a11yStatus: TextView
    private lateinit var blurStatus: TextView
    private lateinit var appsLabel: TextView
    private lateinit var dumpView: TextView
    private lateinit var profileButton: Button
    private lateinit var previewTint: View
    private lateinit var hue: Slider
    private lateinit var sat: Slider
    private lateinit var bright: Slider
    private lateinit var tint: Slider
    private lateinit var dim: Slider
    private val main = Handler(Looper.getMainLooper())
    private var night = false          // which glass profile the sliders are editing

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
            "Frosted glass behind the original notification shade. Idle = nothing running. It appears only when the shade opens and fades out when it closes.",
            14, MUTED, false
        ).apply { setPadding(0, dp(8), 0, dp(18)) })

        // ---- setup ----
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
        page.addView(button("Turn detector ON (opens its switch)") { Detector.openEnablePage(this) })
        page.addView(button("Turn detector OFF now") {
            if (Detector.turnOff(this, "Turned off in the app")) {
                toast("Detector off.")
                main.postDelayed({ refreshStatus() }, 600)
            } else toast("The detector is already off.")
        })
        page.addView(label(
            "If a switch is greyed out: Settings > Apps > Translucent Notify > three-dot menu > Allow restricted settings.",
            12, HINT, false
        ).apply { setPadding(0, dp(8), 0, 0) })

        // ---- one-tap controls ----
        page.addView(label("One-tap on / off", 20, Color.WHITE, true).apply { setPadding(0, dp(26), 0, dp(6)) })
        page.addView(label(
            "Android lets an app switch its own detector OFF instantly, but only YOU can switch it back ON (one tap on its switch page). These shortcuts take you straight there:\n\n" +
                "\u2022 Quick Settings tile: pull the shade, tap the pencil/edit icon, drag \"Glass blur\" in. Detector on: tap = off. Detector off: tap = opens the switch.\n" +
                "\u2022 Home-screen widget (optional): long-press the home screen > Widgets > \"Glass blur\".\n" +
                "\u2022 App icon shortcut: long-press this app's icon > \"Glass on/off\".\n" +
                "\u2022 Notification: when the detector switches itself off, a notification appears; tap it to turn the detector on again.\n" +
                "\u2022 Android's own shortcut: Settings > Accessibility > choose a shortcut (volume keys or accessibility button) > \"Translucent Notify shade detector\". One press toggles it on or off. Menu names vary on OxygenOS.",
            12, MUTED, false
        ))

        // ---- look ----
        page.addView(label("Glass look", 20, Color.WHITE, true).apply { setPadding(0, dp(26), 0, dp(8)) })

        page.addView(label("Preview (over a sample picture)", 12, HINT, false))
        val previewBox = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFFFF8A3D.toInt(), 0xFF2E6BFF.toInt(), 0xFF1DB954.toInt())
            ).apply { cornerRadius = dp(14).toFloat() }
        }
        previewTint = View(this)
        previewBox.addView(previewTint, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        page.addView(previewBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(90)).apply { topMargin = dp(6); bottomMargin = dp(10) })

        page.addView(Switch(this).apply {
            text = "Auto day / night glass (follow the phone's dark mode)"
            setTextColor(Color.WHITE)
            isChecked = Prefs.autoTheme(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Prefs.setAutoTheme(this@MainActivity, on) }
        })
        profileButton = button("") {
            night = !night
            loadProfile()
        }
        page.addView(profileButton)

        page.addView(label("Colour presets", 14, Color.WHITE, true))
        page.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                PRESETS.forEach { addView(swatch(it)) }
            })
        })

        page.addView(label("Hue", 14, Color.WHITE, true).apply { setPadding(0, dp(10), 0, 0) })
        hue = slider(360, Prefs.hue(this, night), "\u00B0") { Prefs.setHue(this, it, night); refreshPreview() }
        page.addView(hue.row)
        page.addView(label("Colour strength (saturation)", 14, Color.WHITE, true))
        sat = slider(100, Prefs.sat(this, night), "%") { Prefs.setSat(this, it, night); refreshPreview() }
        page.addView(sat.row)
        page.addView(label("Colour lightness", 14, Color.WHITE, true))
        bright = slider(100, Prefs.bright(this, night), "%") { Prefs.setBright(this, it, night); refreshPreview() }
        page.addView(bright.row)

        page.addView(label("Tint amount", 14, Color.WHITE, true).apply { setPadding(0, dp(8), 0, 0) })
        tint = slider(80, Prefs.tint(this, night), "%") { Prefs.setTint(this, it, night); refreshPreview() }
        page.addView(tint.row)
        page.addView(label("Darkness (black layer)", 14, Color.WHITE, true))
        dim = slider(70, Prefs.dim(this, night), "%") { Prefs.setDim(this, it, night); refreshPreview() }
        page.addView(dim.row)

        page.addView(label("Blur", 14, Color.WHITE, true))
        page.addView(slider(100, Prefs.blur(this), "") { Prefs.setBlur(this, it) }.row)
        blurStatus = label("", 12, HINT, false)
        page.addView(blurStatus)

        page.addView(label("Extra darkness on Quick Settings", 14, Color.WHITE, true).apply { setPadding(0, dp(8), 0, 0) })
        page.addView(label(
            "Added on top while the Quick Settings tiles are showing. Experimental: it works only if the phone reports the Quick Settings page (see Calibration).",
            12, HINT, false
        ))
        page.addView(slider(50, Prefs.dimQs(this), "%") { Prefs.setDimQs(this, it) }.row)

        page.addView(label("Animation duration", 14, Color.WHITE, true).apply { setPadding(0, dp(8), 0, 0) })
        page.addView(label("Lower = faster. 0 = instant. Applies to fade in and fade out.", 12, HINT, false))
        page.addView(slider(600, Prefs.animMs(this), " ms") { Prefs.setAnimMs(this, it) }.row)

        page.addView(button("Test glass (5 seconds)") {
            val s = ShadeBlurService.instance
            if (s == null) toast("Turn on the shade detector first.")
            else if (!Settings.canDrawOverlays(this)) toast("Allow display over other apps first.")
            else s.preview()
        })

        // ---- banking / protected apps ----
        page.addView(label("Banking and protected apps", 20, Color.WHITE, true).apply { setPadding(0, dp(26), 0, dp(6)) })
        page.addView(label(
            "Banking and wallet apps may refuse to run while ANY accessibility service is on. This app does not hide from those checks. Instead the detector really switches itself off when you open an app you choose here.\n\n" +
                "It switches off right as the app opens. An app that checks only in its very first split second can still complain once: then switch the detector off BEFORE opening it (tile, widget, shortcut). Turn it back on afterwards with the notification or the same shortcuts.",
            12, MUTED, false
        ))
        appsLabel = label("", 13, ACCENT, true)
        page.addView(appsLabel)
        page.addView(button("Choose protected apps") { pickApps() })
        page.addView(Switch(this).apply {
            text = "Switch the detector off when a protected app opens"
            setTextColor(Color.WHITE)
            isChecked = Prefs.autoOff(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Prefs.setAutoOff(this@MainActivity, on) }
        })

        // ---- keep alive ----
        page.addView(label("Keep it alive", 16, Color.WHITE, true).apply { setPadding(0, dp(22), 0, 0) })
        page.addView(label(
            "If the glass sometimes does not appear, OxygenOS may have stopped the detector to save battery. Allow background use for this app. If it still stops, switch the detector off and on again (tile, widget or shortcut).",
            12, MUTED, false
        ))
        page.addView(button("Allow background use (battery)") {
            runCatching {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }.onFailure {
                runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            }
        })
        page.addView(button("Allow notifications") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            else toast("Notifications are already allowed.")
        })

        // ---- calibration ----
        page.addView(label("Calibration", 16, Color.WHITE, true).apply { setPadding(0, dp(22), 0, 0) })
        page.addView(label(
            "If the glass does not appear (or stays on) with the real shade, tap this, then pull the shade down within 4 seconds and keep it open. Come back here and send me the text. For Quick Settings, do it with the tiles open.",
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

        loadProfile()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------- status ----------

    private fun refreshStatus() {
        status(overlayStatus, Settings.canDrawOverlays(this), "ON", "OFF - allow it")
        val enabled = Detector.isEnabled(this)
        status(a11yStatus, enabled, "ON", "OFF - tap \"Turn detector ON\" below")
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        blurStatus.text = when {
            Build.VERSION.SDK_INT < 31 -> "Blur: not supported (Android 12+ needed). Tint only."
            wm.isCrossWindowBlurEnabled -> "Blur: supported on this device."
            else -> "Blur: switched off by the system right now (battery saver, developer option or OEM setting). Tint only."
        }
        val n = Prefs.protectedApps(this).size
        appsLabel.text = if (n == 0) "No protected apps chosen" else "$n protected app(s) chosen"
    }

    private fun status(v: TextView, ok: Boolean, on: String, off: String) {
        v.text = if (ok) on else off
        v.setTextColor(if (ok) GOOD else BAD)
    }

    // ---------- protected apps ----------

    private fun pickApps() {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val chosen = Prefs.protectedApps(this).toMutableSet()
        val checked = BooleanArray(apps.size) { apps[it].first in chosen }
        AlertDialog.Builder(this)
            .setTitle("Protected apps")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, on ->
                if (on) chosen.add(apps[i].first) else chosen.remove(apps[i].first)
            }
            .setPositiveButton("Save") { _, _ ->
                Prefs.setProtectedApps(this, chosen)
                ShadeBlurService.instance?.refreshWatched()
                refreshStatus()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- look helpers ----------

    /** Show the day or night profile in the sliders. */
    private fun loadProfile() {
        profileButton.text = if (night) "Editing: NIGHT glass (tap for day)" else "Editing: DAY glass (tap for night)"
        hue.bar.progress = Prefs.hue(this, night)
        sat.bar.progress = Prefs.sat(this, night)
        bright.bar.progress = Prefs.bright(this, night)
        tint.bar.progress = Prefs.tint(this, night)
        dim.bar.progress = Prefs.dim(this, night)
        refreshPreview()
    }

    private fun refreshPreview() {
        previewTint.setBackgroundColor(
            Frost.color(
                Prefs.rgb(this, night),
                255 * Prefs.tint(this, night) / 100,
                255 * Prefs.dim(this, night) / 100,
                1f
            )
        )
    }

    private fun swatch(p: Preset): View {
        val color = Color.HSVToColor(floatArrayOf(p.hue.toFloat(), p.sat / 100f, p.bright / 100f))
        return TextView(this).apply {
            text = p.name
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(if (p.bright > 60) Color.BLACK else Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(color)
                setStroke(dp(1), 0x55FFFFFF)
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(8); topMargin = dp(6); bottomMargin = dp(2) }
            setOnClickListener {
                hue.bar.progress = p.hue       // listeners save the values and refresh the preview
                sat.bar.progress = p.sat
                bright.bar.progress = p.bright
            }
        }
    }

    private fun slider(max: Int, start: Int, unit: String, onChange: (Int) -> Unit): Slider {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val value = label("$start$unit", 13, ACCENT, true)
        row.addView(value)
        val bar = SeekBar(this).apply {
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
        }
        row.addView(bar)
        return Slider(row, bar)
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

        val PRESETS = listOf(
            Preset("Grey", 0, 0, 50),
            Preset("Light", 0, 0, 95),
            Preset("Dark", 0, 0, 10),
            Preset("Blue", 215, 45, 70),
            Preset("Green", 150, 40, 60),
            Preset("Purple", 270, 45, 65),
            Preset("Warm", 30, 45, 75),
            Preset("Rose", 340, 45, 75)
        )
    }
}
