package com.wasif.translucentnotify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Switching the detector on or off, using only what a normal app is allowed to do:
 *  - OFF: the service can switch itself off (disableSelf). Instant.
 *  - ON: only the user can switch an accessibility service on. We open its own settings page so it is
 *    one tap away (tile, widget, app shortcut, notification, or Android's own accessibility shortcut).
 * No root, no adb, no special permission. While the detector is off it is really off: nothing hides.
 */
object Detector {
    private const val CHANNEL = "detector"
    private const val NOTICE_ID = 42

    private fun component(c: Context) = ComponentName(c, ShadeBlurService::class.java)

    /** Is the detector switched on in Accessibility settings? (Reading this needs no permission.) */
    fun isEnabled(c: Context): Boolean {
        val list = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
        val me = component(c)
        return list.split(':').any { it.isNotBlank() && ComponentName.unflattenFromString(it) == me }
    }

    /** Open the page where the detector can be switched on: its own page if possible, else the list. */
    fun openEnablePage(c: Context) {
        val newTask = Intent.FLAG_ACTIVITY_NEW_TASK
        if (Build.VERSION.SDK_INT >= 31) {
            val opened = runCatching {
                val details = Intent(Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS)
                    .putExtra(Intent.EXTRA_COMPONENT_NAME, component(c))
                    .addFlags(newTask)
                c.startActivity(details)
            }.isSuccess
            if (opened) return
        }
        runCatching { c.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(newTask)) }
    }

    /** Switch the detector off right now (only works while it is running). */
    fun turnOff(c: Context, why: String): Boolean {
        val s = ShadeBlurService.instance ?: return false
        notice(c, why)
        s.disableSelf()
        return true
    }

    /** "Detector is off, tap to turn it on" notification. */
    fun notice(c: Context, text: String) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Detector off", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            c, 2,
            Intent(c, ToggleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        nm.notify(
            NOTICE_ID,
            Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_pause)
                .setContentTitle("Glass detector is off")
                .setContentText("$text. Tap to turn it on.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }

    fun clearNotice(c: Context) {
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTICE_ID)
    }
}

