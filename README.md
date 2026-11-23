# Translucent Notify

Frosted grey blur **behind** the original notification shade on OnePlus 11R (OxygenOS 16).

- Idle: nothing runs over your apps. No overlay, no window, no receivers. The accessibility service only
  receives events from System UI (`android:packageNames`), never from other apps.
- Shade opens: a normal translucent app window (always below System UI) is started with blur + grey tint
  that fade in. Android may collapse the shade when any window starts; if so it is re-opened automatically.
- Shade closes: the tint and blur fade out and the window is removed. Fade length is adjustable.
- Ignored: lock screen, the first moments after unlock, screen off, fingerprint popups.
- No notification listener, root, Shizuku or ADB. No custom icons, text, cards or panels.

## Install

1. Actions > Build release APK > Run workflow > download `translucent-notify-release-apk`.
2. Install `app-release.apk`.
3. Allow "Display over other apps" (only so Android lets the service start the window).
4. Turn on the shade detector in Accessibility. If greyed out: App info > three-dot menu > Allow restricted settings.
5. Adjust tint, blur and animation duration in the app.

## Signing

The workflow signs every build with the same key so updates install over each other.
By default the key is created on the first run and kept in the GitHub Actions cache. Caches are removed after
7 days without a build; then the next APK gets a new key and you must uninstall the old app first.
For a permanent key add these repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Limits: blur needs cross-window blur enabled in OxygenOS (else grey tint only); shade detection is a
heuristic (use "Capture windows" in the app to calibrate).
