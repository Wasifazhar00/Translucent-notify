package com.wasif.translucentnotify

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile. Detector running: one tap switches it off at once (before a banking app).
 * Detector off: one tap opens its switch page (Android only lets the user switch it on).
 */
class DetectorTile : TileService() {
    private val main = Handler(Looper.getMainLooper())

    override fun onStartListening() = refresh()

    override fun onStopListening() {
        main.removeCallbacksAndMessages(null)
    }

    override fun onClick() {
        if (ShadeBlurService.instance != null) {
            Detector.turnOff(this, "Turned off from the tile")
            main.postDelayed({ refresh() }, 500)
        } else {
            openToggle()
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.label = "Glass blur"
        tile.state = if (ShadeBlurService.instance != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    @Suppress("DEPRECATION")
    private fun openToggle() {
        val intent = Intent(this, ToggleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
