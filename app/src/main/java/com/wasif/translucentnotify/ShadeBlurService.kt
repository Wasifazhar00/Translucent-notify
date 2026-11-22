package com.wasif.translucentnotify

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Keeps BackdropActivity (a normal translucent app window, always below the shade) running invisibly
 * behind the foreground app, and switches its blur/tint on only while the shade is open.
 *
 * Rules learned from testing on OnePlus 11R:
 *  - Never start an activity while ANY big System UI window is up (shade opening, fingerprint popup...):
 *    it collapses the shade / cancels the biometric prompt.
 *  - Only the real notification shade / Quick Settings turns the blur on, not other System UI popups.
 *  - Arm the backdrop only after the foreground app has been stable for a moment.
 */
class ShadeBlurService : AccessibilityService() {
    private enum class Sys { NONE, SHADE, OTHER }

    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var anchor: View? = null
    private var previewUntil = 0L
    private var lastLaunch = 0L
    private var armedFor: String? = null
    private var pendingArm: String? = null
    private var lastSysUiClass: String? = null
    private val check = Runnable { evaluate() }
    private val armCheck = Runnable { armNow() }

    /** True while the frosted look should be on. */
    @Volatile var active = false
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addAnchor()
        main.post { evaluate() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.packageName?.toString() == SYSTEMUI
        ) {
            lastSysUiClass = event.className?.toString()
        }
        main.removeCallbacks(check)
        main.postDelayed(check, 40)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        main.removeCallbacksAndMessages(null)
        active = false
        BackdropActivity.instance?.finish()
        anchor?.let { runCatching { wm.removeView(it) } }
        anchor = null
        armedFor = null
        pendingArm = null
        if (instance === this) instance = null
    }

    private fun addAnchor() {
        if (anchor != null || !Settings.canDrawOverlays(this)) return
        val v = View(this)
        val lp = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        runCatching {
            wm.addView(v, lp)
            anchor = v
        }
    }

    private fun evaluate() {
        addAnchor()
        if (SystemClock.uptimeMillis() < previewUntil) return
        when (classify()) {
            Sys.SHADE -> {
                cancelArm()
                setActive(true)          // only flips blur/tint on the existing window
            }
            Sys.OTHER -> {
                cancelArm()              // fingerprint popup etc.: do nothing, launch nothing
                setActive(false)
            }
            Sys.NONE -> {
                setActive(false)
                scheduleArm()
            }
        }
    }

    private fun setActive(on: Boolean) {
        active = on
        BackdropActivity.instance?.setActive(on)
    }

    // ---------- System UI classification ----------

    private fun sysUiBigWindows(): List<AccessibilityWindowInfo> {
        val h = resources.displayMetrics.heightPixels
        val r = Rect()
        return windows.filter { w ->
            if (w.type != AccessibilityWindowInfo.TYPE_SYSTEM) return@filter false
            w.getBoundsInScreen(r)
            r.height() >= h * 0.3f && w.root?.packageName?.toString() == SYSTEMUI
        }
    }

    private fun classify(): Sys {
        val c = sysUiBigWindows()
        if (c.isEmpty()) {
            lastSysUiClass = null
            return Sys.NONE
        }
        val cls = lastSysUiClass?.lowercase().orEmpty()
        if (BIOMETRIC_HINTS.any { cls.contains(it) }) return Sys.OTHER
        val titles = c.mapNotNull { it.title?.toString()?.lowercase() }.filter { it.isNotBlank() }
        if (titles.any { t -> SHADE_HINTS.any { t.contains(it) } }) return Sys.SHADE
        if (SHADE_HINTS.any { cls.contains(it) }) return Sys.SHADE
        // Titles exist but none look like the shade (e.g. a fingerprint dialog): not the shade.
        if (titles.isNotEmpty()) return Sys.OTHER
        // No information at all: fall back to "big, active/focused System UI window = shade".
        return if (c.any { it.isActive || it.isFocused }) Sys.SHADE else Sys.OTHER
    }

    // ---------- arming (only while System UI is quiet) ----------

    private fun topAppPackage(): String? =
        windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .maxByOrNull { it.layer }
            ?.root?.packageName?.toString()

    private fun cancelArm() {
        pendingArm = null
        main.removeCallbacks(armCheck)
    }

    private fun scheduleArm() {
        if (!Settings.canDrawOverlays(this)) return cancelArm()
        val top = topAppPackage()
        if (top == null || top == packageName) return cancelArm()
        if (BackdropActivity.instance != null && top == armedFor) return cancelArm()
        if (pendingArm == top) return
        pendingArm = top
        main.removeCallbacks(armCheck)
        main.postDelayed(armCheck, ARM_DELAY_MS)
    }

    private fun armNow() {
        val top = topAppPackage()
        val want = pendingArm
        pendingArm = null
        if (want == null || top != want) return
        if (classify() != Sys.NONE) return          // a System UI window appeared meanwhile
        val now = SystemClock.uptimeMillis()
        if (now - lastLaunch < 600) return
        lastLaunch = now
        armedFor = top
        launchBackdrop()
    }

    private fun launchBackdrop() {
        runCatching {
            startActivity(
                Intent(this, BackdropActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            )
        }
    }

    /** Settings-screen test: frosted look for 5 s without needing shade detection. */
    fun preview() {
        previewUntil = SystemClock.uptimeMillis() + 5000
        if (BackdropActivity.instance == null) launchBackdrop()
        active = true
        main.postDelayed({ BackdropActivity.instance?.setActive(true) }, 700)
        main.postDelayed({
            previewUntil = 0L
            evaluate()
        }, 5100)
    }

    /** Text dump of all windows, used to calibrate shade detection on the real phone. */
    fun dumpWindows(): String {
        val r = Rect()
        val sb = StringBuilder(
            "class=${classify()} sysUiClass=$lastSysUiClass backdrop=${BackdropActivity.instance != null} " +
                "armedFor=$armedFor top=${topAppPackage()}\n"
        )
        windows.sortedByDescending { it.layer }.forEach { w ->
            w.getBoundsInScreen(r)
            sb.append("L${w.layer} type=${w.type} pkg=${w.root?.packageName} title=${w.title} ")
                .append("act=${w.isActive} foc=${w.isFocused} ")
                .append("h=${r.height()} y=${r.top}\n")
        }
        return sb.toString()
    }

    companion object {
        @Volatile var instance: ShadeBlurService? = null
        private const val SYSTEMUI = "com.android.systemui"
        private const val ARM_DELAY_MS = 1500L
        private val SHADE_HINTS = listOf("shade", "quick settings", "quicksettings", "notification panel", "notificationpanel")
        private val BIOMETRIC_HINTS = listOf("biometric", "authcontainer", "fingerprint", "faceunlock", "udfps")
    }
}
