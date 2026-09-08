# Section 10 pure appearance core handoff

## Delivered boundary

This implements `section-10-document-contract.md` from `10f10a5cfccf68d15ecd6b75fa4e43f53c317497`.
The worker added three production files and three matching test files under `settings/appearance`.
Existing production sources and instrument serialization stayed read-only.
This is a pure JVM core, not a shipped appearance editor or an Android acceptance claim.

The final actual-source gate passed on 2026-09-08 at 14:20:19 UTC: **84 JUnit tests**, comprising 46 new appearance tests and 38 unchanged appearance-value and instrument regressions.
Cached Kotlin 2.4.10 compiled real repository sources with JVM target 17 on OpenJDK 21.0.11.
No Gradle, Android stubs, source adapters, copied production rewrites, device actions, installs or downloads were used.

## Integration API

All declarations are in `dev.phosphor.mobil3.settings.appearance`.

- `AppearanceRecord(id: String, name: String, value: AppearanceValue)` is immutable.
- `AppearanceProvenance(room: String? = null, character: Int? = null, motion: Int? = null, radius: Int? = null, designators: Int? = null)` preserves absent keys and exact raw integers.
- `AppearanceDocument.of(active = CuratedAppearances.amoled, activeId = "curated:amoled", users = emptyList(), legacy = emptyList(), provenance = AppearanceProvenance())` validates and snapshots complete state. Its five arguments have matching immutable properties.
- `AppearanceDocumentCodec` exposes `encode(document): String`, `decode(String): AppearanceDocument`, `decode(ByteArray): AppearanceDocument`, `canonicalContent(document): String`, `contentSha256(document): String`, and `SCHEMA`, `VERSION`, `MAX_BYTES`.
- `AppearanceCollection.of(document = AppearanceDocument.of())` exposes `document`, `modified`, and `record(id)`.
- Collection operations return a new collection: `create(name, value, id = UUID)`, `save(name, id = UUID)`, `update(id, value)`, `rename(id, name)`, `delete(id)`, `apply(id)`, `applyEdit(value, activeId = "")`, `reset()`.
- `AppearanceException` exposes `code`, `message`, and `fix`.

`create` saves a supplied value without activation. `save` saves the active value and associates its new user ID.
`update` changes only the named saved value. It preserves the active value and association, which can become modified.
`applyEdit` uses exactly the caller's association. Its default is unsaved, not the prior association.
Deleting the active user preserves the exact active value and clears only its association while removing that record.
Reset selects curated AMOLED and preserves all user records, legacy records and raw provenance.

Migration supplies `legacy` records from the actual `AppearancePalette` bridge.
The core contains reserved legacy identities, not a second palette table.
Only user names receive the contract's 1..64-code-point, trimmed, no-control and case-independent uniqueness restrictions.
Legacy names remain opaque valid Unicode within the whole document byte limit.
The core imports the existing instrument name policy and strict JSON grammar without editing either.

## Requirement-to-check table

Test names refer to the three new test classes unless stated otherwise.

| Requirement | Observed check |
| --- | --- |
| Exact root, record, value and color fields and types | `everyObjectRequiresAllAndOnlyItsExactFields`, `everyNestedFieldRejectsWrongJsonTypeIncludingUserAndLegacyRecords`, `structuralFieldsRejectWrongTypesAndNoNullIsAdmitted` passed. The nested check traverses more than 400 field paths. |
| All fields, enums, RGB24, ARGB32 and Float endpoints | `everyFieldAndEnumAndExactBoundRoundTrips`, `everyRgbRoleRejectsOutOfRange`, `integerSyntaxIsRequiredAndArgbExtremaAreExact`, and unchanged `AppearanceValueTest` passed. |
| Decimal validation before Float rounding | `allDecimalRangesRejectOutsideBoundsBeforeFloatRounding` and `validAlternateNumberSpellingsCanonicalizeTypedStateBeforeChecksum` passed. |
| Signed zero and nonfinite values | `floatsRejectSignedZeroNonfiniteUnderflowAndHugeExponents` passed. All appearance Float minima are positive, so signed zero is invalid here. The shared canonical encoder still emits `-0.0` where legal. |
| Strict shared grammar, Unicode, UTF-8, duplicate keys, trailing material | `grammarRejectsMalformedDuplicateTrailingUnicodeAndInvalidNumbers` passed, including escaped duplicate keys and unpaired surrogates. |
| 128 KiB UTF-8 including whitespace, depth and node limits | `utf8ByteLimitIncludesWhitespaceAndMultibyteBytes`, `sharedDepthAndNodeLimitsAreNotRelaxed`, `canonicalSizeIsEnforcedAtConstruction` passed. |
| Independent fixed canonical checksum vectors and mutation detection | `independentFixedDigestVectorsMatchExactCanonicalBytesAndDecode` and `unsupportedSchemaVersionAndChecksumMutationsFail` passed. Two hand-authored fixtures were independently hashed with GNU `sha256sum`. |
| Reserved identities, complete legacy set, user 32 and name 64 bounds | Document tests `allReservedIdsAreRejectedInUsers`, `legacyMustBeEmptyOrExactly13WithOptionalCurrent`, `userCountAccepts32AndRejects33`, `nameCodePointBoundsAndSpellingAreExact` passed. The legacy set is asserted against independent contract literals: blossom, blossom_dark, light, dark, chromacore, basalt, afterglow, stonework95, amoled, paper, amber, fable, glass, with optional current. |
| Locale-independent name conflicts and stable IDs | `nameUniquenessMatchesInstrumentPolicyIndependentlyOfLocale`, `userIdsAreCanonicalUniqueAndStable`, and collection rename/update tests passed. |
| Defensive copies, order and exact raw provenance | `callerListsAndExposedCollectionsCannotMutateDocument`, `recordOrderIsNotSortedAndValueEqualityIncludesAllState`, `provenancePreservesAbsenceSentinelsUnicodeAndEveryIntExtreme`, `fullLegacyProvenanceAndOrdered32UserDocumentRoundTrips` passed. Provenance has typed immutable fields rather than an exposed map. |
| Explicit CRUD, apply/edit, active delete and reset isolation | All 13 `AppearanceCollectionTest` tests passed, including every curated and legacy record's edit prohibition. |
| Validate whole replacement before publishing, independent of old corrupt bytes | `lastLegacyRecordValidationFailureCannotPublishEarlierValidFields` and `completeReplacementDoesNotReadCorruptOldBytesOrPublishPartialState` passed. The decoder accepts only incoming bytes and returns no partial state. |
| Shared production remains unchanged | The eight-entry `protected-before.sha256` verification passed. The 38 unchanged regression tests passed alongside the new suite. |

## Exact evidence

Private worker archive directory:
`/home/ben/.jcode/scratch/section10-core-20260908T1404-lcOPkW`

- Final run: `run-xPWjmj/output.log`.
- Actual compiled inputs: `run-xPWjmj/sources.list` and `source-sha256.txt`.
- Cached compiler and test dependencies: `run-xPWjmj/dependencies.list` and `dependency-sha256.txt`.
- Runner: `run-tests.sh`, SHA-256 `f42adf39a3e6c752da1402af48532d91e08cdc7af2c46a4d253ae77c2f2abaa6`.
- Final test jar: `run-xPWjmj/tests.jar`, SHA-256 `3b8292e49f204ec87dd48a0c18bcc23a69998acf2fe16ec496aebace93c2192d`.
- Independent fixture 1: `vector-1.content.json`, SHA-256 `7b55767bf00d0d92162c3c24dffb24801c0df9494000ad31340c698073472341`.
- Independent fixture 2: `vector-2.content.json`, SHA-256 `8948acd92b3e3929926a4a4716f11ada384e48a42a65772043e34f2aec13d9f0`.
- Sealed source archive, runtime hashes and final receipts: `source.tar`, `jdk-sha256.txt`, `REPORT.md`, `MANIFEST.sha256`.

The earlier 44-test and 82-test passes remain in their original run directories.
One later test-only compile failed on Kotlin star-projection traversal. The test was corrected, then the final 84-test gate passed.
No production correction was required after the first passing gate.

## Remaining uncertainty and release

The host suite proves the specified pure value/document/collection boundary, not persistence, migration fidelity, archive admission or runtime appearance.
Legacy fixtures use deliberately arbitrary authored snapshots. They do not claim to reproduce actual palette constants.
Root owns the separate production migration bridge tests, real preferences, 1 MiB enclosing settings archive limit, Android gates, UI preview, lifecycle ownership and device appearance acceptance.
The core has no persistence callback and cannot prove transactional storage rollback or stale preview handling.

Source and build ownership are explicitly released in the worker completion report after sealing these receipts.
No Git mutations were performed. Root owns integration, independent review and commits.
