# Acceptance matrix

A release passes only when every applicable must-level check below has evidence.

## A. Product boundary

- [ ] One production package exists: `dev.phosphor.mobil3`.
- [ ] Debug uses `.debug` and installs beside production.
- [ ] The build has no Play/Fortress flavor dimension.
- [ ] Active source and docs contain no Nexus, Nexidex, product-agent, root, Shizuku, or ProjectM promise.
- [ ] Historical decisions and receipts remain intact outside the active authority chain.

## B. Android compatibility

- [ ] `minSdk` is 29 and compile/target SDK are 36.
- [ ] API 29, 31, 34, and 36 compatibility checks pass.
- [ ] API 29 uses full-display projection through the legacy capture intent.
- [ ] API 34 and newer request the default display through `MediaProjectionConfig`.
- [ ] PiP, cutout, rounded-corner, parcelable, notification, and backup behavior are version-safe.

## C. Privacy and permissions

- [ ] Source and dependency scans find no analytics, telemetry, advertising ID, installation ID, or reporting SDK.
- [ ] The app stores no behavior or usage history.
- [ ] Fresh installation produces no Phosphor-owned traffic before explicit remote use.
- [ ] Every retained permission maps to a visible supported feature.
- [ ] Microphone hardware is optional.
- [ ] In-app and HTTPS privacy surfaces agree with measured behavior.

## D. Settings migration

- [ ] Existing user-facing HUD or display settings migrate once.
- [ ] Nexus principals, sessions, grants, tokens, authorization state, and audit history do not survive migration.
- [ ] Remote hosts remain excluded from Android backup.
- [ ] A failed or malformed legacy envelope falls back safely without blocking startup.

## E. Capture

- [ ] Microphone permission precedes MediaProjection consent when playback capture needs it.
- [ ] The service enters the mediaProjection foreground state before obtaining projection.
- [ ] Consent denial, microphone denial, token rejection, lock, revocation, process death, and stop release resources.
- [ ] The interface distinguishes flowing audio, permitted silence, and failed capture.
- [ ] The app does not reprompt while a valid capture session is active.

## F. Local playback and interface

- [ ] Single-file and folder playback work.
- [ ] MediaSession, notification, lock-screen controls, focus loss, noisy route, seek, and track changes work.
- [ ] The scope remains sample-locked to audible local playback.
- [ ] Existing modes, themes, beam controls, geometry effects, rotation, settings transfer, and PiP survive cleanup.
- [ ] Android system rotation authority and large-screen layouts remain usable.

## G. PC relay

- [ ] Host add, edit, select, browse, play, stop, seek, next, previous, metadata, and artwork paths pass.
- [ ] Audio and geometry toggles retain their protocol meaning.
- [ ] Automatic, Wi-Fi, and mobile routing states remain truthful.
- [ ] `scripts/force-link-states.sh` reproduces connected, silent, stalled, recovered, and reconnecting states.
- [ ] Live playback works over Tailscale.
- [ ] Documentation warns that protocol v2 is not safe for direct public-internet exposure.

## H. Developer CLI

- [ ] `dev/pm3 schema` describes every retained verb.
- [ ] One-shot output follows the required JSON envelope in structured mode.
- [ ] Errors carry `error` and `fix` with exit code 2, 3, or 4.
- [ ] Streams use the declared format.
- [ ] Product state, Nexus, authority, audit, and agent-control verbs are absent.

## I. Automated quality

- [ ] Android unit tests and release lint pass.
- [ ] Debug APK, release APK, and release AAB build from the canonical Gradle workflow.
- [ ] Rust core and relay pass locked tests, formatting, and clippy.
- [ ] Shell scripts pass syntax and boundary tests.
- [ ] Source, manifest, dex, dependency, endpoint, and native-string scans pass.
- [ ] APK zip alignment and every packaged ELF pass 16 KiB checks.

## J. Release identity

- [ ] The Git tag identifies the exact source commit.
- [ ] APK, AAB, source archive, checksums, and build manifest identify the same version and commit.
- [ ] APK and AAB use the approved certificates.
- [ ] GitHub release assets match the local release hashes.
- [ ] The S25 installation matches the GitHub APK package, version, signer, and hash.
- [ ] Previous APK and settings backups remain usable until final validation passes.

## K. Git and publication

- [ ] Private `main` contains the complete selected linear history.
- [ ] No unique commit remains stranded on an obsolete branch.
- [ ] The public tree contains no private host, credential, internal receipt, or archived private authority material.
- [ ] No push to `main` or release publication occurs without Ben's explicit approval.

## Release gate

If a required check lacks evidence, the release is not complete. Record the blocker, evidence, best current result, and smallest next step.
