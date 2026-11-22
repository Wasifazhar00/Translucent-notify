# Translucent Notify

Frosted grey layer **behind** the original notification shade on OnePlus 11R (OxygenOS 16).

- v3 used an overlay window. On OxygenOS 16 it drew ABOVE the shade and blurred it too. Dropped.
- v4 used a normal translucent app window, but launched it when the shade opened. Starting an activity
  collapses the shade, so the shade closed by itself.
- v4.1: the translucent app window (`BackdropActivity`) is launched EARLIER, while the shade is closed, and
  kept invisible behind the foreground app. When the shade opens, only blur + tint are switched on in that
  existing window. Nothing starts while the shade is open. App windows are always below System UI, so the
  shade stays sharp and usable.
- Accessibility service only detects the shade and the foreground app. No notification listener, root,
  Shizuku or ADB. No custom icons, text, cards or panels.

Setup: allow "Display over other apps" (only so Android lets the service start the window from the background),
turn on the shade detector, adjust tint/blur.

Known limits: the app in front is kept "paused" while the invisible backdrop sits above it (video may pause);
if an app opens a new screen in its own task the backdrop can end up below it until the next app change;
blur needs cross-window blur enabled in OxygenOS (else grey tint only); shade detection is a heuristic
(use "Capture windows" in the app).
