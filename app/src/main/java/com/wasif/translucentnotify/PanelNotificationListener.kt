package com.wasif.translucentnotify

import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PanelNotificationListener : NotificationListenerService() {
    private val main = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        NotificationStore.listener = this
        changed()
    }

    override fun onListenerDisconnected() {
        if (NotificationStore.listener === this) NotificationStore.listener = null
        changed()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        changed()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        changed()
    }

    private fun changed() {
        main.post { NotificationStore.onChanged?.invoke() }
    }
}
