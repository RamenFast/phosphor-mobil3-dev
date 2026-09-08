# Section 6 R07/R08 source handoff

Source released at 2026-09-08T08:31:47Z. All fifteen paths in paths.txt are released to the coordinator. No further source edits will occur without reassignment. This completes the bounded source implementation window, not Android or section acceptance.

## Provenance

Contract: docs/plans/mobile-expansion/section-06-color-contract.md at 1dbb3a96e8bf9cf3152c2d15bd6557da684debd5. Contract SHA-256 d2d6b5941e48d4efabcc21a6e388ce02d1f72fb3648e247b67a55c120db22ca8. The supplied mobile-prefixed string was not an object, so the matching actual commit was verified explicitly.

Current coordinator HEAD at source freeze: 39ba832966d6836b0d2a322d827642f98d30352f. The coordinator committed HOLD correction 0591d7345cdbc701dd2549ef62037959c1fbdaca during this window. render.rs was released to the coordinator before any light edits, then returned. The final render diff from HEAD changes only light commands, policy state, suspension notification, and live theme resolution. EnergyEpoch, retirement, retained-frame commit, capture, and pause sections are unchanged by this worker.

Exact source SHA-256 values: source-sha256.txt. Exact paths: paths.txt. Source snapshot: source.tar, SHA-256 eb7613564ba3af809e8a7c21097b7bce8dd451c167dcac1e38ddcc511d1a3959. No Git mutation was performed by this worker.

## Implementation

- LightSettings owns a defensively copied, unmodifiable zero-to-six RGB bank, independent mask, explicit preset, and all cycle fields. Copy, add, edit, delete/reindex and full-tuple validation share that authority.
- LightCycleGuard evaluates full snapshots. Unacknowledged rapid TIMER settings become safe before persistence/publication. Random timing uses its minimum and clamps both bounds coherently. Pending intent is transient and replaced by later edits.
- SettingsArchive writes /2, checks /1 digests with the original schema, preserves scalar/type/size/checksum rules, and skips schema-specific unknown fields. merge validates the effective tuple before writes. Legacy count0 plus nine RGB values becomes three slots with mask0. Count-only /1 imports preserve six existing slots. Invalid RGB-only shrinks and invalid ranges reject without writes.
- MainActivity saves the complete typed light tuple synchronously, publishes one native command, and restores affected preferences on commit or queue rejection. Legacy count retirement follows durable copy and accepted publication. General tuning saves no longer overwrite a rejected light restore. Imports publish their guarded light snapshot once and preserve transient pending intent for LIGHT review.
- Explicit preset selection retains the existing remote theme callback only after successful local publication. Restore/import/generated edits do not trigger that callback.
- Rust owns the production seed, shuffle bag, generated HSV colors, TIMER leg clock, and durations. Repeated same-leg observations do not draw. TRACK holds and steps on the unchanged existing metadata gate. Suspension rebases from the last live color. Saved grid follows slot zero times0.85. Generated grid follows the generated resolved color times0.85. Beam energy/glow are untouched.
- JNI receives exact RGB length plus every typed field, validates the whole tuple, and enqueues one SetLight. Temporary rolls are separate native commands and never exported.
- LIGHT exposes six saved rows with independent labeled add/edit/select/delete actions, owner summary, temporary roll, automatic generation, retained inactive shuffle, TIMER/TRACK, and interval controls. Actions have48dp minimum targets. Sliders have range semantics, setProgress and keyboard arrows. RGB rules provide non-pixel editing alongside HSV. Existing sheet-opening code is untouched.

## Requirement-to-check outcomes

| Requirement | Check and observed outcome |
|---|---|
| Zero/one/six slots, independent selection, delete/reindex, no illustrative startup bank | LightSettingsTest.zeroOneSixAndIndependentSelection and invalidWholeTuplesAndFiniteBounds passed. Actual ScopeUiState constructor checks remain root-owned. |
| Snapshot cannot alias caller mutation | snapshotDoesNotAliasCallerStorage passed, including mutation through a cast. |
| Legacy inactive RGB preserved and count-only six-slot merge | legacyMigrationPreservesInactiveRgbAndPartialSixSlotBanks, originalSchemaChecksumAndLosslessZeroMigration and partialMergesAndFailurePreservation passed. |
| /1 original checksums and invalid fixtures, /2 round trips and partial validation | SettingsArchiveTest retained38 regression tests passed. LightArchiveTest five tests passed. Original /1 single-setting fixtures are frozen to LEGACY_SCHEMA rather than silently becoming /2. |
| Unknown scalar skip, full digest, byte bound, no runtime consent export | Existing archive regression cases and sourceSchemaControlsKnownKeysAndChecksums passed. |
| Rapid range, randomness enabling, TIMER/TRACK transitions, complete pending snapshot | LightSettingsTest guard tests passed. Nine KnownDefaults source checks passed, including guard publication order and LIGHT review controls. |
| Remote preset callback follows successful publication only | explicitPresetThemeForwardingFollowsSuccessfulPublicationOnly passed for accepted and rejected publication. This is a real policy seam with injected callbacks, not an Android/remote runtime check. |
| Native validation, timer interpolation, track hold, no per-observation draws, bounded/equal durations | Ten light_cycle host tests passed within the104-test native suite. |
| Shuffle bag, duplicate RGB identities, refill and membership reset | Native bag/refill and membership-reset tests passed across repeated seeded cycles. |
| Generated precedence, explicit roll, saved colors retained | Native automatic-owner and temporary-roll tests passed. |
| Hidden/HOLD timing rebase and saved grid rule | Native suspend/rebase and grid tests passed. Retained Android pixels were not tested. |
| Existing HOLD/retirement source preserved | Final render diff inspected against coordinator HEAD. Only light regions change. This is source evidence, not GPU validation. |
| Android Rust syntax | rustfmt --emit stdout parsed render.rs and jni_glue.rs successfully into private files. No formatting rewrite or cross compilation occurred. |
| Whitespace and ownership | git diff --check passed. paths.txt lists only the fifteen authorized mobile light/archive paths. |

## Commands and evidence

Native: CARGO_TARGET_DIR=/home/ben/.jcode/scratch/section6-worker-20260908/cargo cargo +1.96.0 test --manifest-path rust/Cargo.toml --locked --offline. Final result104 passed,0 failed. See cargo-final.log.

Pure JVM: kotlin-regression.sh uses the cached Kotlin2.4.10 compiler, cached JUnit4.13.2, current LightSettings/SettingsArchive sources, and real cached GridData/BackgroundLifecyclePolicy dependencies. Final result50 tests passed. See kotlin-final50.log. No substituted validator or fake runtime was used.

Source-only JVM: kotlin-known-source.sh compiles KnownDefaultsTest against genuine cached UI types, then KnownSourceRunner selects only its nine sourceOnly methods. All nine passed. It does not instantiate cached ScopeUiState or claim those classes validate the new UI. See known-source-final.log.

Earlier check attempts found and corrected one malformed test fixture key order and outdated source-string expectations. An attempted broader pure regression compilation exposed Android imports in BackgroundLifecyclePolicy.kt. That source was excluded and its genuine cached pure policy class used instead. No Android compiler, Gradle, Android stub harness, device, GUI, service, network, or audio action was run.

## Authored or changed checks not run here

KnownDefaultsTest.actualScopeUiStateConstructorUsesAcceptedInstrumentDefaults, actualFiveKeyStateAndPoliciesKeepMissingDefaultsAndExplicitOpposites, and untouchedControlValuesRoundTripAndOverridePreviouslyEditedDestination need the coordinator's actual Android unit-test build. LightCycleGuardTest's five existing cases also need that build because applyGuardedCycle remains in the Compose LightSheet source. Their production policy is covered by the new pure tests, but those existing cases were not claimed as run.

All Compose/Kotlin Android compilation, JNI Android compilation/linkage, lint, the complete Android unit suite, instrumentation packaging, source-boundary scanner, independent review, and exact debug/release artifact installation remain unrun by this worker. Source-only assertions do not substitute for them.

## Remaining seams and required acceptance

The JNI Boolean means whole-tuple validation and render-command queue acceptance, not a completed GPU frame. The render owner validates again. Android must prove real publication, SharedPreferences commit/rollback failure handling, local acknowledgement flow, add/edit/delete behavior at six slots, TalkBack/switch/keyboard access, and readable large-font layouts.

The existing PresentationTrackGate and SurfaceHost were untouched. Root still needs their duplicate-metadata/handoff checks and real HOLD/TRACK acceptance. No screenshots, physical audio, phone preferences, or UI behavior were observed. Full acceptance remains blocked on the deliberately reserved root build, independent review and permitted device workflow, not on a claimed successful compile.

No source-contract refinement was required. Root identified an existing remote preset forwarding regression during integration inspection. It was restored before freeze and the accepted/rejected callback policy test now covers that boundary.
