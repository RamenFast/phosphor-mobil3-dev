# Section 7 pure core handoff

## Scope and source

The committed `section-07-instrument-contract.md` remains authoritative. This note records wire details before implementation. The pure core owns authored data, inert documents and immutable user collections only. Root owns Activity, native admission, persistence integration, SAF, UI, apply/undo and device acceptance.

## Exact core choices

- Document fields are exactly `schema`, `version`, `records`, `content_sha256`. Schema is `phosphor.instrument-presets/1`. Integer version is `1` and versions all contained setups. Records contain exactly `id`, `name`, `setup`. Setup contains the contract's snake_case fields, with no version or other metadata in native tuning.
- Light contains exactly `preset`, `slots` (RGB arrays), `selected_mask`, `seconds`, `clock` (`TIMER` or `TRACK`), `generated_auto`, `shuffle`, `random_interval`, `interval_min`, `interval_max`. Construction delegates range, bank and mask rules to the existing `LightSettings` and `LightRgb`. Application guard delegates to `LightCycleGuard` without acknowledging during decode/import.
- Canonical content excludes only `content_sha256`. Object keys sort by ASCII spelling. Records sort by canonical UUID. Array order otherwise remains meaningful. JSON has no whitespace. Strings use minimal JSON escapes, preserve Unicode, and reject unpaired surrogates. Float numbers use the shortest JVM Float decimal converted to plain decimal, without trailing zeros. Signed zero is preserved: positive zero is `0`, negative zero is `-0.0`. This retains exact authored Float and existing LightSettings equality through round trips. Integer fields require integer JSON syntax. Float fields accept JSON numbers, not quoted or boolean values. Decimal bounds are checked before Float conversion.
- SHA256 hashes canonical content UTF-8 and uses 64 lowercase hexadecimal digits. Whitespace, object key order and equivalent numeric spellings do not alter content. Duplicate keys, trailing input and nonstandard JSON syntax are rejected before typed decoding. The encoded input and output limits are 1,048,576 UTF-8 bytes. The parser also bounds nesting and node count before construction.
- Names trim Unicode whitespace at local creation/rename. Imported names must already be trimmed. Names contain 1..64 Unicode code points, no ISO control characters or unpaired surrogates. Conflict comparison applies Unicode simple upper-then-lower case mapping per code point, independent of the default locale. It does not conflate canonically equivalent Unicode spellings or expand characters such as sharp-s.
- IDs use lowercase canonical UUID text. Local creation and duplication generate random UUIDs unless the caller supplies an explicit canonical ID. Imported duplicates within a document are errors, not conflicts to repair.
- Import preview retains the exact base collection. Every incoming record gets an explicit choice: add, keep existing, replace one identified conflicting stable ID, or save a copy with a new unique name and ID. A replacement preserves the target ID. Simultaneous choices are validated as a complete final collection. A stale preview, duplicate target, unresolved conflict or any invalid result changes nothing.
- All collection values defensively copy their lists. Operations return new values. Decode failures remain errors and never become empty collections. A narrow commit helper validates stored bytes, checks the preview base, encodes the full candidate, calls one synchronous persistence callback, and publishes success only on `true`. Root must serialize the read, proposal and callback, preserve bytes on callback failure, and handle Android commit failure semantics. The pure callback cannot prove filesystem rollback.
- `gain` and `autoGain` mean authored local manual gain and authored local auto-gain preference. There is no measured/remote gain, runtime capture adapter, RNG state, listening metadata or automatic application. Root must pass the local authored values, not renderer observations.

## Public integration seams

- `InstrumentSetup(...)` requires all 20 typed fields. `copy(...)` validates a new value. `guardLight(acknowledged)` returns the existing guard's safe setup and optional pending original setup. Neither method submits renderer work.
- `InstrumentPresetCodec.encodeSetup` and `decodeSetup` exchange the metadata-free tuning object. `encode`, `decode(String)`, and `decode(ByteArray)` exchange the portable collection. `exportRecord` selects one stable ID. `canonicalContent` and `contentSha256` expose deterministic fixture seams.
- `InstrumentPresetCollection.empty()` means explicitly absent storage, not failed decoding. `of`, `create`, `update`, `rename`, `delete`, and `duplicate` return validated immutable values. `proposeDuplicateName` creates no record. Curated entries stay in `CuratedInstrumentPresets.all` until an explicit user create action.
- `previewImport` validates the entire document. `conflictsFor` identifies existing stable IDs. `resolveImport` needs a choice for every incoming ID and returns one complete candidate. Discarding the preview cancels without mutation. `KeepExisting` also permits skipping a nonconflicting incoming record.
- `InstrumentPresetCommit.commit(stored, expected, proposed, persist)` returns `Result<InstrumentPresetCollection>`. The callback runs at most once. Invalid bytes, a stale collection or failed callback cannot return success. Persistence file is `phosphor.instrument.presets`, collection key is `collection`.
- Recalled-record association, modified indication, deletion association cleanup, undo, request admission and publication belong to root. A deleted user record never changes its immutable setup or the current runtime tuning through this core.

## Validation and release

The live-source final pass ran 37 JUnit tests: 30 new instrument tests and seven unchanged `LightSettingsTest` tests. All passed. Tests compile the actual three production files and actual `LightSettings.kt`. There is no production source adapter, Android stub, alternative validator or target-native API call. `org.json` only constructs malformed test documents. The production codec has no `org.json` dependency.

| Contract requirement | Concrete checks and result |
|---|---|
| Every setup field, finite bounds, random eligibility, DSP choices | `everyAuthoredFloatBoundaryAndNonfiniteValueIsValidated`, `discreteFieldsAndRandomEligibilityAreExact`, `wireRequiresEveryFieldAndRejectsWrongScalarTypes`: pass |
| Whole light, all bank sizes and membership masks, RGB/timing bounds | `sixSlotTupleAndAllBooleanChoicesRoundTrip`, `completeLightUsesExistingValidatorAndStrictWireTypes`, `lightNumericBoundariesDelegateToTheActualLightValue`: pass |
| Existing rapid guard, no decode acknowledgement | `exactLightGuardIsReusedWithoutChangingSavedSetup`: all TIMER/TRACK, random interval and acknowledgement combinations pass |
| Authored local values, exclusions, curated data | `everyDocumentAndRecordFieldIsRequiredAndNoMetadataCanEnter`, `allCuratedTuplesMatchTheContract`: pass. Actual capture of local versus measured/remote state is not implemented here |
| Canonical deterministic content and checksum | Empty and nonempty fixed canonical fixtures use independently computed `sha256sum` digests. Round trips, key/record ordering, equivalent numbers and signed-zero preservation pass |
| Strict fields/types/schema/IDs/duplicates/checksum/JSON/UTF-8 | Codec rejection tests pass, including escaped duplicate keys, trailing documents, nonstandard numeric grammar, malformed surrogates and resource bounds |
| 64 records, 1 MiB UTF-8, Unicode names and locale-independent conflicts | Collection bounds and byte-bound tests pass. Exactly 1 MiB succeeds. Oversized multibyte input fails. Supplementary code points and US/Turkish/Lithuanian locale cases pass |
| Stable-ID CRUD, explicit duplicates, immutable curated data | CRUD, proposal-only duplicate, generated IDs and defensive list mutation tests pass |
| Inert complete imports, crossed ID/name conflicts, cancel, stale previews | Import choice, crossed-conflict and all-or-nothing batch tests pass. No import calls the guard or runtime |
| Failed persistence and invalid stored bytes | Pure callback false/throw tests preserve fixture bytes and return failure. Invalid/checksum-failed stored bytes and stale bases invoke no callback. Absent storage alone allows explicit empty creation |

Initial verification found a test-helper `copy` naming collision and a genuine signed-zero codec round-trip defect. Both were corrected before the final pass. Final grammar review removed an unnecessary numeric token-length cap. A 10,000-digit equivalent finite spelling now round-trips within the document limit.

Private evidence lives at `/home/ben/.jcode/scratch/r16-core-20260908-SBOJyD/release/`. The release contains an immutable minimal actual-source snapshot, a source archive, owned-path and full-input SHA256 manifests, runner/dependency hashes, fixture hashes and the snapshot test result. Earlier failed and passing attempts remain in the parent directory.

Root reported accepting all four actual Kotlin setup fixtures with its production Rust decoder. That is a separate root-owned host check, not an Android/native integration claim by this worker. The fixtures are Clean XY, Spectral bench, Ambient and a six-slot mask45 setup.

Android compilation, real SharedPreferences failure/rollback, SAF, Activity capture of authored values, native admission, apply/undo, modified association, UI and device acceptance remain unproven by this bounded core. Root must serialize reads, proposal and the single commit callback through its settings owner. It must not replace invalid stored bytes with empty defaults. It must preserve prior durable bytes if Android persistence fails.
