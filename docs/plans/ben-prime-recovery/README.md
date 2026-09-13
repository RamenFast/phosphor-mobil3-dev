# Bring the instrument home
## Ben + Prime · Phosphor Mobile recovery · 12 September 2026

> Pick a sound. See its shape. Bring it closer. Keep a moment.

**Status: consolidated direction, not implementation started.**
The ASUS is our recovery target. The S25 is our interaction reference.
We keep useful new machinery and recover direct, colorful, orderly interaction.

[Next-session prompt](NEXT-SESSION.md) · [Conversation and verbatim Chapel feedback](../../2026-09-12-ben-prime-direction-recovery.md)

## The compass

The microphone is a main experience. A harmonica, piano, flute, voice or bird
becomes something you can see and explore. Chapel called it "mesmerizing";
Ben recalls something like "another way to hear things."

The S25 provides our visual anchors: aligned, uniform, easy-to-tap controls;
legible order; the Glass theme; visible colors in Light. The ASUS provides new
capabilities and useful expanded sections. Neither requires discarding the other.

**Simple first. Depth when sought.** Give ordinary actions visual priority. Put
secondary options nearby or below, not in competition with the instrument.
Color shows something selectable. Motion shows response, resistance or ownership.
No new dashboard, uniform text-row replacement, or decorative redesign campaign.

## What this document owns

This is the current recovery scope and work map. The dated conversation brief is
the evidence/history, not a second task queue. Existing `vision/`, `spec/`,
`MOBILE-EXPANSION-PLAN.md` and the B1-B21 plan retain uncovered requirements.
Before each implementation unit, fold its newer accepted decisions into the
relevant active spec and tests. Do not erase historical receipts or close old
requirements merely because this map is shorter.

Ben's explicit decisions are marked **settled**. Prime's execution ordering and
engineering choices are **proposed** or **delegated**, not invented user quotations.
Creating these documents does not start code work, a build or device testing.

## 01 · Sound, hands, truth

### Settled experience

- Source, relevant capture permission, zoom, play/pause and previous/next are the
  frequent actions. They need direct access, not just a higher place in a long drawer.
- Two-finger pinch is first-class, alongside single-finger zoom.
- With AUTO on, pinch changes **preferred framing**, not AUTO enablement. Remember
  that framing across restarts. Earlier gesture-to-manual-takeover rules are superseded.
- Faint structured sound should grow into view, with a little breathing room.
  The requested 20x or greater enlargement addresses a trace that is simply too small.
  It is not a request for frequency filtering or a shorter time window.
- Ben delegates gain adaptation/tuning to Prime. Diagnose the current ceiling,
  threshold and adaptation behavior before choosing a formula or numeric maximum.
- Media transport follows the currently audible player. Show which player owns
  the controls; a paused Spotify session must not silently seize YouTube's commands.
- Standard playback-plus-mic mixing remains in scope. Its visualization must not
  feed the speaker or create microphone feedback.

### Unit 1 · Let quiet sounds come closer

**First implementation slice.** Microphone → pinch → remembered auto-framing.

Done when the actual ASUS path proves:
- A selected built-in microphone produces visible signal with permission-first startup.
- Quiet structured input is enlarged; silence does not cause uncontrolled runaway.
- Slow and ordinary pinch movements change preferred framing without disabling AUTO.
- The preference survives restart. Define and test its default and reset behavior.
  Explicit manual gain remains usable with AUTO off.
- A later loud sound recovers usable framing without clipping away the important shape.
- Gesture dead zones, existing source switching and audio levels do not regress.

Use controlled quiet/loud/silence fixtures plus bounded live microphone use. Report
measured adaptation and maximum useful magnification. Numeric display changes alone
are not success. Single-finger zoom should share the same visual goal; finalize its
mapping before changing that gesture. Do not add recording, HDR or theme changes here.

**Pause after its verified build/test card.** Ben tests before expanding the implementation scope.

### Unit 2 · One clear source, one truthful transport

Verify standard capture → microphone → playback-plus-mic; permission denial,
partial-input loss and explicit stop; distinguishable playback/mic signals; mic-off
removes only mic; no feedback or stale reader publication. Include actual route/rate
truth and the available built-in mic. USB/Bluetooth evidence waits for a connected
accessory, not a fabricated PASS.

Reproduce Spotify paused while YouTube plays. Commands and displayed state follow
the selected current player; source transport and display HOLD remain distinct.
For simultaneous audible sessions, define a stable visible selection policy rather
than switching on every callback. Never infer runtime behavior from metadata alone.

## 02 · Familiar hands

### Unit 3 · Restore the direct controls

Keep expanded settings and smaller dropdowns. Simplify their inner controls.
Restore the S25 colored swatch grid/direct samples/gradient language while retaining
six saved colors, membership, RGB editing, shuffle and generated/random cycles.
Keep touch alignment, labels and selected-state meaning readable without color alone.

Map quick actions before layout edits: Sources, contextual access recovery, zoom,
transport, quick HUD and PiP controls. Prioritize common tuning over startup prose.
Preserve existing users' looks, values and AMOLED default; do not impose the generic
house default on this project. Glass remains a first-class reference, not an opaque
fallback in disguise when transparency is supported.

Done: real six-slot edit/select/delete/cycle and preset flows, narrow/landscape and
large-font checks, source-specific controls, theme-aware contrast, and clear targets.
A screenshot of an unopened editor is not workflow acceptance.

### Unit 4 · Drawers that answer the hand

Preserve working opening gestures. Reproduce the reported post-scroll inability to
dismiss Settings. Inventory every swipe-dismissable sheet; Settings currently has
a different gesture owner from other sheets. Do not copy a suspected bug into all of them.

Provide readable resistance/card-weight feedback, with reduced-motion behavior and
no hidden animation loop. Distinguish deliberate header pulling, content scrolling,
slider manipulation, reversal and cancellation. Check X, system Back and return
paths. Tune real accidental/deliberate gestures; a source arithmetic test is not comfort.

The exact scrolled-content/header dismissal rule and visual treatment need a small
proposal before implementation. Ben requested liquid-glass-like feedback, not a
wholesale new card material or uncontrolled bounce animation.

### Unit 5 · Appearance and help without a puzzle

**Themes:** verify actual preview/apply/cancel/save/rename/delete/reset and settings
survival. Distinguish app focus faults from automation typing in the wrong field.
Show the effect of a chosen look, not only a fixed dark editor and raw values.
Retain safe recovery for failed saves. Immediate selection+Undo versus draft+Apply
is an open UX choice, not settled by this plan.

**Manual:** short practical normal help about using/reading the scope, plus fun
architecture facts. Clear in-manual Back, swipe-back and system Back through history
before closing. Preserve bestiary charm. Deeper manual behind repeated build-number
taps and a developer setting. Tap count/persistence are open; ordinary recording and
advanced instrument controls must not become secret by assumption.

Done: real navigation/search, readable chapters and art, working history, theme
round trips and no accidental source or permission action from browsing help.

## 03 · Stay present

### Unit 6 · Brightness, lifetime and carried presentation

Investigate reported dim-until-tap behavior after settings/app return. Measure beam
luminance separately from app window brightness and system dimming. Do not solve it
by forcing global brightness. Distinguish a crash, display-off, Activity replacement
and service retirement in the reported app closure.

Verify relevant source/wake ownership, Home/return, screen-off rules, task removal,
linger, and visible PiP/HUD handoff. Preserve one source and one renderer owner.
Exercise HOLD/BLACK inspection/reset/resume and invalidate history on source stop.
No speculative OS/root repair. No cancelled idle soak. Use bounded event-driven tests.

Units 2–6 may be reordered after Unit 1 feedback where dependencies justify it.
Investigations can overlap; user-facing slices and shared-file integration stay bounded.

## 04 · Keep a moment

### Unit 7 · Audio captures, not video

Recording is part of the recovery outcome, not an indefinitely deferred idea.
Build it on the verified source ownership, not a second recorder fighting the first.

| Contract | Settled behavior |
|---|---|
| Content | Audio only, any currently selected supported source |
| Audio integrity | Visual gain/framing never changes recorded audio levels |
| Storage | Dedicated recordings folder, easy file-browser playback |
| Navigation | Captures entry in Sources near file/folder playback |
| Main action | Simple source-associated Record/Stop control |
| Optional disclosure | If needed, size estimates and "don't show next time" |
| Screen off | Stop/save by default; nearby "keep recording when screen is off" option |
| Visible HUD/PiP | Continue; actual screen-off still follows its checkbox |
| Source switch | Stop/save by default; optional continuation keeps the **same file** |
| Size limit | Custom mobile-friendly limit with approaching-limit red text in normal HUD |
| At-limit action | Mutually exclusive Stop & save / Start another file options |

Current root is deferred; recording cannot create root or bypass an app's capture
exclusion. File/relay capture needs a genuine audio stream; geometry-only relay must
report its lack of recordable audio rather than inventing samples. Recording a selected
mix needs a defined faithful audio mix, not visual amplification or speaker monitoring.

Efficient compression is requested with recent-phone capability research. Verify
encoder availability, channels, quality, power and actual hardware support. Preserve
stereo and rate truth. Do not claim lossy is lossless or silently raise the Android floor.
Estimate size using the chosen encoding; actual size and container overhead own limits.

Proposed default at the size limit: Stop & save. Ben has not explicitly selected it.
Also finalize initial size limit, warning threshold, format/quality controls, audio
normalization between switched sources, naming/location and file-management behavior.
Show the small set of user-visible defaults before implementation, not every internal choice.

Done includes real file playback with retained folder access; every available source;
source changes in the same playable file; screen/HUD/PiP matrix; finite-size splitting;
finalization; low/full storage; cancellation; encoder failure; process interruption;
and clear notification/Stop while recording remains active outside the full app.
Disclose actual crash-recovery limits. No hidden capture or raw audio retained as routine
telemetry. Controlled test fixtures have explicit ownership and cleanup.

## What waits, what stays visible

**Later:** root stereo/SoundCloud research, genuine HDR acceptance, three independently
frequency-ranged scopes, elaborate further animation, and a full interactive tutorial.
No new promise of HDR or root acceptance comes from this recovery.

**Small supporting work if useful:** short listening guide, legal demo music and
an external stereo-mic recommendation. No purchase or distribution rights assumed.

**Inherited obligations are not erased:** B1-B21 folder/seek/metadata/relay truth,
slider/dead-zone/grid/auto-gain/settings/lifecycle checks; instrument preset workflows;
six-color migration; accessibility and source boundaries. Map them to the changed
units and preserve remaining gaps. No blanket release claim from a short regression pass.

## Workers without drift

Prime owns scope, source integration, build slot, device actions and the user test card.
At most two live workers and one implementation writer in the project. Independent
research can run in parallel. Assign file ownership; never overlap shared-file writers.

- Audio investigation/implementation: framing, source/mix/transport, later recording.
- Interaction investigation/design: baseline comparison, controls, gestures, themes/help.
- Independent review: rotate in after a writer releases. Review the complete assigned
  user outcome against source and runtime evidence, including failures, not score alone.

Use currently available approved native model routes. Historical Grok fallback, stale
heartbeat mandates and expired model selectors do not revive themselves. Default to
GPT/OpenAI for implementation/trusted review under current harness guidance; isolate
sanitized visual contributors. Never send household/private device data wholesale.
Do not install retired Codex CLI or route Grok through OpenRouter.

Workers do not build/install, operate phones, publish or spawn workers unless the
root explicitly assigns that scope. End asynchronous turns with a concise update,
then inspect completion messages. No polling loops or repeated reviews for their own sake.

## Evidence that reaches Ben

Each unit carries: intended experience → named checks → exact source/APK → result.
Source tests, Android execution, physical observation and Ben's feedback are distinct.
Do not reopen every accepted component. Do reopen a boundary when a regression reaches it.

Before source edits, update the relevant spec and preserve the current rollback state.
After an approved implementation unit, build/install through the project interface,
read back APK/hash/signer, and test actual gestures and source paths. Check the
sibling engine's exact source when it changes; shared code is not copied into mobile.
Never replace or operate the S25 reference without a separate explicit request.

### Ben's test card

- **Build:** exact commit/APK, not only `2.0.0-debug`.
- **Changed:** the short user-visible outcome.
- **Try three things:** concrete actions, not a giant acceptance spreadsheet.
- **Watch:** known limits and the questions this build answers.
- **Recovery:** retained prior APK/preferences and what Prime can restore.

Pause for Ben after Unit 1 and each later agreed test slice. He explores and reports;
Prime carries the backlog, reproduction and integration work. Do not hand him an
engineering checklist or continue the whole roadmap on an old autonomy instruction.

## Boundaries and honest stopping

Current session: documents only. Future session: execute only its explicitly invoked
unit. No release/signing/store push, uninstall/data clear, system/vendor/boot/vbmeta
writes, root configuration changes, S25 operation or cancelled soak. Preserve protected
archives and other sessions. Use the ASUS explicit serial; never default adb to a device.

If blocked: name the missing condition, evidence, best useful result and smallest
next action. Missing accessories or physical HDR measurement must not stop independent
ordinary-mic recovery. A compile, selectable toggle or review score alone is not done.

## Reference receipt

At recovery intake, mobile HEAD `591e2bb` was clean; it is 117 commits after S25 source
`06f84e2eb7da46c758e9f8b388c3537e6c67905d`. Both installed versions say `2.0.0-debug`.

- ASUS `NAAIB70036673ZC`, Android 14/API34. APK SHA256:
  `2bb7b9830f5bd1dbd10256e4dffc6faa9a3ebebff3784e8d2419ac2335e1882b`.
- S25 reference APK SHA256:
  `4a370170cda5410caf8abc82d8cd9a997fd0777c718cbd1ba31c2cd3aa9127fa`.
- Retained S25 APK:
  `dev/scratch/mobile-expansion-20260908T001819Z/root-stereo-06f84e2eb7da.apk`.
- Existing Android XML: 956 passes, not rerun during recovery.

Recheck current Git/APK/ownership at the next session. These identities are receipts,
not permission to overwrite later work. Full historical facts and user wording remain
in the linked dated brief. This document is the concise forward route.

### Consolidation check · 2026-09-12

An independent read-only reviewer compared this plan and the next-session prompt
with the full conversation brief. No material findings. Its optional suggestion
to name framing default/reset behavior explicitly is incorporated in Unit 1.
Local recovery links and whitespace checks passed; Chapel's raw report remains
verbatim. This validates the planning handoff only, not application behavior.
