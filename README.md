# Translucent Notify

A small native Android app exploring supported ways to adjust the appearance of the system notification shade.

## Compatibility truth

Android does not expose a public API for third-party apps to set the system notification shade's alpha/transparency. A normal app overlay cannot reliably recolor or make System UI transparent. This project therefore detects the device/OS and only exposes an experimental system-property path on the specifically verified ColorOS 17 family. It must report unsupported on OxygenOS/other builds rather than pretending that a preview change affects the real shade.

The experimental ColorOS path uses Shizuku (user-authorized shell privileges) and the `debug.systemui.gaussblur_level` property. The property is proprietary, changes blur/appearance levels rather than a documented continuous alpha value, and is not guaranteed across updates. No System UI process is force-crashed by this app.

## Build

Open the project in Android Studio or run `./gradlew assembleDebug` with JDK 17 and Android SDK 35 installed.

## Manual APK build

GitHub Actions includes a manually-triggered workflow (`workflow_dispatch`). Run it from the Actions tab. The debug APK is uploaded as a workflow artifact for download.

## Permissions

Shizuku is optional and only requested for the experimental compatible-device path. No accessibility service, notification listener, overlay permission, account, analytics, or network access is required.
