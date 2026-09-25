# Transpose · the S25 soul in the ASUS body

Ben + Prime · 2026-09-24 · Prime holds creative direction by Ben's request.

> The scope is the room. Everything else is a light switch by the door.

## Decision

We keep building on the latest source (`master`, tag `archive/asus-before-s25-reset-20260924` marks the start).
We do not copy the S25 UI line for line. We transpose its spirit: direct, colorful, aligned, calm.
Every post-S25 capability is reviewed. Useful work is ported into the new surface. Bloat is cut.
If this does not work, Ben's fallback is a reset to the S25 source `06f84e2`. That door stays open.

## Why the last attempt failed

Unit 1 passed 981 tests and failed in Ben's hands in minutes. The tests checked source text and numbers.
Nobody checked whether a person could read, reach and enjoy the thing. Evidence now comes from the phone.

## Creative direction

**The instrument first.** On launch, the live scope fills the screen. Chrome appears on a tap and leaves quietly.
Nothing on the stage talks in engineering. No `×63.69·a`. No fps counters by default. No stacked disclaimers.

**One console, one row.** Aligned square keys with one visual language: transport, SRC, MODE, LIGHT, and TUNE.
Each key does one thing. Same size, same weight, same states. A key never changes shape between states.

**Color you can touch.** LIGHT is a grid of real colored swatches, as on the S25. Six saved colors live there.
Selected state shows by shape and mark as well as hue, so color never carries meaning alone.

**Zoom that feels like closer and farther.** AUTO keeps sound in view. Pinch and drag mean closer/farther, with
a brief quiet cue, not a number. A number exists only in the developer view.

**Settings as a short drawer.** Few sections, each with the controls people change. One sentence of help at most,
only where a control would otherwise confuse. Everything else lives in the manual.

**Accessible to all.** 48dp touch targets. TalkBack labels on every control. Large-font layouts that do not break.
Contrast checked on AMOLED, Glass and Light. Reduced motion respected. One-handed reach for common actions.

**The vibe stays.** CRT glow, phosphor persistence, the turtle's humor in the manual, Glass as a first-class look.
Motion is short and purposeful. The beam is the only thing that should feel alive all the time.

Prime keeps creative agency inside these lines. Choices that change what Ben sees are shown before they land.

## Anti-bloat laws

1. One home per control. No duplicated menus or duplicated paths to the same setting.
2. No explanatory prose under ordinary controls. Help belongs to the manual or a single contextual line.
3. One button family. One size scale, one corner rule, one pressed state, one disabled state.
4. A menu must hold at least three real choices, or it folds into its parent.
5. Diagnostics (fps, gain numbers, signal check internals) live behind the developer unlock.
6. Deleting UI is a valid outcome. Keep the capability when a simpler surface can expose it.

## Acceptance, rebuilt

Tests prove behavior users rely on. They do not prove taste.

- **Keep:** pure behavior tests of real logic (audio, persistence, ownership, parsing).
- **Retire:** tests that only assert source strings or file layout. Retire them when their code is touched.
- **Add:** device UX scripts on the test bed that drive real taps, pinches and drags, capture screenshots,
  and check reachability, target sizes, text truncation, contrast and large-font layouts.
- **Look:** an independent design review of real device screenshots against this direction.
- **Decide:** Ben's hands. Only Ben moves a slice from `verify` to accepted.

## Devices

- **Test bed:** second ASUS `NAAIB700B7373PZ`. Free for any app state, installs and experiments.
  Currently holds the S25 build for side-by-side comparison. Settings backup retained.
- **Ben's ASUS:** `NAAIB70036673ZC`. Receives only builds ready for his feedback, with state preserved.
- **S25:** untouched reference. No operation without a separate ask.

## Work plan

Slices are small and installable. Each ends with a test card for Ben.
Ben may say "continue" instead of testing when he has no time.

### Phase 0 · See both clearly (no code)

- Capture the S25 build and the current build on the test bed: every screen, sheet, state and orientation.
- Inventory all 124 post-S25 commits into a port matrix: keep as is, port into new UI, simplify, or cut.
- Reproduce the mic glitch and the zoom confusion on the test bed with real input. Name the causes.
- Output: `inventory.md`, side-by-side capture set, and root-cause notes.

### Phase 1 · The stage and the console

- Clean stage: no engineering readouts; HUD stats and signal internals move to developer view.
- One-row console in the S25 key language. Source, transport and mode directly reachable.
- Fix the mic glitch at its cause. Rework zoom feedback into closer/farther with AUTO kept calm.
- Test card #1 to Ben.

### Phase 2 · Color and look

- LIGHT swatch grid restored, with six saved colors, editing and cycles ported from the current build.
- Themes: Glass, AMOLED and Light as direct previews, not a raw-value editor. Existing looks preserved.
- Test card #2.

### Phase 3 · Settings and help

- Short settings drawer: sections cut to what people change. Dismissal gesture that works after scrolling.
- Manual: simple everyday help, working Back and swipe-back, deeper material behind the developer unlock.
- Test card #3.

### Phase 4 · The rest of the port

- Remaining keep/port items from the matrix: HUD/PiP, presets, HOLD, startup, mixing, lifecycle and
  brightness fixes. Each through the same UX checks.
- Accessibility pass on the whole app. Large fonts, TalkBack and landscape.
- Test card #4, then a release-readiness list for the Play Store. No store action without Ben.

## Team

At most three subagents, all on `anthropic/claude-opus-5-5`, as Ben asked. Prime also runs on Opus 5.5.

1. **Cartographer:** Phase 0 inventory and port matrix. Read-only.
2. **Designer:** direction mockups (HTML/vector) from real screenshots, then design review of each slice.
3. **Builder/Tester:** one implementation writer at a time, then device UX scripts. Prime owns integration,
   builds, Git and phones.

No two writers touch the same file. Reviews rotate in after a writer releases.

## Boundaries

No release signing, store upload or public push without Ben. No S25 operation. No root or system writes.
Ben's ASUS keeps its data. The test bed may be reset freely. History and receipts stay intact.
If blocked: name the condition, the evidence, the best result so far and the smallest next step.

## Context

Prime asks Ben for a summary compaction at phase boundaries when context runs high.
