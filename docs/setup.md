# Setup guide

## 1. Check the phone

This build requires API 37 or newer. Only Pixel 6a / API 37 has device evidence; a Pixel 11 needs its own checks. Keep your existing launcher installed and accessible for rollback.

On the phone, enable Developer options by tapping Build number seven times under Settings → About phone. Enable USB debugging in Developer options. Connect by a data-capable USB cable, unlock the phone, and approve the computer's debugging fingerprint.

Install Android SDK Platform Tools on the computer and put its directory on PATH. Verify:

```text
adb devices
adb shell getprop ro.product.model
adb shell getprop ro.build.version.sdk
```

The device must show `device`, not `unauthorized` or `offline`, and the API must be at least 37. Connect exactly one authorized device for the helper scripts. If needed on Windows, install the phone's USB driver. Reconnect and approve the prompt if authorization fails.

## 2. Install build prerequisites

Install Git, JDK 17 and an Android SDK (Android Studio's SDK Manager is one way). In SDK Manager install Android SDK Platform 37, Android SDK Build-Tools 36.0.0 and Android SDK Platform-Tools. Set `JAVA_HOME` to the JDK folder and `ANDROID_HOME` to the SDK folder. Add the JDK's `bin` and SDK's `platform-tools` to PATH, then open a new terminal. `java -version` should report 17 and `adb version` should work.

Alternatively, set the SDK path in an untracked `local.properties` in the project root, for example `sdk.dir=C\:/Users/YOUR_NAME/AppData/Local/Android/Sdk` on Windows. Use your actual path. Do not commit this file. The Gradle wrapper downloads the pinned Gradle distribution and dependencies on first build; a network connection is required for those downloads.

```powershell
git clone https://github.com/ianhenrydev/less-often-launcher.git
cd less-often-launcher
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On macOS/Linux replace `.\gradlew.bat` with `./gradlew`. The APK appears at `app/build/outputs/apk/debug/app-debug.apk`. You can also open the project in Android Studio with Gradle JDK set to 17 and the SDK configured. No local `.tools` directory is required.

## Customize the app groups

Edit `app/src/main/assets/app-buckets.json` before building if desired. Preserve the schema and valid JSON; place a package in one group only. The six Home tools and their order reflect the current UI design. Look up installed package IDs with `adb shell pm list packages`; identifiers are case-sensitive. This output describes your phone, so review it before sharing.

Secondary entries open directly. Restricted entries require a quote. Unknown launchable packages are Restricted by default, so apps unique to your phone may need moving to Secondary. Missing configured apps are harmless and listed in setup. Sessions apply to packages, including all their launcher aliases. There is no in-app policy editor; rebuild and install an update after edits. Run unit tests after changing policy and review any assertions tied to the original configuration.

## 3. Record the display baseline and install

Before first activation, leave the display in its normal color state. On Windows:

```powershell
.\scripts\setup-device.ps1 -Baseline .\artifacts\my-phone-display-baseline.private.json
```

For your brother's phone use a different filename. Save each file privately with a separate note of which phone it belongs to. Baselines contain exact values and absent-key state, not a device identifier. Existing baseline files are never overwritten, and the script cannot detect a file from another phone. Do not reuse a path for a different device.

The script checks API compatibility, records the two secure color settings, installs the debug APK, grants the declared permission, then opens setup. It warns for unvalidated devices. Use `-Apk PATH` for a different APK.

For manual installation on any host, **first record both exact outputs below outside the phone**, including `null` if the key is absent. Label the record for this device and keep it for recovery:

```text
adb shell settings get secure accessibility_display_daltonizer
adb shell settings get secure accessibility_display_daltonizer_enabled
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.lessoften.launcher android.permission.WRITE_SECURE_SETTINGS
adb shell am start -n com.lessoften.launcher/.SetupActivity
```

Manual recovery commands are in [recovery.md](recovery.md); a plain-text record is not an input file for the PowerShell recovery script. The grant needs USB debugging, not root.

## 4. Enable the launcher

1. If Android blocks a sideloaded service, open Settings → Apps → Less Often → App info menu → Allow restricted settings. Menu wording may vary. This is an explicit action on the phone.
2. Open Less Often setup → Accessibility settings and enable Less Often. Return to setup and verify the service is connected and the secure-setting grant is present. If it says enabled but disconnected, turn the service off and on again.
3. Choose Home app → Less Often. The Less Often entry in another launcher's app drawer opens setup; setup is also linked from its accessibility settings.
4. Optionally enable Usage Access and Notification Access through setup. They are separate permissions and neither is needed to use the core launcher/gate. Missing statistics should show dashes rather than zero.

## 5. Check a new phone before daily use

Use [acceptance.md](acceptance.md) as the full manual checklist. Its original reference device is Pixel 6a; record Pixel 11 observations separately, without treating the old results as certification.

At minimum, check Phone and Settings remain reachable, Primary and Secondary apps open, and a Restricted app shows the quote challenge. Test typing with your keyboard, Back/Home cancellation, reopening within a grant, and expiry after a real ten minutes. Check notification and link destinations. Verify grayscale while Restricted is foregrounded and exact recorded color restoration on Home. Check lock/unlock and service reconnect, and retain a recovery route. Do not use the Pixel-specific automated suite as proof of Pixel 11 support.

## Updates, sharing and troubleshooting

Use `adb install -r PATH_TO_APK` for updates; preserve the application ID and signing key. A debug APK is automatically signed with the building computer's development key. If you build your brother's initial APK, supply updates built with that same key. A build made on another computer may be rejected as an incompatible update. Do not commit or distribute your private signing key. Do not uninstall to resolve a mismatch until the display baseline is recovered; uninstalling also loses local state.

`INSTALL_FAILED_OLDER_SDK` means the phone OS is too old for this build. If the SDK cannot be found, fix `ANDROID_HOME` or `local.properties`. A missing platform/build-tools error means the required SDK packages must be installed. If restricted apps open setup, check accessibility connection and secure-setting permission there. If grayscale remains after a crash, follow [recovery.md](recovery.md) before clearing data or uninstalling.

For removal: disable Less Often accessibility, switch to your previous Home app, restore/verify the display baseline, then uninstall Less Often. Turn off USB debugging when you no longer need it.
