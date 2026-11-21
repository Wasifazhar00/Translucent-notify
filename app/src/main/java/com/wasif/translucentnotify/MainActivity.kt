package com.wasif.translucentnotify

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var permissionStatus: TextView
    private lateinit var tintLabel: TextView
    private lateinit var blurLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(30,30,34)
        window.navigationBarColor = Color.rgb(30,30,34)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24),dp(36),dp(24),dp(28))
            setBackgroundColor(Color.rgb(30,30,34))
        }
        setContentView(page)

        page.addView(label("Shade Background",27,Color.WHITE,true))
        page.addView(label(
            "A background-only frosted layer for the original notification shade. " +
                "The original panel and its controls stay above the background.",
            14,0xFFD0D0D5.toInt(),false
        ).apply { setPadding(0,dp(10),0,dp(20)) })

        page.addView(label("Required permissions",18,Color.WHITE,true))
        permissionStatus = label("",14,0xFFFFD59A.toInt(),true)
        page.addView(permissionStatus.apply { setPadding(0,dp(8),0,dp(12)) })

        page.addView(Button(this).apply {
            text = "Open Accessibility settings"
            isAllCaps = false
            setOnClickListener { openServiceSettings() }
        })
        page.addView(Button(this).apply {
            text = "Allow display over other apps"
            isAllCaps = false
            setOnClickListener { openOverlaySettings() }
        })
        page.addView(label(
            "Enable Shade Background in Accessibility, then allow Display over other apps. " +
                "Both are required. The app uses a standard app-overlay window intended to sit below critical SystemUI windows, so the native shade content stays above it. OxygenOS shade ordering must be verified on-device.",
            13,0xFFD0D0D5.toInt(),false
        ).apply { setPadding(0,dp(8),0,dp(18)) })

        page.addView(Button(this).apply {
            text = "Open app info"
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                })
            }
        })

        page.addView(label("Grey tint",16,Color.WHITE,true).apply { setPadding(0,dp(22),0,0) })
        tintLabel = label("",14,0xFFD0D0D5.toInt(),false)
        page.addView(tintLabel)
        page.addView(SeekBar(this).apply {
            max = 100
            progress = Prefs.tintPercent(this@MainActivity)
            tintLabel.text = progress.toString() + "%"
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s:SeekBar?,v:Int,fromUser:Boolean) {
                    Prefs.setTintPercent(this@MainActivity,v)
                    tintLabel.text = v.toString() + "% (applies next time shade opens)"
                }
                override fun onStartTrackingTouch(s:SeekBar?) = Unit
                override fun onStopTrackingTouch(s:SeekBar?) = Unit
            })
        })

        page.addView(label("Blur strength",16,Color.WHITE,true).apply { setPadding(0,dp(16),0,0) })
        blurLabel = label("",14,0xFFD0D0D5.toInt(),false)
        page.addView(blurLabel)
        page.addView(SeekBar(this).apply {
            max = 80
            progress = Prefs.blurDp(this@MainActivity)
            blurLabel.text = progress.toString() + " dp"
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s:SeekBar?,v:Int,fromUser:Boolean) {
                    Prefs.setBlurDp(this@MainActivity,v)
                    blurLabel.text = v.toString() + " dp (applies next time shade opens)"
                }
                override fun onStartTrackingTouch(s:SeekBar?) = Unit
                override fun onStopTrackingTouch(s:SeekBar?) = Unit
            })
        })
    }

    override fun onResume() {
        super.onResume()
        if (::permissionStatus.isInitialized) refreshPermissionStatus()
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

    private fun label(text:String,size:Int,color:Int,bold:Boolean) = TextView(this).apply {
        this.text=text
        textSize=size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface,android.graphics.Typeface.BOLD)
        gravity=Gravity.START
        setLineSpacing(dp(3).toFloat(),1f)
    }

    private fun dp(v:Int) = (v*resources.displayMetrics.density).toInt()
}
