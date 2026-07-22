# Master product specification

**Status:** Binding
**Product line:** Phosphor Mobile
**Baseline:** `383472b`

## 1. Scope and release lines

Phosphor MUST be built as two explicitly different distributions from one product source:

| Distribution | Identity | Purpose | Commerce |
|---|---|---|---|
| Play | `dev.phosphor.mobil3` | Public, policy-safe instrument; no exported first-party agent entry in the first public release | Seven-day full trial, then $3.99 one-time Pro |
| Fortress | `dev.phosphor.mobil3.fortress` | Ben-signed instrument, full agent harness built now, privileged experiments | No Play Billing requirement |

The distributions MUST be co-installable. Elevated Fortress code MUST live in a source set or module that cannot enter the Play dependency graph or AAB. Shared state/action schemas may exist in both builds, but the first public Play artifact MUST NOT export the Binder or remote Nexidex agent entry. A later Play release may add a policy-safe signed subset only through a new explicit decision.

## 2. Preserved core

The following existing qualities remain binding:

- One full-bleed scope stage.
- Kotlin/Compose chrome over the Rust/wgpu rendering core.
- Material-free house UI and sharp-cornered controls.
- High-fidelity stereo signal path.
- 120 Hz presentation target on supported displays.
- Local deck, Android playback capture where allowed, microphone, and remote relay sources.
- Existing scope modes and tuning controls unless explicitly superseded.
- No ads, subscription, required account, or behavioral analytics.
- No Python.

## 3. State architecture

### 3.1 One causal store

BOTH builds MUST move user-visible configuration and runtime status into one typed, observable state store. Compose, renderer command generation, Binder, remote protocol, CLI, HUD, persistence, and receipts read or mutate this store through named actions.

A state field MUST NOT have separate agent and UI copies.

Each accepted mutation records:

```text
revision
field
old_value
new_value
by = human | nexus | engine | system | migration
because
transport = ui | binder | tailnet | lifecycle | migration
session_id, if any
ts_monotonic
ts_wall
```

The stamp is written once at mutation time and reused in the delta, visible hand indicator, command acknowledgement, and audit receipt.

### 3.2 Control parity

Every meaningful human control MUST have a typed action and agent schema entry. Every agent-settable state MUST have a visible human representation or an explicit developer-only diagnostic representation.

System consent, Google Play purchases, protected Android settings, and safety confirmations cannot be forged by an agent action. The agent may request that the corresponding human flow be opened.

## 4. Settings architecture

Settings MUST be grouped by user intention rather than Android implementation:

1. Scope and view
2. Light, grid, and beam
3. Rooms and theme packs
4. Sources and capture
5. Remote and Nexidex
6. PiP, overlay, and HUD
7. Motion, sound, and accessibility
8. Permissions and fortress controls
9. Trial, Pro, diagnostics, and about

Every setting row MUST expose:

- desired value;
- effective value;
- availability;
- authority or blocker;
- last writer and timestamp when changed by Nexus/system;
- a direct action or a fix-bearing explanation.

### 4.1 Rotation authority

Android system rotation lock takes final authority.

When the system is locked:

- the current effective orientation is frozen;
- Phosphor rotation controls remain visible but disabled;
- the row says that Android rotation lock is controlling orientation;
- an `OPEN SYSTEM ROTATION` action MAY deep-link where supported, otherwise show exact instructions;
- desired app preferences are retained but do not become effective;
- agent attempts return a typed refusal with a fix rather than bypassing the system.

When Android rotation is unlocked, app scope-lock and UI-lock controls become available again.

### 4.2 Permission fortress

A master **FORTRESS / YOLO** control MUST exist. It enables a requested bundle, but never hides subordinate controls. Each subordinate permission or behavior remains independently adjustable.

Rows distinguish:

- available and off;
- requestable;
- granted but inactive;
- active;
- system-managed;
- unavailable in this distribution;
- denied with a repair action;
- delegated to Nexidex, ADB, or Shizuku in Fortress.

The Play build MUST use only Play-eligible requests and system settings. The Fortress build MAY expose Shizuku, ADB, and local trusted-service paths with explicit provenance and audit.

## 5. Themes

### 5.1 Curated shelf

The primary room picker MUST show six first-party rooms:

1. Blossom Dark, default
2. AMOLED
3. CRT Amber
4. Liquid Glass
5. Chromacore
6. Paper

The exact labels may retain current product naming if those room IDs differ. Existing first-party rooms not on the curated shelf SHOULD migrate to a bundled optional `Legacy Rooms` pack so upgrades do not erase user choices.

### 5.2 Unlimited packs

Users may install unlimited packs subject only to storage, schema, validation, and performance limits.

A pack is inert data. It MAY declare:

- full palette tokens;
- renderer grid, ground, beam, persistence, tint, and related theme defaults;
- room character and dimensional behavior;
- motion rails and duration scale;
- icon and illustration assets in bounded approved formats;
- UI-sound palette references to bundled pack assets;
- preview metadata and author credit.

A pack MUST NOT:

- execute code or expressions;
- fetch remote content during load/render;
- name arbitrary files outside its archive;
- alter permissions, commerce, diagnostics, safety, or trust identity;
- create an unbounded shader or animation workload;
- hide required disclosures or provenance.

The schema MUST be versioned, self-describing, forward-readable, and validated with fix-bearing errors. Theme capabilities should be broad rather than artificially reducing phone UI expressiveness.

### 5.3 Authoring surfaces

The app MUST include:

- `+` install/import action;
- manual editor entry point;
- AI-assisted authoring symbol and explanation;
- link to the repository theme guide;
- live preview with `KEEP` and `REVERT`;
- author, source, compatibility, and validation status.

Nexidex/CLI MUST support schema, validate, add, list, show, preview, keep, revert, export, and remove.

## 6. Beam, grid, and color

- Grid color and alpha MUST be independently adjustable.
- Background/ground tint and alpha MUST be independently adjustable.
- Black-background defaults MUST make the grid legible without overpowering the beam.
- Custom beam color stops MUST increase from 3 to at least 16.
- Beam color modes MUST include solid, multi-stop, random, and spectrum-glide.
- Random MUST exclude black and honor user-defined allowed ranges or banned colors.
- Spectrum-glide MUST interpolate smoothly through the visible hue spectrum rather than taking the shortest muddy RGB path. It is a color-transition rule, not fake signal analysis.
- Transition midpoint behavior MUST be toggleable as **FULL-SPECTRUM GLIDE**.
- Photosensitivity controls apply to all time-driven whole-screen color changes.

## 7. Gestures and motion

### 7.1 Global ownership

One pointer sequence has one owner: system, sheet, compose, console control, or stage. Ownership does not change after recognition.

### 7.2 One-finger scope adjustment

To prevent accidental gain/orbit changes:

- a one-finger adjustment requires a stationary 1.5 second hold;
- a circular progress indicator grows around the contact point;
- motion beyond cancellation slop before arming cancels the action and allows the intended swipe/tap owner to proceed;
- a light haptic marks successful arming;
- after arming, one-finger movement adjusts the declared scope parameter;
- two-finger pinch remains immediately available;
- view lock remains available.

The exact delay is a configurable product constant, default 1500 ms, not a user-exposed tuning slider in the first implementation.

### 7.3 Sheets

While held, a sheet follows the finger one-to-one using synchronous pointer state. No coroutine, spring, or animation sits between the finger and the sheet. Velocity and room motion personality apply only after release.

In landscape, settings and related side sheets always enter from and dismiss to the screen-right edge. Placement and safe-inset behavior are identical in both landscape quadrants.

All controls, switches, selected states, and panel transitions MUST animate purposefully. Reduced motion preserves state feedback with cuts, tint changes, and static indicators.

## 8. PiP, overlay, and HUD

Three independent user controls MUST exist:

- PiP enabled
- transparent overlay enabled
- HUD enabled: on, auto, or off

Standard Android PiP cannot be assumed to support arbitrary transparent windows. It remains the system-owned miniature scope.

The transparent floating scope is a separate `TYPE_APPLICATION_OVERLAY` capability. It MUST support:

- fully transparent outside region;
- configurable center ground color and opacity;
- radial or shaped fade from solid/tinted center to transparent edge;
- interactive and click-through modes;
- drag/resize/dismiss affordances when interactive;
- persistent notification escape handle;
- no second capture session or second audio pipeline;
- no frozen-live state during silence or disconnect.

The Play eligibility of overlay is a policy gate. The initial plan treats it as Fortress-only unless a current Play review proves a tightly scoped user-facing implementation acceptable.

## 9. OOBE and tutorial

First launch provides two or three concise hints and offers a fuller tour. The complete path MUST remain under 30 seconds before the user can reach the live stage.

The tutorial MUST:

- be skippable at every beat;
- use a safe, bundled, low-level stereo example;
- include a concise, captioned, quiet audio narration or voiced instrument explanation, with an immediate mute action;
- explain how left and right channels move the vectorscope;
- visibly render that example through Phosphor;
- teach console summon, source selection, and the armed one-finger adjustment;
- explain other-app capture limits before the first likely silent source;
- defer MediaProjection and overlay permission prompts until the user chooses those features;
- offer concise and comprehensive paths;
- be relaunchable from Settings;
- honor reduced motion and UI-sound settings;
- never require network access.

## 10. UI sounds

UI sounds are optional, subtle, satisfying, consistent, and caused only by real actions.

A master toggle and category toggles MUST cover at least:

- navigation and sheet movement;
- control detents;
- commit/confirm;
- source/live state;
- theme changes;
- warnings/denials.

Use the Android sonification usage path and a preloaded low-allocation sound pool. UI sounds MUST NOT enter snapshots, clips, relay audio, capture analysis, or ProjectM input.

## 11. Tailscale and remote setup

Tailscale connectivity to the home PC and Linux laptop is P0.

The app MUST provide a compact setup surface with:

- current endpoint and link phase;
- `SET UP TAILSCALE` or `OPEN MANUAL` action;
- short explanation of why the private network is used;
- link to repository documentation;
- host discovery/configuration and test action;
- reconnection and fix-bearing error state;
- no requirement to understand ports or protocols during normal setup.

Nexidex remains the standard agent connector. A remote agent reaches Phosphor through the Nexidex/tailnet gate. Phosphor dials outward and never exposes a network listener.

## 12. ProjectM scope view

ProjectM blended visualization is a first-class scope view.

The view MUST expose:

- preset or pack;
- layer blend;
- minimum true-beam visibility;
- geometry-effect amount;
- quality tier;
- transition duration;
- deterministic seed where supported;
- preset license/source metadata;
- silence behavior.

The true beam remains visibly identifiable. The field cannot claim to be literal signal geometry. Both layers receive the same full-resolution PCM and clock. Android's low-fidelity `Visualizer` API remains forbidden.

## 13. Commercial and diagnostics contract

The Play build implements the existing publishing plan:

- full seven-day local trial;
- one $3.99 non-consumable Pro product;
- no auto-charge or subscription;
- no ads;
- minimal diagnostics with informed first-run choice and later toggle;
- Android vitals plus user-reviewed local reports by default.

Trial expiry MUST preserve all user themes, settings, and work. It gates continued paid use without deleting state.

## 14. Explicit non-goals for this build line

- No public internet relay service.
- No arbitrary remote code execution.
- No localhost socket as a same-phone agent shortcut.
- No Python toolchain or runtime.
- No fake all-app capture claim.
- No requirement to make every existing legacy room part of the primary six-room picker.
- No conversion of Phosphor into a generic media dashboard.
