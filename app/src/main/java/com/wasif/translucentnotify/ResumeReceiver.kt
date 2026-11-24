package com.wasif.translucentnotify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Resume after a pause (timer / notification tap) and switch the detector on after boot or update. */
class ResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            OwnerMode.ACTION_RESUME -> OwnerMode.resume(context)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                if (Prefs.ownerAuto(context) && OwnerMode.granted(context)) OwnerMode.setEnabled(context, true)
        }
    }
}
