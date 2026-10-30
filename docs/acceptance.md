# Real Pixel acceptance procedure

Use the Pixel 6a / API 37, not emulator results. Only [validation.md](validation.md) records completed checks. This is the executable checklist for remaining checks; written expectations are not test results.

## Prerequisites and automated slices

Build/install as described in README. Save baseline values before testing. Enable accessibility yourself through Android Settings and choose Less Often as Home. Do not change unrelated app notifications. Keep the phone awake/unlocked for interactive checks and use synthetic probe data wherever possible.

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :probe:assembleDebug :probe:assembleDebugAndroidTest
.\scripts\test-device.ps1 -Mode display
# The display mode requires accessibility disabled to isolate the actual app-permission round trip.
# Re-enable the service yourself before smoke/ten-minute modes.
.\scripts\test-device.ps1 -Mode smoke
.\scripts\test-device.ps1 -Mode notification # start with the probe locked, e.g. after real expiry
.\scripts\test-device.ps1 -Mode color-transitions
```

Smoke installs an unconfigured package with two launcher components. It requires the probe to be locked at the start. It observes opaque challenge presence after a real app launch, cancels via Home, verifies nonrepeat, rejects accessibility clipboard/set-text insertion, types the quote as real key events, and checks that the exact synthetic deep-link screen remains underneath after completion. A second deep link must reopen without another quote during the original package window. It records time from launch request to observed gate, including process startup, rather than claiming zero latency.

Before starting another mode, allow the probe's current window to expire. For a real production-duration session:

```powershell
.\scripts\test-device.ps1 -Mode ten-minute
```

This performs the same smoke steps then leaves the probe foregrounded until the actual 600-second production session expires. Do not use the phone during the wait. The runner never edits deadlines or production duration. It brackets the real grant between completion click and observed gate removal, rejects early gating, and allows two seconds of observation/scheduling latency after the upper grant bound. Reopening must not extend the deadline. Device sleep and manual changes invalidate this foreground-only measurement; repeat if interrupted.

The probe's `display` mode can separately open Sleeper externally, verify the gate and secure-key transform, and return Home to verify baseline, when the service is enabled:

```powershell
adb shell am instrument -w -e mode display com.lessoften.probe.test/com.lessoften.probe.ProbeInstrumentation
```

Notification mode posts only a synthetic probe notification, confirms its native preview data while the package is locked, sends a synthetic response through its real inline-action PendingIntent without opening an activity, then taps its row in the actual Android notification shade. It verifies the gate and destination, opens/closes/reopens the real IME, rejects incorrect punctuation, accepts a correctly typed case/whitespace variation, and checks the second launcher alias shares package access. The inline-action invocation is automated at the PendingIntent boundary; manual notification-toolbar typing and other apps' existing notification preferences remain separate checks. No user's clipboard content is read or replaced.

If the shade row cannot be found by automation, notification mode reports that explicitly and sends the notification's captured, real content PendingIntent with test sender launch options. That still exercises its actual destination and the foreground gate, but does not verify a literal tray tap. The launcher itself gets no extra background-launch privilege.

Color-transition mode first verifies a genuine same-boot, committed access window for the disposable package, completing the normal quote when necessary. It opens 16 distinct Activity windows and navigates back through all 16. A native secure-setting observer must see zero enable-setting writes during that phase, and returning Home must restore both exact baseline settings. An expired or almost-expired previous window is not extended. The read-only deadline precondition uses Android elapsed realtime and boot count, rather than a host clock or an assumed interpretation of `/proc/uptime`.

The disposable probe uses its own `FLAG_KEEP_SCREEN_ON` for the real foreground-duration check and an instrumentation-only screen wake lock during interactive runs. It fails fast if keyguard is locked. The launcher does not change the phone's screen timeout, and no device-wide keep-awake setting is changed.

## Launcher/catalog

- UI-01/02: inspect Home's exact Phone, Messages, Camera, Maps, Clock, Settings order. Confirm text-only dark layout, ordinary status/navigation bars, distinct Secondary/Restricted navigation and alphabetized scrollable lists. Back and Home return Home. Launch one Primary and one Secondary.
- UI-03: compare setup/catalog with the approved configuration. The initial exact 6/57/40 mapping is unit-tested; current installed entries are also reported by display instrumentation. Verify PPM Community / RAZ Care labels from Android rather than inferred names.
- UI-04/05: install/update/disable/enable/remove/reinstall only the disposable probe. It must appear Restricted without source edits; both launcher components must share one package window. After removing it, no stale launch target should remain. Avoid uninstalling any existing user app.
- UI-06: inspect the main manifest and installed exported components. Only Home/setup launch surfaces, protected service binding and private boot receiver exist. No intent extra or UI changes buckets or grants a session.

## Quotes, cancellation, IME and safety

- GATE-01/02/03: open locked Sleeper; try wrong words/punctuation, changed capitalization, repeated spaces/newlines and a correct answer. Only the approved normalization is accepted. Try ordinary paste, selection menu, Ctrl+V, Shift+Insert, clipboard toolbar content and drag/drop. Plain-text commits emitted by a custom keyboard remain outside the enforcement boundary.
- GATE-04: type partial input then leave with Home, Back, Recents, or another app. Returning must require a different quote with blank input, including across different Restricted packages. Repeat for a launcher-originated attempt while Home is underneath.
- GATE-05: tap input to show Gboard, type, hide/show IME, use multiline scrolling and complete the action with the keyboard visible. IME events alone must not cancel the attempt or expose underlying controls.
- GATE-06/OPS-02: lock during partial input; unlock; confirm partial text is discarded and no overlay blocks keyguard. With the user present, exercise an incoming ordinary call and safety/system interruptions, messaging, authentication, Wallet/tickets and navigation. Do not place real emergency calls as a test.

## Time and lifecycle

- TIME-01/02: unlock Sleeper; note its completion time; reopen without extension. Open locked Chrome and verify a separate quote/window. Confirm a second activity/alias from the same package shares the first deadline.
- TIME-03/04/05: run the real 10-minute test; separately let a session expire in the background while using Settings and while locked/asleep. No background expiry should interrupt another app; return after expiry must gate. Foreground expiry must gate without waiting for a new launch/event.
- TIME-06: after a successful window, record its elapsed deadline privately and stop/restart the launcher process (not the whole phone). Re-enable/reconnect as needed; the original deadline must persist without extension. Do not interpret force-stop cleanup as guaranteed.
- TIME-07: with user coordination reboot, reconnect and unlock. Both packages must require new quotes. Recovery must restore any pending transform when no longer in Restricted context.
- TIME-08: with user coordination change/restore wall time/timezone during a session; elapsed deadline must remain unchanged. Unit tests already use an injected elapsed clock but cannot substitute for the device lifecycle checks.

## Entry destinations and native notifications

- ENTRY-01/02: open locked app from launcher, deep link, native notification and Recents. For external entries complete the quote and verify the same requested screen/state, with no generic-home relaunch. Use the probe's `lessoften-test://deep-link/42` and `lessoften-test://notification/42` destinations, then verify real Sleeper navigation/notifications with the user present.
- NOTE-01/02: while the probe is unlocked, tap **Post native test notification**, then allow its session to expire in Home. The notification/preview must remain. Use **Reply test** without opening the app; it should show “Inline reply received without opening the app.” Post another notification to test its content tap while locked. Native notification opens the destination only after the gate. Actual app previews/actions remain subject to the user's existing preferences; never enable another app's disabled notifications to make a test pass.

## Color and operational recovery

- COLOR-01/02/03: visually inspect colored content in an unlocked Restricted app, then Camera, Maps and Home. Restricted-to-Restricted must stay grayscale and preserve the original baseline. Secure-key readings alone do not prove visible output or all OS transform interactions.
- COLOR-04: record settings; with user coordination test a non-default correction and absent keys, entering/leaving Restricted each time. Verify exact values/existence afterward. Restore the original values. Unit tests cover these cases, but deliberate device-setting changes should be recorded separately.
- COLOR-05: exercise process restart and reboot while a transform is owned, both in/out of Restricted context. Ensure journal recovery never saves its own temporary grayscale as baseline. Run the exact recovery script if a crash/force-stop leaves a transform behind.
- OPS-01: disable accessibility and separately revoke `WRITE_SECURE_SETTINGS` with ADB. Setup must identify capability loss. Regrant using README command. Do not leave a revoked permission or grayscale behind at handoff.
- With user coordination exercise bedtime and any existing color-correction settings. If secure-key ownership becomes ambiguous, automatic writes must pause and setup report a conflict; choose how to resolve it before adding an override policy.

Split-screen and PiP are excluded; do not mark them as validated operating modes.

## Test-only cleanup

After testing, return Home and ensure the normal baseline is restored. The following removes only development packages installed by these scripts:

```powershell
adb uninstall com.lessoften.launcher.test
adb uninstall com.lessoften.probe.test
adb uninstall com.lessoften.probe
```

Keep the production launcher and its runtime journal. Do not clear app data or delete the private baseline while recovery is pending. Append actual device results and measured latency to `validation.md`.
