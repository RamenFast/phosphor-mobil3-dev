# Duplicate built-in mic label honesty code review, attempt 2 of 4

- Tree: `phosphor-mobil3` HEAD `a2e16f3ddaa4d069e407d9733744cf224bcb9c9a` plus dirty unreleased mic-label sources.
- Freeze file: `dev/scratch/mic-label-20260910/source.sha256`.
- Freeze hash matched both files: `MicrophoneRoutePolicy.kt` (`07fc376955aa800813e7b785c1c722b032a1218faf61c069bc2aa4276a919dbe`) and `CaptureMixPolicyTest.kt` (`d39a413a74bf9726606a129b0411871516db3a4fb38622a9131624059b7f87bf`). This reviewer holds no freeze.
- Dirty wiring outside the freeze: `CaptureMixPolicyTest.kt`.
- Reviewer: Muse code quality only. No visual-taste or device judgment.
- Score: **9/10**.
- Gate: Ben accepts >= 8/10. Attempt 2 passes. Round 1 report (`mic-label-grok-code-round-01.md`, 7/10 FAIL on F1) stays immutable.
- Historical: round 1 failed on F1 (suffixing `name` poisoned persisted `key`). Round 2 reviews the F1 identity/display split only.

This is source acceptance of grouping duplicate displayed labels by `label` and suffixing `(id)` via `displayTag` only. It is not device, screenshot, or beauty acceptance. ASUS SRC bottom vs back remains unobserved here.

## Diff under review

Production change is inside `MicrophoneRoutePolicy.kt` against the round-1 shape:

- `MicrophoneChoice` gains `displayTag: String = ""`. `key` stays `"$type\n$address\n$name"`. `label` stays `"$name · $kind"`, plus `" ($displayTag)"` only when `displayTag` is non-empty.
- `disambiguate` still groups by `choice.label`, but the collision arm is now `choice.copy(displayTag = choice.id.toString())` instead of `choice.copy(name = "${choice.name} (${choice.id})")`.
- `select`, `supported`, `routed`, and `candidates` are untouched.
- Test `duplicateBuiltInNamesKeepDistinctIds` now locks identity: `key` and `name` unchanged after `disambiguate`, `displayTag` is `"3"` / `"9"`, `select(raw, storedKey)` finds the bottom/back pair both ways, unique rows stay unsuffixed, and the empty-address same-key limit is asserted as null.

No `MicrophoneRoutes`, `MainActivity`, or capture-owner changes. That is correct: the fix lives entirely in the freeze file.

## Evidence classes

Verified by reading both freeze files after hash match, plus the select/start call graph needed to judge a stored key:

- Working `MicrophoneRoutePolicy.kt` and `CaptureMixPolicyTest.kt` SHA-256 both match the freeze. HEAD is `a2e16f3`.
- `MicrophoneRoutes.choices` still runs `disambiguate` on mapped devices. `MicrophoneRoutes.selected` still runs `select` on `devices.map(::choice)` with no `disambiguate` — raw devices, raw keys.
- `MainActivity.chooseMicrophone` still stores `choice.key` from `MicrophoneRoutes.choices` (disambiguated list). `refreshMicrophone` selects from that same disambiguated list. `explainMicrophone` and `MicController.start` resolve the live device through `MicrophoneRoutes.selected`.
- `key` no longer contains `displayTag`, so a stored key from a disambiguated row is byte-identical to the raw device key. Source-read, high confidence.
- Empty stored key still picks `type == 15` with `minByOrNull { it.id }` on whatever list `select` receives.
- Bottom/back fixtures (`15\nbottom\nASUS_AI2202`, `15\nback\nASUS_AI2202`) have distinct keys before and after `disambiguate`. Labels become `ASUS_AI2202 · built-in (3)` and `ASUS_AI2202 · built-in (9)`.

Inherited, not rerun: parent said `./gradlew :app:testDebugUnitTest --tests dev.phosphor.mobil3.CaptureMixPolicyTest` — BUILD SUCCESSFUL, including the extended `duplicateBuiltInNamesKeepDistinctIds`. No Gradle, Git write, or device command ran here.

Unobserved: ASUS SRC sheet, live `AudioDeviceInfo.address` strings, TalkBack, and an actual start after tapping a colliding row.

## Requirements that hold

These are required behavior. They are not defects.

- Two rows with the same displayed label become two labels containing `(id)`. The ASUS-shaped fixture is `ASUS_AI2202 · built-in (3)` and `ASUS_AI2202 · built-in (9)`.
- Device ids stay 3 and 9. `disambiguate` does not merge or drop rows.
- A unique `name ·` kind pair is not suffixed. The `front` + wired-headset pair keeps empty `displayTag` on both rows.
- Stored key omits the display suffix. `key` is derived from `type`/`address`/`name` only; `displayTag` never enters it.
- Null/empty key still selects the lowest-id built-in (`select(out, null)?.id == 3`).
- Empty-address same-name duplicates still share one key, and `select` on that ambiguous key returns null. Per brief section 4 this remaining limit is honest: labels are distinct and no identity is invented. The test asserts it as null rather than inventing a second key.

## Material findings

No open F-findings. F1 is closed:

### F1 (round 1). Suffixing `name` poisoned the persisted identity — CLOSED

**Round-1 contract:** distinct labels must still select and start the same physical input. **Round-2 evidence:** `disambiguate` no longer touches `name` or `key`; `label` suffixes via `displayTag`; `chooseMicrophone` stores an unsuffixed `choice.key`; `MicrophoneRoutes.selected` matches raw devices; `select(raw, storedKey)` finds both bottom (id 3) and back (id 9). The test locks all four seams: `key` equality, `name` equality, `displayTag` value, and both `select` directions. The round-1 mismatch (`select(raw, suffixedKey)` → null → "Selected microphone unavailable") cannot occur because no suffixed key can be produced. High confidence, source plus test wiring.

## Residual notes

These are not attempt-2 fails.

ML-N1. Active status uses `MicrophoneRoutes.choice(device).label` with no `disambiguate`, so a running mic still reports `ASUS_AI2202 · built-in` without `(id)`. Harmless display asymmetry; identity is unaffected.

ML-N2. Types 11, 12, and 22 all display as `USB`. Same product name on two USB kinds still suffixes honestly, and now without identity cost.

ML-N3. One pass only. A third row already named `ASUS_AI2202 (3)` could meet a suffixed copy and collide again. Unlikely on this phone; parent asked for `(id)`.

ML-N4. The suffix is the numeric id, not `bottom` / `back`. Parent asked for `(id)`. Location honesty is a later product choice, not this round.

ML-N5. Tests do not string-lock `putString(SELECTED, choice.key)`. The seam is verified by source read here; no JVM test covers the preference write.

ML-N6. `displayTag` joins the `MicrophoneChoice` data-class equality. Nothing in the call graph compares choices by equality (`chooseMicrophone` matches by `id`, `select` by `key`, `selected` by `id`), so this is inert.

ML-N7. `selected()` re-resolves by `it.id == selected.id` after a key match. `id` stays volatile across reconnects (pre-existing, out of scope). The stored key itself is now stable.

## Test map

| Check | Kind | Result |
| --- | --- | --- |
| Same name, empty address, two ids, two labels | Executed policy | Pass in `duplicateBuiltInNamesKeepDistinctIds` |
| Same name, `bottom` vs `back`, labels end with `(3)` and `(9)` | Executed policy | Pass (parent JVM, source-read here) |
| Ids remain `{3, 9}` | Executed policy | Pass |
| `name` and `key` unchanged after `disambiguate` | Executed policy | Pass (both empty- and distinct-address pairs) |
| Stored key omits display suffix | Source read + executed policy | Pass (`key` excludes `displayTag`) |
| `select(split, front.key)` finds id 3 | Executed policy | Pass |
| `select(raw, storedKey)` finds bottom/back pair both ways | Executed policy | Pass — F1 closed |
| `select(disambiguated, originalKey)` on shared empty-address key | Executed policy | Pass as null (honest limit, asserted) |
| Unique labels unsuffixed | Executed policy | Pass (`displayTag` empty) |
| Null key picks lowest type-15 id | Executed policy | Pass |
| `MicrophoneRoutes.selected` uses raw-device keys | Source read | Pass (no `disambiguate`) |
| ASUS SRC bottom vs back start | Device | Unobserved |

## Limits

No product source, build, device, Git, or network work. This critique file is the only write. No freeze is held.

## Disposition

Accept attempt 2 at **9/10**. Release: pass. The single withheld point covers the inert residuals (undisambiguated active-status label, numeric-id volatility across reconnects, single-pass edge). No further mic-label identity round is needed on this seam.
