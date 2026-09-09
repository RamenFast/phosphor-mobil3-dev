# R06 brightness pin independent review, attempt 3 of 4

- Unit freeze: `dev/scratch/brightness-pin-20260909/source-r3.sha256` against `b222818e86f210fd0b02f9621e61427022ebc63e`.
- All 16 freeze hashes matched the files read for this review, including after the coordinator Android gate.
- Reviewer: Grok 4.6 high. Independent of the writer and of the coordinator build.
- Score: **8/10**.
- Acceptance gate: Ben accepts >= 8/10. This round accepts the bounded source including the canonicalizer repair.
- Historical, immutable: attempt 1 build-blocked unscored. Attempt 2 = **8/10**. Those scores are not changed.

This is source acceptance of the checksum helper plus R06 regression. It is not device or design acceptance.

## Scope

Changed versus attempt 2 freeze: `SettingsArchive.kt`, `SettingsArchiveTest.kt`, `LightArchiveTest.kt`, `AppearanceArchiveTest.kt`, `SettingsArchiveCanonicalTest.kt` (new), contract and execution notes. R06 policy, Activity adapter, UI, and PhosphorScreen hashes are unchanged.

## Evidence classes

Verified here:

- Freeze match for 16 paths.
- `canonicalQuote` compared to the retained Android 10/14 `JSONStringer.string` body in `canonical-probe/JSONStringer.java`.
- Independent SHA-256 of the slash-escaped compact payloads for the ON and OFF golden checksums. Both matched the hardcoded test vectors (`03b83151…`, `a1912253…`). The unescaped host payload matched the rejected host hash (`2e3f0f2c…`).
- Golden quote vectors cover empty string, schema slash, quote+backslash+slash, U+0000..U+001F (short escapes for tab/backspace/newline/form-feed/CR, lowercase `\u00xx` otherwise), and raw non-ASCII including U+0085/U+2028/U+2029 and a supplementary pair.

Inherited, not rerun:

- Writer `canonical-repair/tests-r2.log` `OK (86 tests)`. First `tests.log` failed because a form-feed vector expected `\u000c` while production correctly emitted `\f`. The vector was corrected, not the quoter.
- Coordinator `android-gate-r3.log` `BUILD SUCCESSFUL`, 921 tests, 0 failures. Dual APK, lint, checkEngine.
- Native acceptance of the full ASUS export checksum `ba75b1f9732926475ffd027ba9bd50e7a8dffdcb89f527cb8c30feab73b16905` is a coordinator/writer receipt. This reviewer did not re-decode that 9707-byte archive.

Unobserved: a new ASUS install of the r3 APK.

## Canonicalizer

Production `JSONObject.quote` calls are gone. Checksum bytes come only from `canonicalPayload` plus `canonicalQuote`. Decode still parses with `JSONObject`, then recomputes that same canonical form. There is no second accepted hash.

`canonicalQuote` matches Android JSONStringer. It escapes quote, backslash, and slash. It uses short escapes for tab, backspace, newline, carriage return, and form feed. Other U+0000 through U+001F use lowercase four-digit Unicode escapes. All other UTF-16 code units stay raw. Number spelling remains BigDecimal stripTrailingZeros toPlainString. Envelope pretty-print remains JSONObject.toString(2).

`actualOffDiagnosticVectorKeepsAndroidHashWithoutHostFallback` decodes the Android OFF hash and rejects the old host hash with `checksum_mismatch`. Tampering `false` to `true` also fails. Fixture builders in Settings/Light/Appearance tests now use the same quote spelling so they reach validation after the production change. They are not the independent checksum proof. The independent proof is the hardcoded quote table plus the two fixed hashes, which this reviewer recomputed from the escaped payloads without calling production code.

R06 archive tests remain schema `/2` only for the pin Boolean, `/1` skip, omit-merge, explicit false, and rejected non-bool types. Policy and Activity files did not change.

## Material findings

None on the inspected source.

## Residual notes

R3-N1. Attempt 2 residuals stand: Activity adapter tests are still source-indexOf, HUD uses `presenting`, `activityFocused` is not cleared on pause. This correction does not add Android window evidence.

R3-N2. Pretty file bytes can still differ by host `JSONObject.toString(2)` slash spelling. Checksum is not over those bytes. Decode does not need a fallback.

## Disposition

Accept at 8/10. Preserve attempt 1 unscored and attempt 2 = 8. The canonicalizer repair is a strict Android-compatible checksum spelling, not a relaxed parser. No source lock is held.
