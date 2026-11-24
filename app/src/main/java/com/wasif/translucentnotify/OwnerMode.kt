package com.wasif.translucentnotify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings

/**
 * Owner mode. Works only after ONE one-time grant of WRITE_SECURE_SETTINGS (adb shell pm grant ...).
 * With it the app can switch its own accessibility service on and off, so it can:
 *  - turn the detector back on by itself (boot, app update, app opened, Quick Settings tile),
 *  - revive it if the system stopped it,
 *  - pause it while a protected app is open and resume it later (timer or notification tap).
 * It never hides the service from other apps: while paused the service is really switched off.
 */
object OwnerMode {
    const val ACTION_RESUME = "com.wasif.translucentnotify.RESUME"
    private const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"
    private const val CHANNEL = "owner"
    private const val NOTICE_ID = 42

    fun granted(c: Context) = c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun me(c: Context) = ComponentName(c, ShadeBlurService::class.java)

    private fun enabledList(c: Context): List<String> =
        Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').filter { it.isNotBlank() }

    /** Is the detector switched on in Accessibility settings? */
    fun isEnabled(c: Context): Boolean {
        val me = me(c)
        return enabledList(c).any { ComponentName.unflattenFromString(it) == me }
    }

    fun setEnabled(c: Context, on: Boolean): Boolean {
        if (!granted(c)) return false
        val me = me(c)
        return runCatching {
            val rest = enabledList(c).filter { ComponentName.unflattenFromString(it) != me }
            val next = if (on) rest + me.flattenToString() else rest
            Settings.Secure.putString(
                c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next.joinToString(":")
            )
            if (on) Settings.Secure.putInt(c.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        }.getOrDefault(false)
    }

    /** Switch the detector off. [minutes] = resume by itself after that long; null = only on request. */
    fun pause(c: Context, why: String, minutes: Int?) {
        if (!setEnabled(c, false)) return
        notice(c, why)
        if (minutes != null) scheduleResume(c, minutes) else cancelResume(c)
    }

    fun resume(c: Context) {
        cancelResume(c)
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTICE_ID)
        setEnabled(c, true)
    }

    /** Off then on again: makes Android bind the service afresh if it was stopped. */
    fun revive(c: Context) {
        if (!setEnabled(c, false)) return
        Handler(Looper.getMainLooper()).postDelayed({ setEnabled(c, true) }, 400)
    }

    private fun resumeIntent(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 1,
        Intent(c, ResumeReceiver::class.java).setAction(ACTION_RESUME),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun scheduleResume(c: Context, minutes: Int) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + minutes * 60_000L,
            resumeIntent(c)
        )
    }

    private fun cancelResume(c: Context) {
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(resumeIntent(c))
    }

    private fun notice(c: Context, text: String) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Detector paused", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle("Glass detector paused")
            .setContentText("$text. Tap to resume.")
            .setContentIntent(resumeIntent(c))
            .setAutoCancel(true)
            .build()
        nm.notify(NOTICE_ID, n)
    }
}
