# Translucent Notify

Frosted grey layer **behind** the original notification shade on OnePlus 11R (OxygenOS 16).

- v3 used an overlay window. On OxygenOS 16 that drew ABOVE the shade and blurred it too. Dropped.
- v4 uses a NORMAL translucent app window (`BackdropActivity`): non-touchable, non-focusable, blur-behind + grey tint.
  App windows are always below System UI, so the shade stays sharp and fully usable.
- Accessibility service only detects whether the shade is open, then starts/finishes that window.
  No notification listener, root, Shizuku or ADB. No custom icons, text, cards or panels.

Setup: allow "Display over other apps" (only so Android lets the service start the window from the background),
turn on the shade detector, adjust tint/blur.

Known limits: the app under the shade is paused while the backdrop is up; the system may close the shade when
the window opens; blur needs cross-window blur enabled in OxygenOS (else grey tint only);
shade detection is a heuristic (use "Capture windows" in the app).
