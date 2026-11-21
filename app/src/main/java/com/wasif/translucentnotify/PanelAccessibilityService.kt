package com.wasif.translucentnotify

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView

/**
 * Draws its own notification panel with adjustable opacity. A thin touch strip over the
 * status bar catches a swipe-down and opens the panel instead of the real (broken) shade.
 */
class PanelAccessibilityService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private var strip: View? = null
    private var panel: View? = null
    private var list: LinearLayout? = null

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            hidePanel()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        NotificationStore.onChanged = { refreshList() }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOff, filter)
        }
        addStrip()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

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
        hidePanel()
        strip?.let { runCatching { wm.removeView(it) } }
        strip = null
        runCatching { unregisterReceiver(screenOff) }
        NotificationStore.onChanged = null
        if (instance === this) instance = null
    }

    // ---------- edge strip ----------

    private fun addStrip() {
        if (strip != null) return
        val v = View(this)
        var startY = 0f
        var fired = false
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = e.rawY
                    fired = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!fired && e.rawY - startY > dp(40)) {
                        fired = true
                        showPanel()
                    }
                }
            }
            true
        }
        val lp = overlayParams(ViewGroup.LayoutParams.MATCH_PARENT, statusBarHeight(), 0)
        lp.gravity = Gravity.TOP or Gravity.START
        wm.addView(v, lp)
        strip = v
    }

    // ---------- panel ----------

    fun showPanel() {
        if (panel != null) return
        val opacity = Prefs.opacity(this)
        val bgAlpha = 255 * opacity / 100
        val cardAlpha = (255 * (50 + opacity / 2) / 100).coerceIn(0, 255)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.argb(bgAlpha, 14, 20, 32))

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), statusBarHeight() + dp(10), dp(16), 0)
        }
        root.addView(col, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // header: clock + date, Close
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            textSize = 42f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        left.addView(TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            textSize = 14f
            setTextColor(0xFFB7C1D2.toInt())
        })
        header.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(textButton("Close", 0xFFB8D5FF.toInt()) { hidePanel() })
        col.addView(header)

        // title row: Notifications, Clear all
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(18), 0, dp(6))
        }
        titleRow.addView(TextView(this).apply {
            text = "Notifications"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(textButton("Clear all", 0xFFB8D5FF.toInt()) {
            NotificationStore.listener?.cancelAllNotifications()
        })
        col.addView(titleRow)

        // list
        val scroll = ScrollView(this)
        val listView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(listView)
        col.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        list = listView

        // bottom grab bar: tap to close
        val grab = FrameLayout(this)
        grab.addView(View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(0x99FFFFFF.toInt())
            }
        }, FrameLayout.LayoutParams(dp(44), dp(4), Gravity.CENTER))
        grab.setOnClickListener { hidePanel() }
        col.addView(grab, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))

        val lp = overlayParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        wm.addView(root, lp)
        panel = root
        refreshList(cardAlpha)
    }

    fun hidePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        list = null
    }

    private fun refreshList(cardAlpha: Int = currentCardAlpha()) {
        val l = list ?: return
        l.removeAllViews()
        val items = NotificationStore.current()
        if (items.isEmpty()) {
            l.addView(TextView(this).apply {
                text = if (NotificationStore.listener == null)
                    "Notification access is off. Turn it on in the Translucent Notify app."
                else "No notifications"
                textSize = 14f
                setTextColor(0xFFB7C1D2.toInt())
                setPadding(0, dp(20), 0, 0)
            })
            return
        }
        items.forEach { l.addView(card(it, cardAlpha)) }
    }

    private fun currentCardAlpha(): Int =
        (255 * (50 + Prefs.opacity(this) / 2) / 100).coerceIn(0, 255)

    private fun card(sbn: StatusBarNotification, cardAlpha: Int): View {
        val n = sbn.notification
        val pm = packageManager
        val appLabel = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.argb(cardAlpha, 34, 44, 64))
            }
            setOnClickListener { open(sbn) }
        }
        val icon = ImageView(this).apply {
            runCatching { setImageDrawable(pm.getApplicationIcon(sbn.packageName)) }
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(12) })

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = appLabel
            textSize = 12f
            setTextColor(0xFF9FB4D7.toInt())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        if (title.isNotBlank()) texts.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        if (body.isNotBlank()) texts.addView(TextView(this).apply {
            text = body
            textSize = 14f
            setTextColor(0xFFD3DBE8.toInt())
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
        })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(8)
        row.layoutParams = lp
        return row
    }

    private fun open(sbn: StatusBarNotification) {
        hidePanel()
        val pi = sbn.notification.contentIntent
        if (pi != null) {
            try {
                val opts = if (Build.VERSION.SDK_INT >= 34) {
                    ActivityOptions.makeBasic().apply {
                        setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    }.toBundle()
                } else null
                pi.send(this, 0, null, null, null, null, opts)
            } catch (_: PendingIntent.CanceledException) {
            }
        }
        if (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0) {
            NotificationStore.listener?.cancelNotification(sbn.key)
        }
    }

    // ---------- helpers ----------

    private fun textButton(label: String, color: Int, action: () -> Unit) =
        TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(color)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(8), dp(4), dp(8))
            setOnClickListener { action() }
        }

    private fun overlayParams(w: Int, h: Int, extraFlags: Int): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or extraFlags,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        return lp
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(28)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        @Volatile var instance: PanelAccessibilityService? = null
    }
}
