# Section 6 R07/R08: immutable source map

Status: mapping completed, not implementation acceptance.
Route: confirmed GPT Astra/high. No worker was spawned.
Request received: 2026-09-08T07:18:55.966Z. Source reading ended: 07:22:33Z.
Budget: eight minutes. Product excerpt read set: exactly twelve files, listed below.

This report supports the already approved MOBILE-EXPANSION-PLAN.md. It is not a replacement plan or critique. Root owns implementation, shared state, builds, devices, verification, and integration with Hatchling's changing HOLD work.

## Provenance and boundaries

Mobile repository: /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3
Mobile commit M: a3223b87c8d15fc0da691cfcfe0963ba464d1e92
Shared repository: /home/ben/Dev/ClaudeWorkspace/phosphor
Shared commit S: 7729990bb29f0167ef906d0fbdb44e1e91206955

All product content came from `git show COMMIT:path` or `git grep COMMIT -- paths`. Numbered evidence uses those immutable blobs, not working-tree line numbers. No changing HOLD source was read. Both commit objects were confirmed using `git show -s --format=fuller`. Existing commit messages describe earlier checks, not checks performed here.

Governance read first: ben-context-standards, then /home/ben/Dev/ClaudeWorkspace/AGENTS.md. Mobile root AGENTS.md, CLAUDE.md, and SPEC.md are absent at M. The mobile specification cabinet is spec/README.md, whose authority order appears at lines 5–18. The accepted execution decision is decisions/2026-09-08-mobile-expansion-execution.md, especially lines 7–18. Shared AGENTS.md was read at S. The folder-management-user-preferences skill was read before saving this report.

Canonical and active evidence: MOBILE-EXPANSION-PLAN.md:66–83,155–165,324–325, spec/EXPANSION.md:24–54,124–132, spec/EXPERIENCE.md:141–155, spec/PRODUCT.md:49–61, and spec/ACCEPTANCE.md light/default entries. Discovery also used bounded grep output over README.md, spec/*, docs/*, MOBILE-EXPANSION-PLAN.md and *AGENTS*. Historical docs returned by discovery were not used to override active contracts.

Product excerpt read set, all at M except item 12:
1. app/src/main/kotlin/dev/phosphor/mobil3/ui/ScopeUiState.kt
2. app/src/main/kotlin/dev/phosphor/mobil3/ui/LightSheet.kt
3. app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt
4. app/src/main/kotlin/dev/phosphor/mobil3/settings/SettingsArchive.kt
5. app/src/main/kotlin/dev/phosphor/mobil3/PhosphorNative.kt
6. rust/src/jni_glue.rs
7. rust/src/render.rs
8. app/src/main/kotlin/dev/phosphor/mobil3/SurfaceHost.kt
9. app/src/main/kotlin/dev/phosphor/mobil3/PhosphorApplication.kt
10. app/src/main/kotlin/dev/phosphor/mobil3/SurfaceOwner.kt
11. app/src/main/kotlin/dev/phosphor/mobil3/ui/PhosphorScreen.kt
12. S: crates/phosphor-beam/src/lib.rs

Path-only discovery searched mobile app/src/main, rust/src, app/src/test and shared crates. It returned filenames without reading additional product excerpts. Shared GPU lib.rs and shaders.wgsl were discovered only, not inspected. Test paths below were discovered only. No test bodies, GPU code, working-tree source, device state, or private preferences were read.

## 1. Approved facts, already settled

Canonical section 6 requires six saved RGB slots, add/edit/remove, and independent selected membership. Unselected slots survive. One selected color is solid. Zero selected custom colors returns to an explicit preset.

Random generated color, random interval, and saved-slot shuffle are separate controls. Automatic generated color owns color selection while retaining inactive saved order. Roll-now is required. Shuffle uses a bag with no immediate repeat for at least two distinct selected slots. Rust alone owns clock and random decisions. Decisions happen at leg/track boundaries, not per frame.

TIMER interpolates. TRACK steps and disables interval controls. Random TIMER duration stays within 0.1–60 seconds. Rapid-cycle acknowledgement must cover range edits, imports, randomness toggles and TIMER/TRACK changes. HOLD freezes displayed light without corrupting track/source state.

Archive /2 and strict /1 reading are already specified by spec/EXPANSION.md:52. Preserve the one-MiB limit, checksum, omitted-key merge semantics, whole-payload validation, and all legacy RGB values. Generated-color and random-interval modes default off, saved order defaults ordered, cycle defaults three-second TIMER (EXPANSION:43–46).

## 2. Exact three-color assumptions and smallest edit seams

### Kotlin state and UI

- ScopeUiState:104–113 holds exactly three illustrative colors, customCount=0, cycleSeconds=3.0f, cyclePerTrack=false. It has no saved-slot selection or separate color/interval/shuffle state.
- LightSheet:61–64 uses callbacks `(List<Color>, Int)` and `(Float, Boolean)`, plus acknowledgement callbacks.
- LightSheet:104–111 treats customCount==0 as preset selection and publishes zero count before selecting the preset.
- LightSheet:116–175 uses count chips 1..3, accesses the first count slots, and edits only that active prefix. Shrinking count retains the other list entries. Selection currently means prefix length, not membership.
- LightSheet:183–220 only shows cycle controls for count>=2. Its gradient is the active prefix plus slot zero. LEG is 0.1f..60f. The present LEG slider remains visible in TRACK.
- PhosphorScreen:157–158 declares the two light actions. Lines 774–780 route LightSheet directly to those actions and acknowledgement methods. Extend this existing seam, not another settings path.

### Typed persistence and publication

- MainActivity:1210–1211 separates portable and runtime SharedPreferences. The implementation is direct typed preferences, not a generic settings store.
- MainActivity:1244–1246 snapshots effective count, seconds and mode during general save. It does not synthesize missing custom RGB during clean export.
- MainActivity:1368–1386 accepts stored count 1..3, exactly nine finite RGB components in 0..1, reconstructs slots 0..2, and uses count zero if RGB is absent or invalid. Valid RGB remains in the UI when count is zero.
- The same restore path sends a zero-filled FloatArray(9) with count zero when no RGB exists. That explicitly retires native custom mode. Merely selecting SetBeamColor is not equivalent.
- MainActivity:1857–1868 allocates nine floats, takes three colors, publishes native state, then persists all nine components and count. It has no independent membership.
- MainActivity:1871–1878 publishes cycle seconds/mode directly and persists them. The restore path at 1374–1386 also publishes directly.
- PhosphorApplication:17–38 migrates runtime keys with copy-before-remove commits. Lines 76–84 name phosphor.prefs, phosphor.runtime, and epilepsy_ack as runtime-only. Preserve this existing separation.

### Archive

- SettingsArchive:16–17 currently writes only phosphor.settings/1 and limits input to 1,048,576 bytes.
- Lines 109–115 validate custom_count independently as 0..3, custom_rgb as a comma string of exactly nine finite floats in 0..1 with maximum 512 characters, and cycle_seconds as finite 0.1..60. There is no cross-field selection validation.
- Lines 198–205 reject schemas other than the single current SCHEMA.
- Lines 218–249 checksum raw supplied settings before normalizing supported values. Unknown scalar settings are included in the checksum, then skipped at 251–269. Nested JSON, arrays and null are rejected at 220–226.
- Lines 329–355 canonicalize sorted settings and numeric values, and insert the compile-time SCHEMA into checksum input at line 338. The /1 reader therefore needs the source schema in canonicalization when /2 becomes the writer. Changing only SCHEMA would invalidate old checksums.
- MainActivity:262–288 decodes before typed commit, snapshots only affected keys, restores those snapshots on commit failure, then calls restoreTuning on the UI thread. Omitted keys are not cleared. This is the merge/publication seam for validating the effective combined light state before any renderer publication.

### JNI and Rust

- PhosphorNative:57–60 documents nine floats and exports setCustomBeam, setBeamCycle and cycleAdvance.
- jni_glue:517–535 reads the first nine components into [f32;9], builds three RGB slots, clamps count to 0..3, and derives grid from slot zero multiplied by 0.85. It does not check exact array length or finite/component bounds there.
- render:69–81 encodes `colors: [[f32;3];3]`, count, grid, duration/mode and CycleAdvance.
- render:280–286 stores three colors, prefix count, fixed duration, timer epoch and leg index.
- render:489–515 clamps count to three. Custom edits reset leg and epoch. Zero restores the remembered preset. Duration/mode changes clamp seconds to 0.1..60 and reset epoch. TRACK events increment leg modulo active count when count>=2.
- render:637–668 derives the current color and publishes Theme::custom plus BEAM_RGB. This is the sole measured mobile light-clock authority. Extend this owner or a focused policy called by it.

### Shared boundary, not a shared six-slot rewrite

S crates/phosphor-beam/src/lib.rs:99–104 contains RGB channel triples for one resolved theme. These threes are channels, not saved-slot capacity. Theme::custom at 167–171 consumes one beam RGB and one grid RGB. Mobile render:662 already resolves the cycle before that call. Six-slot policy therefore does not require expanding these shared channel arrays.

The JNI comments call supplied values linear. Measured Kotlin code passes Color.red/green/blue directly (MainActivity:1858–1860), while shared CompositeParams::prepare explicitly applies srgb_to_linear to theme channels (S:350–356). Preserve legacy numeric values and their existing interpretation during migration. A color-space change is not part of six-slot expansion.

## 3. Current cycle and metadata behavior

### Clock

The render loop stores an Instant epoch (render:285). TIMER computes elapsed/seconds, adds cycle_leg, selects a modulo leg and smoothstep-interpolates RGB (644–660). This fixed-duration arithmetic does not currently draw randomness or maintain variable leg durations. The existing clock should gain boundary state rather than drawing randomness in the per-frame color calculation.

TRACK is not presently an instantaneous step. CycleAdvance increments cycle_leg and resets the epoch (512–515). The display then interpolates from that leg to its successor over cycle_secs and holds at the successor (644–660). Entering custom TRACK also starts a first interpolation from leg zero without requiring another track event. The source comment at 637–638 explicitly calls this a fade then hold.

This is measured source behavior, not a request to preserve a conflicting interpretation. Canonical section 6 says TRACK stepping. Root should state the intended transition duration in the active section contract before implementation. The existing comments, UI prose and actual math do not establish one identical meaning of “step.”

The idle loop still receives commands when no surface exists or rendering is paused (288–305). The old epoch is wall-clock based. These observations do not establish Hatchling's new HOLD behavior. Do not infer HOLD semantics from this pre-HOLD commit.

### Metadata-step seam

MainActivity:540–545 forwards Media3 metadata changes to SurfaceHost.metadataChanged. SurfaceHost:24–27 also listens to player onEvents. Lines 86–100 perform work only through the current surface lease, ignore empty metadata, construct track identity, and emit cycleAdvance only when identity changes.

The singleton PresentationTrackGate survives Activity/PiP/HUD handoff (SurfaceHost:113–115). SurfaceOwner:52–55 uses stable mediaId plus source, endpoint and queue index for local items. Empty IDs, remote:now and capture:now instead use source, endpoint, title, artist and album. Lines 57–60 suppress exact duplicate identities. The first nonempty identity counts as a change.

Consequences to preserve or explicitly specify: repeated local metadata does not step, moving to another queue index does, fallback title/artist/album updates can step, and the shared gate suppresses duplicate handoff observations. This is metadata identity, not local decoder-open or auto-gain-reset identity. spec/AUDIO-AND-CONNECTIVITY.md:28 explicitly keeps metadata-driven beam cycling separate from those reset proofs.

## 4. Rapid-cycle acknowledgement and import seam

LightSheet.applyGuardedCycle:39–51 computes `!perTrack && seconds < 1f && !acknowledged`. It updates UI to one second and publishes that safe TIMER value before opening the warning. It does not itself validate finite values or the full legal range.

LightSheet:67–97 keeps pendingSeconds in composition state. KEEP 1 s publishes one second. Explicit acknowledgement saves acceptance and publishes the pending value. Both mode chips and the slider use requestCycle (196–214). MainActivity:1881–1883 stores acknowledgement as runtime epilepsy_ack, default false, and documents permanent persistence.

The current import path does not pass through applyGuardedCycle. It calls restoreTuning after commit, and restoreTuning calls native cycle setters directly. Thus a valid /1 0.1-second TIMER archive is accepted by the archive validator without this UI acknowledgement gate. This is a measured path to include in R07's expanded guard map, not a claim that imports are already guarded.

The minimal seam is one effective light-settings validation/guard decision reused by UI edits, restores/imports and native publication. Preserve the local acknowledgement, safe-before-warning behavior, and TRACK exemption. Do not export epilepsy_ack or treat imported settings as acknowledgement.

For random intervals, guard the effective TIMER minimum, not one sampled duration. Keep invalid bounds out of both persistence publication and native commands. The pending state must retain the complete requested light snapshot so acknowledgement does not combine stale range/mode values.

## 5. Actual defaults and validation evidence

| Setting | Measured current default/domain | Evidence at M unless S marked |
|---|---|---|
| Custom active count | 0, with legal stored active 1..3 and invalid fallback 0 | ScopeUiState:111, MainActivity:1369–1385 |
| Saved RGB on clean install | Absent, not seeded from illustrative UI colors | MainActivity:1370–1385, spec/EXPERIENCE:153–155 |
| Illustrative picker values | #6BFF8C, #35BFFF, #FF4CE1 | ScopeUiState:104–109 |
| Cycle | 3.0 seconds, TIMER (`false`) | ScopeUiState:112–113, MainActivity:1374–1375, render:283–284 |
| Legal LEG/archive duration | Finite 0.1..60 seconds, both endpoints | LightSheet:196–199, SettingsArchive:114 and 50–53 |
| Native duration handling | Clamp 0.1..60, no explicit finite rejection in this command | render:506–509 |
| Rapid acknowledgement | false initially, local runtime Boolean | MainActivity:1881–1883, PhosphorApplication:76–84 |
| Guard threshold | TIMER strictly below 1.0 seconds, TRACK exempt | LightSheet:47–51 |
| Legacy RGB domain | Exactly 9 finite components, each 0..1 | SettingsArchive:109–113, MainActivity:1370–1373 |
| Preset | beam index 7, Solar Gold, RGB [1.0,0.84,0.30] | MainActivity:1295, S beam lib:142–145 |
| Related random controls | Existing beam-energy/glow randomness off, not generated-color randomness | ScopeUiState:49–54, MainActivity:1323–1324 |
| Beam-energy random range | 6..20 default, legal 1..30 | MainActivity:1317, SettingsArchive:72–75 |
| Glow random range | 0.30..0.90 default, legal 0..0.98 | MainActivity:1320, SettingsArchive:73–77 |
| Preserved tuning | AMOLED, mode 1, gain 1.8332275, auto-gain true, energy 8, glow .7, focus .3, geometry amount .6, grid false | ScopeUiState:12–16,43–58, MainActivity:1288–1336 and 1366–1367 |
| Other preserved defaults | Fullscreen true, HUD/BAND 1, view lock false, scope rotation lock true | MainActivity:1334–1341 |
| New generated/random-interval settings | Off by approved contract, not implemented at M | spec/EXPANSION:43–46 |
| New random interval endpoints | No current implemented defaults established | Existing model and archive read set |

Do not confuse beam_energy randomness or random mode selection with R07's new generated beam color.

## 6. Minimal ordered edit/check map for root

These are dependencies within approved section 6, not a competing feature plan. Checks listed here are proposed acceptance work. None ran in this mapper task.

1. Record only unresolved light semantics in the active spec before code. Name the new typed keys, six-slot representation, selected-membership representation, random interval default endpoints and precise TRACK transition behavior. Preserve the already settled defaults and mode precedence above. Use worked cases for legacy count zero with saved RGB and partial archive imports.

2. Add a focused typed light snapshot/validation/migration policy beside existing settings ownership. Replace prefix-count assumptions in ScopeUiState and MainActivity's save, restore and direct setters. Retain all saved colors independently of selection. Validate slot existence, count, selection, finite RGB and duration bounds together before publishing. Checks: 0/1/6 selected, holes after removal, duplicate selections rejected, out-of-range selections rejected, selection removal does not erase other slots, absent RGB remains absent.

3. Evolve SettingsArchive to /2 with source-schema-aware canonical checksum and strict /1 decoding. Verify the original /1 digest before migration. Preserve scalar/inert payload policy unless the active spec explicitly chooses another representation. Migrate each old RGB triple to the same ordered saved slot, retain all three triples even when old count is 0/1/2, and map old active prefix 0..<count to selected membership. Do not fill slots 4–6 with invented saved colors. Check exact legacy float values, count-zero saved colors, active count 1/2/3, old partial keys, /2 six-color round trip, malformed tuples/NaN/infinity/extra tokens, invalid count/membership and checksum rejection. Preserve existing settings on failure and absent-key merge behavior.

4. Extend the existing JNI command and Rust cycle owner together. Transfer a validated coherent light snapshot and explicit roll-now request without exporting RNG state. Replace the fixed three-slot array/count with six-slot-capable storage plus membership. Keep Theme::custom receiving one resolved RGB. Maintain Rust-owned start/end colors, boundary timing, selected order and bag state. Draw generated colors, durations and bag choices only at defined boundaries or explicit roll-now. Check seeded repeatability, endpoint bounds, equal min/max, no draw between boundaries, no immediate repeat across bag refill, each selected slot once per bag, and duplicate-valued distinct slots.

5. Extend LightSheet through ScopeActions and MainActivity, reusing one guard path. Add independent slot CRUD/selection, generated-color roll/automatic controls, random interval bounds and “Le random order.” Preserve inactive saved order while generated mode owns color. Disable interval controls in TRACK, explain the active owner, retain preset fallback, and keep all six colors editable. Check UI-to-native state consistency, safe one-second publication before warning, dismissal/KEEP behavior, explicit acceptance, and mode/min/max/import/toggle paths with and without acknowledgement.

6. Integrate with the existing metadata gate and root's accepted HOLD implementation after Hatchling releases those files. Preserve lease fencing and handoff de-duplication. Define how hidden/held track boundaries update live state without changing the held pixels. Check repeated metadata, first identity, local queue index changes, fallback metadata identities, TIMER/TRACK switches, settings edits while held, resume after multiple boundaries and app/PiP/HUD transfers. Re-read the changed HOLD code only under root's ownership before implementation. This report does not assess it.

Existing test destinations discovered by name-only search:
- app/src/test/kotlin/dev/phosphor/mobil3/settings/SettingsArchiveTest.kt
- app/src/test/kotlin/dev/phosphor/mobil3/settings/KnownDefaultsTest.kt
- app/src/test/kotlin/dev/phosphor/mobil3/ui/LightCycleGuardTest.kt
- app/src/test/kotlin/dev/phosphor/mobil3/FloatingHudPolicyTest.kt

A focused production Rust cycle policy with seeded unit tests is an approved seam under canonical plan:64 and 162. Exact new filename belongs to root. Device/build validation remains the canonical R07/R08 matrix, not a mapper output.

## 7. Semantics still needing an explicit spec decision

- Exact portable keys and slot/membership encoding, including delete/reindex behavior and new-slot initialization. No six-color saved defaults should be inferred from picker illustrations.
- New random interval default endpoints and sampling distribution. The legal range and off-by-default mode are already settled.
- Generated-color distribution/color-space constraints and whether roll-now while automatic mode is off temporarily owns the beam or updates another explicit state. Do not silently overwrite a saved slot.
- TRACK instant step versus the measured existing leg fade. Also define the initial generated TRACK color before any metadata boundary.
- Bag reset rules on membership edits, duplicate RGB values in distinct slots, and mode switches. No immediate repeat for two distinct selected slots is already settled.
- Random-color ownership with zero saved selections. Automatic generated-color precedence is settled, but the UI's zero-selected preset state and retained preset identity need one worked example.
- Boundary catch-up and hold/resume timing for variable durations. Preserve held pixels and current source truth. The changing HOLD implementation was intentionally excluded.
- Partial /1 import behavior when only count or RGB is supplied into an already-six-slot setup. Preserve omitted keys, yet validate the merged effective tuple. Specify the mapping instead of letting an archive decoder invent it.
- Whether an unacknowledged fast import keeps the requested range as pending or normalizes stored values. Native/UI safety before acknowledgement is settled. The new full-snapshot pending semantics are not yet named.
- Grid-color choice when slot zero is removed or unselected, or generated mode is active. The measured legacy rule is always stored slot zero times 0.85, independent of the current interpolated beam.

None of these decisions requires an unrelated renderer, relay, settings-bus or shared-theme rewrite.

## Validation and release

Performed: immutable git source inspection, file/line mapping, twelve-file read-set accounting, canonical/default comparison, and report/source SHA256 calculation. No network, GPU, device, Gradle, Cargo, product tests, source edits, Git mutation, worker spawn or acceptance claim occurred.

Mapping is complete within the stated scope. Implementation remains dependent on root's small semantic spec additions and integration with Hatchling's released HOLD work. The smallest next step is to settle those choices in the active section contract, then use the ordered edit/check seams above.

Read-only mapper explicitly releases. No implementation or shared-state ownership is retained.

## Blob SHA256 manifest

The appended manifest hashes complete immutable blobs, including files whose excerpt reads were selective. It does not imply full-file semantic review. The completion message carries the final report's SHA256 rather than embedding an impossible self-hash.

```text
7d41e332e50de84c076100b03382ce3c323c56745aa937ddd372169074df48ef  M:app/src/main/kotlin/dev/phosphor/mobil3/ui/ScopeUiState.kt
bc2e3697f534cd0668a432a1d78db7a755899b6542ed1a164d006b434556e231  M:app/src/main/kotlin/dev/phosphor/mobil3/ui/LightSheet.kt
71eb4fd9627d0d651f7f448f876a05741fff2d83aa59a21e3c5546e9d010f7b6  M:app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt
e7b5956b222da55aad136c6484d7ebf2b6e18999ee77e36e2c9393ae21d66ca8  M:app/src/main/kotlin/dev/phosphor/mobil3/settings/SettingsArchive.kt
5265f9cb4a57fc790e00d582fdae0aa402b80f61a35e77ddb1a627c06936b241  M:app/src/main/kotlin/dev/phosphor/mobil3/PhosphorNative.kt
e45707189f6f8e2e3890b84dea725d9911f1306eafaaf936a0d550da1ceece17  M:rust/src/jni_glue.rs
d2fcec760cadc255a4e0129f7d3a665a2d7005f14e69c21b1eca32ef2063522c  M:rust/src/render.rs
2fddde401479ef8e5624460fa859448501c873281c2efaae07602f281e3e8fbc  M:app/src/main/kotlin/dev/phosphor/mobil3/SurfaceHost.kt
702605193e08bce1147c6ced97cbf52863044ca0822e0a50e31a184a6a4923aa  M:app/src/main/kotlin/dev/phosphor/mobil3/PhosphorApplication.kt
fe3e03491d10b24faf5157404e41334ae13fc85c82d97b8f4796260138f4fd9b  M:app/src/main/kotlin/dev/phosphor/mobil3/SurfaceOwner.kt
5acd2da57da1f453628a80fa060cac81c73a70a2b022b780f6f03de94714dbfe  M:app/src/main/kotlin/dev/phosphor/mobil3/ui/PhosphorScreen.kt
b1b177307f91d3bcb8f854dbf204ddcfd48f69d0daeefdfaeb57a752858dc41c  S:crates/phosphor-beam/src/lib.rs
901d9c9af102640647fcc6936ca4deb9f973edf95e7be5e30d720a7e4667537e  M:MOBILE-EXPANSION-PLAN.md
1130f8cf637c0048cf8bc2a5abe63ecca618e640df64341461db6e43fd167418  M:spec/README.md
f271f417e7425e89fd2d5fe38df716a4fa5a9a9d94cf4990dd646ee33aba4dee  M:spec/EXPANSION.md
1e17779fb3b535a8004e5f8796a84ce90ccc829a209078f42af37fecbc2ce919  M:spec/EXPERIENCE.md
08f17ef33faa57d4df701a85a2524ee429b25aaeaf100367867cc9c4b9d3f109  M:spec/PRODUCT.md
d4a9028101dc44f3c74609abe0dcc3dfc1353df45deda2cf17ef98d6832fe469  M:spec/ACCEPTANCE.md
1562c4e1f297fac5642cbe3b7ad89ff6f5c19240cbfb67e6321a6a4df2a74d50  M:spec/AUDIO-AND-CONNECTIVITY.md
a297d84060875062bd9ffddc67b26f9a99367b3a8a3313917b62f36e625cd769  M:decisions/2026-09-08-mobile-expansion-execution.md
40db598fbadc6aaf6afdb605b4acb5dac744dd83f2fcb663dd37b50dce471fd9  S:AGENTS.md
```

Report finalized: 2026-09-08T07:25:51Z
