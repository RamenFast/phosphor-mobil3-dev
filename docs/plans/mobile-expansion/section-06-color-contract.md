# Section 6 color implementation contract

This refines approved canonical section 6, not a replacement feature plan. The immutable [source map](section-06-color-source-map.md) records the previous three-slot behavior. Runtime implementation has not started. The coordinator owns these reversible choices and their verification.

## Six saved slots and source ownership

Use one validated typed light snapshot for UI, persistence, imports and one native publication. Saved slots are an ordered list of zero to six RGB triples. Selected membership is a bit mask whose set bits refer only to existing slots. Deleting slot i removes its triple and shifts higher membership bits down. Deselecting never deletes a color. Adding explicitly appends the current preset color, selects the new slot, and opens its editor. Clean startup does not create saved colors from picker illustrations.

One selected saved slot is solid. Two or more selected slots cycle. Zero selected slots uses the remembered explicit preset. Selecting a preset clears selection, not saved colors. The active color owner is visible: preset, saved slots, automatic generated color, or a temporary manual roll.

Automatic generated color takes precedence over selected saved slots. Saved selection and shuffle settings remain editable or visibly inactive and are retained. Turning automatic color off returns to the saved selection or preset. ROLL NOW generates a temporary color immediately and labels it as temporary when automatic generation is off. It never overwrites a saved slot. A subsequent color-owner edit, source setup reload or process restart returns to the configured owner. Runtime random state and temporary rolls are not exported.

## Exact portable fields

| Key | Type and domain | Default |
|---|---|---|
| `custom_slot_count` | Int 0..6 | 0 |
| `custom_selected_mask` | Int 0..63, no bits beyond slot count | 0 |
| `custom_rgb` | Scalar comma string, exactly 3*slot_count finite components in0..1 | Absent for no saved slots |
| `cycle_seconds` | Finite Float0.1..60 | Existing3 |
| `cycle_per_track` | Boolean | Existingfalse |
| `color_generated_auto` | Boolean | false |
| `color_shuffle` | Boolean | false |
| `cycle_random_interval` | Boolean | false |
| `cycle_interval_min` | Finite Float0.1..60 | 3 |
| `cycle_interval_max` | Finite Float min..60 | 6 |

The complete tuple validates before persistence or native publication. A zero-slot explicit tuple permits absent or empty RGB, never a nonempty hidden bank. RGB numeric values retain their existing interpretation. This is not a color-space migration. The legacy `custom_count` is accepted only as migration input, not emitted by the /2 writer.

## Migration and archives

Write `phosphor.settings/2`. Verify /1 checksums using the original source schema before migration. Retain the one-MiB input bound, scalar-only values, complete checksum, known-key type checks and unknown-key skip reporting.

A complete legacy RGB field preserves all three stored triples, including unselected entries. Old count n selects the first n slots. Old count0 plus nine components becomes three saved slots with mask0, not data deletion. Absent old RGB and count0 remains no saved colors. An old positive count without any valid effective saved RGB is invalid, not permission to invent colors.

Partial imports merge before cross-field validation. A /1 count-only import into a six-slot state preserves all six RGB values and selects its first n slots. A /1 RGB-only import explicitly replaces the RGB bank with its supplied three triples and preserves the current selected mask only when that mask fits the new bank. Otherwise reject the whole import with a named fix. A complete /1 count+RGB import replaces both. A /2 partial tuple also validates against the effective existing tuple. Invalid merges preserve all prior settings and renderer state. No unrelated omitted preference is removed.

Retire the legacy count key only after the migrated typed tuple is durably written. Reads may remain compatible for rollback, but /2 publication has one authority. Rollback never runs a destructive schema downgrade over six saved slots.

## Rust clock and randomness

Use one small host-testable Rust cycle policy called by the existing render owner. Shared `Theme` still receives a single resolved RGB and grid RGB. Channel triples in shared code are not six-slot storage.

Use a deterministic injected seed in tests. Production seed and bag state remain native memory only. Draw generated colors, bag order and leg duration at activation, explicit roll, or an actual TIMER/TRACK boundary. Repeated observation within one leg consumes no random values.

Generated colors use a uniform hue, saturation0.65..1 and value1 in HSV, converted to the same numeric RGB convention used by saved colors. Generated-color changes do not increase beam energy or glow. TIMER interpolates from the current resolved color to the selected next color. Each random duration is uniformly sampled within the configured inclusive numeric bounds. Equal endpoints are a fixed duration.

TRACK holds one color and steps once per accepted existing track-identity event. It does not run an implicit initial fade or a timer. Entering TRACK selects the initial saved/generated color once. Interval controls are inactive, retaining their values. The existing shared metadata lease and deduplication gate remain authoritative.

Saved shuffle uses a bag of selected slot identities. Each identity appears once per bag. Prevent the last identity from becoming the first identity of a refill when at least two are selected. Identical RGB values in different slots are still different identities. Explain that identical saved colors can therefore look unchanged. Membership/order changes reset the bag without an immediate repeat where another selected identity exists.

HOLD retains immutable pixels while live TRACK events update the next live color state. No cycle operation mutates the retained image or inspection. TIMER advances only when live rendering observes it. After a long hidden or held interval, rebase from the last live color rather than executing an unbounded wall-clock catch-up loop. No hidden timer, per-frame random draw or sample thread is added.

Preserve the legacy saved-grid rule: first saved slot RGB times0.85. Removing that first slot intentionally changes its replacement. Generated owner uses its generated color times0.85. Preset owner uses its unchanged preset theme.

## Rapid timing acknowledgement

One guard evaluates the effective complete snapshot for edits, restore, imports, enabling randomness and TIMER/TRACK switches. TIMER below1s requires the existing local acknowledgement. Random TIMER uses its minimum, never a sampled value. TRACK is exempt. Acknowledgement remains runtime-only and is never imported.

Without acknowledgement, publish and persist a safe snapshot before presenting the warning. Clamp fixed TIMER duration to at least1s. Clamp random minimum and maximum coherently to at least1s. Retain the complete requested snapshot as transient pending UI state for explicit acceptance. KEEP SAFE or dismissal retains the safe snapshot. New edits replace pending intent rather than combining old fields. Imports show a plain notice and offer the existing LIGHT review surface, never treat archive values as acknowledgement.

## Implementation and checks

1. Add the typed snapshot and migration/guard policy before activation. Test 0/1/6 slots, independent selection, delete/reindex, finite bounds and whole-tuple rejection.
2. Add source-schema-aware /1+/2 archive decoding. Test original checksum fixtures, complete and partial migrations, omitted keys, six-slot round trips and failure preservation.
3. Add native cycle policy and coherent JNI transfer. Test seeded repeatability, no per-frame draws, duration bounds, bag refill, duplicate RGB identities, TRACK events and hidden/HOLD rebasing.
4. Wire existing MainActivity, ScopeActions and LightSheet. Use at least48dp labeled edit/select/delete/add targets, all six slots, explicit owner summary, roll-now, automatic color, interval range and “Le random order”. Preserve the settings opening gesture.
5. Reuse the shared track gate. Test duplicate metadata and handoff identity, no audible/source commands from light edits, guarded import/mode/range transitions and retained HOLD pixels.

Coordinator runs the Android wrapper gate only after source release: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:checkEngine`. Run locked/offline native tests and the existing production source boundary. Freeze source during full gates. Independent Astra R15 review follows the exact checkpoint. Actual six-slot editing, accessibility, rapid-warning flow and HOLD/TRACK behavior need permitted device acceptance. A passing source test is not that acceptance.

Recovery retains the pre-section Git checkpoints and existing installed APK/preferences. No push, global settings change, root action, relay change or protected phone write belongs to this section. A blocked outcome reports the exact failing tuple or native publication seam and preserves the last valid settings.
