# Implementation prompt: OnePlus 11R notification-shade frosted background

Build a native Kotlin Android app for OnePlus 11R (CPH2487), Android 16 / OxygenOS 16.

## Exact goal
When the user swipes down to open the existing OxygenOS notification shade, show only a full-screen frosted/translucent neutral-grey sheet behind the original System UI. Keep the original status icons, clock/date, battery/network indicators, notifications, empty-state message, Quick Settings tiles, horizontal page switching, gestures and navigation bar exactly as OxygenOS draws them.

The reference is a full-screen pale grey softly blurred sheet. There must be no app-created panel, clock, icon, text, cards, buttons, replacement shade, swipe trigger, or custom notification list. The effect remains while the notification shade and Quick Settings pages are open, and disappears when the shade closes.

## Constraints and permissions
- No root, Shizuku, ADB runtime, privileged signature permission, or SystemUI modification.
- Accessibility permission is allowed only to detect the System UI shade window.
- Do not request Notification Listener access; never read, copy, store, or process notification content.
- Do not request SYSTEM_ALERT_WINDOW unless a tested device-specific reason proves it necessary.
- Preserve real shade touch handling and gestures.
- Prefer a non-touchable TYPE_ACCESSIBILITY_OVERLAY background layer, and verify it is below TYPE_NOTIFICATION_SHADE on the target ROM.
- Use translucent grey fill and cross-window blur (FLAG_BLUR_BEHIND / setBlurBehindRadius where supported); handle disabled blur.
- Detect open/closed state through accessibility events and SystemUI window metadata, not swipe interception or a fake panel.
- Remove the layer on shade close, service disconnect, screen off, and service shutdown.
- Never show the layer on ordinary app screens or unrelated SystemUI dialogs.

## Device validation
1. Verify shade open/close detection.
2. Verify overlay is below SystemUI and does not cover/blur SystemUI text, icons or tiles.
3. Verify shade and Quick Settings remain fully touchable and navigable.
4. Verify grey tint/blur is visible through OxygenOS shade.
5. If the shade surface is opaque, stop and report the limitation; do not draw above the shade or fake success.
6. Test empty/non-empty notifications, expanded Quick Settings, horizontal page changes, rotation, screen off/on and service restart.
7. Build/install APK and report actual results separately.

## Acceptance criteria
The only visible app contribution is the reference-style translucent blurred grey background behind the original notification area. No extra panel functionality is permitted. If OxygenOS prevents this without privileged SystemUI modification, document that limitation rather than shipping a misleading workaround.
