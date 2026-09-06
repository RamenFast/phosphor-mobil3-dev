# Acceptance matrix

A release passes only when every applicable must-level check below has evidence.

## A. Product boundary

- [ ] One production package exists: `dev.phosphor.mobil3`.
- [ ] Debug uses `.debug` and installs beside production.
- [ ] The build has no Play/Fortress flavor dimension.
- [ ] Active source and docs contain no removed integration or privileged-capture promise.
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
- [ ] `WAKE_LOCK` is held only by a visible surface or live playback/capture source and is released after stop.
- [ ] Microphone hardware is optional.
- [ ] In-app and HTTPS privacy surfaces agree with measured behavior.

## D. Settings migration

- [ ] Existing user-facing HUD or display settings migrate once.
- [ ] Legacy principals, sessions, grants, tokens, authorization state, and audit history do not survive migration.
- [ ] Remote hosts remain excluded from Android backup.
- [ ] The five portable settings keys and the legal 0.1-through-60-second cycle range survive archive round trips.
- [ ] A failed or malformed legacy envelope falls back safely without blocking startup.

## E. Capture

- [ ] Microphone permission precedes MediaProjection consent when playback capture needs it.
- [ ] The service enters the mediaProjection foreground state before obtaining projection.
- [ ] Consent denial, microphone denial, token rejection, lock, revocation, process death, and stop release resources.
- [ ] The interface distinguishes flowing audio, permitted silence, and failed capture.
- [ ] The app does not reprompt while a valid capture session is active.

## F. Local playback and interface

- [ ] Single-file and recursive folder-tree playback work, including nested and invalid entries.
- [ ] MediaSession, notification, lock-screen controls, focus loss, noisy route, seek, and track changes work.
- [ ] The last local queue item drains to a native ended state. Play after completion restarts it from zero rather than repeatedly pausing its remaining tail.
- [ ] The scope remains sample-locked to audible local playback.
- [ ] Existing modes, themes, beam controls, geometry effects, rotation, settings transfer, and PiP survive cleanup.
- [ ] Android system rotation authority and large-screen layouts remain usable.

## G. PC relay

- [ ] Host add, edit, select, browse, play, stop, seek, next, previous, metadata, and artwork paths pass.
- [ ] Audio and geometry toggles retain their protocol meaning.
- [ ] Relay traffic follows Android's Tailscale route without a process-wide physical-network bind.
- [ ] `scripts/force-link-states.sh` reproduces connected, silent, stalled, recovered, and reconnecting states.
- [ ] Live playback, direct-file playback, and recursive folder-tree playback work over Tailscale.
- [ ] Documentation warns that protocol v2 is not safe for direct public-internet exposure.

## H. Developer CLI

- [ ] `dev/pm3 schema` describes every retained verb.
- [ ] One-shot output follows the required JSON envelope in structured mode.
- [ ] Errors carry `error` and `fix` with exit code 2, 3, or 4.
- [ ] Streams use the declared format.
- [ ] Product state mutation, runtime administration, authority, audit, and product automation verbs are absent.

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

## L. Pre-v2 B1-B21 lived-repair matrix

Each row requires its named automated gate and an honest live receipt. A visual row remains open until Ben accepts the measured device result.

| Done | ID | Observable acceptance |
|---|---|---|
| [ ] | B1 | Console, active-sheet, and overflow bounds block scope pinch and drag through exactly 333ms after movement or dismissal. The 24dp rotated exclusion margin applies to every pointer without blocking chrome reveal or an armed console pull. |
| [ ] | B2 | Five consecutive capture-to-microphone switches with permission already granted start a moving beam. Only a post-stop idle observation may start the microphone. |
| [ ] | B3 | The regular app stays network-dormant until relay selection. A fresh Linux relay then proves Tailscale live audio, a direct root file, a whole-folder first item, nested end-of-file advance, disconnect, and reconnect. |
| [ ] | B4 | A direct control file and a recursive local folder containing nested audio and one invalid entry play in stable order without a freeze, ANR, or dark false-success. |
| [ ] | B5 | With fixed device brightness, deposited trace luminance and a nondefault beam setting remain stable after chrome motion, settings cycles, and surface recreation. Intentional modal scrim dimming is excluded. |
| [ ] | B6 | A tagged local folder item and a real captured Spotify session show honest title and artist. Captured controls work, and stale or superseded metadata cannot replace the current face. |
| [ ] | B7 | A captured session exposes a working in-app seek rule only when the session advertises seek and reports a real duration. A non-seekable session exposes no dead rule. |
| [ ] | B8 | The capture play/pause glyph and routed action match audible playing, buffering, paused, and resumed states without changing local playback truth. |
| [ ] | B9 | After a paused local ring fills, repeated scrub, next, and previous actions leave the interface and sound path responsive. Only the latest requested item resumes. |
| [ ] | B10 | FEEL, MOTION, CORNERS, and LABELS each change visible chrome immediately while STYLE is open and persist after close and reopen. |
| [ ] | B11 | Main and picture-in-picture scope surfaces remain awake beyond a shortened captured timeout while a source is live. The display becomes sleep-eligible after stop, and the original device timeout is restored. |
| [ ] | B12 | Quick and full `pip_auto_enter` controls stay synchronized. Off blocks automatic entry only, on restores it, and manual picture-in-picture works in both states. |
| [ ] | B13 | No DECK destination or console volume row remains. SOURCE queue jump and persisted always-visible controls work, including both the 4-second and tap-to-hide paths. The console contracts without changing its remaining controls. Android retains volume ownership. |
| [ ] | B14 | Seek, tuning, and range rules acquire across a 44dp lane and retain sharp 2dp tracks, square thumbs, live beam accent, and direct jump behavior. |
| [ ] | B15 | A measured S25 grid receipt proves visible, repeatable CPU/GPU grid parity. Left-only and right-only fixtures publish the correct raw amplitude and absolute dBFS side only when `grid_data=true`. |
| [ ] | B16 | Xy45 rotates both trace and grid by 45 degrees in CPU and GPU paths. Xy, swirl, dots, and other modes restore a Cartesian grid without an alpha change. |
| [ ] | B17 | Same-package updates preserve edits, the settings archive round-trips all legal values and five new keys, and a clean install gets the accepted defaults with `custom_count=0`. |
| [ ] | B18 | With always-visible controls off, disabling double tap removes playback toggling and single-tap delay. Enabling it restores double-tap playback. |
| [ ] | B19 | An Android bottom-edge swipe or pinch with any pointer in the 88dp band never changes scope gain or orbit. The armed one-finger upward console pull still works. |
| [ ] | B20 | Loud, silent, then loud audio keeps stable framing. Only a proven new local item resets peak tracking; metadata refresh does not. The 6.0 clamp, 0.92 headroom, and 0.05 glide remain unchanged. |
| [ ] | B21 | With `linger_background=false`, recents removal stops local, relay, capture, and microphone sources and clears capture consent. Linger preserves only sources with a real service owner and never claims unsupported microphone survival. |

### B21 regression conditions

These conditions extend the B21 row. They are not additional accepted cards.

- [ ] Both service callback orders retire the removed task once. Delayed consent, source starts and saves cannot revive it.
- [ ] A newer live task survives a delayed old removal callback. Reopening a retained service, disabling linger and removing the next task stops the source.
- [ ] A stopped capture owner cannot recreate PlaybackService or clear a newer owner's mirror. A new playback service restores a surviving capture mirror without a new projection or consent request.
- [ ] Activity destruction stops its own microphone without stopping a newer activity's owner. Picker navigation preserves its existing source.
- [ ] Repeated local, capture and relay player release completes without a native stop or external transport command from the face.
- [ ] Replacement playback waits for actual predecessor cleanup without converting a wait timeout into permanent new-owner failure. A real reader timeout, owner destruction and subsequent successful stop retry permit recovery.
- [ ] Ordinary playback-service destruction preserves a separately owned microphone or capture source. Default task removal ends a cleaned-up started capture service even after timeout, while retaining the actual reader failure.
- [ ] Android receipts establish actual task callback delivery and task-membership timing, including microphone-only operation with no playback service.

## Release gate

If a required check lacks evidence, the release is not complete. Record the blocker, evidence, best current result, and smallest next step.
