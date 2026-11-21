package com.wasif.translucentnotify

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var permissionStatus: TextView
    private lateinit var tintLabel: TextView
    private lateinit var blurLabel: TextView
    private lateinit var selectedColorLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(30, 30, 34)
        window.navigationBarColor = Color.rgb(30, 30, 34)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
            setBackgroundColor(Color.rgb(30, 30, 34))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }
        setContentView(scroll)

        page.addView(label("Shade Background", 27, Color.WHITE, true))
        page.addView(label(
            "Customize the tint over the original notification shade. This app does not replace the panel or read notification content.",
            14, 0xFFD0D0D5.toInt(), false
        ).apply { setPadding(0, dp(8), 0, dp(20)) })

        page.addView(label("Required permissions", 18, Color.WHITE, true))
        permissionStatus = label("", 14, 0xFFFFD59A.toInt(), true)
        page.addView(permissionStatus.apply { setPadding(0, dp(8), 0, dp(12)) })

        page.addView(actionButton("Open Accessibility settings") { openServiceSettings() })
        page.addView(actionButton("Allow display over other apps") { openOverlaySettings() })
        page.addView(actionButton("Open app info") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            })
        })

        page.addView(sectionTitle("Tint color"))
        selectedColorLabel = label("", 14, 0xFFD0D0D5.toInt(), false)
        page.addView(selectedColorLabel)
        val colorsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf("Soft grey", "Charcoal", "Deep black", "Blue-grey").forEachIndexed { index, name ->
            val button = Button(this).apply {
                text = name
                isAllCaps = false
                textSize = 11f
                setPadding(dp(3), dp(4), dp(3), dp(4))
                setOnClickListener {
                    Prefs.setTintColor(this@MainActivity, index)
                    refreshColorLabel()
                }
            }
            colorsRow.addView(button, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                marginEnd = dp(3)
            })
        }
        page.addView(colorsRow)
        refreshColorLabel()

        page.addView(sectionTitle("Tint intensity"))
        tintLabel = label("", 14, 0xFFD0D0D5.toInt(), false)
        page.addView(tintLabel)
        page.addView(SeekBar(this).apply {
            max = 100
            progress = Prefs.tintPercent(this@MainActivity)
            updateTintLabel(progress)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, value: Int, fromUser: Boolean) {
                    Prefs.setTintPercent(this@MainActivity, value)
                    updateTintLabel(value)
                }
                override fun onStartTrackingTouch(s: SeekBar?) = Unit
                override fun onStopTrackingTouch(s: SeekBar?) = Unit
            })
        })
        page.addView(label(
            "Tip: use a lower intensity if the overlay makes notification text or icons look washed out.",
            12, 0xFFAAAAAF.toInt(), false
        ).apply { setPadding(0, dp(2), 0, dp(8)) })

        page.addView(sectionTitle("Experimental background blur"))
        val blurToggle = CheckBox(this).apply {
            text = "Enable Android cross-window blur"
            setTextColor(Color.WHITE)
            textSize = 14f
            isChecked = Prefs.blurEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setBlurEnabled(this@MainActivity, checked)
            }
        }
        page.addView(blurToggle)
        page.addView(label(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                "Warning: Android blur-behind blurs the screen underneath this overlay. On OxygenOS this can also blur notification cards, icons and text. Leave it off if you want notifications sharp."
            else
                "Cross-window blur requires Android 12 or newer. Your Android version does not expose this feature.",
            12, 0xFFFFC58A.toInt(), false
        ).apply { setPadding(0, dp(2), 0, dp(8)) })

        blurLabel = label("", 14, 0xFFD0D0D5.toInt(), false)
        page.addView(blurLabel)
        page.addView(SeekBar(this).apply {
            max = 80
            progress = Prefs.blurDp(this@MainActivity)
            updateBlurLabel(progress)
            isEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, value: Int, fromUser: Boolean) {
                    Prefs.setBlurDp(this@MainActivity, value)
                    updateBlurLabel(value)
                }
                override fun onStartTrackingTouch(s: SeekBar?) = Unit
                override fun onStopTrackingTouch(s: SeekBar?) = Unit
            })
        })

        page.addView(label(
            "Changes are saved automatically and apply the next time the shade opens. Close and reopen the notification panel after changing settings.",
            12, 0xFFAAAAAF.toInt(), false
        ).apply { setPadding(0, dp(18), 0, 0) })
    }

    override fun onResume() {
        super.onResume()
        if (::permissionStatus.isInitialized) refreshPermissionStatus()
    }

    private fun sectionTitle(value: String) =
        label(value, 17, Color.WHITE, true).apply { setPadding(0, dp(20), 0, dp(4)) }

    private fun actionButton(value: String, action: () -> Unit) =
        Button(this).apply {
            text = value
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun updateTintLabel(value: Int) {
        tintLabel.text = "$value% opacity strength"
    }

    private fun updateBlurLabel(value: Int) {
        blurLabel.text = "$value dp blur radius"
    }

    private fun refreshColorLabel() {
        val names = listOf("Soft grey", "Charcoal", "Deep black", "Blue-grey")
        selectedColorLabel.text = "Selected: " + names[Prefs.tintColor(this)]
    }

    private fun openServiceSettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlaySettings() {
        startActivity(Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        ))
    }

    private fun refreshPermissionStatus() {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val expected = ComponentName(this, PanelAccessibilityService::class.java)
        val enabled = manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { info ->
            info.resolveInfo.serviceInfo.let {
                it.packageName == expected.packageName && it.name == expected.className
            }
        }
        val overlayAllowed = Settings.canDrawOverlays(this)
        permissionStatus.text = buildString {
            append("Accessibility: ")
            append(if (enabled) "Enabled" else "Not enabled")
            append("\nDisplay over other apps: ")
            append(if (overlayAllowed) "Allowed" else "Not allowed")
        }
        permissionStatus.setTextColor(
            if (enabled && overlayAllowed) 0xFF8FE3A0.toInt() else 0xFFFFD59A.toInt()
        )
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean) =
        TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.START
            setLineSpacing(dp(3).toFloat(), 1f)
        }

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density).toInt()
}