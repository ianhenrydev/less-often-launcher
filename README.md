# Less Often

A personal, text-only Android launcher for a Pixel 6a running Android 17 (API 37). Primary tools open immediately. Secondary apps have their own alphabetical list. Restricted packages require typing an offline quote for an independent 600-second elapsed-time window, and use whole-display grayscale while foregrounded.

**MVP accepted and spec closed:** October 30, 2026, at version **0.1.1**, following the user's successful testing. The [closed MVP spec](launcher-mvp-spec.md) is the baseline for continued development; subsequent changes should use new specs. Detailed verification coverage remains in the validation report.

**Current version: 0.1.1.** The update fixes color flashes during Restricted-app activity changes. The Pixel passed 32 activity transitions with zero grayscale-setting writes, exact Home restoration, real 10-minute foreground expiry, keyboard/quote checks, and same-boot process recovery. See [docs/validation.md](docs/validation.md) for evidence and the remaining manual checks. The packaged APK is `artifacts/LessOften-0.1.1-debug.apk`.

## Build

Use JDK 17 and Android SDK Platform 37.0 with Build Tools 36.0.0. Set `ANDROID_HOME` to the SDK, or use an untracked `local.properties` containing `sdk.dir=...`. On Windows escape a drive colon (`C\:/...`).

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The sideloadable, debug-signed APK is `app/build/outputs/apk/debug/app-debug.apk`. Keep the same development signing key for future updates; Android rejects differently signed updates. Debug signing is intended for this personal ADB workflow, not store distribution. Do not uninstall to fix a signing mismatch without first recovering the recorded color baseline.

Pinned toolchain: Gradle 9.5.0 (wrapper with SHA-256), Android Gradle Plugin 9.3.3, built-in Kotlin / Compose compiler 2.3.10, Compose BOM 2026.09.00, Activity Compose 1.12.4, JUnit 4.13.2, JVM-test JSON 20250517. Compile/target/min SDK are 37. No network permission or runtime quote API is used.

This workspace also has a project-local JDK and SDK under ignored `.tools/`. `scripts/build.ps1` can find that JDK. On another computer install the prerequisites above; `.tools/` is not a shipped dependency.

## Install and enable

Connect exactly one authorized Pixel 6a with USB debugging enabled:

```powershell
.\scripts\setup-device.ps1
```

The script verifies the device model/API, saves exact color-setting values in ignored `artifacts/display-baseline.private.json`, installs the APK, grants the declared development permission, and opens setup. It never overwrites an existing baseline file. Before running setup against an already active installation, retain that file and use the app's journal for the latest baseline if necessary.

On the phone:

1. If Android reports restricted settings, open Settings → Apps → Less Often → App info menu → Allow restricted settings. This is an explicit user step.
2. In Less Often setup, open Accessibility settings and enable Less Often. The service uses window/package metadata; it does not traverse messages or app contents.
3. Use **Choose Home app** and select **Less Often**.
4. Complete [the device acceptance checks](docs/acceptance.md). In particular verify opaque gating, IME interaction, notification/link destinations, and one real 10-minute foreground session.

Exact manual permission/install commands:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.lessoften.launcher android.permission.WRITE_SECURE_SETTINGS
adb shell am start -n com.lessoften.launcher/.SetupActivity
```

The setup activity is also the Less Often app entry in other launchers and is accessible through the accessibility service's settings link. Its capability status reports disconnection, missing permission, or pending display recovery. Normal Home remains text-only. Phone and Android Settings are reachable from both Home and setup.

## Source configuration

`app/src/main/assets/app-buckets.json` is the authoritative versioned configuration, extracted verbatim from the specification: **6 Primary, 57 Secondary, 40 Restricted**, with case-sensitive identifiers. Unknown launchable packages default to Restricted. To change policy, edit source and rebuild/install an update. There is no runtime policy editor or session-grant broadcast/deep link.

Primary labels/order follow the approved names. Secondary/Restricted display real installed activity labels, sorted using the device locale. Each launcher activity is a separate entry; sessions belong to its package. The two package-only Secondary apps use Android's installed labels. Missing entries are omitted and listed in setup. A configured package's mapping survives uninstall/reinstall. Only this launcher's own components and non-drawer platform infrastructure avoid the unknown-app default; other launchers and explicitly Restricted system apps are not broadly exempted.

Quotes and licensing: [docs/quotes.md](docs/quotes.md). Runtime state is private SharedPreferences, synchronously committed before grants or color writes. Backup/device-transfer exclusions prevent copying sessions or display journals to another device. No unfinished challenge is persisted.

## Recovery and limits

See [docs/recovery.md](docs/recovery.md) for exact baseline restoration, service/launcher rollback, force-stop recovery, and ADB commands. Do this before uninstalling if a transform is pending.

The service detects and covers an app after it appears; brief exposure is possible. This is owner-controlled personal friction. Accessibility disconnection, force-stop, changing launchers, or uninstalling can bypass it. Notifications/previews/inline actions stay native. Restrictions are package-wide, including Sleeper, and do not filter embedded content in permitted apps. Split-screen/PiP are excluded. Grayscale affects the whole display, including IME/system UI while a Restricted app is active. Concurrent color-setting changes pause automatic writes and require reviewing the journal; bedtime/other transformations need device testing.

## Validation tools

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest :probe:assembleDebug :probe:assembleDebugAndroidTest
.\scripts\test-device.ps1 -Mode display      # service must be disabled; exact settings round trip
.\scripts\test-device.ps1 -Mode smoke        # service enabled, Home chosen, unlocked phone
.\scripts\test-device.ps1 -Mode ten-minute   # real production-duration foreground test
.\scripts\test-device.ps1 -Mode notification # native notification destination, IME, punctuation and alias
.\scripts\test-device.ps1 -Mode color-transitions # 32 real activity transitions; no grayscale-setting changes
```

The `probe` module is a disposable app with synthetic destinations and a native test notification. It is never included in the launcher APK. The test runner uses actual typed key events and the production quote/duration rules, with no grant endpoint. Smoke/ten-minute tests require the probe to be locked at the start: wait until any previous test session expires. They drive the phone; avoid other phone use during a run. See [docs/acceptance.md](docs/acceptance.md) for test coverage and cleanup.
