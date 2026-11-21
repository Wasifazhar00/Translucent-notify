# Translucent Notify

A compact native Android app for supported notification-shade appearance controls.

## Important Android limitation

Android exposes no public API that lets an ordinary third-party app continuously change the real System UI notification shade's transparency. A normal app overlay cannot reliably alter the shade itself.

This project therefore does not fake an opacity slider. It detects the system build and only enables an experimental proprietary appearance control on ColorOS 17 builds matching the currently researched implementation. It uses Shizuku with explicit user approval to write `debug.systemui.gaussblur_level`. The three device-defined presets are not calibrated opacity percentages and may require a System UI/device restart to take effect.

**OxygenOS 16 / OnePlus 11R is not currently a verified target.** On unrecognized builds the app disables apply/reset and makes no system change. Do not bypass this guard based on version-number similarity; a device-specific implementation must be verified first.

## App characteristics

- Native Kotlin Android app; no Compose or large UI framework.
- Minimum Android version: Android 8.0 (API 26).
- Compile/target SDK: Android 15 (API 35).
- Shizuku is optional and used only for the experimental compatible-device path.
- No root requirement, ads, account, analytics, notification listener, accessibility service, or overlay permission.
- No forced System UI crash/restart.

## Build locally

Install JDK 17, Android SDK Platform 35, Android Build Tools 35.0.0, and Gradle 8.9. Then run:

```sh
gradle --no-daemon assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Build APK from GitHub (manual trigger)

1. Open the repository's **Actions** tab.
2. Select **Build APK**.
3. Press **Run workflow** on the `main` branch.
4. Wait for the job to finish successfully.
5. Download the `translucent-notify-debug-apk` artifact from the completed run and extract the APK.

The workflow is manual-only (`workflow_dispatch`) and retains artifacts for 14 days. It builds an installable debug-signed APK; it does not publish a Play Store release.
