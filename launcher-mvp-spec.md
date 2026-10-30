# Less Often — MVP implementation specification

**App name:** Less Often. Use this as the Android app label and in user-facing setup and documentation.

**Folder/project name:** `less-often-launcher`. Use this for the source repository/root folder and Gradle `rootProject.name`.

**Status:** Product decisions and initial app buckets approved by the user. Implementation has not started.

**Approved:** October 30, 2026.

**Target device:** Google Pixel 6a, stock Android 17 / API 37. The Android version and launchable package inventory were verified through read-only ADB queries.

**Purpose of this document:** Give a new Codex implementation chat everything needed to build this personal launcher without reconstructing the planning conversation. The complete approved app configuration is embedded in Appendix A. A separate `app-buckets.json` contains the same configuration for convenience.

## 1. Implementation brief

Build Less Often, a minimal Android launcher that keeps routine tools easy to reach and puts deliberate friction in front of selected apps. The user likes the visual restraint of Before Launcher and the reduced appeal of grayscale apps, but needs normal Pixel reliability, texting, navigation, authentication, and tickets.

The MVP has three configurable buckets:

| Bucket | Where it appears | Access | Color behavior |
| --- | --- | --- | --- |
| Primary | Home screen, in approved order | Immediate | Preserve the user's normal display settings |
| Secondary | Separate screen opened by a `Secondary` button | Immediate | Preserve the user's normal display settings |
| Restricted | Separate screen opened by a `Restricted` button | Retype a short quote to unlock that individual app for 10 elapsed minutes | Grayscale whenever that app is foregrounded, including during its unlocked window |

New, uncategorized launchable apps default to Restricted. App categories and ordering are configured in source and changed using a development computer; there is no on-device category editor.

**Do not silently alter the approved behavior to accommodate an implementation shortcut.** If Android 17 prevents a required behavior, show the evidence and ask the user to choose between concrete alternatives. Technical choices below are recommendations made under the user's delegation of stack selection; the behavioral requirements are approved decisions.

## 2. Scope and boundaries

### Included

- A text-only home screen and two text-only app lists.
- The exact initial app mapping in Appendix A.
- Automatic Restricted classification for new launchable apps.
- An offline quote challenge, independently applied to each Restricted app.
- Persistent, independent 10-minute access windows.
- Foreground-based whole-display grayscale for Restricted apps.
- An accessibility-based gate for entry through this launcher, notifications, links, and Recents.
- Normal notifications, including previews and inline actions.
- Build, device setup, recovery instructions, and meaningful validation on the target phone.

### Excluded

- MapTap, a dedicated browser wrapper, domain allowlists, daily game quotas, and daily screen-time budgets.
- A custom Android ROM, root, bootloader changes, and mandatory device-owner/kiosk provisioning.
- Anti-tamper controls that block switching launchers, uninstalling the app, disabling accessibility, or changing Android settings.
- Filtering feeds or tabs inside another app; Sleeper is gated as an entire package.
- Notification filtering, notification mirroring, and replacement notification UI.
- Split-screen and picture-in-picture as supported MVP operating modes.
- A configuration screen that can change buckets or shorten the challenge/session rules.
- Extra home-screen features such as widgets, a launcher clock/date, badges, news, or search.

The user is willing to reset the phone or change settings if genuinely necessary. That is not a requirement to reset it. Start with the accessibility service and an ADB-granted permission on the existing installation.

### Accepted enforcement limits

This is deliberate personal-use friction, not an Android security boundary. The user explicitly accepts:

- A Restricted app may appear briefly before the accessibility service detects and covers it.
- Disabling the accessibility service can bypass the gate.
- Notification previews and inline actions remain usable while the corresponding app is locked.
- Android Settings remains freely available as a Primary app.
- Switching launchers and uninstalling the launcher are not prevented.

The policy applies to app packages, not to content inside permitted apps. An embedded browser/feed inside a Secondary app is not separately blocked by classifying Chrome or another app as Restricted.

Do not describe this implementation as intercepting every activity launch before it happens or as resistant to a determined owner. Conversely, an icon hidden in a drawer is insufficient: an enabled, functioning service must actively gate ordinary app openings and session expiry.

## 3. Home screen and app lists

### Home screen

- Plain dark background, light app names, left-aligned text.
- App names only for the app entries: no app icons, badges, descriptions, clocks, or dates supplied by the launcher.
- Preserve normal Android status and navigation bars. The system status-bar clock is not a launcher clock.
- Show these Primary entries in this exact order:

  1. Phone
  2. Messages
  3. Camera
  4. Maps
  5. Clock
  6. Settings

- Below the Primary entries, show separate `Secondary` and `Restricted` text buttons. Visually distinguish these navigation controls from app entries with restrained typography/spacing or button treatment.
- Selecting a Primary entry launches it immediately.

### Secondary and Restricted screens

- Separate screens, each reached by its corresponding home-screen button.
- Text app names; alphabetical lists; vertical scrolling as needed.
- Back returns to the home screen.
- A Secondary app opens immediately.
- A Restricted app opens immediately only while its own valid session exists. Otherwise, present its quote challenge.
- Do not add on-device sorting, bucket changes, or hidden gestures that edit configuration.

### App identity and discovery

- Match approved configuration using the exact, case-sensitive package identifier. Labels are not policy identifiers.
- Discover currently installed, enabled launchable applications using appropriate Android launcher/package APIs and declared package visibility.
- A normal launcher entry is the unit shown to the user; its package is the unit of restriction and session state. If multiple launcher activities share a package, they must share one access window, not create separate bypasses.
- Refresh the catalog when packages are installed, removed, enabled, disabled, or updated. New launchable packages are Restricted without a rebuild.
- Keep the source configuration authoritative for known packages even after uninstall/reinstall; do not treat reinstalling as a category reset.
- Do not turn every Android system package into a Restricted app. Keyboard, keyguard, System UI, permission UI, and other transient platform infrastructure are not new app-drawer entries.
- Exclude this implementation's own Home/gate/setup components from the unknown-app default to prevent self-lockout. This narrow infrastructure exception does not reclassify another installed launcher, including the explicitly Restricted Before Launcher.
- Do not blanket-exempt all system apps: Chrome, Google, and Play Store are explicitly Restricted in the approved mapping.
- For the two entries identified only by package (`com.cincsystems.ppm1` and `com.comit.caregiver`), resolve their real installed labels through Android APIs. Their Secondary classification is approved regardless of the label. Do not invent identities or silently recategorize them.
- If a configured app is absent, retain its source mapping, omit its unusable launcher entry, and report the discrepancy during setup validation. Do not install replacements automatically.

## 4. Quote challenge

### Approved behavior

- Before granting a Restricted app a session, display a short quote and require the user to type it.
- Each quote contains **8–16 words**.
- Use a bundled, offline quote collection. No quote API, network dependency, or AI generation at unlock time.
- Every new attempt selects a quote different from the immediately previous displayed quote, including after a canceled attempt and when moving between Restricted apps.
- Require matching words and punctuation; ignore capitalization and extra whitespace.
- Disable paste into the challenge input.
- Leaving the challenge screen cancels the attempt and discards partial input. Locking the phone also cancels it.
- Showing/using the keyboard does not cancel the attempt.
- Successful completion unlocks only the specific app being challenged.

### Implementation requirements

- Display the target app name, the quote, an input, and a clear completion action. Keep this functional and visually minimal.
- Compare text after trimming, collapsing consecutive whitespace to one space, and applying consistent case normalization. Do not drop punctuation or ignore incorrect words.
- Keep a stable quote ID and persist the most recently displayed ID so routine process restarts do not immediately repeat it.
- The collection must contain multiple distinct quotes. Select quotations with accurate attribution and usable licensing; record source/license metadata in the repository. Attribution need not be typed.
- Suppress ordinary paste, selection-toolbar paste, and clipboard insertion paths controlled by the input. Do not claim the app can distinguish every keyboard-generated insertion from hand typing; a custom keyboard is outside this personal-use enforcement boundary.
- Do not automatically restore an unfinished challenge after process death or device reboot. A new attempt requires a new quote.
- Home, leaving via Back, app switching, or locking the device must not leave an actionable challenge over another app. The keyboard opening/closing is not equivalent to leaving.
- A real system interruption that takes the user away from the challenge cancels it; never obstruct calls, keyguard, or device safety UI to preserve challenge progress.

## 5. Independent 10-minute sessions

Each Restricted package has independent state:

```text
Locked
  -> Challenging(app, quote)
  -> Unlocked(app, expiry)
  -> Locked when expiry is reached

Challenging -> Locked if canceled
Device reboot -> all sessions Locked
```

Rules:

1. The window begins when the quote is successfully completed.
2. Its duration is exactly 600 seconds of elapsed time, not foreground usage time.
3. Switching apps, locking the screen, sleeping, or returning home does not pause or restart the window.
4. Reopening the same app during its window does not require another quote and does not extend expiry.
5. Unlocking one app does not unlock another. More than one independent window can exist at once.
6. When a foreground Restricted app expires, actively cover/gate it again. Do not wait for the user to relaunch it or for another accessibility event to occur.
7. When a background app expires, mark it locked; do not interrupt the unrelated foreground app. Gate it when it is next opened.
8. A new successful challenge grants a new 600-second window. There is no approved daily quota or cooldown.
9. Sessions survive launcher/service process restart within the same boot, but expire across a device reboot.

Recommended timing implementation:

- Use `SystemClock.elapsedRealtime()` for elapsed deadlines because it includes deep sleep and is unaffected by wall-clock changes. Persist a deadline and a boot discriminator with each session. [Android SystemClock reference](https://developer.android.com/reference/android/os/SystemClock#elapsedRealtime())
- On recovery, validate the boot discriminator before trusting a saved deadline. Missing, invalid, or cross-boot state must not manufacture a new session.
- Persist a successful grant before exposing the target app. Avoid a race between the foreground observer and the grant write.
- Reconcile current foreground state and expiry when the service reconnects and when the phone wakes/unlocks.
- Use a deadline callback while an unlocked Restricted app is foregrounded, with revalidation after wake/reconnect. Do not rely on WorkManager's deferred scheduling or a new window-change event to enforce foreground expiry.

## 6. Access gate and notifications

### Required entry paths

With the accessibility service enabled and healthy, apply the same per-package rules when an app opens through:

- This launcher's Restricted list.
- A notification tap.
- A link or other app-to-app navigation.
- Android Recents.

Use a full, opaque, input-blocking gate after detecting a locked Restricted foreground app. Simply showing a reminder notification, a toast, or an easy-to-dismiss banner is not sufficient.

For launches originating in this launcher, show the challenge before launching the app where possible. For external launches, the underlying app may already have opened; the accepted implementation covers it after detection.

Preserve the requested destination through the gate: completing a notification/link challenge should continue to the app screen the user originally requested, rather than silently discarding it and launching a generic home activity. Validate this with the chosen overlay/activity approach on Android 17. If a reliable approach cannot preserve a required destination, surface that limitation before substituting different behavior.

### Notifications stay native

- Do not suspend or disable Restricted packages to implement locking.
- Do not hide, cancel, copy, or reissue their notifications.
- Existing user notification settings remain authoritative; the launcher does not enable notifications the user disabled.
- Previews and inline notification actions remain usable by explicit user choice. The gate controls opening the application UI, not every possible background action.
- Opening an activity from a notification action still encounters the foreground gate when applicable.

Android package suspension hides notifications, making it incompatible with this requirement. [DevicePolicyManager reference](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setPackagesSuspended(android.content.ComponentName,%20java.lang.String[],%20boolean))

### Foreground tracking

- Use a user-enabled `AccessibilityService` and relevant window events. Track the underlying application rather than treating the latest event's package as the entire foreground truth.
- Keyboard, this launcher's gate, and platform overlays must not accidentally create a new app session or disable restrictions on the underlying target.
- Do not globally exempt a real app simply because it supplies an authentication or other service. A separately opened Restricted app still needs its own challenge.
- Avoid a recursive gate when the launcher/gate itself becomes visible.
- Collect only the package/window metadata needed for behavior; do not scrape messages or app screen contents.
- Test background activity restrictions and overlay keyboard interaction on the actual target version; do not depend on an unverified ability to start arbitrary activities from a background service. [Accessibility service reference](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService), [Android activity security](https://developer.android.com/guide/components/activities/secure-bal)

## 7. Grayscale

### Approved behavior

- All Restricted apps use grayscale whenever they are foregrounded, including during a valid access window.
- Primary/Secondary apps and this launcher's ordinary home/drawer screens return to the prior display state when leaving the Restricted context.
- The transform affects the whole display. The user accepts that the keyboard and system UI can also appear grayscale while a Restricted app is active.
- Do not claim per-surface or per-window color isolation.

### Implementation approach and recovery

Use the Android display color-correction secure settings, after verifying their behavior on the target phone:

```text
accessibility_display_daltonizer = 0
accessibility_display_daltonizer_enabled = 1
```

Android uses the monochromacy mode to apply a grayscale display transform. These are implementation-level settings; retest after significant OS updates rather than treating them as a permanent public app API. [Android display service implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/display/color/ColorDisplayService.java)

- Declare `android.permission.WRITE_SECURE_SETTINGS` and grant it using ADB during setup. This is not an ordinary runtime permission prompt, and becoming a device owner does not automatically replace this step. [Android permission definition](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/AndroidManifest.xml)
- Capture the existence and value of the settings before this launcher changes them. Persist that baseline before applying the transform.
- Keep the baseline while moving directly between Restricted apps; do not accidentally save the launcher's own grayscale state as the user's original state.
- Restore the prior values on leaving Restricted context. If a setting was originally absent, restore absence rather than inventing a default.
- Preserve existing color-correction preferences. Restoration does not necessarily mean forcing full color if the user's baseline was already grayscale or another correction mode.
- Reconcile the journal/baseline on process restart and boot. On recovery, apply grayscale only when currently required; otherwise repair a transform left behind by this app.
- Avoid unnecessary repeated writes for duplicate accessibility events.
- Verify interactions with bedtime grayscale and other display settings. Do not continually overwrite user/OS settings in a loop; if concurrent changes make restoration ambiguous, document the observed behavior and ask before adding a policy that overrides those features.
- Do not claim a crash or force-stop always triggers cleanup. Provide a development-computer recovery command/script that restores recorded baseline values.

## 8. Configuration and technical stack

### Configuration

- Ship a versioned source configuration with the exact package mapping in Appendix A and `defaultBucket = Restricted`.
- Preserve Primary array order. Alphabetize Secondary and Restricted lists by their displayed app names at runtime.
- Validate that a package appears in at most one bucket and that configured Primary entries are unique.
- Keep quote data, session duration, and bucket policies in source-controlled build inputs. Changes are made on a development computer and deployed through an updated APK.
- Do not expose a runtime broadcast, deep link, exported activity, shared-storage file, or preferences UI that silently changes buckets or grants a session.
- Session state and display-restoration state are runtime data; they are distinct from source configuration.
- Preserve case in identifiers such as `com.Slack`, `com.Avelo.app`, and `com.google.android.GoogleCamera`.
- The approved configuration includes apps that could be distracting in Secondary and practical apps in Restricted. Preserve those user choices; do not substitute your own app-category judgments.

### Recommended implementation choices

The user delegated technical stack choice. Use:

- **Kotlin** for Android application code, consistent with Android's Kotlin-first recommendation. [Kotlin guidance](https://developer.android.com/kotlin/first)
- **Jetpack Compose** for launcher and challenge UI, using standard Android UI components where needed for accessibility-overlay/IME compatibility. [Compose documentation](https://developer.android.com/develop/ui/compose/documentation)
- A small application with a clear UI/state layer and testable policy logic; avoid an unnecessary multi-module framework. Coroutines, StateFlow, and ViewModel are suitable. [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
- An app-private durable store, such as AndroidX DataStore, for session/quote/baseline state. Serialize policy transitions so competing events cannot overwrite grants or baseline snapshots.
- A Gradle Kotlin DSL project, Gradle wrapper, and pinned dependency versions. At implementation time choose a stable Android Gradle Plugin, compatible JDK/Kotlin setup, and stable Compose BOM; record exact versions in the repository. Do not use dynamic `+` versions or choose previews unnecessarily.
- `compileSdk = 37` and `targetSdk = 37` for the verified Android 17 device. This MVP has no requirement for older-device compatibility; a minimum SDK of 37 is a reasonable implementation choice. [Android 17 SDK setup](https://developer.android.com/about/versions/17/setup-sdk)
- A sideloadable APK and ADB-based development workflow for this personal device. A Play Store publication workflow is not part of this MVP.

Suggested responsibilities, not mandatory class names:

| Responsibility | Owns |
| --- | --- |
| App catalog | Launchable apps, installed labels, package changes |
| Bucket policy | Approved configuration, unknown-app default |
| Session repository | Independent deadlines, boot identity, durable grants |
| Quote engine | Corpus, selection without immediate repeat, text comparison |
| Foreground monitor | Relevant app/window tracking and lifecycle reconciliation |
| Gate controller | Show/dismiss challenge, preserve original destination, expiry gate |
| Display controller | Apply grayscale, baseline journal, restoration/recovery |
| Launcher UI | Home screen, Secondary list, Restricted list |

## 9. Device setup and recovery deliverables

Implement a repeatable setup checklist and scripts where useful:

1. Verify the connected target is the intended Pixel and read its release/API level.
2. Build/install the APK; select it as the Android Home app through normal Android settings.
3. Have the user enable its accessibility service. Handle any Android sideloaded-app restricted-settings step explicitly; do not bypass it silently.
4. Grant `WRITE_SECURE_SETTINGS` via ADB after the APK declares it.
5. Verify actual foreground gate and grayscale behavior before claiming setup is complete.
6. Record original color settings for recovery without publishing device identifiers or private phone data.

Illustrative command; replace the placeholder with the implemented application ID:

```sh
adb shell pm grant YOUR_APPLICATION_ID android.permission.WRITE_SECURE_SETTINGS
```

The app must know when required capabilities are missing and provide a clear setup/recovery path. Do not silently report restrictions as active after an accessibility or grayscale-permission failure. Keep setup/error UI separate from the normal minimal home screen, and do not strand access to Phone or Settings.

Document recovery using Android Settings/another Home app and the development computer. Provide exact baseline restoration steps. Do not blindly reset color-correction preferences as a cleanup shortcut.

Do not factory-reset, uninstall existing user apps, or change the user's notification preferences as part of ordinary setup.

## 10. Implementation order

### Phase 1 — prove the Android 17 behavior

Before polishing UI, implement a small feasibility slice on the Pixel:

- Enable grayscale through the granted permission and restore the exact previous settings.
- Detect Sleeper (`com.sleeperbot`) opening externally and present an opaque gate that accepts keyboard input.
- Confirm that its normal notifications remain visible and that a notification/link destination is preserved through the gate.
- Verify the gate can appear on a foreground deadline without another launch event.
- Verify Home, Phone, Settings, the keyboard, and screen lock remain usable.

Record what was actually tested. Do not proceed on the assumption that an emulator result proves these behaviors on the Pixel. If a platform limitation contradicts the spec, finish the unaffected investigation and ask the user about the concrete conflict.

### Phase 2 — build the policy and launcher

- Implement catalog discovery, source configuration, and the three screens.
- Implement the quote engine and independent durable sessions.
- Integrate foreground detection, expiry enforcement, and grayscale restoration.
- Use the complete approved mapping without extra recategorization.

### Phase 3 — validate and hand off

- Run the relevant automated tests and the real-device acceptance checks below.
- Build the APK and provide source, setup, rollback, and limitations documentation.
- Report any check not run as unverified. No silent substitution of mock behavior for device-level behavior.

## 11. Acceptance criteria

### Launcher and catalog

| ID | Check | Expected result |
| --- | --- | --- |
| UI-01 | Open Home after setup | Phone, Messages, Camera, Maps, Clock, Settings in that order; text-only, dark background; no launcher clock/date/icons |
| UI-02 | Open each drawer | Separate Secondary/Restricted screens; alphabetical text lists; Back returns Home |
| UI-03 | Compare catalog with approved mapping | Initial snapshot: 6 Primary, 57 Secondary, 40 Restricted; no duplicated package assignments |
| UI-04 | Install an otherwise unconfigured benign launchable test app | It appears as Restricted without changing source configuration |
| UI-05 | Remove/update/reinstall a configured app | Catalog updates; source bucket does not change; no stale crashing launch target |
| UI-06 | Inspect on-device settings and exported app components | No supported path for editing app buckets or granting access without a quote |

### Quote and session behavior

| ID | Check | Expected result |
| --- | --- | --- |
| GATE-01 | Open locked Sleeper | Quote required; 8–16 words; underlying app cannot be used after gate takes control |
| GATE-02 | Submit correct words with different case/extra spaces | Accepted; punctuation and words still must match |
| GATE-03 | Submit incorrect punctuation/words or attempt paste | Incorrect text rejected; ordinary paste paths unavailable |
| GATE-04 | Leave a partial challenge, then return | Partial attempt canceled; new quote differs from the previously displayed quote |
| GATE-05 | Open/close/use the keyboard | Challenge remains usable and does not cancel merely because of IME events |
| GATE-06 | Lock the phone during a challenge | Attempt canceled; no gate blocks keyguard or calls |
| TIME-01 | Complete Sleeper quote and reopen it before deadline | No second quote; original deadline unchanged |
| TIME-02 | While Sleeper is unlocked, open locked Chrome | Chrome needs its own quote; Sleeper's deadline is unchanged |
| TIME-03 | Leave a Restricted app or lock the phone during its window | Time continues to elapse; returning after expiry requires a quote |
| TIME-04 | Remain in a Restricted app beyond expiry | Foreground gate appears and prevents continued interaction until another quote succeeds |
| TIME-05 | Allow an app to expire while a Primary/Secondary app is foregrounded | No unrelated interruption; expired app is gated on next entry |
| TIME-06 | Restart app/service process within the same boot | Valid sessions retain their original deadlines; no free extension |
| TIME-07 | Reboot phone | All prior access windows invalid; quote required again |
| TIME-08 | Change wall-clock time/timezone | Elapsed session duration is unchanged |

### Entry paths, notifications, and grayscale

| ID | Check | Expected result |
| --- | --- | --- |
| ENTRY-01 | Open locked app via launcher, notification, link, and Recents | Each supported path reaches the quote gate |
| ENTRY-02 | Complete a notification/link challenge | Original app destination remains usable; no silent replacement with generic app Home |
| NOTE-01 | Receive a Restricted-app notification while locked | Native notification and preview remain visible, subject to existing user preferences |
| NOTE-02 | Use an inline notification action | Remains usable; if an action opens an app UI, that UI encounters the normal gate |
| COLOR-01 | Use unlocked Sleeper and view media | Whole display grayscale while it is foregrounded |
| COLOR-02 | Switch to Camera, Maps, or Home | Original display settings restored; no stale grayscale caused by this app |
| COLOR-03 | Switch between two Restricted apps | Grayscale stays active; baseline is not overwritten with the temporary transform |
| COLOR-04 | Start with non-default color correction, then enter/leave a Restricted app | Exact baseline restored, including whether secure setting keys originally existed |
| COLOR-05 | Restart/reboot after this app applied grayscale | Recovery reconciles foreground state and restores baseline when appropriate |
| OPS-01 | Disable accessibility or revoke the secure-setting grant | Capability loss is identifiable; no false claim of active enforcement; recovery documented |
| OPS-02 | Exercise incoming calls, messaging, keyboard, authentication, Wallet/tickets, and navigation | Core daily-driver functions remain usable; report any incompatibility rather than hiding it |

Automate policy tests for quote comparison/selection, independent timing, cross-boot invalidation, duplicate configuration, unknown-app defaults, and grayscale-baseline ownership. Use focused instrumentation/device checks for overlays, IME, entry paths, notifications, and display effects. An injected clock can accelerate timing tests, but also perform a real 10-minute device session before marking timing complete.

Acceptance is functional. Measure and report gate transition behavior; do not invent a zero-latency guarantee. Split-screen/PiP are excluded and must not be presented as validated modes.

## 12. Completion and new-chat handoff

The implementing chat should deliver:

- The Android source repository with the approved configuration and offline quote corpus.
- A reproducible Gradle build and installable APK.
- Setup and recovery instructions, including exact ADB commands for the chosen application ID.
- A brief validation report mapping the acceptance criteria to passed, failed, or unverified evidence.
- Clearly documented platform limits and any remaining issues that affect daily use.

Suggested initial prompt for a new implementation chat:

> Implement the attached Less Often MVP specification. Name the app Less Often and use `less-often-launcher` for the source repository/root folder and Gradle project name. The product decisions and app buckets are approved. Begin with the Android 17 feasibility slice, then build the MVP and verify it on my Pixel 6a. Use the embedded app mapping exactly, preserve the accepted accessibility-based enforcement limits, and ask before changing product behavior if you find a concrete incompatibility. Do not reset my phone or modify unrelated apps as part of setup.

This specification is ready to implement. The remaining uncertainties are empirical Android/device behavior to investigate in Phase 1, not permission to invent different product requirements.

## Appendix A — complete approved app configuration

The following JSON is self-contained and matches the separately supplied `app-buckets.json`. Counts: **6 Primary, 57 Secondary, 40 Restricted; 103 total**. The `primary` array is ordered. New launchable packages not listed here use `defaultBucket`.

Friendly names were initially inferred from package IDs and approved in the bucketing exercise. Package identifiers are the authoritative mapping. Resolve the two explicitly unidentified display labels on-device without changing their buckets.

```json
{
  "schemaVersion": 1,
  "approvedOn": "2026-10-30",
  "defaultBucket": "Restricted",
  "sessionDurationSeconds": 600,
  "primary": [
    {"package":"com.google.android.dialer","name":"Phone"},
    {"package":"com.google.android.apps.messaging","name":"Messages"},
    {"package":"com.google.android.GoogleCamera","name":"Camera"},
    {"package":"com.google.android.apps.maps","name":"Maps"},
    {"package":"com.google.android.deskclock","name":"Clock"},
    {"package":"com.android.settings","name":"Settings"}
  ],
  "secondary": [
    {"package":"com.beemdevelopment.aegis","name":"Aegis Authenticator"},
    {"package":"com.aa.android","name":"American Airlines"},
    {"package":"com.Avelo.app","name":"Avelo Airlines"},
    {"package":"com.x8bit.bitwarden","name":"Bitwarden"},
    {"package":"com.blueshieldca.prod","name":"Blue Shield of California"},
    {"package":"com.google.android.calculator","name":"Calculator"},
    {"package":"com.carfax.mycarfax","name":"CARFAX Car Care"},
    {"package":"com.chase.sig.android","name":"Chase"},
    {"package":"com.google.android.contacts","name":"Contacts"},
    {"package":"com.delta.mobile.android","name":"Delta"},
    {"package":"com.discord","name":"Discord"},
    {"package":"host.exp.exponent","name":"Expo Go"},
    {"package":"com.figoinsurance.petcloud","name":"Figo Pet Cloud"},
    {"package":"com.google.android.apps.nbu.files","name":"Files by Google"},
    {"package":"com.github.android","name":"GitHub"},
    {"package":"com.google.android.gm","name":"Gmail"},
    {"package":"com.google.android.apps.authenticator2","name":"Google Authenticator"},
    {"package":"com.google.android.calendar","name":"Google Calendar"},
    {"package":"com.google.android.apps.docs.editors.docs","name":"Google Docs"},
    {"package":"com.google.android.apps.docs","name":"Google Drive"},
    {"package":"com.google.android.apps.tycho","name":"Google Fi"},
    {"package":"com.google.android.apps.chromecast.app","name":"Google Home"},
    {"package":"com.google.android.keep","name":"Google Keep"},
    {"package":"com.google.android.apps.photos","name":"Google Photos"},
    {"package":"com.google.android.apps.docs.editors.sheets","name":"Google Sheets"},
    {"package":"com.google.android.apps.walletnfcrel","name":"Google Wallet"},
    {"package":"com.tplink.kasa_android","name":"Kasa"},
    {"package":"com.overdrive.mobile.android.libby","name":"Libby"},
    {"package":"com.lifetimefitness.interests.fitness","name":"Life Time"},
    {"package":"com.liveoakbank.mobile.prod","name":"Live Oak Bank"},
    {"package":"me.lyft.android","name":"Lyft"},
    {"package":"com.azure.authenticator","name":"Microsoft Authenticator"},
    {"package":"com.microsoft.teams","name":"Microsoft Teams"},
    {"package":"org.mozilla.firefox.vpn","name":"Mozilla VPN"},
    {"package":"epic.mychart.android","name":"MyChart"},
    {"package":"com.nest.android","name":"Nest"},
    {"package":"com.microsoft.office.outlook","name":"Outlook"},
    {"package":"com.pandora.android","name":"Pandora"},
    {"package":"net.sharewire.parkmobilev2","name":"ParkMobile"},
    {"package":"com.passportparking.mobile","name":"Passport Parking"},
    {"package":"com.google.android.apps.safetyhub","name":"Personal Safety"},
    {"package":"com.philips.lighting.hue2","name":"Philips Hue"},
    {"package":"com.google.android.apps.recorder","name":"Recorder"},
    {"package":"com.quickenloans.myql","name":"Rocket Mortgage"},
    {"package":"com.Splitwise.SplitwiseMobile","name":"Splitwise"},
    {"package":"com.spotify.music","name":"Spotify"},
    {"package":"com.statefarm.pocketagent","name":"State Farm"},
    {"package":"net.thunderbird.android","name":"Thunderbird"},
    {"package":"com.ticketmaster.mobile.android.na","name":"Ticketmaster"},
    {"package":"com.ubercab","name":"Uber"},
    {"package":"com.cincsystems.ppm1","name":"Unidentified app: com.cincsystems.ppm1"},
    {"package":"com.comit.caregiver","name":"Unidentified app: com.comit.caregiver"},
    {"package":"com.united.mobile.android","name":"United Airlines"},
    {"package":"com.venmo","name":"Venmo"},
    {"package":"com.google.android.apps.weather","name":"Weather"},
    {"package":"com.whatsapp","name":"WhatsApp"},
    {"package":"com.tmsoft.whitenoise.full","name":"White Noise"}
  ],
  "restricted": [
    {"package":"com.twentythreeandme.app","name":"23andMe"},
    {"package":"com.amazon.mShop.android.shopping","name":"Amazon Shopping"},
    {"package":"com.beforesoft.launcher","name":"Before Launcher"},
    {"package":"com.betterment","name":"Betterment"},
    {"package":"com.booking","name":"Booking.com"},
    {"package":"com.bose.bosemusic","name":"Bose"},
    {"package":"com.nhl.hurricanes.app","name":"Carolina Hurricanes"},
    {"package":"com.openai.chatgpt","name":"ChatGPT"},
    {"package":"com.android.chrome","name":"Chrome"},
    {"package":"com.anthropic.claude","name":"Claude"},
    {"package":"com.dominospizza","name":"Domino's"},
    {"package":"com.dd.doordash","name":"DoorDash"},
    {"package":"com.yazam.empower.passenger","name":"Empower"},
    {"package":"com.espn.fantasy.lm.football","name":"ESPN Fantasy"},
    {"package":"com.fantasypros.myplaybook","name":"FantasyPros My Playbook"},
    {"package":"org.mozilla.firefox","name":"Firefox"},
    {"package":"com.fitbit.FitbitMobile","name":"Fitbit"},
    {"package":"com.flightaware.android.liveFlightTracker","name":"FlightAware"},
    {"package":"com.google.android.apps.bard","name":"Gemini"},
    {"package":"com.goodreads","name":"Goodreads"},
    {"package":"com.google.android.googlequicksearchbox","name":"Google"},
    {"package":"com.google.android.videos","name":"Google TV"},
    {"package":"com.groupme.android","name":"GroupMe"},
    {"package":"hardcover.app","name":"Hardcover"},
    {"package":"com.lowes.android","name":"Lowe's"},
    {"package":"com.netflix.mediaclient","name":"Netflix"},
    {"package":"com.google.android.apps.pixel.nowplaying","name":"Now Playing"},
    {"package":"com.google.android.apps.tips","name":"Pixel Tips"},
    {"package":"com.android.vending","name":"Play Store"},
    {"package":"com.redfin.android","name":"Redfin"},
    {"package":"com.Slack","name":"Slack"},
    {"package":"com.sleeperbot","name":"Sleeper"},
    {"package":"com.sporcle.geneva","name":"Sporcle"},
    {"package":"com.stremio.one","name":"Stremio"},
    {"package":"com.fivemobile.thescore","name":"theScore"},
    {"package":"com.uniqlo.usa.catalogue","name":"UNIQLO"},
    {"package":"org.wikipedia","name":"Wikipedia"},
    {"package":"com.google.android.apps.youtube.music","name":"YouTube Music"},
    {"package":"com.google.android.apps.youtube.unplugged","name":"YouTube TV"},
    {"package":"com.zillow.android.zillowmap","name":"Zillow"}
  ]
}
```

