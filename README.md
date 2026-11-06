# Less Often

An Android launcher that adds a pause before opening distracting apps. Everyday tools open directly; Restricted apps ask you to type a short offline quote, then open for an independent ten-minute session with whole-display grayscale.

Less Often started as a personal launcher. It is now available under the [MIT license](LICENSE), with the bundled original quotes dedicated under [CC0](docs/quotes.md).

## Status and compatibility

**Version 0.2.0 is accepted for personal use**, confirmed November 6, 2026 after more than a week of daily testing. It adds fixed Home tiles, app icons and optional local usage awareness. Version 0.1.1 remains the accepted MVP baseline. See [validation evidence](docs/validation.md) and [current limitations](docs/limitations.md).

- Requires **Android API 37 or newer** (`minSdk = 37`). Check the actual OS/API on your phone; the model name alone does not establish compatibility.
- Device testing so far covers a **Pixel 6a on Android 17 / API 37**. **Pixel 11 has not been tested.** Setup permits API 37+ devices but this is not a compatibility guarantee.
- Installation uses a computer, USB debugging and an explicit ADB permission grant. No root or device-owner provisioning is required.
- There is no Play Store installation flow or production release-signing configuration. Building the repository produces a debug APK.

## How it works

| Group | Behavior |
| --- | --- |
| Primary | Fixed Home tools: Phone, Messages, Camera, Maps, Clock and Settings. |
| Secondary | Alphabetical drawer; opens immediately. |
| Restricted | Separate drawer; type a quote for 600 seconds of access per package. Unknown launchable apps default here. |

Sessions use elapsed time: reopening does not extend them, and background time counts. Notifications and inline actions stay native. Grayscale affects the entire display while a Restricted app is foregrounded, including system UI and the keyboard.

The app list is currently a source configuration inherited from the original author's phone: **6 Primary, 57 Secondary, 40 Restricted** entries. Missing installed apps are omitted and listed in setup. Review [customization](docs/setup.md#customize-the-app-groups) before using it on another phone.

Optional Usage Access enables local screen-time, unlock and Restricted-use measurements. Optional Notification Access counts observed notification instances without storing their contents. Tap today's activity on Home for totals, coverage and seven-day history. A dash means unavailable; partial coverage may omit activity. Comparisons need at least three eligible prior days through a shared time of day.

## Get started

Follow the [complete setup guide](docs/setup.md) for prerequisites, building, installation, permissions, Pixel 11 checks, updates and troubleshooting.

On Windows, after installing JDK 17, Android SDK Platform 37, Build Tools 36.0.0 and Platform Tools, and setting `JAVA_HOME` and `ANDROID_HOME`:

```powershell
git clone https://github.com/ianhenrydev/less-often-launcher.git
cd less-often-launcher
.\scripts\build.ps1
adb devices
.\scripts\setup-device.ps1
```

Connect exactly one authorized phone. Setup records a private display recovery baseline, installs the APK, grants `WRITE_SECURE_SETTINGS` and opens setup. On the phone, allow restricted settings if Android requests it, enable the accessibility service, and choose Less Often as Home. Usage and Notification Access are optional. Read the full guide before making it your daily launcher.

**For a second phone, use its own baseline file** (for example `-Baseline .\artifacts\brother-display-baseline.private.json`). Never restore one phone's baseline onto another. The script preserves existing files, so choose a fresh path for each phone.

## Privacy and permissions

There is no Internet permission, analytics, telemetry, account or runtime quote service. Runtime state lives in app-private SharedPreferences; backups and device transfer are disabled. See [privacy details](docs/privacy.md).

Accessibility observes package/window metadata to gate apps, and secure-settings access applies/restores grayscale. These are powerful permissions you explicitly enable. The launcher is personal friction, not a security boundary: its owner can bypass it by disabling the service or switching launchers. Brief app exposure before detection is possible.

## Recovery and uninstall

Keep the private baseline file. Before uninstalling or clearing app data, disable Less Often accessibility, select another Home app, and restore/verify any pending display baseline. Force-stop or crashes can leave grayscale behind. Follow [recovery and rollback](docs/recovery.md), including exact ADB restoration commands; do not guess color-setting defaults.

## Development

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

macOS/Linux: use `./gradlew` with the same tasks (run `chmod +x gradlew` if needed). The PowerShell helper scripts are designed for Windows; use the manual ADB setup instructions elsewhere.

Output: `app/build/outputs/apk/debug/app-debug.apk`. Toolchain: Gradle 9.5.0 with wrapper checksum, Android Gradle Plugin 9.3.3, Compose compiler 2.3.10, Compose BOM 2026.09.00, Activity Compose 1.12.4. Compile/target/min SDK are 37. Ignored `.tools/` is a local convenience, not a repository dependency.

- [Contributing and verification](CONTRIBUTING.md)
- [Architecture](docs/architecture.md)
- [Manual acceptance checklist](docs/acceptance.md)
- [Validation history](docs/validation.md)
- [Closed MVP specification](launcher-mvp-spec.md) and [0.2 specification](launcher-v0.2-spec.md)
- [Open-source release checklist](docs/releasing.md)

The separate `probe` module and device instrumentation are development tools, excluded from the launcher APK. Device test scripts deliberately require the validated Pixel 6a / API 37 and drive the phone; see the acceptance guide before running them.
