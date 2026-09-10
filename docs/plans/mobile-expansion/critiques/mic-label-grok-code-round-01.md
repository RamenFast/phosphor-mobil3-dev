# Duplicate built-in mic label honesty code review, attempt 1 of 4

- Tree: `phosphor-mobil3` HEAD `a2e16f3ddaa4d069e407d9733744cf224bcb9c9a` plus dirty unreleased mic-label sources.
- Freeze file: `dev/scratch/mic-label-20260910/source.sha256`.
- Freeze hash matched `MicrophoneRoutePolicy.kt` (`e8f563c69e30baa8ffb6aa7d28fef1ac43c9f87887005baa11bea7c40ef280f2`). This reviewer holds no freeze.
- Dirty wiring outside the freeze: `CaptureMixPolicyTest.kt`.
- Reviewer: Grok code quality only. No visual-taste or device judgment.
- Score: **7/10**.
- Gate: Ben accepts >= 8/10. Attempt 1 does not pass. Identity must stay off the display suffix, then independent round 2.
- Historical: none. This is round 1 of 4.

This is source acceptance of grouping duplicate displayed labels by `label` and suffixing `(id)`. It is not device, screenshot, or beauty acceptance. ASUS SRC bottom vs back remains unobserved here.

## Diff under review

Two files, +8 / -2. Production change is two tokens in `disambiguate`:

- Grouping key moves from `choice.key` (`type` + `address` + `name`) to `choice.label` (`name ·` kind).
- Collision still does `choice.copy(name = "${choice.name} (${choice.id})")`.
- Test `duplicateBuiltInNamesKeepDistinctIds` adds an ASUS-shaped pair with addresses `bottom` and `back`, and asserts two labels containing `(3)` and `(9)`.

No `MicrophoneRoutes`, `MainActivity`, or capture-owner changes. `MicrophoneChoice.key` still includes `name`.

## Evidence classes

Verified by reading the freeze file after hash match, plus dirty tests as wiring evidence, plus the select/start call graph needed to judge a stored key:

- Working `MicrophoneRoutePolicy.kt` SHA-256 matches the freeze. HEAD is `a2e16f3`. Dirty set is exactly the policy file plus `CaptureMixPolicyTest.kt`.
- `MicrophoneRoutes.choices` still runs `disambiguate` on mapped devices. `MicrophoneRoutes.selected` still runs `select` on `devices.map(::choice)` with no `disambiguate`.
- `MainActivity.chooseMicrophone` stores `choice.key` from `MicrophoneRoutes.choices` (already disambiguated). `refreshMicrophone` selects from that same disambiguated list. `explainMicrophone` and `MicController.start` resolve the live device through `MicrophoneRoutes.selected`.
- Empty stored key still picks `type == 15` with `minByOrNull { it.id }` on whatever list `select` receives.
- Unique labels are unchanged. Two type-15 rows named `ASUS_AI2202` with different addresses now share one displayed label before suffix, so both names gain `(id)`.

Inherited, not rerun: parent said JVM tests passed, including `duplicateBuiltInNamesKeepDistinctIds` with different addresses. No Gradle, Git write, or device command ran here.

Unobserved: ASUS SRC sheet, live `AudioDeviceInfo.address` strings, TalkBack, and an actual start after tapping a colliding row.

## Requirements that hold

These are required behavior. They are not defects.

- Two rows with the same displayed label become two labels. The ASUS-shaped fixture is `ASUS_AI2202 (3) · built-in` and `ASUS_AI2202 (9) · built-in`.
- Device ids stay 3 and 9. `disambiguate` does not merge or drop rows.
- A unique `name ·` kind pair is not suffixed. A wired or USB row next to one built-in keeps its old label.
- Null/empty key still selects the lowest-id built-in. First launch with no stored preference can still start that default.
- Supported types, Bluetooth ack, format trials, and `routed` are untouched.

## Material findings

### F1. Suffixing `name` poisons the persisted identity, so a colliding SRC row cannot start

**Priority: high. Contract:** distinct labels must still select and start the same physical input. Evidence: source plus the stored-key/select seam, high confidence.

`label` is derived from `name`. `key` is `"$type\n$address\n$name"`. Collision handling mutates `name`, so it mutates identity.

ASUS-shaped pair after `disambiguate`:

```
raw keys     15\nbottom\nASUS_AI2202
             15\nback\nASUS_AI2202
stored keys  15\nbottom\nASUS_AI2202 (3)
             15\nback\nASUS_AI2202 (9)
```

`chooseMicrophone` persists the suffixed key. `refreshMicrophone` selects against the disambiguated list, so the SRC checkmark can light. `MicrophoneRoutes.selected` compares that stored key to raw device keys. `select` returns null. `explainMicrophone` and `MicController.start` then report "Selected microphone unavailable. Connect it or choose an input".

The user-visible split is: the row they tapped is checked, and start says the input is gone.

This is new on the ASUS path. Different addresses already made distinct keys, so the old grouping left `name` alone and `selected()` could match. Same-key duplicates (empty address, same name) already had this poison. The test still asserts it:

```
assertNull(MicrophoneRoutePolicy.select(out, a.key))
```

That is `select(disambiguated, originalKey)`. Production start is the other mismatch: `select(raw, suffixedKey)`. The new bottom/back assertions never lock either `select` against a suffixed key. They only lock label text.

Baking `id` into the stored name is also unstable after Android reassigns `AudioDeviceInfo.id` on reconnect. Even a later `selected()` that called `disambiguate` first would still miss after reboot.

**Smallest correction:** keep `name` and `key` as identity. Put `(id)` only on the displayed label, inside this freeze file (a display flag, qualifier, or a `label` that suffixes without `copy(name=)`). Leave `chooseMicrophone` storing `choice.key`. Leave `MicrophoneRoutes.selected` matching raw devices. Unique labels stay unsuffixed. Colliding labels stay distinct. Do not persist `id` inside `name`.

## Residual notes

These are not attempt-1 fails.

ML-N1. Active status uses `MicrophoneRoutes.choice(device).label` with no `disambiguate`, so a running mic would still say `ASUS_AI2202 · built-in` after a successful start. Harmless until F1 is closed.

ML-N2. Types 11, 12, and 22 all display as `USB`. Same product name on two USB kinds now suffixes. That is honest collision handling, still subject to F1 if the user taps one.

ML-N3. One pass only. A third row already named `ASUS_AI2202 (3)` could meet a suffixed copy and collide again. Unlikely on this phone.

ML-N4. The suffix is the numeric id, not `bottom` / `back`. Parent asked for `(id)`. Location honesty is a later product choice, not this round.

ML-N5. Tests do not string-lock `MicrophoneRoutes.selected` or `putString(SELECTED, choice.key)`. F1 would not have failed JVM.

ML-N6. Default null-key start still works, because it never uses `key`. The break is the explicit pick the new labels exist to support.

## Test map

| Check | Kind | Result |
| --- | --- | --- |
| Same name, empty address, two ids, two labels | Executed policy | Pass in `duplicateBuiltInNamesKeepDistinctIds` |
| Same name, `bottom` vs `back`, labels contain `(3)` and `(9)` | Executed policy | Pass (parent JVM, source-read here) |
| Ids remain `{3, 9}` | Executed policy | Pass |
| Unique labels unsuffixed | Source read | Pass (`copy` only when count > 1) |
| Null key picks lowest type-15 id | Executed policy | Pass |
| `select(disambiguated, originalKey)` | Executed policy | Pass as null (encodes the poison) |
| `select(raw, suffixedKey)` matches start | Source / seam | **Fail F1** |
| Stored key omits display suffix | Source read | **Fail F1** (`copy(name=)`) |
| `MicrophoneRoutes.selected` uses disambiguated keys | Source read | No. Raw `devices.map(::choice)` |
| ASUS SRC bottom vs back start | Device | Unobserved |

## Limits

No product source, build, device, Git, or network work. This critique file is the only write. No freeze is held.

## Disposition

Reject attempt 1 at **7/10**. Release: fail. Round 2 reviews the F1 identity/display split only. Distinct colliding labels, unsuffixed unique labels, and null-key default selection still have to hold.
