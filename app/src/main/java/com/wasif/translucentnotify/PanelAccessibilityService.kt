package com.wasif.translucentnotify

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Intentionally does not create a window over the notification shade.
 *
 * A normal third-party app cannot place an overlay underneath the privileged
 * SystemUI notification shade on OxygenOS. Keeping this service inert prevents
 * the app from covering or blurring notification content.
 */
class PanelAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}
