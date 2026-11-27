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

/** A slider row: the label + bar view, and the bar itself so presets can move it. */
class SliderRow(val row: LinearLayout, val bar: SeekBar)

/** One colour preset button. */
class ColourPreset(val name: String, val hue: Int, val sat: Int, val bright: Int)

class MainActivity : Activity() {
    private lateinit var overlayStatus: TextView
    private lateinit var a11yStatus: TextView
    private lateinit var blurStatus: TextView
    private lateinit var appsLabel: TextView
    private lateinit var skipLabel: TextView
    private lateinit var dumpView: TextView
    private lateinit var profileButton: Button
    private lateinit var previewTint: View
    private lateinit var hue: SliderRow
    private lateinit var sat: SliderRow
    private lateinit var bright: SliderRow
    private lateinit var tint: SliderRow
    private lateinit var dim: SliderRow
    private val main = Handler(Looper.getMainLooper())
    private var night = false          // which glass profile the sliders are editing

    private val bg: Int = Color.parseColor("#0C111B")
    private val muted: Int = Color.parseColor("#B7C1D2")
    private val hint: Int = Color.parseColor("#9AA8BC")
    private val accent: Int = Color.parseColor("#B8D5FF")
    private val good: Int = Color.parseColor("#7FCEA9")
    private val bad: Int = Color.parseColor("#FFB4A8")
    private val dumpColor: Int = Color.parseColor("#D3DBE8")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(42), dp(24), dp(32))
            setBackgroundColor(bg)
        }
        setContentView(ScrollView(this).apply { addView(page) })

        page.addView(label("Translucent Notify", 28, Color.WHITE, true))
        page.addView(label(
            "Frosted glass behind the original notification shade. Idle = nothing running. It appears only when the shade opens and fades out when it closes.",
            14, muted, false
        ).apply { setPadding(0, dp(8), 0, dp(18)) })

        // ---- setup ----
        page.addView(label("1. Display over other apps", 16, Color.WHITE, true))
        overlayStatus = label("", 13, muted, false)
        page.addView(overlayStatus)
        page.addView(button("Allow display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })

        page.addView(label("2. Shade detector (accessibility)", 16, Color.WHITE, true)
            .apply { setPadding(0, dp(18), 0, 0) })
        a11yStatus = label("", 13, muted, false)
        page.addView(a11yStatus)
        page.addView(button("Turn detector ON (opens its switch)") { Detector.openEnablePage(this) })
        page.addView(button("Turn detector OFF now") {
            if (Detector.turnOff(this, "Turned off in the app")) {
                toast("Detector off.")
                main.postDelayed({ refreshStatus() }, 600)
            } else {
                toast("The detector is already off.")
            }
        })
        page.addView(label(
            "If a switch is greyed out: Settings > Apps > Translucent Notify > three-dot menu > Allow restricted settings.",
            12, hint, false
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
            12, muted, false
        ))

        // ---- look ----
        page.addView(label("Glass look", 20, Color.WHITE, true).apply { setPadding(0, dp(26), 0, dp(8)) })

        page.addView(label("Preview (over a sample picture)", 12, hint, false))
        val previewBox = FrameLayout(this)
        val sample = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#FF8A3D"), Color.parseColor("#2E6BFF"), Color.parseColor("#1DB954"))
        )
        sample.cornerRadius = dp(14).toFloat()
        previewBox.background = sample
        previewTint = View(this)
        previewBox.addView(
            previewTint,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        val previewParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(90))
        previewParams.topMargin = dp(6)
        previewParams.bottomMargin = dp(10)
        page.addView(previewBox, previewParams)

        val autoTheme = Switch(this)
        autoTheme.text = "Auto day / night glass (follow the phone's dark mode)"
        autoTheme.setTextColor(Color.WHITE)
        autoTheme.isChecked = Prefs.autoTheme(this)
        autoTheme.setOnCheckedChangeListener { _, on -> Prefs.setAutoTheme(this, on) }
        page.addView(autoTheme)

        profileButton = button("") {
            night = !night
            loadProfile()
        }
        page.addView(profileButton)

        page.addView(label("Colour presets", 14, Color.WHITE, true))
        val presetRow = LinearLayout(this)
        presetRow.orientation = LinearLayout.HORIZONTAL
        for (p in PRESETS) presetRow.addView(swatch(p))
        val presetScroll = HorizontalScrollView(this)
        presetScroll.isHorizontalScrollBarEnabled = false
        presetScroll.addView(presetRow)
        page.addView(presetScroll)

        page.addView(label("Hue", 14, Color.WHITE, true).apply { setPadding(0, dp(10), 0, 0) })
        hue = slider(360, Prefs.hue(this, night), "\u00B0") { v ->
            Prefs.setHue(this, v, night)
            refreshPreview()
        }
        page.addView(hue.row)
        page.addView(label("Colour strength (saturation)", 14, Color.WHITE, true))
        sat = slider(100, Prefs.sat(this, night), "%") { v ->
            Prefs.setSat(this, v, night)
            refreshPreview()
        }
        page.addView(sat.row)
        page.addView(label("Colour lightness", 14, Color.WHITE, true))
        bright = slider(100, Prefs.bright(this, night), "%") { v ->
            Prefs.setBright(this, v, night)
            refreshPreview()
        }
        page.addView(bright.row)

        page.addView(label("Tint amount", 14, Color.WHITE, true).apply { setPadding(0, dp(8), 0, 0) })
        tint = slider(80, Prefs.tint(this, night), "%") { v ->
            Prefs.setTint(this, v, night)
            refreshPreview()
        }
        page.addView(tint.row)
        page.addView(label("Darkness (black layer)", 14, Color.WHITE, true))
        dim = slider(70, Prefs.dim(this, night), "%") { v ->
            Prefs.setDim(this, v, night)
            refreshPreview()
        }
        page.addView(dim.row)

        page.addView(label("Blur", 14, Color.WHITE, true))
        val blurRow = slider(100, Prefs.blur(this), "") { v -> Prefs.setBlur(this, v) }
        page.addView(blurRow.row)
        blurStatus = label("", 12, hint, false)
        page.addView(blurStatus)

        page.addView(label("Extra darkness on Quick Settings", 14, Color.WHITE, true).apply { setPadding(0, dp(8), 0, 0) })
        page.addView(label(
            "Added on top while the Quick Settings tiles are showing. Experimental: it works only if the phone reports the Quick Settings page (see Calibration).",
            12, hint, false
        ))
        val qsRow = slider(50, Prefs.dimQs(this), "%") { v -> Prefs.setDimQs(this, v) }
        page.addView(qsRow.row)

        page.addView(label("Animation duration", 14, Color.WHITE, true).apply { setPadding(0, dp(8), 0, 0) })
        page.addView(label("Lower = faster. 0 = instant. Applies to fade in and fade out.", 12, hint, false))
        val animRow = slider(600, Prefs.animMs(this), " ms") { v -> Prefs.setAnimMs(this, v) }
        page.addView(animRow.row)

        page.addView(button("Test glass (5 seconds)") {
            val s = ShadeBlurService.instance
            if (s == null) toast("Turn on the shade detector first.")
            else if (!Settings.canDrawOverlays(this)) toast("Allow display over other apps first.")
            else s.preview()
        })

        // ---- apps the glass leaves alone ----
        page.addView(label("Apps the glass leaves alone", 20, Color.WHITE, true).apply { setPadding(0, dp(26), 0, dp(6)) })
        page.addView(label(
            "The glass is an app window, so the app under it is paused for a moment. Apps with an app lock, video or camera can lock, reload or close because of that. Add such apps here: over them the glass is never started and the shade works exactly as without this app.",
            12, muted, false
        ))
        skipLabel = label("", 13, accent, true)
        page.addView(skipLabel)
        page.addView(button("Choose apps to skip") {
            pickApps("Skip the glass over", AppFilter.skipApps(this)) { AppFilter.setSkipApps(this, it) }
        })
        val homeOnly = Switch(this)
        homeOnly.text = "Glass only over the home screen (every app untouched)"
        homeOnly.setTextColor(Color.WHITE)
        homeOnly.isChecked = AppFilter.homeOnly(this)
        homeOnly.setOnCheckedChangeListener { _, on -> AppFilter.setHomeOnly(this, on) }
        page.addView(homeOnly)

        // ---- banking / protected apps ----
        page.addView(label("Banking and protected apps", 20, Color.WHITE, true).apply { setPadding(0, dp(26), 0, dp(6)) })
        page.addView(label(
            "Banking and wallet apps may refuse to run while ANY accessibility service is on. This app does not hide from those checks. Instead the detector really switches itself off when you open an app you choose here.\n\n" +
                "It switches off right as the app opens. An app that checks only in its very first split second can still complain once: then switch the detector off BEFORE opening it (tile, widget, shortcut). Turn it back on afterwards with the notification or the same shortcuts.",
            12, muted, false
        ))
        appsLabel = label("", 13, accent, true)
        page.addView(appsLabel)
        page.addView(button("Choose protected apps") {
            pickApps("Protected apps", Prefs.protectedApps(this)) { Prefs.setProtectedApps(this, it) }
        })
        val autoOff = Switch(this)
        autoOff.text = "Switch the detector off when a protected app opens"
        autoOff.setTextColor(Color.WHITE)
        autoOff.isChecked = Prefs.autoOff(this)
        autoOff.setOnCheckedChangeListener { _, on -> Prefs.setAutoOff(this, on) }
        page.addView(autoOff)

        // ---- keep alive ----
        page.addView(label("Keep it alive", 16, Color.WHITE, true).apply { setPadding(0, dp(22), 0, 0) })
        page.addView(label(
            "If the glass sometimes does not appear, OxygenOS may have stopped the detector to save battery. Allow background use for this app. If it still stops, switch the detector off and on again (tile, widget or shortcut).",
            12, muted, false
        ))
        page.addView(button("Allow background use (battery)") {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (e2: Exception) {
                    toast("Open Settings > Apps > Translucent Notify > Battery and allow background use.")
                }
            }
        })
        page.addView(button("Allow notifications") {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            } else {
                toast("Notifications are already allowed.")
            }
        })

        // ---- calibration ----
        page.addView(label("Calibration", 16, Color.WHITE, true).apply { setPadding(0, dp(22), 0, 0) })
        page.addView(label(
            "If the glass does not appear (or stays on) with the real shade, tap this, then pull the shade down within 4 seconds and keep it open. Come back here and send me the text. For Quick Settings, do it with the tiles open.",
            12, hint, false
        ))
        page.addView(button("Capture windows in 4 s") {
            val s = ShadeBlurService.instance
            if (s == null) {
                toast("Turn on the shade detector first.")
            } else {
                main.postDelayed({ dumpView.text = s.dumpWindows() }, 4000)
            }
        })
        dumpView = label("", 11, dumpColor, false)
        dumpView.typeface = Typeface.MONOSPACE
        dumpView.setTextIsSelectable(true)
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
        val k = AppFilter.skipApps(this).size
        skipLabel.text = if (k == 0) "No apps skipped" else "$k app(s) skipped"
    }

    private fun status(v: TextView, ok: Boolean, on: String, off: String) {
        v.text = if (ok) on else off
        v.setTextColor(if (ok) good else bad)
    }

    // ---------- app picker ----------

    private fun pickApps(title: String, current: Set<String>, save: (Set<String>) -> Unit) {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val chosen = current.toMutableSet()
        val checked = BooleanArray(apps.size) { apps[it].first in chosen }
        val names = Array(apps.size) { apps[it].second }
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMultiChoiceItems(names, checked) { _, i, on ->
            if (on) chosen.add(apps[i].first) else chosen.remove(apps[i].first)
        }
        builder.setPositiveButton("Save") { _, _ ->
            save(chosen)
            ShadeBlurService.instance?.refreshWatched()
            refreshStatus()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    // ---------- look helpers ----------

    /** Show the day or night profile in the sliders. */
    private fun loadProfile() {
        profileButton.text =
            if (night) "Editing: NIGHT glass (tap for day)" else "Editing: DAY glass (tap for night)"
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

    private fun swatch(p: ColourPreset): View {
        val color = Color.HSVToColor(floatArrayOf(p.hue.toFloat(), p.sat / 100f, p.bright / 100f))
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.RECTANGLE
        shape.cornerRadius = dp(12).toFloat()
        shape.setColor(color)
        shape.setStroke(dp(1), Color.argb(85, 255, 255, 255))

        val view = TextView(this)
        view.text = p.name
        view.textSize = 11f
        view.gravity = Gravity.CENTER
        view.setTextColor(if (p.bright > 60) Color.BLACK else Color.WHITE)
        view.background = shape
        view.setPadding(dp(14), dp(10), dp(14), dp(10))
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.rightMargin = dp(8)
        params.topMargin = dp(6)
        params.bottomMargin = dp(2)
        view.layoutParams = params
        view.setOnClickListener {
            hue.bar.progress = p.hue       // listeners save the values and refresh the preview
            sat.bar.progress = p.sat
            bright.bar.progress = p.bright
        }
        return view
    }

    private fun slider(max: Int, start: Int, unit: String, onChange: (Int) -> Unit): SliderRow {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        val value = label("$start$unit", 13, accent, true)
        row.addView(value)
        val bar = SeekBar(this)
        bar.max = max
        bar.progress = start
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                value.text = "$p$unit"
                onChange(p)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}

            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        row.addView(bar)
        return SliderRow(row, bar)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean): TextView {
        val view = TextView(this)
        view.text = value
        view.textSize = size.toFloat()
        view.setTextColor(color)
        if (bold) view.typeface = Typeface.DEFAULT_BOLD
        view.setLineSpacing(dp(3).toFloat(), 1f)
        return view
    }

    private fun button(text: String, action: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.setOnClickListener { action() }
        return b
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private companion object {
        val PRESETS: List<ColourPreset> = listOf(
            ColourPreset("Grey", 0, 0, 50),
            ColourPreset("Light", 0, 0, 95),
            ColourPreset("Dark", 0, 0, 10),
            ColourPreset("Blue", 215, 45, 70),
            ColourPreset("Green", 150, 40, 60),
            ColourPreset("Purple", 270, 45, 65),
            ColourPreset("Warm", 30, 45, 75),
            ColourPreset("Rose", 340, 45, 75)
        )
    }
}
