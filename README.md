# Shade Background

Native Android experiment for OnePlus 11R (CPH2487) / OxygenOS 15–16.

## Setup
1. Install the APK and open Shade Background.
2. Allow **Display over other apps**.
3. Open Android Accessibility settings and enable **Shade Background**.
4. Open the notification panel. A translucent grey layer will appear.

## Current behavior (after latest fix)
- The app draws a clean translucent grey tint when the notification shade is open.
- **Blur is intentionally disabled by default** so notification icons, Quick Settings tiles and text stay sharp.
- The layer is non-touchable. All gestures, notifications and menus remain fully interactive.

## Important platform limitation
On modern OxygenOS the accessibility / application overlay almost always ends up **above** the SystemUI notification shade.

A normal third-party app **cannot** force a window below the native notification shade without:
- Root + SystemUI modification, or
- An LSPosed / Xposed module that hooks SystemUI.

Because of this limitation the grey layer will sit on top of the shade. We therefore removed `FLAG_BLUR_BEHIND` so the icons and tiles are not frosted/blurred.

## What the accessibility service does
It only watches for a large, top-anchored SystemUI window to decide when the shade is open. It never reads, stores or processes notification content.

## Build
Pushes to `main` trigger the GitHub Actions workflow that builds the APK.
You can also run **Actions → Build APK → Run workflow** manually.
