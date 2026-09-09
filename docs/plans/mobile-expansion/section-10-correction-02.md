# Section 10 correction 02: current appearance presentation

Written before code changes on 2026-09-08 at 15:45 UTC. Baseline mobile `485bc9fa946cf3e33cc32c062868f4192a0327b6`, shared `0ffd658d7f19e68180c2720e0500b23644619e90`.

This addendum corrects only F1 through F4 in the immutable ROUND 1 report at `/home/ben/.jcode/scratch/section10-round1-485bc9-jjzFcM/REPORT.md`. That report and its 7/10 score remain unchanged. Source implementation and host checks do not establish Android acceptance.

## F1: current ROOM controls

The effective authored appearance supplies ROOM labels and the starting value for each action. Saved-record association is not a style baseline. Legacy override preference keys remain migration provenance, not current control authority.

Each ROOM control sends only its own field. FEEL cycles the four concrete characters and deliberately selects that character's coupled defaults, including motion, duration, density, radius, typography, designators and panel treatment. MOTION cycles concrete motion choices and retains its existing coupled duration default. CORNERS cycles 0, 8 and 12 dp, starting from the displayed radius. LABELS toggles the displayed Boolean. These flattened controls no longer display a misleading `match` state.

A CORNERS or LABELS action preserves every other authored field. A MOTION action preserves every field except motion and its coupled duration. FEEL preserves colors and the two palette behavior flags. Selecting a legacy ROOM selects that room's built-in appearance without carrying stale overrides. Successful publication clears the obsolete active override mirror. No raw preference is rewritten.

Checks exercise the migration Annotated to curated AMOLED to CORNERS schedule, and edited curated-associated motion/density to CORNERS schedule. They also check each field edit and coupled FEEL against actual production defaults.

## F2: readable presentation without authored rewrites

StatusBand always has an opaque authored plane backplate. Source, gain and contextual text use opaque presentation colors checked against that exact backplate. Black or bright native content cannot change the composite.

Flat keys, chips and essential outlines use opaque control interiors and contrast-resolved presentation roles. Normal text requires 4.5:1. Essential boundaries require 3:1. Resolution first retains the requested color when it passes, then uses a readable black or white foreground. This layer does not modify AppearanceValue, saved colors, sampled beam values, exports or the native beam. Glass retains translucent material outside the readable interior.

Checks use Light over black and white scope content, Glass with `withBeam(0, 0, 0)`, selected text and an unselected essential outline. The editor keeps its explicit authored-color correction and explains the separate automatic presentation protection.

## F3: active spacing and background

Density multiplies shared chrome padding and gaps in the active Appearance editor, settings section rows and console. Text size does not scale. Interactive controls retain at least 48 dp minimum targets. Two otherwise equal previews at 0.85 and 1.25 produce different spacing through the same production spacing function.

The authored plane paints StatusBand and a visible console/editor frame gutter. Opaque readable interiors remain surface-colored. A plane-only preview therefore changes current chrome without touching the full-bleed native scope or HUD window. Checks compare isolated plane changes and spacing endpoints. Physical geometry and accessibility remain target checks.

## F4: lifecycle and purposeful motion

Android reduced-motion state becomes observable Compose state and refreshes on each Activity resume. The existing native reduced-motion setting receives the same current value. No observer service or poller is added.

BenchPost owns no clocks when covered, hidden, in PiP, reduced or CUT. Reduced/CUT presentation shows the complete static readout rather than a timed reveal. Eligible visible presentation uses the existing finite three-line reveal and retirement. Changing visibility or motion policy cancels that effect. Once retired within its owner, reopening a sheet does not restart the cold-start readout.

Settings expansion keeps existing immediate content ownership and anchor geometry. Its chevron makes a finite state transition that explains expansion, rather than animating measured layout beneath the anchor owner. CUT and reduced paths use the final static geometry. Overflow's existing active glyph receives a finite state-linked index movement. Existing AppearanceMotionPolicy governs eligibility. Neither glyph owns an idle timer.

Checks cover policy combinations, finite state endpoints, source lifecycle refresh and exact caller visibility wiring. Real Compose-clock cancellation, resume behavior and frame counts remain unrun until root's Android gate and authorized target pass.

## Boundary and release

Changes stay in the assigned Activity, UI, narrowly necessary new pure appearance presentation policy and focused tests. SettingsGestureAdapter, SettingsInteraction, storage, codecs, collections, source/audio, native/shared code, canonical plan, HANDOFF and original reports remain untouched.

No Gradle, build slot, Android/JNI/GPU, device, ADB, GUI, audio, network, services, downloads, Python, Git mutation or workers are used. Cached actual-source JVM tests are permitted. Root owns the Android gate after explicit source/build release, due by 16:15 UTC. A private sealed report records exact changed paths and hashes, commands, observed checks and honest blockers.

## Observed host checks, 15:59 UTC

The cached actual-source JVM runner passed 132 tests across 11 suites. Its 16 new tests cover both F1 owner/adapter schedules, one-field preservation, coupled FEEL, exact contrast composites, density spacing, plane isolation, bounded POST callbacks and cancellation, glyph endpoints and production wiring. The unchanged legacy bridge test also checks 64,350 combinations. These combinations are not additional test cases.

All 17 changed Kotlin files passed a supplementary PSI syntax check. This does not resolve Android or Compose APIs. `git diff --check` passed. The private evidence directory is `/home/ben/.jcode/scratch/section10-correction02-PrGmp7`.

The existing RoomStyleOverrideTest keeps all behavioral tests. Four source-form expectations now name the authoritative style and derived sample choices. AppearanceRuntimeWiringTest changes one old callback expectation. SettingsControlAccessTest replaces two old conditional-size source expectations with two per-control unconditional 48 dp checks. No test was removed. The complete RoomStyleOverrideTest and SettingsControlAccessTest suites were not run in this cached host invocation.

The Android build/lint/full suite, Activity resume, Compose frame cancellation, measured layout, touch targets, accessibility, bright/black moving-content pixels and source continuity remain root acceptance checks. No report score or section availability is promoted by these host results. The immutable private report supplies the explicit source/build release and exact hashes.

## Coordinator integration, 2026-09-09

Released source and the immutable private manifest verified in full before gate267547pfgo. Actual Android compilation succeeded. The891-test suite had one failure in the existing StageGesturePolicyTest source-order assertion: its literal old unscaled console padding no longer exists. Inspection confirms card geometry is still reported before both the new plane gutters and density-scaled inner padding. The correction now requires all three markers to exist and orders the report before both padding stages. No production gesture behavior or test was removed. The failed XML archive is retained separately. Native126, GPU3, boundary and source-inventory checks passed. Full Android rerun is required, and no phone acceptance is claimed.

Full frozen rerun409985gj6g passed at2026-09-09 01:30UTC:891 JVM tests/74 suites,126 native tests,3 offscreen GPU tests, Android compilation/lint/both debug APKs, engine/release-helper/source-boundary checks. Mobile/shared inventories stayed unchanged. This is working-source integration, not final reviewed clean freeze or device acceptance.

Retained prefix: `dev/scratch/mobile-expansion-20260908T001819Z/appearance-correction02-integration-r2`.
- App APK SHA256 `9d8efbc099e40ef444f2e78364673a28696ca7ba95d419c5df43a7851a9c3a58`.
- Companion APK SHA256 `b0ed73a8b86af785406977f2630e9883a5e9229b454091a0fcd3df8364a19340`.
- JVM XML archive SHA256 `e34e83729972b7adf0ba6d9e5f29988afa265c68a48f70067c55b9ffa4d1ba02`.
- Runner SHA256 `5b8315e6457b6b98675157715879a712858b23de000542f622e38ced272a18d8`.
No installation or device action occurred. Cat's original private release remains unchanged, with explicit source release recovered from disk rather than inferred from the elapsed session pause.
