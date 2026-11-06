# Less Often — Quiet instrument and usage awareness

**Status:** Accepted and closed November 6, 2026. The user confirmed the 0.2 changes after more than a week of daily testing. See [validation evidence](docs/validation.md) for recorded checks and remaining device-specific limitations.

**Specification date:** October 30, 2026.

**Planned release:** 0.2.0.

**Scope update, October 31, 2026:** The user removed TalkBack-specific testing and acceptance from this release. The other accessibility, scaling, navigation, and inset requirements remain.

**Baseline:** Accepted MVP 0.1.1, preserved in [launcher-mvp-spec.md](launcher-mvp-spec.md).

**Target:** Existing personal Google Pixel 6a, Android 17 / API 37.

## 1. Intent and approved decisions

Make the launcher feel deliberately designed while preserving immediate access to routine tools and deliberate friction for Restricted apps. Usage feedback should be factual, glanceable, and locally computed.

The user selected the **Quiet instrument** concept and approved:

- A fixed six-app home screen with simple line symbols and labels.
- An always-dark palette: charcoal surfaces, warm light text, muted sage accent.
- Home metrics: **screen time, device unlocks, restricted minutes**.
- Today’s totals and seven-day history in a usage detail screen, including notification counts.
- Comparisons with usual usage by the same time of day once sufficient data exists.
- Installed app icons in Secondary and Restricted drawers.
- A consistent visual treatment for Home, drawers, and the quote challenge.

This specification changes the MVP’s text-only presentation and exclusions of launcher date/time and usage features. All enforcement, package policy, independent session duration, display restoration, recovery, and accepted bypass limits remain baseline requirements unless explicitly amended here.

## 2. Scope

### Included

- Quiet instrument Home, drawer restyling, and quote-screen restyling.
- Optional Usage Access and Notification Access setup with clear capability states.
- Local usage collection, daily aggregation, seven-day history, and same-time comparisons.
- A read-only usage detail screen reachable from Home.
- Upgrade-safe local storage and testing on the target phone.

### Excluded

- Runtime app reordering, bucket editing, icon packs, custom wallpapers, widgets, and theme settings.
- Daily quotas, new blocking rules, gamification, streaks, scores, warning-color escalation, and usage reminders.
- Notification badges, notification text retention, notification mirroring, filtering, or dismissal.
- Cloud sync, accounts, telemetry, Internet permission, and export.
- Work profiles, multiple users, split-screen, and picture-in-picture usage attribution.

## 3. Visual system

Use these initial design tokens; adjust slightly during visual QA for contrast and rendering while retaining the approved character:

| Token | Initial value | Use |
| --- | --- | --- |
| Background | `#171917` | Opaque screen background |
| Surface | `#232A22` | Home tiles and input surfaces |
| Primary text | `#E6E8DF` | Titles, app labels, values |
| Secondary text | `#A7AFA1` | Supporting labels and navigation |
| Divider | `#373E34` | Quiet structural separators |
| Accent | `#A6B79A` | Restrained focus and selected emphasis |

- Use the platform sans-serif family, regular/medium weight, and tabular numerals for changing values.
- Start with 28 dp horizontal screen padding, 12 dp tile gaps, and 14 dp tile corner radius. Scale to the device and accessibility settings.
- Starting type sizes: clock 56 sp, screen titles 26 sp, metric values 22 sp, app labels 17 sp, supporting text 12–14 sp. Do not shrink essential text to fit.
- Preserve native system status/navigation bars and their insets. Respect display cutouts, keyboard, font scaling, and system navigation.
- All actions have at least 48 dp touch targets and visible pressed/focus feedback. Text and icons meet applicable contrast needs against their actual surface.
- Use no looping animation, gradients, ornamental imagery, or notification dots. Restrained transition/press feedback is permitted.
- Production remains dark regardless of system theme. The brainstorming preview was illustrative, not a requirement to follow host appearance.

## 4. Home

Top-to-bottom hierarchy:

1. Small `Less Often` wordmark and localized weekday/date.
2. Large local clock following the device’s 12/24-hour preference, without seconds.
3. Compact three-column usage strip labeled as today’s activity: `Screen time`, `Unlocks`, `Restricted`.
4. Six equal tiles in two columns and three rows.
5. Separate `Secondary` and `Restricted` drawer navigation controls near the bottom.

The grid order is fixed, read left-to-right and top-to-bottom:

| Left | Right |
| --- | --- |
| Phone | Messages |
| Camera | Maps |
| Clock | Settings |

- Each tile has a small consistent line symbol above its label: phone, message bubble, camera, map, clock, settings. Use shipped vector assets, not installed app artwork, for these six symbols.
- Primary package mappings remain the existing source configuration. Each tile resolves a valid component at launch; no duplicate Primary tiles for package aliases.
- Preserve fixed positions when an app is unavailable. Show its tile disabled with an accessible unavailable state; never silently launch an alternative package.
- Primary tiles launch immediately. Opening Home or returning via system Home cancels pending quote attempts as before.
- The whole usage strip is a labeled action opening usage details. It does not launch an app or grant access.
- Display durations as whole minutes below an hour and hours/minutes above it. Use `<1m` for a positive duration below a minute, and `0m` for a known zero. Detail views can use fuller labels.
- Unknown/unavailable data displays `—`, never a fabricated zero. A short `Enable usage access` or `Tracking incomplete` message appears where relevant and leads to details/setup.
- Once eligible, show one quiet comparison line below the strip, based on screen time, e.g. `12m less than usual by now`.
- Keep the grid and footer comfortably reachable on the Pixel at default font size. Allow vertical scrolling at larger text/display sizes rather than clipping controls or forcing tiny text.

## 5. Secondary and Restricted drawers

- Separate screens with a Home/back action, a clear title, and alphabetical sections using locale-aware sorting and existing installed activity labels.
- Use a vertically scrolling list. Each row contains a 32–36 dp installed icon and app label; names wrap as needed.
- Secondary icons use their normal colors. Restricted icons use a local grayscale rendering of the same icon. Do not change whole-display color settings simply to render the drawer.
- Alphabetic section labels are quiet and noninteractive. Group non-letter starts under `#` where appropriate for the locale.
- Restricted rows show `Locked` or `Open · 7m left` as secondary text. Active time reflects the existing package session, including time spent elsewhere; opening the drawer never starts or extends a session.
- A valid active session opens immediately. Otherwise retain the existing quote challenge.
- Cache icons, load without blocking scrolling, invalidate on package changes, and provide a neutral fallback icon. Preserve distinct activity entries and package-wide shared session state.
- Back returns Home. No app sorting controls, search, configuration gestures, or notification badges in this release.

## 6. Restricted quote challenge

- Reuse the same dark palette, spacing rhythm, and typography in the existing opaque accessibility overlay.
- Header: small grayscale app icon and app name, plus a quiet `10-minute session` label.
- Instruction: `Type this quote to open for 10 minutes.`
- Render the quote prominently with comfortable line spacing and subdued attribution underneath.
- Give the typing field an explicit surface/boundary, readable hint, and visible focus state. Keep it reachable when the keyboard opens; allow scrolling.
- Preserve the actual input protections, exact quote-validation rules, offline quote source, and error behavior.
- Keep `Open for 10 minutes` and a clearly visible `Go Home` action. The latter cancels rather than granting access. Do not add a skip route or shorten the challenge.
- When reliable local data exists, show `2 sessions opened today` for that package. Count only completed durable grants; avoid predicting that the current attempt will succeed.
- The successful grant still uses the existing monotonic ten-minute deadline and package-wide enforcement. Styling must not alter launch destinations, clipboard protections, overlay opacity, focus, cancellation, or display continuity.

## 7. Metric definitions

All metrics describe the current user and local calendar day. A new day begins at local midnight, not after a rolling 24-hour interval.

### Screen time

- Define as elapsed time when the display is interactive and the device is unlocked, including time on Home and in system screens.
- Exclude always-on/ambient display, locked-screen time, and screen-off background audio.
- Derive intervals from Android usage/lock-state events; do not simply sum package foreground totals, which need not equal device screen time.
- Treat this as Less Often’s defined metric; do not promise an exact match with Digital Wellbeing.

### Device unlocks

- Count a confirmed transition from locked to unlocked while interactive.
- Do not count Home visits, app switches, duplicate keyguard-hidden events, or screen wakes while already unlocked as new unlocks.
- Validate fingerprint, PIN, notifications waking the screen, screen off/on, and lock-delay behavior on the actual Pixel.

### Restricted minutes

- Count actual foreground use of a Restricted package while the phone is interactive and unlocked.
- Exclude time spent typing a blocking challenge, time on Home, other apps, and time merely covered by a still-valid ten-minute grant.
- Treat the keyboard as part of the underlying app session; do not count the IME as a separate app. Suspend attribution for blocking system interruptions when the underlying app is not actively being used.
- Use usage events with the gate’s own challenge state to avoid counting an app underneath a blocking overlay. Persist enough gate state to reconcile interruptions; mark unresolved intervals incomplete rather than guessing.
- Respect classification at collection time; future source bucket changes do not rewrite old daily totals.
- Restricted time must not exceed screen time for the same complete interval.

### Notifications

- Count newly observed eligible notification instances after Notification Access is enabled, using a notification listener.
- Exclude ongoing/persistent status notifications, group-summary duplicates, and notifications from Less Often itself.
- Updates to an already-active notification key do not increase the count. A removal followed by a later new post can count as a new instance, even if the app reuses its key.
- On initial connection/reconnection, establish a baseline of already-active keys without counting them as newly received. This intentionally does not reconstruct missed history.
- The metric counts observed notification instances, not messages: multiple messages updating one active notification can count as one.
- Store only identifiers/timing needed to deduplicate and daily counts; never retain titles, bodies, sender names, or message content.
- Notifications remain native and behave exactly as before.

### Restricted sessions

- Count successfully committed grants per package and day. Failed or canceled challenges, reopens within a valid grant, and service reconnection do not count.
- Commit the count idempotently with the grant or via a recoverable grant identifier, so a crash cannot duplicate it.
- Surface the total in usage details and the package count on the challenge. This is additional context, not a new limit.

## 8. Usage detail screen and comparisons

- Home/back action, title `Today`, and readable totals for screen time, unlocks, restricted minutes, notifications, and restricted sessions opened.
- Include a seven-day history: today and the six preceding local calendar dates. Show compact labeled daily values; a small selectable bar chart may use screen time as the default measure, with other metrics available by labeled selection.
- Selecting a day shows its totals and coverage status. No per-app usage ranking or notification content in this release.
- Every metric distinguishes complete, partial, and unavailable coverage. Today is naturally in progress; missing tracking coverage is a separate condition.
- Provide permission setup links when needed without making access a condition of normal launcher use.
- Compare today’s screen time with the arithmetic mean at the same local clock time across the preceding seven days, excluding days with incomplete coverage through that time.
- Require at least three eligible prior days and complete coverage today through the comparison time. Until then, omit the comparison; details can explain `Building your baseline`.
- Store five-minute cumulative screen-time checkpoints, and compare at the latest completed five-minute boundary shared by today and the eligible days. Do not compare a partial day with full-day totals.
- In details, state the number of baseline days and cutoff time. Round the difference to minutes; differences below one minute read `About usual by now`.
- Use factual language and neutral styling for both higher and lower use. No red warnings or celebration animations.

## 9. Access, collection, and retention

- Add optional Usage Access and Notification Access setup entries with status and concise explanations. Existing accessibility/secure-setting setup remains independently meaningful.
- Usage Access enables screen time, unlocks, and restricted usage collection. Notification Access independently enables notification counting. Local grant counts do not require either new access.
- Permission denial/revocation never blocks Primary apps or disables the existing gate. Display appropriate missing-data states and direct links to system settings.
- Collect incrementally, with persisted cursors and deduplication. Refresh when Home/details becomes visible and periodically while visible; avoid querying history every frame or keeping a new permanent foreground service solely for statistics.
- Reconcile overlapping query windows safely to handle late events without double counting. Persist daily aggregates because Android retains fine-grained usage events for only a limited period.
- During first setup, backfill only metrics/intervals that the available events can establish reliably. Do not invent notification history or pre-install challenge exclusions. Mark partial days accordingly.
- Bound retention to today plus seven preceding days, enough for the comparison baseline. Prune older aggregates/checkpoints and temporary deduplication data; the visible history is today plus six days.
- Use app-private local storage excluded from backup/device transfer. No network access or analytics. Avoid retaining raw usage events after reconciliation except the bounded overlap/state needed for correctness.
- Split intervals at midnight and handle reboot, process death, event gaps, and system clock/time-zone changes explicitly. Duration arithmetic must not create negative or inflated intervals after a clock change.
- On time-zone or manual-clock discontinuities, preserve recorded totals with their original day/zone metadata and mark affected periods ineligible for comparison; do not fabricate a seamless history. Handle daylight-saving repeats/skips explicitly or exclude affected days from same-time comparison.
- A tracking outage is not zero activity. Reconstruct from available events when reliable; otherwise flag gaps. Notification listener outages remain partial because missed notifications cannot be reliably backfilled.
- Collection and storage failures must not delay launching an app, grant access, extend a deadline, or change display restoration behavior.

## 10. Implementation boundaries

- Home/drawers/details remain Compose surfaces. Share visual tokens with the native `GateView`; replacing its input/overlay mechanism is unnecessary for this design change.
- Keep usage aggregation independent of enforcement policy and display ownership. Use background work for queries/aggregation and deliver small immutable UI snapshots.
- Extend `AppCatalog` to supply/cache installed artwork without changing the policy identity model.
- Preserve the installed app identity, signing key, existing app configuration, and display recovery journal through an in-place upgrade. Never uninstall as an upgrade step.
- Update setup, README, architecture, and validation documentation during implementation. Do not rewrite the closed MVP specification as if these features shipped in 0.1.1.
- If target-device evidence prevents a defined metric or interaction, document the limitation and resolve the behavior with the user before silently substituting another definition.

## 11. Acceptance criteria

### Presentation and navigation

- Pixel screenshots confirm the charcoal/warm-text/sage Quiet instrument design across Home, both drawers, usage details, and the gate with keyboard open.
- Home has the approved six fixed tiles and three approved metrics. Date/time follow locale and clock preference; production does not switch to a light theme.
- All screens work with larger text, gesture navigation, and system insets. No clipped labels, inaccessible footer actions, or hidden gate actions.
- Drawer icons load smoothly, missing icons have fallbacks, and Restricted drawer icons are grayscale without whole-display setting changes.
- Unavailable Primary packages preserve their disabled positions; package updates/removals cannot crash launches or leave stale identity targets.

### Measurement correctness

- Deterministic aggregation checks cover lock/unlock duplicates, ambient display, background audio, foreground app switches, overlay time, IME, system interruptions, midnight, reboot, process restart, late/replayed events, permission loss, and clock changes.
- Notification checks cover new posts, updates, grouped posts, persistent notifications, removal/repost, initial connection, and reconnect without duplicate increments.
- Same-time comparison checks cover fewer than three eligible days, partial days, five-minute cutoff alignment, rolling retention, and invalidated clock/time-zone periods.
- A scripted device session verifies known elapsed screen use and Restricted use, with measured event/reporting latency documented. Verify fingerprint/PIN unlock counts separately.
- Denied/revoked access shows unavailable/partial values rather than zero, and restoring access does not duplicate history.
- Validate first install and in-place upgrade without altering enforcement or recovery state. Audit stored fields to confirm no notification content is retained.

### Enforcement regression

- Preserve quote typing/paste restrictions, Home/cancel behavior, notification/link/Recents entry, independent sessions, real ten-minute expiry, and same-boot process recovery.
- Re-run relevant display-continuity checks: Restricted activity transitions do not flash color; Home/permitted apps restore the exact captured baseline.
- Statistics collection failure does not interfere with opening Primary apps or gating Restricted apps.
- Run the project’s build, unit tests, and lint, plus focused device tests and visual review. Record evidence and any residual limitations in validation docs before release acceptance.

## 12. Delivery order

1. Implement the shared visual system, fixed Home, icon drawers, and styled gate; verify existing enforcement.
2. Add optional access setup, local aggregation/storage, and notification counting with correctness tests.
3. Connect Home totals and usage details, seven-day history, and eligible comparisons.
4. Perform device measurement, accessibility, upgrade, visual, and enforcement acceptance; package the update and document results.

No implementation is claimed by this document. There are no outstanding product questions following the user’s three explicit design/metrics/history selections; the operational details above are implementation defaults for this release.

## References

- [Android UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
- [Android usage events](https://developer.android.com/reference/android/app/usage/UsageEvents.Event)
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [LauncherActivityInfo and icons](https://developer.android.com/reference/android/content/pm/LauncherActivityInfo)
