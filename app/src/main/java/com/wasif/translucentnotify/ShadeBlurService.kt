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
 * ON-DEMAND blur behind the notification shade. Idle = nothing: no window, no overlay, and the service
 * only receives events from System UI (never from other apps).
 *
 * Shade opens  -> start BackdropActivity (a normal app window, always below the shade) with blur + tint.
 *                 Starting an activity makes Android collapse the shade, so the shade is re-opened
 *                 automatically right away (GLOBAL_ACTION_NOTIFICATIONS).
 * Shade closes -> BackdropActivity is finished, nothing is left behind.
 * Other System UI popups (fingerprint etc.) are ignored.
 */
class ShadeBlurService : AccessibilityService() {
    private enum class Sys { NONE, SHADE, OTHER }

    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var anchor: View? = null
    private var previewUntil = 0L
    private var lastLaunch = 0L
    private var reopenPending = false
    private var lastSysUiClass: String? = null
    private val check = Runnable { evaluate() }
    private val clearPending = Runnable { reopenPending = false }

    /** True while the frosted look should be on. */
    @Volatile var active = false
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastSysUiClass = event.className?.toString()
        }
        main.removeCallbacks(check)
        main.post(check)
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
        reopenPending = false
        BackdropActivity.instance?.finish()
        removeAnchor()
        if (instance === this) instance = null
    }

    // ---------- main logic ----------

    private fun evaluate() {
        if (SystemClock.uptimeMillis() < previewUntil) return
        when (classify()) {
            Sys.SHADE -> {
                setActive(true)
                if (BackdropActivity.instance == null &&
                    SystemClock.uptimeMillis() - lastLaunch > 800
                ) {
                    reopenPending = true            // the launch will collapse the shade: re-open it
                    main.removeCallbacks(clearPending)
                    main.postDelayed(clearPending, 1000)
                    launchBackdrop()
                }
            }
            Sys.OTHER -> setActive(false)           // fingerprint popup etc.: ignore
            Sys.NONE -> {
                setActive(false)
                if (reopenPending) {
                    reopenPending = false
                    performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)   // bring the shade back over the backdrop
                } else {
                    BackdropActivity.instance?.finish()   // shade really closed: remove everything
                }
            }
        }
    }

    private fun setActive(on: Boolean) {
        active = on
        BackdropActivity.instance?.setActive(on)
    }

    fun onBackdropResumed() {
        removeAnchor()
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
        if (titles.isNotEmpty()) return Sys.OTHER
        return if (c.any { it.isActive || it.isFocused }) Sys.SHADE else Sys.OTHER
    }

    // ---------- launching ----------

    /** 1x1 invisible overlay that exists only for the instant of the launch (lets Android allow it). */
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

    private fun removeAnchor() {
        anchor?.let { runCatching { wm.removeView(it) } }
        anchor = null
    }

    private fun launchBackdrop() {
        lastLaunch = SystemClock.uptimeMillis()
        addAnchor()
        runCatching {
            startActivity(
                Intent(this, BackdropActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            )
        }
        main.postDelayed({ removeAnchor() }, 1500)   // safety: never leave the anchor behind
    }

    /** Settings-screen test: frosted look for 5 s without needing shade detection. */
    fun preview() {
        previewUntil = SystemClock.uptimeMillis() + 5000
        if (BackdropActivity.instance == null) launchBackdrop()
        active = true
        main.postDelayed({ BackdropActivity.instance?.setActive(true) }, 700)
        main.postDelayed({
            previewUntil = 0L
            setActive(false)
            BackdropActivity.instance?.finish()
        }, 5100)
    }

    /** Text dump of all windows, used to calibrate shade detection on the real phone. */
    fun dumpWindows(): String {
        val r = Rect()
        val sb = StringBuilder(
            "class=${classify()} sysUiClass=$lastSysUiClass backdrop=${BackdropActivity.instance != null} " +
                "reopenPending=$reopenPending\n"
        )
        windows.sortedByDescending { it.layer }.forEach { w ->
            w.getBoundsInScreen(r)
            sb.append("L${w.layer} type=${w.type} title=${w.title} ")
                .append("act=${w.isActive} foc=${w.isFocused} ")
                .append("h=${r.height()} y=${r.top}\n")
        }
        return sb.toString()
    }

    companion object {
        @Volatile var instance: ShadeBlurService? = null
        private const val SYSTEMUI = "com.android.systemui"
        private val SHADE_HINTS = listOf("shade", "quick settings", "quicksettings", "notification panel", "notificationpanel")
        private val BIOMETRIC_HINTS = listOf("biometric", "authcontainer", "fingerprint", "faceunlock", "udfps")
    }
}
