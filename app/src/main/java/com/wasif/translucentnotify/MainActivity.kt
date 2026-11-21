package com.wasif.translucentnotify

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var tintLabel: TextView
    private lateinit var blurLabel: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(30,30,34)
        window.navigationBarColor = Color.rgb(30,30,34)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24),dp(40),dp(24),dp(28))
            setBackgroundColor(Color.rgb(30,30,34))
        }
        setContentView(page)
        page.addView(label("Shade Background",27,Color.WHITE,true))
        page.addView(label("A background-only frosted layer for the original notification shade. No replacement panel, icons, notifications or custom controls.",14,0xFFD0D0D5.toInt(),false).apply { setPadding(0,dp(10),0,dp(22)) })
        page.addView(label("Accessibility permission",17,Color.WHITE,true))
        page.addView(label("Used only to detect when the System UI shade is open. Notification text is not read or stored.",13,0xFFBDBDC4.toInt(),false).apply { setPadding(0,dp(8),0,dp(12)) })
        page.addView(Button(this).apply {
            text = "Open accessibility settings"; isAllCaps = false
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        page.addView(label("Grey tint",16,Color.WHITE,true).apply { setPadding(0,dp(22),0,0) })
        tintLabel = label("",14,0xFFD0D0D5.toInt(),false)
        page.addView(tintLabel)
        page.addView(SeekBar(this).apply {
            max = 100; progress = Prefs.tintPercent(this@MainActivity)
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
        page.addView(label("Blur strength",16,Color.WHITE,true).apply { setPadding(0,dp(18),0,0) })
        blurLabel = label("",14,0xFFD0D0D5.toInt(),false)
        page.addView(blurLabel)
        page.addView(SeekBar(this).apply {
            max = 80; progress = Prefs.blurDp(this@MainActivity)
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
        page.addView(label("Important: the layer is positioned below the Android notification-shade window. It is visible only if OxygenOS renders that shade surface translucently. An ordinary app cannot change an opaque System UI surface.",12,0xFFFFD59A.toInt(),false).apply { setPadding(0,dp(24),0,0) })
    }
    private fun label(text:String,size:Int,color:Int,bold:Boolean) = TextView(this).apply {
        this.text=text; textSize=size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface,android.graphics.Typeface.BOLD)
        gravity=Gravity.START; setLineSpacing(dp(3).toFloat(),1f)
    }
    private fun dp(v:Int) = (v*resources.displayMetrics.density).toInt()
}