# Translucent Notify

Frosted grey blur **behind** the original notification shade on OnePlus 11R (OxygenOS 16).

- Idle: nothing runs over your apps. No overlay, no window, no receivers. The accessibility service only
  receives events from System UI (`android:packageNames`), never from other apps.
- Shade opens: a normal translucent app window (always below System UI) is started with blur + grey tint
  that fade in. Android collapses the shade when any window starts, so it is re-opened automatically.
- Shade closes: the tint and blur fade out and the window is removed. Fade length is adjustable.
- Ignored: lock screen, the first moments after unlock, screen off, fingerprint popups.
- No notification listener, root, Shizuku or ADB. No custom icons, text, cards or panels.

Setup: allow "Display over other apps" (only so Android lets the service start the window), turn on the
shade detector, adjust tint / blur / animation duration.

Limits: blur needs cross-window blur enabled in OxygenOS (else grey tint only); shade detection is a
heuristic (use "Capture windows" in the app to calibrate).
