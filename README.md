# Shade Background

Native Android experiment for OnePlus 11R / OxygenOS 16.

## Intended effect
Only a full-screen neutral-grey frosted/translucent background while the original System UI notification shade or Quick Settings pages are open. Original clock, icons, notifications, tiles, gestures and navigation remain System UI-owned. No app-created panel or notification content.

## Approach
- AccessibilityService detects System UI shade window metadata.
- A non-touchable TYPE_ACCESSIBILITY_OVERLAY is used as a background layer.
- AOSP window type ordering places accessibility overlays below TYPE_NOTIFICATION_SHADE.
- Grey translucent fill and cross-window blur are used where supported.
- Layer is removed when shade closes.
- No notification listener, root, Shizuku, ADB, or draw-over-other-apps permission.

## Important limitation
This is an experiment, not a guaranteed System UI modification. The effect is visible only if OxygenOS renders the notification-shade surface translucently. If the shade is fully opaque, an ordinary app cannot change that System UI surface. OxygenOS may also use different accessibility window class names; detection must be tested on the actual device. The service does not inspect or store notification text.

## Build
Manually run **Actions → Build APK → Run workflow**.
