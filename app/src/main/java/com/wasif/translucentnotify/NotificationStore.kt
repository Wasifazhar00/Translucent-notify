package com.wasif.translucentnotify

import android.app.Notification
import android.service.notification.StatusBarNotification

object NotificationStore {
    @Volatile var listener: PanelNotificationListener? = null
    @Volatile var onChanged: (() -> Unit)? = null

    fun current(): List<StatusBarNotification> {
        val all = runCatching { listener?.activeNotifications?.toList() }.getOrNull()
            ?: return emptyList()
        return all
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .sortedWith(
                compareBy<StatusBarNotification> { it.isOngoing }
                    .thenByDescending { it.postTime }
            )
    }
}
