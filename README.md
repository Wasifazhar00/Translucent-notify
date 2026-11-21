# Shade Background

Native Android experiment for OnePlus 11R (CPH2487) / OxygenOS 16.

## Setup
1. Install the APK and open Shade Background.
2. Allow **Display over other apps**.
3. Open Android Accessibility settings and enable **Shade Background**.
4. Open the notification panel. The app attempts to show a grey translucent/blurred layer beneath the native SystemUI shade.

## Intended behavior
The app does not replace the notification shade. It does not draw over notification icons, notification cards, Quick Settings tiles, menus, or controls. The layer is a non-touchable application overlay placed below critical SystemUI windows. When the native shade has transparent areas, the tint can show through those areas while native shade content remains above it.

Accessibility window metadata is used only to detect a large, top-anchored SystemUI window. The service does not traverse notification nodes, read notification text, or store notification content.

## Important platform limitation
This approach depends on OxygenOS exposing the shade as a transparent SystemUI window and allowing the application overlay to remain visible underneath it. If OxygenOS makes the shade background opaque or suppresses application overlays while the shade is open, a normal third-party app cannot force the native SystemUI background to become transparent. That requires OEM/SystemUI support or privileged system modification.

## Build
Manually run **Actions → Build APK → Run workflow**. Pushes to main also trigger the build.
