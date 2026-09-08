# Section 10 combined integration correction01

At15:10UTC the released appearance/settings/manual gate156041kxzn completed with871 JVM tests, three source-assertion failures, and a source boundary rejection. Native/GPU/source inventories passed. Both source inventories were unchanged during the gate. The original worker handoff and all original review reports remain untouched.

The three assertions describe source forms intentionally replaced by the settled runtime contract. Before changing them, the coordinator inspected each failing line and the actual adapter:

1. KnownDefaults expects `SharedPreferences.getString` for the legacy room fallback. The new read uses the already-read raw map with a safe String cast, retaining AMOLED for missing/wrong-type input. Require this exact fallback inside the no-committed-appearance branch and retain the no-write assertions.
2. SettingsControlAccess expects only one access provider in all Sheets. The original ROOM is now explicitly accessible too. Require exactly one provider in each Settings and ROOM body and exactly two overall. Existing pointer and range checks remain.
3. SheetEntryPolicy matches a literal `Modifier.clickable` expression. The explicit close now has a48dp target and semantics before the unchanged clickable/dismiss callback. Assert both labeled target branches and the exact close callback, while retaining back, scrim, header, nested and settle checks.

The source boundary's tracking regex also matches the ordinary audio word used in new manual prose. Replace that word with “signal level.” Do not weaken the tracking rule or add a scanner exemption. No tracking dependency or runtime operation is being added.

The next complete gate must pass actual Android compilation, all unit tests, lint, both APKs, engine/native/GPU/source checks and source boundary. These corrections are not Android input, appearance pixels or device acceptance.
