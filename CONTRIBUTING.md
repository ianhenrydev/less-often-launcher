# Contributing

Bug reports and focused pull requests are welcome. This is a personal launcher being prepared for wider use; compatibility claims should be backed by device observations.

For a bug, include app version, phone model, Android version/API, steps, expected/actual behavior and whether the accessibility service is connected. For statistics include permission and coverage status. Remove private screens, notification content, device identifiers and baseline files from attachments.

Read [setup](docs/setup.md), [limitations](docs/limitations.md) and [architecture](docs/architecture.md). Keep changes focused. New behavior should describe its effect on sessions, gating, display restoration and privacy where relevant. Do not add telemetry, remote quote dependencies or grant bypass endpoints as incidental changes.

Run from the repository root:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Use `./gradlew` on macOS/Linux. Add meaningful unit coverage for policy/state changes. UI, accessibility and secure-setting behavior need actual-device checks; use [acceptance](docs/acceptance.md) and record exactly what passed and what remains unverified.

Device instrumentation requires separate APKs:

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest :probe:assembleDebug :probe:assembleDebugAndroidTest
.\scripts\test-device.ps1 -Mode display
```

The display mode requires the service disabled. Other modes include `launcher`, `smoke`, `ten-minute`, `notification`, `color-transitions`, `usage`, `history` and `segment`; inspect the acceptance guide and script prerequisites first. These tests drive a real Pixel 6a / API 37 and may disconnect the service. Keep a recovery baseline and avoid other phone use during a run. The test runner attempts reconnection after app-targeted instrumentation, but host termination or USB loss can prevent cleanup.

Do not commit SDKs, local paths, signing keys, APKs, display baselines, diagnostic dumps or screenshots containing private data. Contributions are provided under the repository's MIT license; original bundled quotes retain their CC0 dedication. Third-party dependencies retain their own licenses.
