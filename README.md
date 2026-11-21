# Translucent Notify

A minimal native Android app explaining the system-level limits on notification-shade transparency.

## Important limitation

Android 16 does not expose a supported public API for an ordinary third-party app to change the real System UI notification shade's transparency. This app therefore does not show a fake opacity slider or claim to apply unsupported changes.

## App characteristics

- Native Kotlin Android app.
- No Shizuku, root, ADB commands, hidden system commands, or background services.
- No notification listener, accessibility service, overlay permission, ads, analytics, or Internet permission.
- Minimal UI and no special permissions.

## Build APK from GitHub

1. Open the repository's **Actions** tab.
2. Select **Build APK**.
3. Press **Run workflow** on the `main` branch.
4. Download the generated APK artifact after the workflow succeeds.

The workflow is manually triggered.
