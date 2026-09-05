# Phase 05: settings survive updates and failed imports

Entry: Phase 04 PASS and accepted B17 settings behavior rechecked.
Outcome: one typed definition governs portable values, and import success means committed and visibly applied settings.

## ▸ 05.1 Consolidate the accepted portable definition, initially inert

📁 Existing: `M/app/src/main/kotlin/dev/phosphor/mobil3/settings/SettingsArchive.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/settings/LegacySettingsMigration.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/ui/ScopeUiState.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/PhosphorApplication.kt`.
NEW unless B17 supplies it: `M/app/src/main/kotlin/dev/phosphor/mobil3/settings/InstrumentSettings.kt`, `M/app/src/test/kotlin/dev/phosphor/mobil3/settings/InstrumentSettingsContractTest.kt`.

Use direct typed values and a fixed key/validation table. Preserve SharedPreferences names and `phosphor.settings/1` semantics.
The definition owns defaults, bounds and portable/runtime classification. No generated registry or storage-engine replacement.
Preserve edited values. Missing values use accepted B17 defaults, not the stale pre-B17 defaults in the planning snapshot.
Do not persist measured automatic gain over manual tuning. Apply complete effective settings before first surface and after recreation.
Test all legal endpoints, five B keys, missing custom RGB, unknown fields, nonfinite numbers and custom-color consistency.
Hosts, credentials, media URIs, consent and runtime metadata stay outside portable archives and OS transfer.

✅ Run all settings cases, including the new named class:

```bash
cd "$M"
./gradlew --no-daemon :app:testDebugUnitTest --tests 'dev.phosphor.mobil3.settings.*'
```

Expected: every portable key round-trips, edits survive missing/new fields, and obsolete authority data remains excluded.
No production caller uses the new definition until the contract tests pass.

## ▸ 05.2 Activate one import commit and restoration path

📁 Existing: `M/app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt`, `M/app/src/main/kotlin/dev/phosphor/mobil3/ui/ScopeUiState.kt`, `M/app/src/main/res/xml/backup_rules.xml`, `M/app/src/main/res/xml/data_extraction_rules.xml`.
NEW: `M/app/src/main/kotlin/dev/phosphor/mobil3/settings/SettingsTransfer.kt`, `M/app/src/test/kotlin/dev/phosphor/mobil3/settings/SettingsTransferTest.kt`, `M/app/src/androidTest/kotlin/dev/phosphor/mobil3/settings/SettingsSurvivalTest.kt`.

Extract the existing document-read/decode/commit/restore sequence, not all Activity behavior.
Decode and validate the complete archive before touching preferences. Apply all imported portable keys in one existing-store transaction.
Keep untouched values. On failed commit, restore touched values and report restore failure honestly if it also fails.

Apply committed state on the main thread before displaying success. A destroyed Activity must not claim visible application.
At restart, read the complete persisted snapshot. Test interruption before commit, after disk commit and before UI application.
This contract needs no second journal, saved history or temporary credential-bearing archive.

Use explicit portable-file inclusion in backup rules where compatible. Keep runtime containers excluded across API 29/31/34/36 branches.

✅ Run host failures, then real update/import/recreation D4:

```bash
cd "$M"
./gradlew --no-daemon :app:testDebugUnitTest --tests dev.phosphor.mobil3.settings.SettingsTransferTest
./gradlew --no-daemon :app:lintDebug :app:checkEngine :app:assembleDebugAndroidTest
export TEST_CLASS=dev.phosphor.mobil3.settings.SettingsSurvivalTest
run_device_case
```

Expected: bad checksum, oversized/unreadable document, cancellation and failed persistence never partially report success.
Same-package debug upgrade, process restart, surface recreation and import/export preserve edits without starting a source or network.
Clean-default testing uses a disposable test device/profile, never production uninstall or data clear.

↩ Rollback: revert transfer activation before definitions. Restore the saved portable export through a compatible build if data changed.
Preserve prior APK and export until upgrade acceptance. Use a forward-fix version if signer/version rules block downgrade.
⛔ No invented RGB defaults, archive meaning change, cross-package preference copy or implicit consent restoration.
