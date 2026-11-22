# Translucent Notify

Frosted grey layer **behind** the original notification shade on OnePlus 11R (OxygenOS 16).

- One non-touchable `TYPE_APPLICATION_OVERLAY` window (layer 12) with blur-behind + grey tint.
- Status bar (15) and notification shade (17-19) draw above it, so they stay sharp and fully usable.
- Accessibility service only detects whether the shade is open. No notification listener, root, Shizuku or ADB.
- No custom icons, text, cards or panels.

Setup: allow "Display over other apps", turn on the shade detector, adjust tint/blur.
Limit: if OxygenOS has blur switched off, only the grey tint is shown. Shade detection is a heuristic;
use "Capture windows" in the app to calibrate it.
