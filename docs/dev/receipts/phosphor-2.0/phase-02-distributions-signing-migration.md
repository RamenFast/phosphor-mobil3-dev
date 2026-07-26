# Phase 02 distributions, signing, migration, and boundary receipt

**Date:** 2026-07-26
**Behavior changed:** Yes, distribution/build behavior and settings migration UI only
**Privileged behavior activated:** No
**Scope:** Compile-time Play/Fortress seam, package identity, fail-closed signing, settings export/import, Play boundary proof, and release lint safety

## Result

Phase 02 host validation passes. This phase establishes separate compile-time distributions without adding Binder, Shizuku, ADB-sidecar, overlay, or private Nexus implementation.

- Play package: `dev.phosphor.mobil3`
- Fortress package: `dev.phosphor.mobil3.fortress`
- Version: `2.0.0`
- Play label: `Phosphor`
- Fortress label: `Phosphor Fortress`
- Both release APKs: non-debuggable
- Fortress certificate SHA-256: `e4d14ce2d62983acd393f012cbce759b6c97bdcca979feeb04a97afb279d9b00`
- Play installed-app certificate: still unknown pending Google Play App Signing enrollment

No S25 installation, co-installability, data-preservation, or Play-installed signer claim is made in this phase.

## Compile-time distribution seam

`app/build.gradle.kts` now defines the `distribution` flavor dimension with `play` and `fortress` source sets. The two release identities are separate at compilation, not runtime feature flags.

Typed source-set profiles live at:

- `app/src/main/kotlin/dev/phosphor/mobil3/distribution/Distribution.kt`
- `app/src/play/kotlin/dev/phosphor/mobil3/distribution/DistributionCapabilities.kt`
- `app/src/fortress/kotlin/dev/phosphor/mobil3/distribution/DistributionCapabilities.kt`

The profiles report only implementation actually compiled today. Agent control, privileged audio, and transparent overlay remain `false` in both distributions until later phases add and prove them. Fortress alone admits the private feature source set and private endpoint seeding. Play's build-time host list is unconditionally empty; a later public user flow may collect a relay address at runtime.

Rust debug and release libraries now build into isolated generated directories under `app/build/generated/jniLibs/`. This removes the prior shared-output race and keeps generated native libraries out of source sets.

## Fail-closed signing proof

Release signing no longer falls back to Android debug signing. Environment inputs override machine-local properties so CI and negative tests can force exact inputs.

Negative matrix results:

| Case | Result |
|---|---|
| Isolated source copy with every `PLAY_UPLOAD_*` input absent | Gradle exit `1`; `PLAY_UPLOAD release signing unavailable: missing ...` |
| Fortress `RELEASE_STORE_FILE` points to a missing store | Gradle exit `1`; keystore unavailable |
| Fortress inputs point to a valid foreign fixture certificate | Gradle exit `1`; certificate mismatch |
| Fortress inputs use the estate store | pass; APK signer is `e4d14c...d9b00` |

The Play APK/AAB used for host proof were signed by an explicitly non-release local fixture upload certificate:

```text
2fda9a02f280be68a60be7d2b3c4d0f5807629bcf88e82ce14369d1dd59a3523
```

Those Play artifacts are not release candidates and must never be published. The fixture private key is not stored in Git or the durable evidence archive.

## Settings migration

The app now exposes Android document-provider export and import actions for schema `phosphor.settings/1`.

Properties:

- maximum archive size: 1 MiB;
- explicit allowlist of portable user settings;
- endpoints, media paths, consents, purchase data, authorization material, and unknown local preferences are excluded;
- only inert scalar values are accepted;
- nested/object/array or executable-shaped values are rejected;
- known fields receive strict type, range, and format validation;
- schema, source package, version, distribution, RFC 3339 timestamp, and settings values are all bound into one canonical SHA-256 digest;
- imports apply through one atomic preferences commit;
- unknown scalar settings are verified and skipped for forward compatibility;
- unknown major schemas, tampering, malformed metadata, and out-of-range values fail with an error and repair instruction.

Unit tests pass in both Play and Fortress projections. This is a content-integrity and migration mechanism, not yet the host-signed release rollback export required by Phase 12. S25 debug-to-Fortress import remains pending device validation.

## Play boundary scanner

`scripts/check-play-boundary.sh` follows the agent-first CLI contract:

- one-shot JSON envelope with `status`, `tool`, `version`, and non-empty UTC `ts`;
- `schema` self-description;
- fix-bearing errors;
- exits `0` success, `2` unavailable evidence, `3` bad input, and `4` runtime/boundary failure;
- TTY human output and non-TTY JSON output.

`app:checkPlayBoundary` builds the Play AAB, records the resolved Play runtime graph, and scans:

- Play-visible source sets;
- symlinks;
- archive entries, symbolic-link modes, and member strings;
- merged manifest;
- runtime dependencies;
- private endpoint markers;
- Fortress package, Binder/Nexus, Shizuku, ADB-sidecar, shell-capture, and overlay markers.

The scanner rejects absolute or parent-traversing archive entries and rejects symbolic links both from central-directory Unix modes before extraction and from the extracted tree as defense in depth. It scans each regular-file member independently to prevent cross-entry string concatenation false positives.

Android NDK r28b `libc++_shared.so` legitimately contains `/data/local/tmp`. The scanner exempts only the exact packaged outputs derived from the pinned NDK with `llvm-strip --strip-unneeded` and `llvm-strip --strip-debug`; filename-only exemptions are forbidden. Final proof reported two verified runtime exemptions and no Play boundary hits.

Relevant derived hashes:

```text
NDK source libc++:      ab4e6c71b96b851de45a8a9bd86369e7dbc2130a44b3b4520564be94847910f2
strip-unneeded output:  cd61762848882a16c8244c964a6f396c0caa0b440588a210ce9cc4ab0e6d9f0c
strip-debug output:     cb304e649e49cb65766da9d20a2d8bb8bd0c7cfd383e1f73838f838d1263c548
```

Positive, forbidden-marker, cross-entry false-positive, malicious symbolic-link, corrupt-archive, absolute-path, and parent-traversal fixtures all pass their expected outcomes. The fixture suite also asserts unavailable-evidence exit `2` and bad-input exit `3`, while scanner/runtime violations remain exit `4`.

## Release lint safety

Full Play and Fortress lint initially exposed 126 errors. No lint baseline was created.

Root fixes:

- `CaptureService` now checks `RECORD_AUDIO` immediately before `AudioRecord` construction, handles permission/configuration/start exceptions by dying closed, treats duplicate starting/flowing intents idempotently so they cannot replace the active projection or create a second reader thread, returns `START_NOT_STICKY` because MediaProjection consent cannot be silently renewed, and publishes explicit starting/flowing/permission-needed/error truth;
- `MainActivity` marks capture live only after the service reports successful `AudioRecord.startRecording()`, clears false live state on service failure or projection loss, and surfaces a repair instruction in the source sheet;
- all four Media3 unstable-API implementation scopes use non-propagating AndroidX `@OptIn(UnstableApi::class)` annotations.

Both `lintPlayRelease` and `lintFortressRelease` now pass. Remaining lint warnings/hints are visible in the reports and are not converted into hidden baseline debt.

## Final validation

The final source state passed:

```bash
./gradlew \
  :app:testPlayDebugUnitTest :app:testFortressDebugUnitTest \
  :app:assemblePlayRelease :app:bundlePlayRelease :app:assembleFortressRelease \
  :app:lintPlayRelease :app:lintFortressRelease :app:checkPlayBoundary
(cd rust && cargo test --release)
(cd relay && cargo test)
shellcheck scripts/check-play-boundary.sh scripts/test-play-boundary.sh
scripts/test-play-boundary.sh
git diff --check
git ls-files '*.py'
```

Results:

- Android release/test/lint/boundary Gradle gate: pass, 171 tasks, no lint baseline;
- Play boundary: pass, 6 source checks, 4 artifact checks, 2 cryptographically verified NDK runtime exemptions;
- Rust release: 32 passed, 0 failed;
- relay: 15 passed, 0 failed;
- boundary fixtures: pass;
- tracked Python: empty;
- private protected file SHA-256: `beb8ae3a05b903580f70fb41bf5de3db99cfadae103ae0e929c19d8b406a3583`;
- public protected file SHA-256: `f22de087ad79c38b0178d7a7fa149f5dfa45be18b19b583e2faed88d966cc461`.

Final host artifact hashes:

```text
73ba0f2014d92a2236b0bc56530aaa171411e5a32b40e1e47674d7209dc3c071  app-play-release.apk (fixture upload signer)
0215e580a639a991c2f53b77d8eb08498bf82c4dc7b0b844961725e273fc9893  app-play-release.aab (fixture upload signer)
1b02951cf743ab4e37c3126a2793041b8a998e3ae159fb775759c325b8b316ea  app-fortress-release.apk (RamenFast signer)
```

## Durable evidence

Host artifacts, certificate reports, package reports, signing failures, dependency inventory, lint reports, and checksums are archived at:

```text
/media/ben/Mass storage/agenticTinkering/Codex/phosphor-2.0/phase-02-host-validation-20260726T0027Z
```

Archive manifest SHA-256:

```text
6697a34536991fdc532faa1d9ceee2f25ef24dbe52606b97e59b1ea24dcf7759
```

The fixture keystore is deliberately excluded.

## Rollback and next activation gate

Phase 01 rollback remains:

```bash
git revert checkpoint/phosphor-2.0.0-phase-01
```

After this receipt commits, an SSH-signed annotated tag `checkpoint/phosphor-2.0.0-phase-02` is created at the Phase 02 commit and pushed with the release branch. Exact Phase 02 rollback is:

```bash
git revert checkpoint/phosphor-2.0.0-phase-02
```

Phase 03 may introduce only inert causal schemas and tests. No Binder, tailnet authority, Shizuku, overlay, or other active privileged transport is enabled by this checkpoint.
