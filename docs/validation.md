# Validation report — updated November 6, 2026

## Current status — version 0.2.0

**Accepted; spec closed November 6, 2026.** The user confirmed the 0.2 changes after more than a week of daily testing. This supersedes the earlier candidate status. Acceptance is for personal use on the existing Pixel 6a; Pixel 11 remains unvalidated. The historical checks below retain their original scope: user acceptance does not imply that every separately instrumented edge case was performed.

### Development validation history

On November 1, the user reported that Restricted apps opened launcher setup. Android showed the Less Often accessibility service enabled but disconnected after app-targeted instrumentation had force-stopped the launcher. Reconnecting the service restored the gate, and the launcher navigation and prelaunch challenge check passed. The device test script now reconnects the service in cleanup after app-targeted tests, preserving other enabled services; both successful instrumentation and a deliberate Usage Access failure were checked. A forcibly terminated host process or lost USB connection can still prevent cleanup. Setup now explains how to reconnect an enabled but disconnected service.

The 0.2 work-in-progress build has been installed in place on the Pixel 6a. Home, the two drawers, the gate, access settings and the usage detail screen are connected. Screen and Restricted values can be partial; the UI labels the coverage and does not present unobserved data as zero. Same-time comparisons require eligible checkpoints on at least three prior days, so no live multi-day baseline has yet been observed. These observations preceded the user acceptance recorded above.

- Home, Secondary, Restricted, details and gate screenshots were inspected. Restricted icons were grayscale while the status bar remained in color; the gate actions were reachable by scrolling with the keyboard open.
- Home was checked at 1.5× and 2× font scale; at 2× the footer remained reachable by scrolling. The original 1.0× scale was restored. Gesture Home and Back were exercised, then the original three-button navigation was restored. TalkBack was briefly enabled during an earlier check and the original accessibility-service list was restored; the user subsequently removed TalkBack-specific testing and acceptance from scope.
- The existing launcher navigation test passed: fixed Home order, direct Primary/Secondary launches, separate drawers, prelaunch challenge and cancellation. The earlier smoke test passed typed completion, clipboard restrictions, external destinations and valid-session reopening. A subsequent smoke run encountered the intentionally still-valid test session and is not counted as a failure of the gate.
- The native-notification regression passed for the notification content destination, inline-action PendingIntent, keyboard behavior, quote matching and package-alias session sharing. A separate literal tray tap on a synthetic probe notification opened locked Chrome; after dismissing Chrome's system default-browser dialog without changing the default, the quote gate appeared and the notification remained present. The synthetic notification was then removed.
- In-place updates preserved app data and the configured accessibility service; the 0.2.0 candidate has the same signing certificate as 0.1.1. A forced process stop removed the Less Often service from the enabled list on this device; it was restored without removing Bitwarden. The recorded display baseline was restored on Home. The app-targeted usage instrumentation also disconnected the service; it was reconnected before subsequent gate testing.
- An isolated fresh-install package with a temporary application ID displayed setup with Usage Access and Notification Access disabled and no stored session or display journal. The test package was removed and the original gate was restored and verified bound. This checks fresh local state and setup, but does not replace a first installation under the production application ID.
- A gate-only Chrome interaction exposed an overcount after a reboot event; the reducer was corrected to require confirmed gate coverage after a gap. A subsequent gate-only read recorded zero Restricted time, and a valid probe session produced a partial nonzero count. Precise latency and longer-duration measurement remain to be verified.
- A synthetic notification counted once; updating the same active key did not increment the count. On a controlled disconnect, an already-active notification was baselined at reconnect; an update remained uncounted, and removal followed by repost counted once. Synthetic notifications were dismissed. Inspection of notification storage showed daily counts and connection state, with no notification content.
- With Usage Access revoked, Home showed dashes and an enable-access prompt. The launcher navigation and gate-cancellation regression passed with access denied. Usage Access was restored and the two recorded unlocks remained unchanged.
- The real-duration foreground expiry test passed: the gate appeared 600,466 ms after the completion click, with a 40 ms observation bracket. A separate run passed 32 Restricted activity transitions with zero grayscale-setting writes and exact baseline restoration. During the ten-minute window, the reported Restricted increase was about 580 seconds; Android reported a 19-second probe activity pause during that window, which accounts for most of the difference. The count was partial, and this is not evidence of exact equivalence or a complete latency bound.
- Same-boot process recovery was exercised with the Restricted probe foregrounded: the launcher process changed, the probe deadline remained exactly unchanged, the gate service rebound with the display journal active, and Home restored the recorded 0/0 display baseline and cleared the journal.
- Device instrumentation checks found incremental replay and a full query agreed within a 10-second observation tolerance, both before and after a partial segment was introduced. An isolated previous-day query recovered retained events and marked the result partial. The instrumentation itself disconnected the accessibility service on this Pixel; the service was restored and verified bound afterward.
- The October 31 device check found the day marked incomplete after a detected clock or boot discontinuity. The updated build established a new partial segment and recorded 60 seconds of screen time from that segment while preserving the October 30 totals. Comparisons exclude the affected day. The user then locked and unlocked the Pixel once with fingerprint and once with PIN; the observed total rose from 0 to 2, matching two Android locked-to-unlocked transitions. A separate isolated on-device simulation preserved a prior total, resumed partial collection and replayed without duplication; an actual repeated clock or zone change remains unverified.
- In a subsequent locked-screen check, a synthetic notification left the Pixel dozing. Waking the still-locked screen did not create an unlock; after the user unlocked once, the count rose from 2 to 3. A separate high-importance synthetic notification also left the Pixel dozing; after it was removed and the user unlocked once, the count rose from 4 to 5. These checks did not exercise a notification that turns the display fully interactive.
- The candidate at that validation checkpoint built successfully with 45 passing unit tests and a passing Android lint task. Its debug signing certificate matches 0.1.1, and the installed APK was verified to match `artifacts/LessOften-0.2.0-candidate-debug.apk`. Launcher navigation, isolated historical backfill and simulated discontinuity tests passed on that candidate. That candidate was subsequently accepted through user testing as recorded above.
- Deterministic checks cover portions of lock and screen state, duplicate unlocks, gate exclusion, interruptions, midnight, saved reducer state, replay, comparison eligibility, daylight-saving exclusions and retention boundaries. A repeated real clock/time-zone discontinuity, a notification that makes the display fully interactive, and a naturally accumulated three-day comparison baseline remain unverified. Historical values and any unresolved coverage remain labeled partial.

## Previous accepted MVP — version 0.1.1

**MVP accepted; spec closed October 30, 2026.** Following further personal testing, the user confirmed that the app works perfectly and explicitly accepted 0.1.1 as the MVP. The [MVP spec](../launcher-mvp-spec.md) is now the completed baseline for future development. Acceptance preserves the passed, partial and unverified evidence below; unperformed checks have not been relabeled as passed.

Less Often is installed on the Pixel 6a running Android 17 / API 37, selected as Home, with its accessibility service enabled and WRITE_SECURE_SETTINGS granted. Core launcher, gate, quote, independent-deadline policy, external destinations, native notification intent/action, keyboard interaction, real foreground expiry, display restoration and process recovery have been exercised. Some acceptance checks below still require manual or further device validation; this report does not mark them passed by inference.

The user physically confirmed that the normally green battery indicator appears gray in Restricted context. The activity-navigation color flash was reproduced and fixed in 0.1.1; subsequent user testing confirmed that the app works perfectly, with no outstanding user-reported MVP issues at acceptance.

## Grayscale navigation regression

- Before the fix: 32 actual same-package Activity transitions produced 46 enable-setting changes; 23 observer callbacks saw color restored.
- After the fix, using the same Pixel and transition sequence: **32 Activity transitions, zero grayscale-setting writes**, original deep-link destination preserved, then both exact baseline values restored on Home.
- DisplayContinuity retains an owned transform during up to 1,000 ms of inconclusive metadata, including temporarily absent roots and inactive background Home windows. Repeated uncertain events cannot extend that interval. Confirmed Home/permitted foreground, lock/off and teardown restore normally. Feedback interruption now reconciles window state rather than unconditionally restoring color.
- The six new unit tests also verify baseline ownership, immediate positive-exit restoration, bounded recovery, startup reconciliation and lock/reset behavior. Access grants and the production 600-second session duration are unchanged.

## Passed evidence

- Gradle builds the launcher and development probe APKs. The wrapper and dependencies are pinned; compile/target/min SDK are 37.
- **20 unit tests passed, 0 failures.** Android lint: **0 errors, 6 advisory warnings**. The exact configuration was structurally compared with the spec; PowerShell scripts passed syntax checks.
- Original live catalog: **6 Primary / 57 Secondary / 40 Restricted**, no missing configured entries. Android labels: com.cincsystems.ppm1 = PPM Community; com.comit.caregiver = RAZ Care. Temporary probe entries defaulted to Restricted without a policy change.
- Actual Home order, direct Primary Settings and Secondary Calculator launch, separate drawers, prelaunch Chrome challenge, Back/Home cancellation, discarded partial input and different next quote passed. On this Pixel, Settings delegates its window to com.google.android.settings.intelligence; the test recognizes that observed platform window.
- Offline quote completion, cancellation/nonrepeat, rejected accessibility paste/set-text, case/extra-whitespace acceptance and incorrect-punctuation rejection passed on the phone.
- Actual Gboard opening, Back hiding it without losing the quote, reopening it and completion passed. A direct screenshot/IME diagnostic confirmed the same behavior; the test uses Android IME state rather than assuming every keyboard window appears in the automation window list.
- External deep-link destination and native notification content destination survived the gate. The native notification and preview data remained registered while locked. A synthetic RemoteInput response through the real inline-action PendingIntent completed without opening the app. The shade row was not found by automation, so the runner explicitly used the captured native content PendingIntent; a literal tray tap is still manual.
- Two launcher aliases for the probe shared one package window.
- **Real production-duration expiry passed:** gate observed **600,365 ms after the completion click**, with an 89 ms grant-observation bracket. Initial gate observed within **428 ms** of the launch request, including startup. This timing run preceded the color-only 0.1.1 patch; session/deadline logic was not changed.
- Actual externally launched locked Sleeper was gated; grayscale settings applied, and Home restored the observed baseline exactly. The test resolves its explicit launcher component; its earlier implicit MAIN/LAUNCHER shell intent was unresolvable and was not counted as a gate failure.
- **Actual launcher-process restart passed:** the process changed, exact committed session deadline and display baseline survived unchanged, foreground grayscale reconciled, then Home restored the saved 0/0 baseline and cleared the journal. No new grant or deadline extension was written by the test.
- Isolated app-permission monochromacy writes and exact baseline restoration passed on this Pixel. The saved-baseline recovery script also restored and verified its unchanged 0/0 values.

During initial device validation, Android-only whitespace-regex and native input MIME-configuration failures were fixed and retested. Failed harness runs caused by sleep/keyguard, native uppercase button labels, package-window delegation, startup pre-gate sampling and an unresolvable implicit launch were not counted as passing evidence. Test-only wake locks now protect interactive runs; no global screen-timeout or keep-awake setting was changed.

## Acceptance mapping

| IDs | Result and limits |
| --- | --- |
| UI-01 | Passed Home order and visual text-only dark layout with ordinary system bars. |
| UI-02 | Separate drawers, scrolling implementation and Back/Home navigation verified; complete runtime alphabetical order was not exhaustively traversed on-device. |
| UI-03 | Passed exact source mapping and original live 6/57/40 snapshot, including package-only labels. |
| UI-04, UI-05 | Unknown probe default and repeated updates observed; exhaustive enable/disable and configured-app remove/reinstall checks remain pending. Existing user apps were not uninstalled. |
| UI-06 | Passed source/manifest inspection: no policy editor, session-grant intent/broadcast/provider or shared-storage policy. Service binding protected, boot receiver private. |
| GATE-01, GATE-02 | Actual Sleeper/probe gate and typed normalization passed. Brief pre-detection exposure remains accepted. |
| GATE-03 | Wrong words/punctuation and accessibility insertion rejection passed. All hardware/toolbar clipboard variants remain manual; keyboard-generated plain text is an accepted boundary. |
| GATE-04 | Back/Home, partial-input discard and persisted quote nonrepeat passed. All system-interruption/app-switch variants were not exhaustively run. |
| GATE-05 | Passed actual Gboard open/hide/reopen and completion without cancellation. |
| GATE-06 | Explicit lock-during-partial-challenge safety run remains pending. |
| TIME-01 | Passed valid-window reopen and real original-deadline expiry; no extension. |
| TIME-02 | Independent repositories/unit timing and separate Chrome challenge passed; two simultaneous real successful package grants not exhaustively measured. |
| TIME-03, TIME-05 | Elapsed-deadline policy tested; controlled sleep/background-expiry phone runs remain pending. |
| TIME-04 | Passed actual 600-second foreground expiry without relaunch. |
| TIME-06 | Passed actual same-boot process restart with identical persisted deadline. |
| TIME-07, TIME-08 | Cross-boot invalidation and elapsed-clock logic unit-tested. Deliberate reboot and device wall-clock-change acceptance runs remain pending. |
| ENTRY-01, ENTRY-02 | Launcher, explicit external entry, deep link and actual native notification content intent passed with destination preservation. Literal tray tap and explicit Recents test remain pending. |
| NOTE-01, NOTE-02 | Native notification/preview data and real inline-action PendingIntent passed; physical shade preview and toolbar typing remain manual. Other apps' preferences unchanged. |
| COLOR-01 | User physically confirmed whole-display grayscale in Restricted context. |
| COLOR-02 | Exact Home restoration passed repeatedly, including after process restart; Camera/Maps transitions not separately measured. |
| COLOR-03 | Same-package multi-activity regression passed with zero writes; journal ownership unit tests passed. Direct two-Restricted-package transition still merits a separate phone check. |
| COLOR-04 | Actual original baseline 0/0 round trip passed. Non-default and absent-key baselines passed unit tests only. |
| COLOR-05 | Actual process restart and baseline recovery passed; controlled boot and conflicting-OS-setting cases remain pending. |
| OPS-01 | Missing-service status, successful setup and live process reconnection observed. Explicit permission-revocation/disable transition run remains pending. |
| OPS-02 | User reports successful daily testing. Calls, messaging, authentication, Wallet/tickets and navigation were not individually certified by automation. |

## Artifacts and remaining limits

APK: artifacts/LessOften-0.1.1-debug.apk, with sibling SHA-256 file. Source, setup, recovery and repeatable test commands are in README.md and docs/acceptance.md. Temporary probe packages are development-only and are not included in the launcher APK.

The temporary probe and its instrumentation package were removed after validation. Final device verification confirmed versionCode 2 / versionName 0.1.1, Less Often still selected as Home, accessibility enabled, secure-settings permission granted, and the original mode 0 / enabled 0 display settings restored on Home.

No zero-latency or pre-launch interception guarantee is made. The gate is owner-controlled friction, not an Android security boundary. Split-screen/PiP are excluded. Concurrent bedtime/color-correction interactions, all reboot/safety paths and the manual items above remain unverified. No factory reset, existing user-app removal or existing notification preference change was performed. Device identifiers and private phone contents are not in this report; recovery values and debugging screenshots remain in ignored local artifacts.
