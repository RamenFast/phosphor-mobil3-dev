# Pre-v2 B1-B21 receipt index

**Latest physical checkpoint:** [September 6 S25 testing and installed corrections](phase-15-16-s25-2026-09-06.md). Console volume is removed, the grid is measurably stronger, and actual SOURCE queue taps are repaired. B8 and the documented final-matrix gaps remain open.

Status: B9 Phase 2 passed with replay-verified Android log evidence. Spotify permission recovery is recorded separately. Final behavior receipts remain pending.

A B card passes only when its automated gate and named live receipt agree. A screenshot without state evidence is not a pass. A missing receipt keeps the card open.

## Index

| B IDs | Planned receipt | Final regression receipt | State |
|---|---|---|---|
| B9 | `phase-02-b9-seek-stress.md` | `phase-15-crash.md` | Phase 2 PASS; VERIFY pending final regression |
| B4 | `phase-03-b4-folder-tree.md` | `phase-15-crash.md` | Phase 3 required live PASS; final regression pending |
| B2 | `phase-04-b2-mic.md` | `phase-15-sources.md` | Phase 4 live PASS 5/5; final regression pending |
| B6, B7, B8 | `phase-05-b6-b7-b8-media-truth.md` | `phase-15-truth.md` | exact Phase7 installed; local title/artist and seek recovery observed; paused seek publication and complete captured transport remain VERIFY |
| B21 | `phase-06-b21-lifecycle.md` | `phase-15-lifecycle.md` | actual mic/local recents removal cleaned source services; capture/relay/linger and mic-only/no-service cases remain VERIFY |
| B3 | `phase-07-b3-relay-matrix.md` | `phase-17-relay.md` | committed gate4 and exact APK installed/readback verified; bidirectional tailnet ping passed; relay/Linux acceptance pending |
| B1, B18, B19 | `phase-08-gestures.md` | `phase-16-gestures.md` | frozen source review and exact clean 272-test artifact passed; six receipt hashes verified; installation and live gestures pending |
| B10, B12, B13, B14 | `phase-09-ui.md` | `phase-16-ui.md` | committed 992c08a, separate reviews and exact 308-test APK passed; six artifact hashes verified; installation and Android acceptance pending |
| B5, B11 | `phase-10-display.md` | `phase-16-display.md` | committed 7f70e29, separate reviews and exact 348-test APK passed; 33 native gate cases and six artifact hashes verified; installation and Android acceptance pending |
| B15, B16, B20 | `phase-11-render.md` | `phase-16-render.md` | exact two-commit artifact built and source/host reviewed; physical grid measurement, render checks and Ben visibility acceptance pending |
| B17 | `phase-12-b17-settings.md` | `phase-16-settings.md` | committed859c674 exact debug artifact verified; partial emulator startup/persistence/PiP observed, native emulator rendering blocked; S25 preservation and full defaults remain OPEN |

The supplemental [rotation and sheet-continuity receipt](phase-12-rotation.md) records the reviewed 420-case JVM correction, selected native/shared checks and remaining Android boundaries. It does not close a B card.

The supplemental [exact Phase7 live checkpoint](phase-07-installed-live-checkpoint.md) records the settings-preserving upgrade, actual local/mic workflows, replay-verified log window and remaining gaps. Historical offline receipts retain their original timestamped limits.

The [Phase14 host preflight](phase-14-host-preflight.md) records full mobile-native/relay tests and the unresolved shared-format and publisher-fixture gates. It is not Phase14 acceptance.

Phase 14 records the full automated gate and exact installed debug APK. Phase 18 adds the commit list, artifact hashes, signer evidence, gate outputs, final receipt hashes, and the three unchanged external release reds: `signing.release`, `provenance.release`, and `release.bundle`.

## Redaction law

The supplemental `spotify-permission-recheck-2026-09-05.md` records B6/B7/B8 recovery after granting notification access. It does not close Phase 5.

Tracked receipts must contain enough evidence to repeat and audit the check without exposing private estate state.

Do not record:

- a literal ADB serial
- a private hostname, Tailscale address, LAN address, or saved relay endpoint
- credentials, tokens, keys, passwords, keystore paths, or private host lists
- a personal media-library path or unrelated file name
- raw private logs, settings exports, screenshots, recordings, or device backups

Use stable role labels such as `S25`, `relay-host`, and `clean-user-fixture`. Replace a literal serial or address with `[redacted]`. Keep raw evidence under the ignored `dev/scratch/pre-v2-<timestamp>/` tree. A tracked receipt may include a narrow redacted excerpt and the SHA-256 of the raw artifact.

Hashes, package names, version codes, issue numbers and URLs, source commits, Android build identifiers, and signing-certificate fingerprints are allowed when they prove the tested identity. Public sibling issues and receipts must also omit private mobile issue details and estate-specific device facts.

Before a receipt lands, run a targeted secret and private-marker scan. If redaction removes the fact that proves the result, keep the card open and record a safe verification method instead of weakening the claim.

## Required device and artifact fields

Each device receipt records:

| Field | Required value |
|---|---|
| Device role | `S25` or the named emulator role |
| Device model | Model reported by Android |
| Android version | Release and API level |
| Device build | Build identifier used for the check |
| ADB identity | `[redacted]`; never the literal serial |
| Package | Debug or production application ID |
| App version | Version name and version code |
| Mobile source | Exact Git commit |
| Sibling source | Exact sibling-engine commit when applicable |
| APK identity | Local SHA-256 and installed read-back SHA-256 |
| Signer identity | Signing-certificate SHA-256 |
| Settings state | Only named test values; private export stays in scratch |
| Start state | Cold/warm process, selected source, consent, and permission state |
| Time window | Start and end timestamps with time zone |

A relay receipt also records the relay source commit, binary SHA-256, schema version, service state, and redacted Tailscale-route evidence.

## Phase receipt template

Copy this template for each tracked phase receipt.

```markdown
# Phase NN: <B IDs and outcome>

- Date and time zone:
- Status: PASS | VERIFY | BLOCKED
- B IDs:
- Private issue refs:
- Public sibling issue refs, if applicable:
- Mobile commit:
- Sibling commit, if applicable:
- Device role and model:
- Android release, API, and build:
- ADB serial: [redacted]
- Package, version name, and version code:
- Local APK SHA-256:
- Installed APK SHA-256:
- Signer SHA-256:
- Relay binary/source SHA-256, if applicable:

## Starting state

State the process, source, permission, consent, network, and settings conditions that affect the result.

## Commands and automated evidence

List exact commands and exit results. Link or hash any private raw output stored in `dev/scratch/`.

## Live procedure and observations

List the ordered user actions. Record timestamps and observed source, transport, beam, audio, service, process, and notification state.

## B-card results

- BNN: PASS | VERIFY | BLOCKED: evidence

## Redaction check

- [ ] No literal serial, private host, address, credential, personal path, or raw private state.
- [ ] Public-safe evidence contains no private mobile or estate detail.
- [ ] Each retained claim still has a reproducible check.

## Rollback and restoration

Record the pinned prior APK or source point. Confirm restoration of changed device, settings, relay, screen-timeout, and fixture state.

## Blocker, if any

**Blocked:** exact bottleneck. **Evidence:** facts that establish it. **Best current result:** last verified result. **Next step:** smallest action that resolves it.
```

Do not change `drift` from 21 to 0 until every B1-B21 row has its final PASS receipt. Visual items remain `verify` until Ben accepts the measured device result.
