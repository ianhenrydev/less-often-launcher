# Setup, display recovery, and rollback

Application ID: `com.lessoften.launcher`. Home: `.MainActivity`. Setup: `.SetupActivity`. Accessibility service: `.GateService`.

## Leave or disable Less Often

Android Settings remains Primary. In Settings → Accessibility disable Less Often, then in Settings → Apps → Default apps → Home app choose the previous launcher. Less Often setup also links to both settings. Disabling accessibility normally invokes cleanup, but crashes and force-stops do not guarantee cleanup. Verify the color settings afterward.

Open setup directly if needed:

```powershell
adb shell am start -n com.lessoften.launcher/.SetupActivity
adb shell am start -a android.settings.ACCESSIBILITY_SETTINGS
adb shell am start -a android.settings.HOME_SETTINGS
```

If the service interferes before you can reach settings, `adb shell am force-stop com.lessoften.launcher` stops this app. This can leave grayscale behind; follow baseline restoration below. Do not clear all Android accessibility settings: that would disable unrelated services.

## Exact baseline restoration

The app journals both settings **before** changing either, preserving whether each existed. The journal persists across process restart and boot. While a Restricted app remains foregrounded it keeps the original baseline. Leaving restores exact values, including absence via `Settings.Secure.putString(key, null)`. Boot, service teardown/reconnect, wake, and Home reconciliation repair a pending transform when appropriate. Deep sleep does not extend sessions.

The setup script also saves the original values without device identifiers in `artifacts/display-baseline.private.json`. Keep this file private and retain it outside the phone. It is never overwritten automatically. If user preferences have changed since setup, the **current app journal** is the more recent baseline; prefer it when pending.

First disable Less Often accessibility in Android Settings. Then, for a pending journal in the installed debug APK:

```powershell
.\scripts\recover-display.ps1 -FromAppJournal
```

For the saved development-computer baseline instead:

```powershell
.\scripts\recover-display.ps1 -Baseline .\artifacts\display-baseline.private.json
```

The script rejects missing/invalid fields and verifies the restored values. It does not guess defaults. A null JSON setting means the secure key was absent and must be deleted. To restore manually, substitute each exact recorded value:

```powershell
adb shell settings put secure accessibility_display_daltonizer_enabled RECORDED_ENABLED_VALUE
adb shell settings put secure accessibility_display_daltonizer RECORDED_MODE_VALUE
```

If a key was recorded as absent, use its corresponding delete command instead:

```powershell
adb shell settings delete secure accessibility_display_daltonizer_enabled
adb shell settings delete secure accessibility_display_daltonizer
```

On the initial October 30 device check both keys existed with value `0`. Those values describe that observation only; recovery uses the file/journal, not these illustrative values. After manual/script restoration, open Less Often setup and choose **Restore recorded display baseline** before re-enabling the service. That reconciles and clears a journal whose baseline is now restored. The scripts do not expose/edit sessions, buckets or quote state.

## Conflicting color changes

If the secure color-correction keys change during an owned transform, the controller detects divergence and pauses reapplication instead of continuously overwriting Android or user changes. It retains a conflict/journal when restoration is ambiguous. Setup reports the problem. Review bedtime, color-correction and other display settings, decide whether to retain the newer preference or restore the saved baseline, and recover explicitly. The current recovery script deliberately restores the recorded baseline only. Do not add an override policy for bedtime without choosing that behavior explicitly.

Night Light/bedtime may use other display transforms; changing these secure keys alone does not prove their combined visible result. That interaction remains part of actual-phone acceptance.

## Permission loss and removal

```powershell
adb shell pm grant com.lessoften.launcher android.permission.WRITE_SECURE_SETTINGS
```

This restores the app's declared development permission; it does not enable accessibility. If revoked, gates may continue but grayscale/recovery can fail, and setup reports missing capability. Restore baseline through ADB if necessary. The app never reports all restrictions active solely because it holds the Home role.

To remove Less Often, first disable its service, select another Home app, restore/verify its pending display baseline, and then uninstall **this app only**. No factory reset, existing app removal, notification setting changes, package suspension or device-owner provisioning is required.
