package com.wasif.translucentnotify

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * On-demand blur behind the notification shade. Idle = nothing: no window, no overlay, no receivers,
 * and the service only gets events from System UI (plus the few apps marked as protected).
 *
 * Shade opens  -> start BackdropActivity (a normal app window, always below the shade). Starting an
 *                 activity may collapse the shade; if it does, it is re-opened right away. If the shade
 *                 is still open shortly after the backdrop is ready, it survived: no re-open is armed.
 * Shade closes -> BackdropActivity fades out and finishes; nothing is left behind.
 *
 * Never wait for an event that may not come: while the shade is open (or looks like it is about to be),
 * the state is re-checked every POLL_MS, and every event is followed by one more check. That is what
 * makes the glass appear even when the first launch is slow, fails once, or the shade was pulled during
 * the unlock grace period. With the shade closed and no glass on screen there is no polling at all.
 *
 * Lock screen, the moments after unlock, screen off, fingerprint popups: ignored.
 * A protected app opening switches this service off by itself (disableSelf).
 * Everything notable is written to EventLog so a miss can be read afterwards.
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
    private var resumedAt = 0L
    private var unlockedAt = 0L
    private var otherSince = 0L
    private var lastEventAt = 0L
    private var awaitingReopenUntil = 0L
    private var wasLocked = false
    private var reopenPending = false
    private var sysUiPresent = false
    private var qsOpen = false
    private var whyIgnored = ""
    private var lastState: Sys? = null
    private var lastIgnored = false
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
        refreshWatched()
        Detector.clearNotice(this)
        announceState()
        EventLog.add("detector connected")
    }

    /** Listen to System UI plus the protected apps (so they can trigger the automatic switch-off). */
    fun refreshWatched() {
        val info = serviceInfo ?: return
        info.packageNames = (listOf(SYSTEMUI) + Prefs.protectedApps(this)).toTypedArray()
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        lastEventAt = SystemClock.uptimeMillis()
        val pkg = event.packageName?.toString()
        if (pkg != null && pkg != SYSTEMUI) {
            // Only protected apps reach this point: opening one switches the detector off.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                Prefs.autoOff(this) && pkg in Prefs.protectedApps(this)
            ) {
                EventLog.add("protected app opened: $pkg -> detector off")
                Detector.turnOff(this, "Switched off for a protected app")
            }
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastSysUiClass = event.className?.toString()
        }
        // Check now, and once more shortly after: the window list can lag behind the event.
        main.removeCallbacks(check)
        main.post(check)
        main.postDelayed(check, FOLLOW_UP_MS)
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
        EventLog.add("detector stopped")
        main.removeCallbacksAndMessages(null)
        active = false
        reopenPending = false
        BackdropActivity.instance?.finish()
        removeAnchor()
        if (instance === this) instance = null
        announceState()
    }

    /** Tell the widget and the tile that the detector started or stopped. */
    private fun announceState() {
        runCatching { GlassWidget.refresh(applicationContext) }
        runCatching {
            TileService.requestListeningState(
                applicationContext, ComponentName(applicationContext, DetectorTile::class.java)
            )
        }
    }

    // ---------- main logic ----------

    private fun evaluate() {
        val now = SystemClock.uptimeMillis()
        if (now < previewUntil) return
        val ignored = suppressed(now)
        val state = if (ignored) Sys.OTHER else classify()

        // Log only changes, so the recorder stays short and readable.
        if (state != lastState || ignored != lastIgnored) {
            lastState = state
            lastIgnored = ignored
            EventLog.add(
                "state=$state" + (if (ignored) " (ignored: $whyIgnored)" else "") +
                    " qs=$qsOpen backdrop=${BackdropActivity.instance != null}"
            )
        }

        when {
            state == Sys.SHADE -> {
                awaitingReopenUntil = 0L
                active = true
                val backdrop = BackdropActivity.instance
                if (backdrop != null) {
                    backdrop.setActive(true, qsOpen)
                    // Shade still open well after the backdrop came up: it was NOT collapsed by the
                    // launch (e.g. empty notification list), so never re-open it later.
                    if (reopenPending && resumedAt > lastLaunch && now - resumedAt > SURVIVE_MS) {
                        reopenPending = false
                        EventLog.add("shade survived the launch (no reopen)")
                    }
                } else if (now - lastLaunch > LAUNCH_GAP_MS) {
                    // First try, or the last try did not produce a window: (re)start it.
                    reopenPending = true                    // the launch may collapse the shade: re-open it
                    main.removeCallbacks(clearPending)
                    main.postDelayed(clearPending, PENDING_TIMEOUT_MS)
                    launchBackdrop()
                }
            }
            state == Sys.NONE && reopenPending -> {
                reopenPending = false
                awaitingReopenUntil = now + REOPEN_WAIT_MS
                EventLog.add("shade collapsed after launch -> reopening it")
                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)   // bring the shade back over the backdrop
            }
            state == Sys.NONE && now < awaitingReopenUntil -> Unit  // waiting for the shade to come back
            else -> {
                reopenPending = false
                awaitingReopenUntil = 0L
                release()
            }
        }

        // A big System UI window that is not (yet) recognised as the shade: keep looking for a moment,
        // its title / focus can arrive a little after the window itself.
        if (state == Sys.OTHER && !ignored && sysUiPresent) {
            if (otherSince == 0L) otherSince = now
        } else {
            otherSince = 0L
        }

        // Keep checking only while it matters. Never rely on the next event alone.
        val unlockGrace = ignored && power.isInteractive && !keyguard.isKeyguardLocked
        val keepChecking = BackdropActivity.instance != null ||
            reopenPending ||
            now < awaitingReopenUntil ||
            state == Sys.SHADE ||
            unlockGrace ||
            (otherSince != 0L && now - otherSince < OTHER_RECHECK_MS)
        if (keepChecking) {
            main.removeCallbacks(check)
            main.postDelayed(check, POLL_MS)
        }
    }

    /** Fade the backdrop out and remove it. */
    private fun release() {
        active = false
        val backdrop = BackdropActivity.instance ?: return
        EventLog.add("release: glass fading out")
        backdrop.hideAndFinish()
    }

    fun onBackdropResumed() {
        resumedAt = SystemClock.uptimeMillis()
        EventLog.add("glass window ready ${resumedAt - lastLaunch} ms after launch")
        removeAnchor()
    }

    /** Screen off, lock screen, or the moments right after unlock: never show the blur. */
    private fun suppressed(now: Long): Boolean {
        if (!power.isInteractive) {
            whyIgnored = "screen off"
            return true
        }
        if (keyguard.isKeyguardLocked) {
            wasLocked = true
            whyIgnored = "locked"
            return true
        }
        if (wasLocked) {
            wasLocked = false
            unlockedAt = now
        }
        if (now - unlockedAt < UNLOCK_GUARD_MS) {
            whyIgnored = "unlock grace"
            return true
        }
        whyIgnored = ""
        return false
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
        qsOpen = false
        val c = sysUiBigWindows()
        sysUiPresent = c.isNotEmpty()
        if (c.isEmpty()) {
            lastSysUiClass = null
            return Sys.NONE
        }
        val cls = lastSysUiClass?.lowercase().orEmpty()
        val titles = c.mapNotNull { it.title?.toString()?.lowercase() }.filter { it.isNotBlank() }

        // The windows' CURRENT titles are the most reliable; the last event's class can be stale.
        if (titles.any { t -> NOT_SHADE_HINTS.any { t.contains(it) } }) return Sys.OTHER
        val shade = when {
            titles.any { t -> SHADE_HINTS.any { t.contains(it) } } -> true
            titles.isNotEmpty() -> false
            NOT_SHADE_HINTS.any { cls.contains(it) } -> false
            SHADE_HINTS.any { cls.contains(it) } -> true
            else -> c.any { it.isActive || it.isFocused }
        }
        if (!shade) return Sys.OTHER
        qsOpen = titles.any { t -> QS_HINTS.any { t.contains(it) } } || QS_HINTS.any { cls.contains(it) }
        return Sys.SHADE
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
        val now = SystemClock.uptimeMillis()
        val sincePrev = now - lastLaunch
        lastLaunch = now
        addAnchor()
        val ok = runCatching {
            startActivity(
                Intent(this, BackdropActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            )
        }
        EventLog.add(
            "launch glass (anchor=${anchor != null}, ${sincePrev} ms since previous launch)" +
                (if (ok.isFailure) " FAILED: ${ok.exceptionOrNull()?.javaClass?.simpleName}" else "")
        )
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

    /** Windows now plus the flight recorder: what the detector sees, and what it did and when. */
    fun dumpWindows(): String {
        val r = Rect()
        val now = SystemClock.uptimeMillis()
        val sb = StringBuilder(
            "class=${classify()} qs=$qsOpen sysUiClass=$lastSysUiClass backdrop=${BackdropActivity.instance != null} " +
                "locked=${keyguard.isKeyguardLocked} reopenPending=$reopenPending animMs=${Prefs.animMs(this)} " +
                "lastEventAgoMs=${now - lastEventAt} sinceLaunchMs=${now - lastLaunch}\n"
        )
        windows.sortedByDescending { it.layer }.forEach { w ->
            w.getBoundsInScreen(r)
            sb.append("L${w.layer} type=${w.type} title=${w.title} ")
                .append("act=${w.isActive} foc=${w.isFocused} ")
                .append("h=${r.height()} y=${r.top}\n")
        }
        sb.append("\n--- flight recorder (newest last) ---\n").append(EventLog.dump())
        return sb.toString()
    }

    companion object {
        @Volatile var instance: ShadeBlurService? = null
        private const val SYSTEMUI = "com.android.systemui"
        private const val POLL_MS = 80L
        private const val FOLLOW_UP_MS = 150L         // one more look after every event
        private const val OTHER_RECHECK_MS = 1500L    // keep looking at an unrecognised big System UI window
        private const val SURVIVE_MS = 300L           // shade still open this long after backdrop is up = it survived
        private const val LAUNCH_GAP_MS = 600L        // before the launch is tried again
        private const val PENDING_TIMEOUT_MS = 1500L  // slow launches: the collapse can come late
        private const val REOPEN_WAIT_MS = 700L
        private const val ANCHOR_TIMEOUT_MS = 1500L
        private const val UNLOCK_GUARD_MS = 1200L
        private val SHADE_HINTS = listOf("shade", "quick settings", "quicksettings", "notification panel", "notificationpanel")
        private val QS_HINTS = listOf("quick settings", "quicksettings", "qspanel", "qs_panel")
        private val NOT_SHADE_HINTS = listOf("biometric", "authcontainer", "fingerprint", "faceunlock", "udfps", "keyguard", "lockscreen", "bouncer")
    }
}
