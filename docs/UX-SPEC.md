# phosphor-mobil3 — Mobile UX Spec

**Target:** Samsung Galaxy S25 · 6.2" 120 Hz LTPO AMOLED · 2340×1080 · centered punch-hole · Android 15/16 One UI
**Thesis:** The scope is the app. Every pixel of chrome is a guest in the scope's house — summoned, translucent, sharp-cornered, and quick to leave. Light on black; the S25's OLED *is* the phosphor.

---

## 0. Design-language bindings (from ben-ui-design + desktop theme.rs)

- Sharp corners everywhere (`radius: 0`). Hairline 1 px `line`/`line_strong` frames. No pills, no info boxes.
- Mono face for all data (mode names, ×gain, Hz, timestamps, fps); humanist face only for prose (consent copy, gentle notes).
- Dimensional hierarchy: **exactly three carved-stone controls exist in the whole app** — the play/pause key, the LIVE capture key, and the theme stone in settings. Everything else is flat, hairline-framed. Depth = importance.
- All chrome reads the `Palette` token table (plane/surface/ink/line/accent/stone triple) ported verbatim from desktop `theme.rs`. All 12 rooms ship. **Default room: Blossom Dark.** AMOLED (true `#000`) is surfaced as the recommended room on first run ("this panel was built for it" — one gentle line, not a nag). `accent_follows_beam` rooms (Blossom Dark, Afterglow) breathe with the live beam on mobile too.
- Sheets and popouts are Obsidian-persistent: they never vanish because a finger drifted; they close on ✕, scrim tap (click-away), drag-down, or system Back.
- Animations: 80–200 ms, eased, purposeful. Honor Android's reduced-motion (remove-animations accessibility setting).

---

## 1. Navigation model

### 1.1 One activity, four layers, five states

There are no "screens." There is one full-bleed scope stage and layers that visit it:

```
Layer 3  SYSTEM      Android dialogs (capture consent, share sheet, permissions)
Layer 2  SHEETS      bottom sheets & anchored popouts (translucent; scope live behind)
Layer 1  CONSOLE     transient chrome bands (top status, bottom console strip)
Layer 0  STAGE       the scope, edge-to-edge, under cutout and gesture bar
```

App states: **STAGE** (chrome hidden) · **CONSOLE** (chrome visible, auto-hides) · **SHEET** (one or more sheets/popouts open) · **COMPOSE** (drawing mode owns the surface) · **PIP** (system-owned floating scope).

### 1.2 The gesture arbiter — single-owner law

Desktop's multi-owner gesture bugs are the named debt. Mobile fixes it structurally: one `GestureArbiter` classifies each pointer sequence **once, at recognition time**, and assigns exactly one owner. No component ever "also" handles a touch. Ownership precedence, highest first:

1. **System** — screen edges (back/home) belong to Android. The app registers zero edge gestures. Full stop.
2. **Active sheet/popout** — while any Layer-2 surface is open, it owns all touches inside itself; the scrim owns everything else (tap = dismiss top surface). The scope receives **no** gestures in SHEET state.
3. **Compose** — in COMPOSE, one finger draws. Nothing else on the canvas.
4. **Console widgets** — a touch that begins on a visible console control belongs to that control.
5. **Scope stage** — everything else, resolved by the table below.

### 1.3 Stage gesture map (STAGE and CONSOLE states)

| Gesture | 2D modes (xy, xy45, swirl, dots, waveform, ring, spectrum, radial, tunnel) | 3D modes (attractor, helix) |
|---|---|---|
| 1-finger tap | toggle console | toggle console |
| 1-finger double-tap | play/pause (the beam arbiter, = desktop Space) | same |
| 1-finger long-press (450 ms, light haptic) | context popout at the finger (the right-click analog) | same |
| **1-finger drag** | **GAIN** — vertical component drives deflection scale; a mono readout ribbon (`× 1.38`) etches in beside the thumb; light haptic tick crossing unity ×1.00 | **ORBIT** — gentle sensitivity, drift resumes 4 s after release |
| 2-finger pinch | GAIN, coarse (same parameter — pinch = "bigger" everywhere) | DOLLY |
| **2-finger horizontal swipe** | **MODE STEP** prev/next through the 11 modes — works identically in every mode; medium haptic detent + mode-name toast | same |
| 2-finger vertical swipe | GLOW (persistence %), mono readout ribbon | same |

That is the entire per-mode difference: **one-finger drag means gain in 2D and orbit in 3D**, and it is declared on-screen — the first time each family is entered, a one-line mono hint fades through the bottom of the stage (`drag · gain` / `drag · orbit  pinch · dolly`) and never returns after two sightings.

Auto-gain: when auto is breathing the gain, the readout shows `× 1.12 · auto`; a manual drag takes over (auto tag fades), long-press the readout ribbon → popout offers "return to auto."

### 1.4 Reach-zone law

All *interactive* chrome lives in the bottom 40% of the portrait screen (thumb arc). The top band is **read-only status**, never a tap target. Sheets open from the bottom; popouts spawned by long-press appear at the finger but clamp their action rows toward the screen's lower half.

---

## 2. Surfaces

### 2.1 Stage + Console (scope home)

```
┌──────────────────────────────┐
│ src·spotify   ◉   xy · ×1.0a │  ← status band: mono, flanks the punch-hole,
│                              │     ink_2 at 70% alpha, read-only
│                              │
│         (the scope,          │
│        edge to edge,         │
│      under the cutout,       │
│     under the gesture bar)   │
│                              │
│ ───────── deck handle ────── │  ← 24 px hairline grab bar
│ ┌──────────────────────────┐ │
│ │ ‹Track Title — Artist›   │ │  ← console strip (surface @ 86% alpha,
│ │ ◂◂  [ ▶ ]  ▸▸    MODE SRC ⋯ │     hairline top rule)
│ └──────────────────────────┘ │
│        (gesture bar)         │
└──────────────────────────────┘
```

- **Status band** (top): splits left/right of the centered punch-hole — `src · <name>` left, `<mode> · ×<gain>` right. The punch-hole is treated as furniture, not fought. When the Nerd HUD is on, it stacks below the left status column.
- **Console strip** (bottom): the carved **play/pause stone** (the one dimensional control on this surface; press = it sinks, `carve_with_face` port), flat prev/next, title/artist in mono with a slow marquee only on overflow, and three flat mono keys: `MODE`, `SRC`, `⋯` (overflow popout: snapshot, clip, postcard, compose, kit, light, room, settings, HUD).
- **States:** console auto-hides after 4 s of no interaction (fade + 8 px settle-down, 160 ms). Tap summons it (fade-in 120 ms). It never blocks the beam — panels are the room's `surface` at ~86% alpha so the trace ghosts through.
- **A seek rule** (hairline, square thumb, mono timestamps at the ends) appears in the console strip only when the loaded deck owns the transport and the track is seekable.
- **Burn-in:** console luminance is capped (chrome never exceeds ~60% of panel max while the beam is live); when visible longer than 60 s continuously, the whole console layer pixel-shifts ±1 px on a slow walk. Status band does the same always.

### 2.2 Deck sheet (transport / now playing / queue)

Drag the deck handle up (or tap it) to open. Three detents:

- **Peek** = the console strip itself (state CONSOLE).
- **Half** — now playing: sharp-cornered hairline-framed cover art (left), title/artist/album mono stack, seek rule, transport row (carved play stone travels up from the console — it is the *same* control, one identity), cubic-taper volume rule with % readout, and a `QUEUE ▾` hierarchy link.
- **Full** — the queue/library: current folder as a gapless playlist (mono rows: index · title · duration), shuffle / repeat off·all·one as flat toggles, `OPEN…` (SAF file/folder picker), breadcrumb hierarchy `Library › <folder>` instead of any tab chrome.

**The transport law, ported:** *the loaded deck owns the transport.* When the beam scopes another app (capture), the deck sheet shows **that** player — its metadata and art from the system `MediaController`, `via Spotify` in muted mono — and the transport buttons drive that session. One source of truth; picking a capture source pauses the local deck, resuming the local deck (double-tap or play stone) takes the beam back. The sheet header always states plainly which deck is loaded: `deck · phosphor` / `deck · spotify`.

Sheet mechanics (all sheets): translucent `surface` over the live scope, hairline top rule, drag-down or scrim-tap or Back to dismiss, 200 ms decelerate ease, no bounce.

### 2.3 Source popout (`SRC`)

An anchored popout above the console key, grouped by hierarchy headings, not tabs:

```
SOURCE
──────────────────────────
MY LIBRARY
  ▸ resume last folder
  ▸ open file / folder…
OTHER APPS
  ▸ everything playing        ← playback capture, all media
MICROPHONE
  ▸ built-in mic
──────────────────────────
⏻ LIVE                        ← carved stone toggle; off = capture torn
                                 down, ~0% battery
```

- Picking a source starts scoping immediately (desktop law). The row wearing the checkmark is **what actually feeds the beam** — never an aspiration.
- Silent source past the sleep window → the stage says `no signal · <source>` in quiet mono center-low, never a black mystery.
- **The consent moment** (first "Other apps" pick): before the system MediaProjection dialog fires, one calm card in the reading face —

  > *Android will ask you to let Phosphor see what's playing. Phosphor turns that sound into light on this screen — nothing is recorded, nothing leaves your phone. You can turn it off any time with the LIVE key.*

  `CONTINUE` (flat, accent frame) fires the system dialog. Denial lands gently: the card stays, reworded ("No worries — the library and microphone still work fine"), no red, no exclamation marks. The projection notification Android requires is styled minimal. Mic pick triggers `RECORD_AUDIO` the same way (one-line explainer first).
- **Honesty law:** apps that opt out of capture (Spotify, YouTube Music, DRM streamers) arrive as silence. The source popout and docs say so plainly; "visualize my Spotify" lives on desktop Phosphor.

### 2.4 Mode switcher (`MODE`)

Two-finger swipe is the fast path (§1.3). The `MODE` key opens a half-sheet for the deliberate path — **and the sheet is translucent, so tapping a mode switches it live behind the glass before you dismiss**:

```
MODE                          ×
──────────────────────────────
XY
  ◇ xy · scope art        ●
  ◇ xy · goniometer 45°
  ◇ xy · swirl
  ◇ xy · dots
3D
  ◈ attractor · takens
  ◈ time helix
TIME
  ◇ waveform
  ◇ ring oscillogram
SPECTRUM
  ◇ spectrum
  ◇ radial
  ◇ tunnel
```

Rows: engraved static glyph (tiny etched vector of the mode's characteristic figure, `ink_2`), mono name, one-word note where needed. Active row wears a hairline accent rim. No live thumbnails — 11 simultaneous renders is a battery decision, and the live scope *behind the sheet* is the preview. Sheet stays open across taps (browse freely).

Transition on any mode change: the **tube-flip** — trace collapses to a horizontal line (70 ms), re-blooms as the new mode (110 ms), medium haptic detent. Suppressed under reduced-motion (hard cut).

### 2.5 Light sheet (beam color + cycle)

From `⋯ › LIGHT`. Half-sheet, translucent (live preview behind, always):

- 10 preset phosphors as a grid of sharp square swatches, mono labels beneath (`P7 GREEN`, `AMBER`, …). Active = accent rim.
- `CUSTOM` expands the editor: 1–3 color slots (tap a slot → HSV square popout, sharp), a **gradient strip** previewing the ring, `ADVANCE` as a flat segmented pair `TIMER · EVERY SONG`, and the seconds rule with mono readout.
- **Photosensitivity guard:** dragging below 1 s stops at 1 s and opens the confirmation as a full-attention card — serious but warm, reading face, no haptic celebration:

  > *Below one second, the whole screen changes color rapidly. For some people with photosensitive epilepsy this can trigger seizures. Only continue if that's safe for you and anyone watching.*

  `KEEP 1 s` (default focus) / `I understand — allow faster`. Acceptance persists forever, as on desktop.
- Undo/redo for the last five appearance changes: two small flat keys in the sheet header.

### 2.6 Room sheet (12 chrome themes)

From `⋯ › ROOM`. A 2-column grid of **self-portrait tiles**: each tile is rendered in its own palette (plane ground, surface card, ink label, accent tick, a 3-line stone bevel sample) so switching rooms reads as changing the *room*, not the paint. Blossom Dark first, AMOLED second with a one-time muted caption `true black · made for this panel`. Rooms with `accent_follows_beam` show a slowly breathing accent dot on their tile. Selecting crossfades the whole chrome via `lerp_to` + smoothstep (240 ms — the one deliberately longer animation; it's a room change).

### 2.7 Kit browser + editor

From `⋯ › KIT`. Browser half-sheet:

- Rows: mono name · byte size (`haunt · 291 B`) · author credit if present. `NONE` row at top. Tap = wear it live (behind the glass). Long-press → details popout: the chain spelled as hierarchy `rotate › wobble › matrix`, credit, `SHARE` / `DELETE`.
- `+ NEW KIT` / `EDIT` opens the **editor**, full-height sheet, still translucent — the chain is edited *against the running beam*:

```
KIT · untitled                    ×
────────────────────────────────
≡ rotate      θ  ×0.35 ────────
≡ wobble      hz 2.1  depth 0.4
≡ ringmod     hz 180  mix 0.5
────────────────────────────────
+ ADD STAGE          SAVE  SHARE
```

- Each stage: drag-handle `≡` (reorder), transform name mono, params as **drag-value readouts** (horizontal drag on the number scrubs it, tap types it). Swipe a stage row left → flat `REMOVE` reveal.
- `+ ADD STAGE` → popout listing the six transforms with one-line descriptions.
- Imported kits are validated on the way in; a broken kit toasts its error in gentle tone (`this kit didn't land — <reason>`) and never applies. `.phoskit` files arriving via share/Open With deep-link straight to this browser with the kit pre-selected.

### 2.8 Compose mode

Entered via `⋯ › COMPOSE` or the long-press context popout. The stage becomes the instrument:

```
┌──────────────────────────────┐
│ compose · draw a shape     ✕ │
│                              │
│        ~ your stroke ~       │
│      glows and LOOPS as      │
│           audio              │
│                          ┃   │
│                          ┃←──── pitch rule, vertical, carved thumb,
│                          ┃      mono `216 Hz`, 80–400
│                          ┃   │
│  CLEAR          WAV   DONE   │
└──────────────────────────────┘
```

- **One finger draws. That is the only stage gesture in COMPOSE.** The stroke lays down as live glow; on release the shape *is* audio, looping at constant traversal speed (corners stay corners).
- While drawing, audio previews live — you hear the shape being born under your fingertip; a very fine haptic texture ticks with sharp direction changes. Soft haptic pulse on each loop start after release.
- Pitch: the vertical rule on the right (inside the inset, not the system edge) replaces desktop scroll; drag its square thumb, mono Hz readout rides the thumb.
- `WAV` exports 10 s → MediaStore + share sheet. `DONE`/`✕`/Back leaves (Back is intercepted only in this state, standard predictive-back with the compose layer peeling).

### 2.9 Share / postcard flow

From `⋯` or the long-press context popout — the popout's capture cluster:

- **SNAPSHOT** — instant offline re-render PNG of what you saw. Feedback: a single restrained phosphor bloom on the trace + light haptic (no white flash — this is an OLED at night). Saved to `Pictures/Phosphor/`, then a small anchored popout: the framed thumbnail + `SHARE` / dismiss by tap-away.
- **CLIP 10 s** — a 1 px accent progress rule runs the screen's perimeter while the last 10 s mux with audio; result popout with looping preview + `SHARE`.
- **POSTCARD (.phos)** — records the playing track's trace with your credit line (one-time name prompt, editable in settings). While recording, the perimeter rule runs in the beam's color. On completion: the postcard popout shows a live miniature replay, `stamped · trace by <you>`, and `SEND` → Android share sheet. Receiving: a `.phos` shared *to* Phosphor plays immediately on the stage, `trace by <them>` fading in — the postal service of glow, delivered by the share sheet.
- `.phoskit` shares ride the same rails (MIME/extension registered for both).

### 2.10 Settings

Full-height sheet, grouped by mono headings (hierarchy, no boxes): **SIGNAL** (Focus px, scope sample rate, auto-gain), **DISPLAY** (grid toggle, max fps `120 · follows panel`, keep-awake, ember auto-dim + minutes, burn-in shift toggle), **ROOM & LIGHT** (links into §2.5/2.6; the **theme stone** lives here — the third carved control), **CAPTURE** (LIVE default, consent status, revoke path), **POSTCARDS** (credit name), **PERFORMANCE** (GPU quality/render scale, Nerd HUD toggle), **ABOUT** (gentle one-paragraph note, GPL-3.0, "the beam remembers"). Every numeric is a draggable mono readout, tap to type.

---

## 3. Mobile-native moments

- **Lock screen & notification:** full `MediaSession` — art, transport, seek — when the local deck owns transport. Notification artwork = embedded cover art; when none, a periodically refreshed still of the live trace (at most once per 30 s). When scoping another app, Phosphor posts no competing media notification; only the mandatory capture notification, minimal and quiet.
- **Media buttons / volume keys:** hardware media keys → the loaded deck (the transport law). Volume keys → system media volume, untouched semantics; Phosphor never intercepts.
- **Haptics map** (low intensities): mode detent — medium tick · gain unity crossing — light tick · long-press recognized — light · sheet detent snap — light · compose stroke texture — fine, low scale · loop start — soft pulse · postcard stamped — double tick ("postmark") · photosensitivity confirm — deliberately none.
- **Edge-to-edge & cutout:** the scope surface renders behind cutout and gesture bar (`LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`, transparent bars). Chrome respects insets; the status band flanks the punch. Landscape: pure scope; console appears as the same bottom strip, insets honored.
- **One-handed:** everything interactive in the thumb arc (§1.4); sheets never require reaching the top — ✕ duplicates as drag-down/scrim/Back.
- **Keep-awake + ember:** screen stays awake while the beam is live. Optional **ember mode**: after N minutes untouched, chrome is gone and the beam's brightness budget eases to ~60% over 3 s — a banked fire, not a screensaver. Any touch restores instantly.
- **Burn-in:** auto-hiding chrome (4 s), luminance-capped console, ±1 px slow pixel-shift on any persistent overlay. The beam itself is immune by nature — it moves.
- **PiP:** pressing Home while the beam is live auto-enters PiP (`setAutoEnterEnabled`). PiP = pure scope, zero chrome; one PiP action (play/pause); tap expands per system. Capture keeps feeding it. This replaces desktop mini mode outright.
- **120 Hz:** render loop targets the panel rate; drops to panel-driven lower LTPO rates when ember mode dims and signal is quiet.
- **Predictive back:** Back peels the top layer only — sheet → console → (in STAGE) system back-out. Compose intercepts once, as above.

---

## 4. Cut or deferred — honestly

| Desktop feature | Mobile fate | Why |
|---|---|---|
| Mini + glass windows | **Cut** → PiP is the analog | Android has no always-on-top translucent app windows |
| Vacuum routing | **Cut for v1** | Android's audio policy doesn't permit third-party re-routing |
| MPRIS | **Replaced** by MediaSession/MediaController — same law, native form | |
| CLI / agent surface on device | **Deferred** | The station lives on the desktop; `dev/pm3` is the project's conforming surface |
| Keyboard shortcuts, hover tooltips, scroll-wheel | Replaced wholesale by §1.3; tooltip content moves into persistent popouts | No hover, no keys |
| CPU (cairo) renderer choice | Cut — GPU only | Renderer picker is desktop debt |
| Full-track headless render to mp4 | Deferred | The 10 s clip covers the mobile share need |
| Track-change system toasts | Cut | MediaSession notification already carries this |
| Mix several apps (per-app selection) | **Deferred, wanted** | Capture delivers a mixed media stream by default; per-app solo returns when per-uid capture is proven |
| Photosensitivity guard, gapless, .phos/.phoskit, compose, cycle, all 12 rooms, all 11 modes | **All kept.** None of the soul is cut | |

---

## 5. Signature moments

1. **Thermionic warm-up.** Cold launch: a single point blooms at center with a faint thermal flicker, then the trace unfurls into the last-used mode as audio arrives — ≤ 1.2 s total, and it *is* the loading time, not added to it. Warm resume: instant light, no ceremony tax. Reduced-motion: fade only.
2. **The beam answers your finger.** Gain isn't a slider on a panel — it's the figure itself swelling under your thumb, mono `×1.38` etched beside it, a single tick as you cross unity.
3. **The tube-flip.** Two-finger swipe: trace collapses to a line, detent tick, re-blooms as the next mode — 180 ms of switching functions on a real scope.
4. **Fingertip electron gun.** Compose on glass: you hear the shape while you draw it, feel its corners, and the release makes it sing on loop. Then `WAV` → share sheet: a sound you drew, sent from a phone.
5. **The postal service of glow.** A `.phos` postcard arrives through the share sheet like a photo — but it *plays*, in the receiver's room, with `trace by you` fading in. Sending one stamps a double-tick haptic postmark.
