# Ben + Prime: Phosphor Mobile direction recovery

Date: 2026-09-12 (Ben's local date)
Status: recovery and collaborative planning only. No implementation approval.

**Forward route:** [Bring the instrument home](plans/ben-prime-recovery/README.md)
consolidates the agreed direction into bounded work units.
[Next-session prompt](plans/ben-prime-recovery/NEXT-SESSION.md) starts Unit 1 only when
Ben invokes it for implementation. The material below preserves the discussion.

## Situation

Ben wants to bring the latest ASUS build back toward the direct, colorful,
readable UI/UX of the version he enjoys on his S25. Keep useful new capabilities,
but finish their everyday interactions before expanding further.

Ben has been using and testing the ASUS build live and received direct user
feedback today. He confirmed that the current complaints concern the ASUS build,
not the older S25 build. Individual reports below are Ben's account; we have not
attributed particular observations to the other user or reproduced them this session.

The project is recoverable. The source and retained APKs exist. The problem is
not simply missing features: several features exist but feel undercooked, the
interface has become harder to operate, and the direction has drifted.

Prime's interpretation: development added capabilities, replaced major UI paths,
and then polished individual components faster than it verified the complete user
experience. Test counts and review scores do not override actual user feedback.
This interpretation is not a diagnosis of every reported bug.

## Exact reference points

Repository: `/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3`.

| Surface | Identity | Role |
|---|---|---|
| S25 | `2.0.0-debug`, code `2000000`; retained source `06f84e2eb7da46c758e9f8b388c3537e6c67905d` | Preferred interaction/design reference, not blanket bug-free acceptance |
| ASUS | `2.0.0-debug`, code `2000000`; installed APK matches current local debug APK | Recovery target |
| Current mobile source | `591e2bb`, 117 commits after the S25 reference, including documentation/tests | Preserve new work while planning selective recovery |

S25 installed and retained APK SHA256:
`4a370170cda5410caf8abc82d8cd9a997fd0777c718cbd1ba31c2cd3aa9127fa`.

ASUS installed and local APK SHA256:
`2bb7b9830f5bd1dbd10256e4dffc6faa9a3ebebff3784e8d2419ac2335e1882b`.

These were checked read-only during this session. No app launch, installation,
capture, permission change, preference change or source-code edit occurred.
The same version label covers substantially different development builds.
The S25 source association comes from the exact retained APK receipt in
`plans/mobile-expansion/section-02-stereo-trials.md`, not the version label alone.

The mobile and sibling engine trees were clean at intake. Only planning documents
have changed in this recovery session. Existing test XML contains 956 passing
Android unit tests; those tests were not rerun here and do not prove current UX.

## Ben's feedback and desired direction

### 1. Settings structure and inner controls

- Expanded sections are comprehensive. Keep the useful grouping.
- Dropdown/popout treatment is appropriate for smaller items.
- Controls inside expanded sections need cleanup and easier interaction.
- Frequently changed settings should be near the top of the drawer.
- Do not equate consistent formatting with usable controls.
- Exact priority order and control designs still need discussion.

### 2. Drawer gestures and physical feedback

- Accidental pull-down protection helps.
- Ben reports that after scrolling, pulling down cannot dismiss Settings; X is required.
- Check every drawer/screen that previously allowed swipe dismissal.
- Preserve the settings-opening interaction that already works.
- Add visible resistance and a sense of card weight/gravity, described by Ben as
  liquid-glass-like feedback. Exact material, deformation and motion are undecided.
- Decide how scrolling back to the top, header dragging, deliberate dismissal,
  cancellation and system Back should cooperate before implementing.

Source comparison: other sheets retain the older gesture owner. Settings alone
has the new owner, including a cancellation state that can require close/reopen.
This is a plausible failure path, not a reproduced explanation of Ben's report.

### 3. Themes and appearance

- Theme configuration is confusing.
- Ben is unsure whether settings actually work. Test their visible effects and persistence.
- Separate a confusing workflow from an actual failed setting; both need evidence.
- Current curated choices load a draft, with PREVIEW/APPLY/CANCEL/SAVE after raw
  fields. A fixed dark editor can make the selected theme's effect unclear.
- Prior ASUS automation failed to enter/save a named look because input reached
  numeric fields. That does not yet distinguish app focus trouble from test-driver error.
- Do not discard saved appearances or silently change their values.

### 4. Manual and progressive depth

- Ben likes the manual, but finds navigation difficult and normal content overloaded.
- Provide a clear Back action and swipe-back navigation.
- System Back should navigate inside the manual before closing it when history exists.
- Focus everyday help on reading and using the scope/app, with fun architecture facts.
- Move deeper material behind a secret developer setting, unlocked by repeatedly
  tapping the app build number. Tap count and unlock persistence remain undecided.
- Preserve the fun and character without requiring technical reading for basic use.

Source comparison: chapter BACK/INDEX/PREVIOUS/NEXT controls exist, but the manual
has no local system-Back or chapter-swipe handler. Existing five-tap art discovery
unlocks the bestiary, not a developer manual. A source button's existence does not
prove users can find it.

### 5. Restore colorful, direct interaction

- Ben explicitly asks to bring back the old view and extend it.
- Restore visible selectable colors and interactive visual elements.
- Avoid a uniform, colorless interface in place of meaningful color choices.
- Keep the new six-color capability without assuming its current text-row UI must stay.

Source comparison: `7ac1e55` replaced the Light sheet's colored three-column preset
grid, directly tappable custom squares and gradient with text-heavy rows. Current
saved slots have a small sample and separate Select/Edit/Delete actions. This is
an actual widget replacement, not just a theme mismatch.

### 6. Overall recovery posture

- Features are present, but many need to be cooked properly.
- Potential bugs remain. Do not turn uncertainty into either dismissal or a blanket failure claim.
- Refocus together before implementation. Recover the ASUS experience, using the
  S25 interaction language as a reference rather than resetting to that entire codebase.
- Ben wants Prime to ask detailed questions before a later implementation session.
- Ben will request a handoff prompt for that session after direction is settled.

## What happened after the S25 reference

1. Instrument expansion: floating HUD, HOLD/inspection, six colors/random cycling,
   instrument presets and signal check.
2. UX replacement: retained expandable settings, a new Settings-only dismissal
   mechanism, authored appearance editor and indexed comprehensive manual.
3. Further features and polish: foreground brightness, tactile console, service-owned
   mic/mixing, HDR request path, default-source startup, sheet/glyph/contrast fixes.

Several major UX replacements preceded the Grok continuation. This is not evidence
that one model alone caused the drift. Preserve useful source work and assess actual
workflows instead of assigning success or failure by author or review score.

## Work still on the map

These are pending verification/design areas, not a queue authorized for autonomous execution.

- Ordinary playback capture plus microphone mixing on ASUS: provisional first
  technical unit, subject to the recovery priorities we agree next.
- Microphone route/accessory handling, two distinguishable mixed signals, mic-off
  isolation, failure recovery and no speaker feedback.
- Settings ordering, easier controls and coherent drawer gestures.
- Color selection, full six-slot editing/cycling and archive round trips.
- Theme preview/apply/save/reset correctness and preservation of existing looks.
- Instrument preset workflows, distinct from appearance presets.
- Manual navigation, basic content and hidden deeper content.
- HOLD/BLACK, inspection, return-to-live and app/PiP/HUD transfer behavior.
- Startup/recreation, accessibility, large fonts and reduced motion.
- Genuine HDR remains unproven; an HDR request and SDR fallback are not HDR acceptance.
- Original B1-B21 obligations remain visible: metadata/transport truth, seeking,
  folder playback, relay, dead zones, usable sliders, brightness/grid/auto-gain,
  controls/PiP/double-tap options, settings survival and task-removal behavior.
  Later expansion does not erase their incomplete final acceptance.

Root audio is deferred. The S25 research proved controlled 16 kHz mono capture,
not original stereo or physical audibility. The stereo trial stopped at a monitor
queue guard; a startup-buffer correction remains untested. ASUS has no root-audio
success receipt. Root is not a prerequisite for ordinary standard capture/mic mixing.

## Proposed recovery approach, not yet a decision

Keep the latest useful source capabilities and storage. Recover the older colorful,
direct controls within the sections Ben likes. Separate everyday controls/help from
advanced editing and developer material. Verify complete gestures and tasks, not
only component screenshots or tests.

Alternatives remain open: a narrow set of repairs to current UI, a broader return
to the S25 visual arrangement with selected additions, or a basic/advanced split.
No reset, rollback, feature removal or new interface architecture is approved here.

## Questions for Ben

1. What did today's other user try to do? Where did they hesitate, get stuck, or
   misunderstand the app? What did they like?
2. Which five settings do you personally change most often? Which belong directly
   on the scope/quick controls, and which should lead the main drawer?
3. Which parts of the S25 look/interaction are essential to preserve, and which ASUS
   improvements should definitely stay? Colored swatches and expanded sections are
   already recorded; identify the remaining anchors.
4. Should a downward swipe inside scrolled content first return to the top, while
   dragging the header can always dismiss? How should intentional dismissal feel?
5. For appearance, should tapping a theme change it immediately with Undo, or show
   a preview before Apply? What should happen when closing an unapplied editor?
6. What are the three things a newcomer should learn from the normal manual? Should
   deeper help be hidden entirely or reachable through a quiet advanced entry?
7. Should the developer unlock survive restarts, and what belongs behind it besides
   the deeper manual? Do not assume every advanced user control should be secret.
8. What must a complete playback-plus-mic experience let someone hear, see and
   control? Confirm that mixing is for visualization, not microphone speaker output.
9. Is the first recovery milestone audio completion, or restoring the main daily
   controls before testing audio? The earlier audio-first choice remains provisional.

## Session boundaries and next deliverable

Today: recover facts, preserve feedback, ask questions and agree direction.
Later: Ben requests a self-contained implementation prompt using those decisions.

Do not build, install, reset, publish, change phone settings, resume the cancelled
soak, or execute the entire backlog from this document. Preserve the S25 reference.
No new capture or root experiment follows from this brief.

Primary plan/history sources:
- `../MOBILE-EXPANSION-PLAN.md`
- `../PRE-V2-B1-B21-EXECUTION-PLAN.md`
- `../GPT6-REVIEW-LATER.md` (historical interruption, not current state by itself)
- `FEEDBACK.md` recovery section and `ASKS.md`
- `plans/mobile-expansion/EXECUTION.md` and dated checkpoints

This brief consolidates the current discussion. It does not replace the canonical
product specs, erase old receipts, or declare untested work complete.


## Chapel field feedback and Ben's answers, 2026-09-12

Source: Ben's direct follow-up in this recovery session. ASUS is the tested build.
The following raw report is preserved verbatim. Its uncertain wording stays uncertain.

### Raw report supplied by Ben

# Chapel Sep 12th 2026 app feedback
- Need 20x zoom for seeing bird sounds, maybe even more zoom than that
- Using the Mic was they *key feature*, seeing the harmonica and piano, flute 🪈, all of it, voice as well. So "mesmerizing"
- Said 3 scope in one (audio frequency ranges independent) could be an interesting idea, maybe with different colors on each one
- "it's like another way to hear things" (something like that was said)
- Maybe an interactive tutorial would be helpful, and a visual representation of the data
- Auto gain definitely needs work
- **Chapel naturally went for a two finger zoom, that needs to be a first class usability feature**
- Glitch/bug when playing Spotify and a song from YouTube, 2 media players conflict, and the play/pause button has weird behavior (wouldn't play, would quickly pause, play/pause symbol was wrong when it did(I think))
- Should have a recommended external mic, left and right.
- Need music picked out for demo
- **RECORDING**. needs to be a thing, non intrusive, maybe a little red dot in the corner when on
- Still have the brightness bug, when messing around in settings/window selecting phosphor again it has the screen dim until I tap
- App closed, possibly due to screen automatically turning off

### Ben's frequently used actions

1. Source selection.
2. Permission prompt to allow capture.
3. Zooming in/out: two-finger pinch is first-class, alongside single-finger zoom.
4. Play/pause and track navigation. These glitched when Spotify was paused and
   YouTube was also playing.

These are everyday tasks, not merely settings to move upward in a long drawer.
The design still needs to decide their quick-control placement and source-specific
availability. Permission access must remain understandable without prompting for
microphone, projection or other access unrelated to the selected task.

### What feels like home on the S25

Ben likes uniform buttons in aligned, easy-to-tap spaces. Controls feel orderly,
legible and easy to operate. A little more color/vector energy is welcome, but it
must not cost alignment or usability. He particularly likes the Glass theme and
visible colors in the scope Light section. Preserve these as recovery anchors,
not permission to replace the complete interface again.

### Consolidated interpretation and issue map

This is planning interpretation, not new implementation authority.

**Product center supported by the field report:** the built-in microphone is a
major experience. Live instruments, voices and birds make Phosphor an immediate
way to explore sound, not only a display for recorded music. The approximate
"another way to hear things" quote is a strong direction cue, not an exact transcript.

**Reported defects to reproduce:**
- Automatic gain is unsatisfactory. Related to B20, but the exact current failure
  (too small, unstable, too slow, noise amplification, or another effect) needs description.
- Competing Spotify/YouTube sessions produce unreliable transport and possibly
  incorrect symbols. Related to B6/B7/B8 transport truth; do not assume the old root cause.
- Brightness stays dim after settings/window return until a tap. Repeat of the B5
  symptom family; distinguish beam luminance, window brightness and system dimming.
- App closure may follow screen timeout. Related to B11/B21 lifecycle concerns,
  but crash, display-off, task removal and service stop are not yet distinguished.

**Usability priorities:**
- First-class two-finger pinch plus usable single-finger zoom.
- Greater enlargement for quiet/small signals, with 20x or more requested. Clarify
  signal gain versus inspection magnification versus time/frequency scale before
  selecting a mechanism or changing numeric limits.
- Quick, orderly access to source, relevant consent and truthful transport.
- Aligned uniform touch areas, legible controls, restrained vector/color energy.
- Preserve Glass and restore visible color selection.

**New proposals requiring scope decisions:**
- Recording: Ben now explicitly requests it for planning. Define audio, scope
  video, both, still images or another output; source coverage, privacy, storage,
  export and background behavior before implementation. A small red indicator is
  suggested, not the complete recording interaction. Earlier export exclusions
  cannot be treated as resolved until the new scope is agreed and specs updated.
- Three independently frequency-ranged scopes with different colors: exploratory
  idea, not an approved three-renderer design or immediate build requirement.
- Interactive tutorial and visual explanations of data: discuss a small optional
  learn-by-doing path alongside the simpler manual.
- Recommend an external left/right microphone: clarify genuine stereo, device
  compatibility, physical use and budget before researching or buying anything.
- Curate demo music: decide signal/instrument examples and usage/distribution rights.

### Next questions, small first batch

1. Recording: save the sound, a video of the moving scope with sound, or both?
2. Bird-sound zoom: enlarge the quiet trace, reveal a smaller time slice, isolate a
   frequency range, or some combination? What happened when existing zoom ran out?
3. Auto-gain: did the trace stay too small, jump around, react too slowly, or magnify
   background noise between sounds?

Later questions: when Spotify is paused and YouTube plays, should transport follow
YouTube automatically or remain pinned to an explicitly selected player? How should
that ownership be visible? What exactly should single-finger zoom do, and how should
it coexist with drawers and pinch? Keep source identity and available controls truthful.

No implementation order is finalized by this update. The field report strengthens
standalone microphone usability as a priority; playback-plus-mic mixing remains a
separate capability, not a substitute for getting the microphone experience right.


## Recording and gain clarification, 2026-09-12

Ben answered the first three field-feedback questions:

- Recording means **audio only**, not scope video. Phosphor can recreate visuals
  by playing the captured audio. No exact replay of historical tuning, colors or
  timing is promised by audio storage alone.
- Use an efficient compressed format. For this capture-format decision, assume
  a modern phone made within the last three years and investigate actual codec
  and hardware-encoder support. No codec, bitrate, container or encoder has been
  selected or measured yet. This assumption does not silently change the app's
  existing Android compatibility floor.
- Add a dedicated **Captures** entry in Sources, below file/folder playback choices.
  A capture folder opened through a browser is acceptable; a separate elaborate
  library is not required. Exact location and management workflow remain open.
- The requested 20x-or-greater zoom addresses a trace that is **simply too small**.
  It is not presently a request for frequency separation or a shorter time window.
- Auto-gain stays too zoomed out. Quieter/subtler sounds still show structure, but
  auto-gain does not enlarge them enough. This is Ben's observed symptom, not yet
  evidence of the cause. Existing gain limits, quiet-signal thresholds, noise
  handling and gain adaptation are investigation candidates, not diagnosed faults.

Separate visualization gain from saved audio level. Proposed default for discussion:
record source audio without applying scope zoom/auto-gain. This is not yet a ratified
recording contract. Quiet-signal enlargement must not be mistaken for recovered audio
information or implemented by silently modifying recordings.

### Next recording and interaction decisions

1. Does first-version recording cover microphone only, or also standard playback
   capture and playback-plus-mic? Existing local-file and relay recording remain
   unspecified; do not add them by assumption.
2. Should recording continue with the screen off or while another app is visible,
   with a clear recording notification and Stop action?
3. When Ben pinches while auto-gain is enabled, should the gesture take manual
   control, or change the desired framing while auto-gain remains active?

Codec selection follows the agreed source/channel/quality needs and on-device
capability checks. No build, capture experiment or implementation occurred here.


## Recording controls and remembered auto framing, 2026-09-12

Ben's latest decisions supersede the open mic-only and pinch-ownership questions.
This remains planning, not implementation authorization.

### Recording

- Record **whatever source is currently selected**. Cover all supported source
  kinds, rather than a microphone-only first version. This includes local/relay
  playback and standard playback-plus-microphone when selected. Deferred root
  capture remains unavailable; recording does not remove platform capture exclusions.
- Make recording a simple, source-associated toggle. Exact placement/wording
  needs a layout decision; do not create a separate competing audio source just
  to record the current source.
- Store recordings in their own folder and expose Captures in Sources. Files
  must be easily playable through a file browser. Avoid an unnecessary custom library.
- If a pre-record popup is necessary, give estimated file sizes and a
  "don't show next time" checkbox. A popup is conditional, not mandatory on every start.
  Estimates depend on the selected encoding and duration; distinguish estimates
  from the actual growing file size.
- Default: stop recording when the screen turns off.
- Provide a nearby "keep recording when screen is off" checkbox.
- Recording continues while Phosphor is presented in the floating HUD or PiP.
  Both HUD and PiP must be quickly toggleable from quick settings.
- Provide a user-settable custom file-size limit with mobile-friendly controls.
- Show a red text warning as the limit approaches, integrated with normal HUD
  information rather than an intrusive interruption. Threshold and exact units
  remain undecided. Meaning must remain readable, not conveyed by color alone.
- At-limit behavior remains a question: stop and finalize, or split into another
  file? Source changes during recording also need a clear session/file rule.
- Clarify whether HUD/PiP continuation refers to screen-on background presentation
  only. Until resolved, do not infer that an invisible HUD/PiP overrides the
  screen-off checkbox or that all background audio must continue.

### Auto-gain and zoom

- With auto-gain enabled, pinch adjusts **preferred visual framing**, rather
  than switching auto-gain off and taking manual control.
- Preferred framing survives app restarts.
- This deliberately changes the earlier manual-takeover gesture contract.
  Preserve manual gain as a separate explicit behavior when auto-gain is off.
- Greater magnification must serve quiet visible structure, including birds and
  subtle instruments, not just increase a label or numeric maximum.
- Single-finger zoom remains first-class. Whether it adjusts the same preferred
  framing value should be confirmed, not silently given a different meaning.
- Define limits, default/reset, silence/noise handling, adaptation and loud-transient
  behavior before implementation. Do not prescribe a DSP formula from the symptom alone.
- Proposed recording-level separation remains unconfirmed: scope framing should
  not silently alter captured audio amplitude.

### Next small clarification batch

1. At the chosen size limit, stop and save or continue into another file?
2. On source change, close the old recording and stop, or automatically start a
   new recording for the newly selected source?
3. Confirm that visible HUD/PiP keeps recording with the screen on, but actual
   screen-off still obeys the nearby checkbox.

Implementation prompt and architecture choices remain for a later session after
Ben and Prime finish refining the direction.


## Recording limits, source changes and control hierarchy, 2026-09-12

Ben resolved the recording lifecycle questions:

- Offer both size-limit actions: **stop and save**, or **start another file**.
  These are mutually exclusive choices, not independent checkboxes. Ben explicitly
  requires that incompatible settings cannot be selected together. Suggested
  presentation: one labeled two-option selector; exact visual design is still open.
- The default size-limit action has not been explicitly selected. Prime proposes
  stop and save as the predictable default; confirm before compiling the final spec.
- On source switch, **stop and save by default**.
- Provide an optional **continue REC on source switch** toggle.
- HUD/PiP recording continues while visibly presented with the screen on.
- Actual screen-off always follows the screen-off recording checkbox. Merely
  retaining a HUD/PiP service or mode flag does not override this setting.

### Normal first, deeper by exploration

Ben's design requirement: default recording behavior takes visual priority over
settings below/to the side. A normal user should understand the instrument quickly,
while deeper choices become available through longer exploration.

Proposed hierarchy, not a finished layout:
1. Clear Record/Stop control and current source, recording status and elapsed time.
2. Actual file size and approaching-limit warning in normal HUD information.
3. Nearby screen-off option, as already requested.
4. Secondary recording options for custom size limit, one mutually exclusive
   at-limit action, and continue-on-source-switch.

Do not hide current recording status, chosen source or an approaching limit behind
advanced settings. Do not confuse the developer-manual unlock with access to normal
recording options. Exact continuation behavior (one file spanning source changes
versus a new file without another Record tap), limit defaults, units and warning
threshold remain to be settled. All work remains planning only.


### Source-switch continuation file identity, 2026-09-12

Ben confirms: with **Continue recording on source switch** enabled, keep recording
into the **same file**. Do not automatically create a new file for the new source.
With the toggle off, source switch still stops and saves by default. The separate
size-limit action remains authoritative; this option does not bypass the limit.
Planning only; no recording implementation has started.


## Convergence decisions, 2026-09-12

Ben accepts Prime's compass: recover familiarity, make ownership truthful, welcome
subtle sound, and preserve what people discover.

### Latest decisions

- Media transport follows the **current player**, rather than requiring a pinned
  player. In context this is the currently audible player. Multiple simultaneous
  audible sessions and handoff timing require an explicit, tested selection policy;
  do not infer that the most recently created or paused session owns transport.
- Ben delegates auto-framing tuning to Prime. Prefer following faint visible
  structure if the automatic zoom can keep up, with a little breathing room rather
  than tightly filling/clipping the viewport. Responsiveness, silence/noise handling
  and sudden loud input require real microphone tests, not a guessed formula.
- Scope zoom/auto-framing changes the picture only. Recordings preserve selected
  audio as faithfully as possible, without visualization gain applied to them.
  Efficient compression remains requested; do not call a lossy encoder lossless.
  Recording a selected mix still needs the agreed source mix, not an unrelated
  amplified visual buffer. Define its level/clipping behavior before encoding.

### Proposed execution structure discussed with Ben

One recovery milestone, two internal steps:

1. Trustworthy, familiar instrument: colorful/aligned controls, accessible source
   and transport, drawers/manual Back, theme correctness, remembered auto-framing,
   quiet-signal usability, current-player controls, dimming/screen-off diagnosis,
   standard capture-plus-mic completion.
2. Keep a moment: all-supported-source audio recording, Captures folder/replay,
   agreed screen/source/limit policies, interruption/storage/encoder recovery.

Root, genuine HDR, three filtered scopes, elaborate further animation and a full
interactive tutorial remain later candidates. A short listening guide and bounded
sample/demo material can support recovery tests without enlarging the UI rewrite.
This is a proposed cut to compile into the later handoff, not authority to start it.

Prime proposed parallel bounded investigation of audio and interaction, followed
by independent requirement-linked review. One writer per shared file; Prime owns
integration, exact build identity and ASUS operations. No worker autonomously
redesigns an adjacent feature. Ben receives short build test cards naming changes,
three tasks to try, known issues and exact build identity. He need not manage the
implementation graph or remember the backlog.

### Next planning deliverable

Consolidate the accumulated decisions into one implementation-ready recovery brief
and a self-contained next-session prompt when Ben requests it. Resolve conservative
engineering defaults explicitly in that brief instead of repeatedly asking Ben to
choose internal algorithms. Separate ratified behavior, delegated tuning, proposed
defaults and still-open user choices. No build or device mutation is authorized by
this planning checkpoint.
