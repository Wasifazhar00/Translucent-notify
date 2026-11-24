package com.wasif.translucentnotify

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * Invisible one-tap switch used by the widget, the app shortcut and the notification:
 * detector running -> switch it off now; detector off -> open its switch page.
 */
class ToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ShadeBlurService.instance != null) {
            Detector.turnOff(this, "Turned off by you")
            Toast.makeText(this, "Glass detector off", Toast.LENGTH_SHORT).show()
        } else {
            Detector.openEnablePage(this)
        }
        finish()
    }
}
