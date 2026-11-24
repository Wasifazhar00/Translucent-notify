# Translucent Notify

Frosted glass **behind** the original notification shade on OnePlus 11R (OxygenOS 16). Personal-use app.

- Idle: nothing runs over your apps. No overlay, no window, no receivers. The accessibility service only
  receives events from System UI (plus apps you mark as protected), never from other apps.
- Shade opens: a normal translucent app window (always below System UI) fades in with blur, tint colour and
  darkness. Android may collapse the shade when any window starts; if so it is re-opened automatically.
- Shade closes: the glass fades out and the window is removed. Fade length is adjustable.
- Day / night glass profiles, colour presets, hue / strength / lightness / darkness sliders, live preview.
- Ignored: lock screen, the first moments after unlock, screen off, fingerprint popups.
- No root, no adb, no developer options, no special permissions, no notification listener.

## Install

1. Actions > Build release APK > Run workflow > download `translucent-notify-release-apk`.
2. Install `app-release.apk`.
3. Allow "Display over other apps" (only so Android lets the service start the window).
4. Turn the detector on (button in the app). If greyed out: App info > three-dot menu > Allow restricted settings.

## Banking and protected apps

Some banking apps refuse to run while any accessibility service is on. This app does not hide from those checks.
Instead the detector really switches itself off:

- OFF is instant: the service switches itself off (`disableSelf`) when a protected app opens, or on one tap
  (tile, widget, app shortcut).
- ON always needs one tap from you (Android allows nothing else without adb): tap the "Glass detector is off"
  notification, the tile, the widget or the app shortcut. Android's own accessibility shortcut toggles it too.

## Signing

The workflow signs every build with the same key so updates install over each other. By default the key is
created on the first run and kept in the GitHub Actions cache (removed after 7 days without a build; then
uninstall the old app first). For a permanent key add repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`.

Limits: blur needs cross-window blur enabled in OxygenOS (else tint only); shade detection is a heuristic
(use "Capture windows" in the app to calibrate).
