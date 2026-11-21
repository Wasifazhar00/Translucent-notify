# Shade Background

Native Android experiment for OnePlus 11R (CPH2487) / OxygenOS 15–16.

## Setup
1. Install the APK and open Shade Background.
2. Allow **Display over other apps**.
3. Open Android Accessibility settings and enable **Shade Background**.
4. Open the notification panel.

## Current behavior (Smart Region version)

Because a normal app cannot place a window *under* the SystemUI notification shade, the overlay stays on top.

To reduce the impact on icons and tiles we use a **smart non-uniform tint**:

- **Center area** (where notifications + Quick Settings usually sit) → much lighter / more transparent
- **Outer edges** → stronger grey tint
- Completely non-touchable so all gestures still work

This is not perfect hole-punching, but it keeps the main content more readable than a flat full-screen blur/tint.

## Platform limitation
On modern OxygenOS a third-party app cannot force a layer below the notification shade. True under-panel blur requires root + LSPosed / SystemUI modification.

## Build
Pushes to `main` automatically trigger the GitHub Actions APK build.
