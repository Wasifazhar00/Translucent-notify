package com.wasif.translucentnotify

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * On-demand blur behind the notification shade. Idle = nothing: no window, no overlay, no receivers,
 * and the service only gets events from System UI (never from other apps).
 *
 * Shade opens  -> start BackdropActivity (a normal app window, always below the shade). Starting an
 *                 activity collapses the shade, so it is re-opened right away.
 * Shade closes -> BackdropActivity fades out and finishes; nothing is left behind.
 * Lock screen, the moments after unlock, screen off, fingerprint popups: ignored.
 */
class ShadeBlurService : AccessibilityService() {
    private enum class Sys { NONE, SHADE, OTHER }

    private lateinit var wm: WindowManager
    private lateinit var power: PowerManager
    private lateinit var keyguard: KeyguardManager
    private val main = Handler(Looper.getMainLooper())
    private var anchor: View? = null
    private var previewUntil = 0L
    private var lastLaunch = 0L
    private var unlockedAt = 0L
    private var wasLocked = false
    private var reopenPending = false
    private var lastSysUiClass: String? = null
    private val check = Runnable { evaluate() }
    private val clearPending = Runnable { reopenPending = false }
    private val dropAnchor = Runnable { removeAnchor() }

    /** True while the frosted look should be on. */
    @Volatile var active = false
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        power = getSystemService(Context.POWER_SERVICE) as PowerManager
        keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastSysUiClass = event.className?.toString()
        }
        main.removeCallbacks(check)   // coalesce bursts of events into one evaluation
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
        val now = SystemClock.uptimeMillis()
        if (now < previewUntil) return
        val state = if (suppressed(now)) Sys.OTHER else classify()
        when {
            state == Sys.SHADE -> {
                active = true
                val backdrop = BackdropActivity.instance
                if (backdrop != null) {
                    backdrop.setActive(true)
                } else if (now - lastLaunch > LAUNCH_GAP_MS) {
                    reopenPending = true                    // the launch collapses the shade: re-open it
                    main.removeCallbacks(clearPending)
                    main.postDelayed(clearPending, PENDING_TIMEOUT_MS)
                    launchBackdrop()
                }
            }
            state == Sys.NONE && reopenPending -> {
                reopenPending = false
                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)   // bring the shade back over the backdrop
            }
            else -> {
                reopenPending = false
                release()
            }
        }
    }

    /** Fade the backdrop out and remove it. */
    private fun release() {
        active = false
        BackdropActivity.instance?.hideAndFinish()
    }

    fun onBackdropResumed() = removeAnchor()

    /** Screen off, lock screen, or the moments right after unlock: never show the blur. */
    private fun suppressed(now: Long): Boolean {
        if (!power.isInteractive) return true
        if (keyguard.isKeyguardLocked) {
            wasLocked = true
            return true
        }
        if (wasLocked) {
            wasLocked = false
            unlockedAt = now
        }
        return now - unlockedAt < UNLOCK_GUARD_MS
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
        if (NOT_SHADE_HINTS.any { cls.contains(it) }) return Sys.OTHER
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
        main.removeCallbacks(dropAnchor)
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
        main.postDelayed(dropAnchor, ANCHOR_TIMEOUT_MS)   // safety: never leave the anchor behind
    }

    /** Settings-screen test: frosted look for 5 s without needing shade detection. */
    fun preview() {
        previewUntil = SystemClock.uptimeMillis() + 5000
        if (BackdropActivity.instance == null) launchBackdrop()
        active = true
        main.postDelayed({ BackdropActivity.instance?.setActive(true) }, 700)
        main.postDelayed({
            previewUntil = 0L
            release()
        }, 5100)
    }

    /** Text dump of all windows, used to calibrate shade detection on the real phone. */
    fun dumpWindows(): String {
        val r = Rect()
        val sb = StringBuilder(
            "class=${classify()} sysUiClass=$lastSysUiClass backdrop=${BackdropActivity.instance != null} " +
                "locked=${keyguard.isKeyguardLocked} reopenPending=$reopenPending\n"
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
        private const val LAUNCH_GAP_MS = 800L
        private const val PENDING_TIMEOUT_MS = 1000L
        private const val ANCHOR_TIMEOUT_MS = 1500L
        private const val UNLOCK_GUARD_MS = 1200L
        private val SHADE_HINTS = listOf("shade", "quick settings", "quicksettings", "notification panel", "notificationpanel")
        private val NOT_SHADE_HINTS = listOf("biometric", "authcontainer", "fingerprint", "faceunlock", "udfps", "keyguard", "lockscreen", "bouncer")
    }
}
