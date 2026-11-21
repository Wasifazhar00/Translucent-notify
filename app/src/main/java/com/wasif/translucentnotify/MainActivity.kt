package com.wasif.translucentnotify

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private val requestCode = 4102
    private val property = "debug.systemui.gaussblur_level"
    // These are proprietary appearance presets observed on a limited ColorOS 17 build range.
    private val levels = intArrayOf(5, 4, 3)
    private val labels = arrayOf("Light glass", "Balanced", "Dark")
    private var selected = 0
    private lateinit var status: TextView
    private lateinit var selection: TextView
    private lateinit var slider: SeekBar
    private lateinit var apply: Button
    private lateinit var permission: Button

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, _ ->
        if (code == requestCode) runOnUiThread { refreshStatus() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 20, 31)
        window.navigationBarColor = Color.rgb(15, 20, 31)
        window.decorView.systemUiVisibility = 0
        Shizuku.addRequestPermissionResultListener(permissionListener)
        buildScreen()
        refreshStatus()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    private fun buildScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(30), dp(22), dp(28))
            setBackgroundColor(Color.rgb(12, 17, 27))
        }
        val scroll = ScrollView(this).apply { addView(page) }
        setContentView(scroll)

        page.addView(text("Translucent Notify", 28, Color.WHITE, true))
        page.addView(text("A tiny control panel for supported system notification-shade appearance modes.", 15, 0xFFB7C1D2.toInt(), false).also {
            it.setPadding(0, dp(8), 0, dp(22))
        })

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(0xFF1C2637.toInt())
        }
        page.addView(panel, margin(width = -1, top = 0, bottom = 16))
        panel.addView(text("DEVICE COMPATIBILITY", 12, 0xFF9FB4D7.toInt(), true))
        status = text("Checking device…", 16, Color.WHITE, true).also { it.setPadding(0, dp(10), 0, dp(4)) }
        panel.addView(status)
        panel.addView(text("Android does not provide a public API for third-party apps to set notification-shade transparency. This experimental control is limited to a proprietary ColorOS 17 implementation and is not a general Android transparency slider.", 13, 0xFFB7C1D2.toInt(), false))

        page.addView(text("Shade appearance", 20, Color.WHITE, true).also { it.setPadding(0, dp(10), 0, dp(8)) })
        selection = text(labels[selected], 17, 0xFFB8D5FF.toInt(), true)
        page.addView(selection)
        slider = SeekBar(this).apply {
            max = 2
            progress = selected
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selected = progress
                    selection.text = labels[selected]
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        page.addView(slider, margin(-1, 8, 8))
        page.addView(text("Three device-defined presets only. They are not calibrated opacity percentages.", 12, 0xFF9AA8BC.toInt(), false))

        apply = button("Apply selected appearance") { applySelected() }
        page.addView(apply, margin(-1, 22, 8))
        permission = button("Grant Shizuku permission") { requestShizukuPermission() }
        page.addView(permission, margin(-1, 4, 8))
        page.addView(button("Reset to system default") { writeProperty(-1, "System default saved. It may take effect after System UI/device restart.") }, margin(-1, 4, 8))
        page.addView(button("Check compatibility again") { refreshStatus() }, margin(-1, 4, 8))

        page.addView(text("How it works", 18, Color.WHITE, true).also { it.setPadding(0, dp(26), 0, dp(8)) })
        page.addView(text("On a recognized ColorOS 17 build, Shizuku can authorize this app to write a proprietary SystemUI property. The value may only take effect after SystemUI/device restart. This app deliberately does not crash or force-restart SystemUI.", 13, 0xFFB7C1D2.toInt(), false))
        page.addView(text("No root • No ads • No account • No notification access • No overlay permission", 12, 0xFF7FCEA9.toInt(), true).also {
            it.setPadding(0, dp(24), 0, 0)
        })
    }

    private fun refreshStatus() {
        val colorOs = readProp("ro.build.version.oplusrom.display").ifBlank {
            readProp("ro.build.version.oplusrom")
        }
        val recognized = colorOs.matches(Regex("V?17(\\..*)?"))
        val shizukuRunning = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val granted = runCatching {
            shizukuRunning && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        status.text = when {
            recognized && granted -> "Compatible ColorOS 17 detected • Shizuku authorized"
            recognized && shizukuRunning -> "ColorOS 17 detected • Shizuku permission required"
            recognized -> "ColorOS 17 detected • Start Shizuku first"
            else -> "Unsupported system build • ${manufacturerModel()}"
        }
        val supported = recognized
        slider.isEnabled = supported
        apply.isEnabled = supported && granted
        permission.isEnabled = supported && shizukuRunning && !granted
        permission.text = if (granted) "Shizuku permission granted" else "Grant Shizuku permission"
        if (!supported) {
            status.text = status.text.toString() + "\nThis device's System UI has no verified control path in this app. No change will be attempted."
        }
    }

    private fun requestShizukuPermission() {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            toast("Install and start Shizuku, then try again.")
            return
        }
        if (Shizuku.isPreV11()) {
            toast("This Shizuku version is too old.")
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            refreshStatus()
        } else {
            Shizuku.requestPermission(requestCode)
        }
    }

    private fun applySelected() {
        writeProperty(levels[selected], "Saved. It may take effect after System UI/device restart.")
    }

    private fun writeProperty(value: Int, successMessage: String) {
        val colorOs = readProp("ro.build.version.oplusrom.display").ifBlank {
            readProp("ro.build.version.oplusrom")
        }
        if (!colorOs.matches(Regex("V?17(\\..*)?"))) {
            toast("Unsupported system build. No changes made.")
            refreshStatus()
            return
        }
        if (!runCatching { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)) {
            toast("Shizuku permission is not available.")
            refreshStatus()
            return
        }
        Thread {
            val result = runCatching {
                val command = "setprop $property $value"
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
                )
                method.isAccessible = true
                val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
                process.waitFor()
            }.getOrElse { -1 }
            runOnUiThread {
                if (result == 0) toast(successMessage)
                else toast("Could not apply. Check Shizuku and its version.")
            }
        }.start()
    }

    private fun readProp(name: String): String = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("getprop", name))
        process.inputStream.bufferedReader().use { it.readText().trim() }
    }.getOrDefault("")

    private fun manufacturerModel() = "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}"

    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.START
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun margin(width: Int, top: Int, bottom: Int) = LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
