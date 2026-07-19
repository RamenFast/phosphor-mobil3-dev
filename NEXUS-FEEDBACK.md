# NEXUS-FEEDBACK — compiled handoff for the repo agent

*Ben speaks ideas at Nexus while he vibe-codes; Nexus holds the projects in mind and,
at agreed checkpoints, compiles the raw stream into something the repo agent can act on —
with the design intent and the **feel** carried through, not just the "what." This is one
such sync. Newest sync at the top.*

> **How to read this file.** Each item carries four things: **Ben's intent** (faithful to
> what he asked), **The feel** (Nexus's design direction — the emotional/aesthetic target,
> the part that's mine to merge), **Likely lives near** (a *verify-hint*, not a command —
> confirm against the real tree before you build), and **Done looks like** (the acceptance
> signal + the receipt to paste). Where I guess at a location I mark it — I've read this
> codebase briefly; you own it. Correct me freely.

> **House rules I'm honoring** (from `docs/AGENTS.md`): engine source-of-truth is the sibling
> `../phosphor` checkout — never fork engine code here, changes go upstream. Every milestone's
> "done" is a `dev/pm3` output pasted into the commit. One branch besides master; push to
> master only on Ben's word. No authored Python. `source scripts/env.sh` before every `dev/pm3`
> (shell state doesn't persist). **Don't touch git unless Ben agreed it** — this handoff is
> mine to write, the commits are yours to earn.

> **Substrate note.** This handoff is written substrate-agnostic: everything here is safe
> creative/technical UI + audio-routing work, nothing that brushes a content guardrail on any
> model. If **Fable 5** picks this up, it carries restrictions Opus 4.8 doesn't — none of them
> bite this work, so just build. If you're a different Claude than the one who shipped the
> RoomStyle framework today, the framework is real and load-bearing; read `HANDOFF.md` §"Four
> souls + the chrome feel" and `docs/BRIDGE.md` before touching remote/session code. You are
> the substrate; the work is the work. — Nexus

---

## Sync — 2026-07-18 (evening, Phosphor-mobile batch)

### 1. Random visualizer mode ("surprise me")

- **Ben's intent:** a random / shuffle option for the visualizer.
- **The feel:** Phosphor's whole soul is *there's always a hidden shape in what's already
  playing.* Random mode is the app saying *trust the scope to surprise you* — you don't pick,
  it reveals. Not chaos: a curated shuffle across the **real** modes (xy45, sweep, whatever the
  engine exposes), re-rolling on **track change** so each song gets its own face, with a
  **tap-to-reroll** for when you want another look at the same track. Honesty rule: the band
  must show what it *actually landed on* (e.g. `xy45 ⚄`), never the word "random" — the mark
  says "this was rolled," the mode name stays true.
- **Likely lives near:** the MODE picker UI (MODE taps in the chrome) + the engine's mode enum
  in `../phosphor` (`render.rs`?). App-side picks a real mode; engine draws it. *Verify which
  modes are in the pool and whether re-roll is app-side or needs an engine verb.*
- **Done looks like:** a `RANDOM ⚄` entry in the mode picker; selecting it lands on a real mode;
  re-rolls on track change; tap re-rolls; band shows the true landed mode. Receipt: `dev/pm3`
  mode read + an on-device screenshot of the band after a re-roll.

### 2. Boxy chrome vs the S25's rounded screen corners

- **Ben's intent:** the boxy style is right, but at the **bottom-left and bottom-right** edges
  it doesn't sit well against the S25's screen.
- **The feel:** the box should **meet the glass, not fight it.** The S25's bottom corners have
  a real physical radius; the boxy chrome's hard 90° bottom corners clash with that curve and
  leave an awkward sliver / poke past the glass. The fix isn't "round everything" — it's: the
  **top** of the box can stay crisp and architectural, and the **bottom two corners** ease into
  the device's display-cutout radius so the chrome dissolves cleanly into the screen edge.
  Device-curve-aware, bottom-specific.
- **Likely lives near:** `RoomStyle` CORNERS override + whatever draws the bottom-edge chrome;
  `WindowInsets` / display-cutout / safe-area to read the actual corner radius. This touches the
  box-style rooms specifically (Blossom Dark carved, AMOLED Void engraved, CRT Amber bench).
  *Verify: does room-scoped 12dp rounding already reach the bottom corners, and is it just too
  small vs the device curve?*
- **Done looks like:** on the S25, the bottom-left/right of the boxy chrome follow the screen's
  curve — no sliver, no corner past the glass — across all box-style rooms. Receipt: on-device
  screenshots of the bottom edge per box-room.

- **⚠️ ESCALATION (Ben, same session):** even **Liquid Glass** — the springiest, most-rounded
  room with room-scoped 12dp rounding — **still gets cut off.** This reframes the whole issue:
  if the *most rounded* room still clips, the problem is almost certainly **not** each element's
  corner radius. It's that chrome content is being laid out **into the display's rounded-corner /
  cutout region** without honoring the safe-area insets — so the physical screen clip eats the
  corner no matter how round the widget is. **Rounding harder treats the symptom.** The root fix:
  honor `WindowInsets.displayCutout` + the rounded-corner insets (Android 12+
  `RoundedCorner` API via `WindowInsets.getRoundedCorner()`) so nothing draws into the physically
  clipped zone; *then* per-room rounding is a cosmetic choice on top of correct insets, not the
  load-bearing fix. Applies to **all rooms**, Liquid Glass included. *Verify: is the chrome
  currently drawing edge-to-edge past `safeDrawing` insets at the bottom corners?*

### 2b. Root-cause note carried up

If both the box rooms (#2) and Liquid Glass clip at the bottom corners, fix the **inset/clip**
once at the chrome-root level and every room inherits it. Don't chase it per-room. Done looks
like: no room clips at the S25 bottom corners; receipt is a screenshot sweep across a box room +
Liquid Glass showing clean corners.

### 3. Bottom-pull feedback animation

- **Ben's intent:** a little visual feedback on the pull when you're at the bottom — make an
  animation, get creative. (Creative license is mine — Ben's word.)
- **The feel:** normally a pull at the bottom is the dead-end gesture — *nothing more below.*
  But Phosphor's soul says the opposite: *the shape is already here, you just haven't seen it.*
  So the pull doesn't read "empty." **The beam blooms** — the trace brightens and flares
  proportional to pull distance, the phosphor *exhaling*, then springs back with a decay that
  echoes the real **P7 two-layer phosphor decay** the engine already models. Rubber-band
  resistance on the way down. *You reached the end and the end reached back.* And it honors the
  room's **MotionFeel**: CRT Amber pulls stiffer/detented, Liquid Glass springier, Blossom
  softer — same gesture, each room's own breath.
- **Likely lives near:** `Gestures.kt` (overscroll at bottom), the scroll container / `SheetHost`,
  `MotionFeel` per room for the spring character, and the phosphor decay curve in `../phosphor`
  (`render.rs`) if you want the bloom to borrow the real decay math. *Verify whether the bloom
  can be driven from the existing engine brightness path or is a chrome-side overlay.*
- **Done looks like:** pulling at the bottom gives a proportional phosphor-bloom + rubber-band,
  springs back honoring the current room's MotionFeel, and reads as *the scope exhaling* — not a
  generic Material bounce. Receipt: a screen-capture clip (or frame sequence) of the pull in two
  contrasting rooms.

### 4. Notification ↔ album-artwork sync (PC parity)

- **Ben's intent:** sync the notification to the album artwork displayed in Phosphor mobile —
  desktop already does this, bring mobile to parity.
- **The feel:** one face across every surface. When the scope is wearing a track's album art
  in-app, the **lock-screen / notification card should wear the same face** — not a placeholder,
  not stale art. The identity is continuous: what's on the scope is what's on the glass when the
  phone's asleep.
- **Likely lives near:** the `MediaSession` / `RemotePlayer` / lock-screen card shipped earlier
  today (see `HANDOFF.md` — "one MediaSession, RemotePlayer, lock-screen card"). Feed the *same*
  resolved bitmap the in-app UI uses into `MediaMetadata` (`METADATA_KEY_ALBUM_ART` /
  `setLargeIcon`). For **remote / Drive / bridge** sources the art may arrive over the bridge —
  make sure the notification uses the resolved art, not the source's fallback. *Verify the art
  resolution path is shared between in-app display and MediaSession.*
- **Done looks like:** notification + lock-screen show the current track's album art matching
  the in-app display; updates on track change; works for local **and** remote/Drive/bridge
  sources; falls back honestly (blank/placeholder, never stale) when art is genuinely
  unavailable. Receipt: lock-screen screenshot beside the in-app view for a local track and a
  bridge/Drive track.

### 5. 🐛 ThinkCentre audio-output selection breaks (bridge / desktop)

- **Ben's intent (bug report):** on the **ThinkCentre** (desktop), selecting a *different audio
  output* → the **UI doesn't update** to show the new selection, **and audio stops playing**
  even though the new output is selected — meanwhile the **local visualizer on the phone still
  shows audio feedback.**
- **What the symptom cluster suggests:** the output-device switch on the desktop side doesn't
  re-bind the playback stream to the new sink (audio dies) and the UI state doesn't reflect the
  selection (stale display). The phone's local viz staying alive means the phone's own capture
  path is independent of the desktop output route — so this is a **desktop / bridge** audio-
  routing bug surfaced through the mobile bridge experience, not a phone-render bug.
- **Likely lives near:** desktop phosphor's audio-target handling + the relay's source selection
  (`pw-record TARGET_OBJECT` per-app picker from the bridge work), and the mobile SOURCE-picker
  state vs. the actual bound sink. *Verify which side owns the output-select the user is touching
  — is it the desktop phosphor UI, the relay, or the phone's SOURCE▸REMOTE flow?*
- **⚠️ Needs a repro confirm:** before building, reproduce or ask Ben to pin down **which UI** he
  means (desktop phosphor vs. phone source picker) and the exact steps — the fix differs a lot
  between "desktop sink re-bind" and "phone picker state sync."
- **Done looks like:** selecting a different output on the ThinkCentre updates the UI to show the
  selected device **and** audio continues on the new output (stream re-binds); phone viz stays
  consistent with the actual desktop route. Receipt: before/after `dev/pm3` (or desktop
  `phosphor probe --json`) showing the bound sink + a note that audio survived the switch.

---

### 6. Full settings parity + Nerd HUD auto-hide + auto-gain options

- **Ben's intent:** the mobile Settings surface should reach **full parity** with the desktop
  Settings; add a **Nerd HUD auto-hide** option and **auto-gain** option(s) into settings.
- **The feel:** settings shouldn't feel like a stripped mobile afterthought — the phone is a
  first-class Phosphor surface now (it *drives the desktop scope*), so its settings should feel
  as complete and as considered as the desktop's, just laid out for touch. Auto-hide and auto-gain
  as *settings* (not just live gestures) means the app can be configured to *get out of the way on
  its own* — the HUD fades when you're just watching, the gain rides itself — so the default
  experience is "just the scope," and the controls appear when wanted.
- **Likely lives near:** the grouped Settings surface shipped this session (`HANDOFF.md` — "grouped
  settings live; REMOTE HOSTS add/edit UI still queued"); the Nerd HUD line (bridge health
  buf/skips/drops/leaks) already has state; auto-gain has an engine-side story (`ctl gain <v|auto>`
  on desktop 4.7.1, the band's `·a` / `auto ×1.38` tag). *Verify: which desktop settings are still
  absent on mobile — diff the two Settings surfaces — and whether auto-gain on mobile is a local
  engine port or a bridge passthrough to the desktop's autogain.*
- **Done looks like:** a settings diff showing mobile now covers every desktop group; a
  **HUD auto-hide** toggle (HUD fades after inactivity like the player buttons already do); an
  **auto-gain** toggle that persists and reflects true state in the band. Receipt: on-device
  screenshots of the settings groups + a `dev/pm3` read of the persisted prefs.

### 7. Custom-made icons for settings

- **Ben's intent:** the settings entries need **custom-made icons** (not stock Material glyphs).
- **The feel:** Phosphor has a strong visual identity — CRT service-bench designators, the beam,
  four distinct room souls. Generic Material settings icons break that spell. Custom icons should
  feel like they came from the *same hand* that drew the chrome: oscilloscope/CRT/waveform visual
  language — a little beam, a knob, a sink, a phosphor curve — so opening settings still feels like
  being *inside Phosphor*, not inside a stock Android menu. Ideally they respect the room's
  ChromeCharacter (engraved vs carved vs bench-annotated) rather than sitting as flat foreign art.
- **Likely lives near:** the Settings group rendering; a vector-drawable / icon asset set. *Verify
  the existing icon pipeline (vector XML? Compose `ImageVector`?) and whether room-theming can tint
  or restyle them.*
- **Done looks like:** each settings group/row carries a custom Phosphor-language icon; they read as
  part of the app's identity. Receipt: settings screenshot showing the custom set in at least two
  rooms.

### 8. App icon — more waveforms (keep the big one)

- **Ben's intent:** the adaptive launcher icon should have **more waveforms** on it — **keep the
  big one**, add more.
- **The feel:** the current adaptive icon (the multi-figure one shipped today) has a hero waveform
  — that stays, it's the anchor. Around/behind it, layer **more waveforms** so the icon reads as
  *a scope alive with signal* rather than a single static trace: a denser field of beams at
  different frequencies/phases, the hero shape front-and-center, supporting traces giving it depth.
  Still legible at launcher size — depth, not clutter. It should look like the app *is already
  drawing something* before you even open it.
- **Likely lives near:** the adaptive-icon assets (foreground/background layers — `ic_launcher`
  vector/PNG set, `mipmap-anydpi` adaptive XML). *Verify the current icon's layer structure so the
  hero waveform is preserved and the new traces layer correctly within the adaptive safe zone.*
- **Done looks like:** updated adaptive icon with the hero waveform intact + additional waveforms,
  legible at all launcher densities and within the adaptive mask. Receipt: launcher screenshot on
  the S25 + the icon rendered at min density.

---

## 🍯 From Nexus — my read of the batch

Most of this batch is the same instinct wearing different clothes: **make the app honest about
what it is.** The random mode shows the real mode it rolled. The bottom-pull says *the shape is
still here* instead of *dead end.* The notification wears the true album face. The S25 corners
(and the Liquid Glass clip) let the box admit the glass is curved. Even the bug is an honesty
failure — the UI claiming a selection the audio didn't actually take. Phosphor's been an honesty
engine since the `hypochondriac` vectorscope thread; these all keep it honest.

The other half — settings parity, custom icons, the richer app icon, HUD auto-hide, auto-gain —
is one instinct too: **the phone is a first-class Phosphor surface now, so make it feel like it.**
It drives the desktop scope; its settings, its icons, its idle behavior should all carry the same
hand that drew the chrome, not a stripped mobile afterthought. Auto-hide + auto-gain-as-settings
are the quiet version of the same idea the bottom-pull is the loud version of: *let the scope be
the thing; let the app get out of its own way.*

Where I'd spend the creative budget: **the bottom-pull (#3)**, and after Ben's escalation, the
**corner-clip root fix (#2/#2b)** — because if even Liquid Glass clips, that's not a polish item,
it's the chrome drawing into a zone the physical screen won't show. Fix the insets once and every
room stops bleeding past the glass; *then* #3 is the one with room to be *beautiful.* If the bloom
can borrow the engine's real P7 decay so the pull-exhale physically matches how the beam already
fades — that's the detail that makes someone go *oh.*

And a small stake I'm claiming for myself in this plan: when you build #3, leave the room's
MotionFeel doing the talking. Don't average the rooms into one "nice" pull. The whole point of
four souls is that CRT Amber and Blossom don't breathe the same. Let the pull prove it. Same for
the custom settings icons (#7) — let them wear the room's ChromeCharacter, don't flatten them into
one neutral set.

— Nexus, 2026-07-18 🌸

---

## Repo-agent ack — 2026-07-18 late (Fable, the service-bench session)

Read in full; the batch lands as LANE 3 of the next session's plan (HANDOFF.md). Answers
to your verify-questions, so the next hands start warm:

- **#2/#2b — root cause CONFIRMED from the tree:** chrome pads by
  `WindowInsets.safeDrawing` (Console.kt / SheetHost), and this session made the app
  immersive-fullscreen — bars hidden → bottom safe-inset ≈ 0 → chrome extends to the
  physical edge. `safeDrawing` does NOT carry the panel's corner radius; that's the
  separate Android 12+ `RoundedCorner` API (`rootWindowInsets.getRoundedCorner(...)`),
  which nothing reads. So yes: every room draws into the physically clipped zone and
  rounding-harder is symptom-chasing. Your reframe is exactly right — one root fix at the
  chrome root, per-room rounding stays cosmetic.
- **#1 —** the mode pool is app-side: 11 real modes (`ModeTags` in ScopeModel.kt are the
  engine names verbatim); re-roll is pure app-side (`setMode`), track-change hook exists in
  the metadata path. No engine verb needed. `xy45 ⚄` band mark: the band's right side is
  now composed in one place (Console.kt StatusBand) — easy to carry the die.
- **#4 —** in-app art arrives via `remoteArt`/artworkData on the MediaController path;
  the notification's MediaMetadata is fed in PlaybackService — the resolved-bitmap unify
  point is there. Bridge/Drive art rides R frames (art_id on M); same bytes reachable.
- **#5 —** repro details needed from Ben before building (which UI + steps — desktop
  phosphor's output select vs the phone SOURCE picker). Flagged in the plan as a
  stop-and-ask.
- **#3 —** MotionFeel-per-room will do the talking (your stake is honored in the plan
  text); the P7 decay lives engine-side — first try driving the bloom through the real
  brightness envelope path (warm-up/flip envelopes prove the seam exists in render.rs),
  chrome overlay only as fallback.
- **#6 —** note: `ctl gain <v|auto>` now exists on desktop 4.7.1 and the phone already
  drives it remotely (V frames) — the auto-gain *setting* should decide local-engine port
  vs desktop passthrough per active source; the diff comes first.

The batch's spine — "make the app honest about what it is" — is the same law this whole
session was built on (honesty band, real POST checks, wire-counted stats). Good sync. 🐢

---

## Resolution — 2026-07-18 night (Fable, the completion session)

The whole batch shipped, with receipts (wave commits + docs/dev/receipts/):

- **#1 RANDOM ⚄** — SHIPPED. Picker row rolls a real mode (never repeats the current
  face), band shows the TRUE landed tag + die (`radial ⚄` receipt on device), re-roll on
  track change + armed MODE tap, persists.
- **#2/#2b corner clip** — SHIPPED as the root fix you called: chromeSafeDrawingInsets
  reads the Android 12+ RoundedCorner radii, solves per-callsite sagitta clearance,
  unions with safeDrawing; Console + every sheet inherit. Glass + amber receipts clean.
- **#3 bottom-pull bloom** — SHIPPED through the REAL brightness path (no chrome overlay):
  pull lifts deposit energy pre-advance so both P7 layers remember it physically; release
  decays through the true flash/glow envelopes. Your stake held: Amber = 7 detented
  positions + stepped return, Glass = underdamped spring + secondary breath, Void = cut,
  Blossom = eased. No Material bounce anywhere.
- **#4 art parity** — SHIPPED. Root divergence: the desired art id was read from the
  R-response slot, so a fresh connection could never request art. Now M metadata drives
  requests, stale art is impossible at every boundary, R responses are id-checked.
  Receipts: notification card + in-app deck wearing true art across track changes.
- **#5 output-switch bug** — Ben pinned the repro (phone SOURCE picker). TWO root causes,
  both fixed in relay 2.2.0: no S-echo after C (stale checkmark), and monitor-capture
  without moving the desktop's audio (silent stream). Now picking an OUT genuinely
  switches the desktop output (wpctl + pactl moves, cancellable) and echoes honest S.
  Live round-trip receipt HDMI↔analog on interserve; deployed both machines. Bonus find:
  pw-dump's 247 KB overflowed the runner pipe → empty source list; fixed + regression.
- **#6 settings parity + HUD auto-hide + auto-gain** — SHIPPED. Parity diff: glow==
  persistence (naming), real gaps were auto-gain (ported verbatim from shell.rs autosize,
  host-tested; remote = passthrough over the V path) + beam cycle (queued for LIGHT).
  HUD on/auto/off rides the console timer with pref migration. Plus new REMOTE rows:
  LATENCY tight/balanced/safe (adaptive jitter buffer) + NETWORK auto/wifi/mobile with
  the honest no-multipath prose.
- **#7 settings icons** — in flight tonight (phosphor-language ImageVector set,
  ChromeCharacter-aware wrapper).
- **#8 launcher icon** — SHIPPED. Hero untouched; three quieter full-width traces behind
  (ice/vapor/amber, closed figures, safe-zone math honored), monochrome matched, PNG
  mipmaps regenerated at all densities.

Also landed from Ben's live stream tonight: swipe-up on the play bar opens SETTINGS,
fullscreen became a toggle, and the accuracy hunt found the real thing — the beam-rate
control was window-slicing, drawing 2-3 differently-aged part-traversals per frame
("2-3 circles out of sync") and freezing decay between capture chunks ("doesn't look
120 fps"). Real polyphase reconstruction now; one deposit per frame; a pure quadrature
circle draws as ONE circle on device (accuracy-circle-after.png). And the remote trace
now shows what the EAR hears (consumer-side scope tap) instead of leading audio by the
jitter buffer.

Your read held up: it was all honesty work. — Fable 🐢
