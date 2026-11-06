# Privacy

Less Often has no Internet permission, account, analytics, telemetry or remote quote API. Building it downloads developer dependencies; the installed launcher does not send usage data to a server.

| Permission/capability | Purpose and data |
| --- | --- |
| Accessibility service | Identifies foreground packages using window/package metadata and displays the quote gate. It does not traverse message text or app contents. |
| WRITE_SECURE_SETTINGS | Applies grayscale and restores the exact recorded color settings. Granted explicitly via ADB. |
| Usage Access (optional) | Reads Android screen, keyguard and foreground events for local screen time, unlock and Restricted-use measurements. |
| Notification Access (optional) | Observes notification instances and connection state to count them. Android grants broad listener access; this implementation does not store notification content or reissue notifications. |
| Boot completed | Reconciles persisted state after boot. |
| Launcher package visibility | Discovers launchable activities for the app catalog; no unrestricted package query permission is requested. |

Sessions, quote selection, display recovery journals, usage reducer state and daily aggregates are in app-private SharedPreferences. The UI exposes seven-day history; coverage and recovery bookkeeping are also persisted. Android backup/device transfer is disabled. Revoking optional access stops that source of collection; it does not promise deletion of previously collected values. Clearing app data or uninstalling deletes local state, but first restore any pending display baseline using [recovery.md](recovery.md).

The computer's display-baseline files, screenshots, APKs and diagnostic outputs are separate local artifacts. They are ignored by Git, not automatically erased. Review any issue attachments for package lists, notifications, personal screens, device identifiers and local paths. The bundled app policy itself reveals the original author's service/provider choices; it is public source configuration, not a runtime upload.
