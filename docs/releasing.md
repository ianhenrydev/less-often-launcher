# Preparing a public release

The repository license covers project code and documentation. Original quote texts retain their CC0 dedication described in [quotes.md](quotes.md). Gradle, AndroidX and other third-party components retain their own licenses; MIT does not relicense those dependencies.

Before changing repository visibility or publishing a source snapshot:

- Review the complete proposed source and Git history for secrets and personal material. `.gitignore` prevents new accidental additions; it does not remove previously committed data. Review author email metadata and the bundled personal app list/specifications deliberately.
- Exclude `.tools`, `.gradle`, `local.properties`, signing keys, private baseline files, app-data dumps, screenshots and generated artifacts. Never publish your development signing key. If a real secret was ever committed, revoke it and address history before publication.
- Include all intended source files, including currently new/untracked 0.2 classes, resources, tests and the 0.2 specification. Verify a clean checkout builds without ignored local tooling.
- Run the build, unit tests and lint in CONTRIBUTING.md. Record device checks separately; do not label Pixel 11 supported until checked. Version 0.2.0 is accepted for personal use; distinguish that from validation on additional devices.

Before distributing an APK:

- Choose a version and label candidate versus accepted release accurately. The existing build is debug-signed; it is not a configured production release pipeline.
- Decide who owns a stable signing key for future updates. Keep the private key outside the repository with a secure backup. Same package plus same signing key is required for in-place updates; an existing debug install cannot accept an APK signed by a different key.
- Build from the intended source, record its revision, publish a SHA-256 checksum alongside any APK, and provide setup/recovery instructions. A debug build is debuggable and should be labeled accordingly.
- Retain third-party notices as required by shipped dependencies. Do not bundle private recovery files or the development probe/instrumentation APKs as the launcher.

This checklist does not publish the repository, create a release or certify additional devices. Review the final public contents before making them visible.
