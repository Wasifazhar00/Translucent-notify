package com.wasif.translucentnotify

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: one tap switches the detector off (before a banking app) or back on. */
class DetectorTile : TileService() {
    private val main = Handler(Looper.getMainLooper())

    override fun onStartListening() = refresh()

    override fun onClick() {
        if (OwnerMode.granted(this)) {
            if (OwnerMode.isEnabled(this)) OwnerMode.pause(this, "Paused from the tile", null)
            else OwnerMode.resume(this)
            main.postDelayed({ refresh() }, 600)
        } else {
            openAccessibilitySettings()
        }
    }

    override fun onStopListening() {
        main.removeCallbacksAndMessages(null)
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.label = "Glass blur"
        tile.state = if (OwnerMode.isEnabled(this)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    @Suppress("DEPRECATION")
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
