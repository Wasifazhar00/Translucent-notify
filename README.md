# Shade Background

Native Android experiment for OnePlus 11R / OxygenOS 16.

## Accessibility setup (Android 13+)
1. Open Shade Background.
2. Tap **Step 1 — Open app info**.
3. If Android blocks the service, open the top-right three-dot menu and select **Allow restricted settings**. Android may show this option for sideloaded apps.
4. Return to the app and tap **Step 2 — Open accessibility settings**.
5. Find Shade Background under installed/downloaded services, enable it, and accept Android's confirmation.
6. Return to the app; it checks whether the service is enabled.

Android intentionally requires the user to approve restricted settings and Accessibility. An app cannot silently grant itself these privileges or bypass the confirmation.

## Privacy
The service uses Accessibility window metadata to detect the System UI shade. It does not request Notification Listener access and does not read or store notification text.

## Intended effect and limitation
Only a grey frosted/translucent background layer is attempted behind the original notification shade and Quick Settings. The original System UI owns all icons, notifications, tiles and gestures. The layer uses a non-touchable TYPE_ACCESSIBILITY_OVERLAY and supported blur APIs. Visibility depends on OxygenOS shade window layering and whether the shade surface is translucent. An opaque System UI shade cannot be made transparent by an ordinary app.

## Build
Manually run **Actions → Build APK → Run workflow**.
