# Settings and startup recovery integration gate

## Exact scope

On 2026-09-08 at13:05UTC Daisy released all seven settings paths and the build hold.
The coordinator verified every released file hash in CHANGED-SHA256SUMS before running the prepared gate.
The existing tree was retained. The baseline archive was not extracted over newer recovery changes.

Gate788107bhla ran13:06:28–13:08:02UTC and exited0.
Mobile baseline was8c9d56e3a9cf27fa540c578751f67f150374f47b plus the seven released settings paths.
Shared source remained0ffd658d7f19e68180c2720e0500b23644619e90.
Both complete tracked/untracked source manifests and path lists were identical before and after the gate.
This is frozen working-source integration evidence, not a clean final release freeze or installation.

## Observed checks

- 711 JVM tests across61 suites passed, with zero failures or errors.
- 126 native host tests passed.
- Three offscreen RADV retained-frame API tests passed. They are not Android compositor tests.
- Gradle Android/Kotlin/JNI compilation, debug unit tests, lint, engine checks and release helper build passed.
- Both debug application and androidTest APKs were built in the same invocation.
- The production source boundary check passed.
- The coordinator retained both APKs and the complete JVM XML results before any later build.

The gate includes recovery914f585, the appearance value tests and the released settings integration.
Writer38 host cases are included in the full JVM gate, not added again to711.
No device, audio, GUI or protected phone surface was changed.

## Receipts

Prefix: `dev/scratch/mobile-expansion-20260908T001819Z/settings-recovery-integration-1316`.
Runner: `run-settings-recovery-integration-1316.sh` in the same directory.

| Artifact | SHA256 |
| --- | --- |
| Runner | c78684c435976b57dce6f4241e7574ecd5e3d8e543cccf2e5064898a2a3443f3 |
| App APK | 04c4ef217e84319c7024097cb08f67c151da03cabc0f0bb7c24cbb3b4df62057 |
| androidTest APK | 7faed5e40d950eff06dbd4f2ff34d0aa074e144912ac1a98403c1b215389fa50 |
| JVM results archive | bd19f7a9a90e2d49aefe06d3f6bebb90fa670cc8a7a9f00dfd17adabdd9c7294 |
| Mobile source manifest | 91bad9a51db70c2c7d63ca385081e71bf059bb70e9a1b6eb6b3b2d423c0a1db5 |
| Shared source manifest | 581cd08f0a1fedcb8470e6848957edcec1017012bb772462e01280bc1d30acb9 |

The prefix's retained.sha256 inventories these retained files and the result envelope.

## Remaining acceptance

Section9 still requires independent full review and actual Compose pointer/nested-scroll, anchor, large-font and accessibility checks.
R16 original scores7/7/7 remain unchanged. Its fourth full review can now assess the compiled recovery correction.
Actual Activity recreation, storage failure, native receipt scheduling and SAF delivery remain untested on Android.
R17 round2 has separately reported capture-retirement precedence and stale zero-read health findings on6355074.
That immutable review and the later corrections remain separate from this gate.
