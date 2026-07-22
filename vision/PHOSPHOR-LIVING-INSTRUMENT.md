# Phosphor: Sharp Stone, Living Signal

**Status:** Binding product vision
**Ratified:** 2026-07-22
**Owner:** Ben
**Scope:** Phosphor Mobile, its developer build, its Google Play build, and its relationship with Nexidex

## The instrument

Phosphor is a living audio instrument. It turns real sound into light, geometry, motion, and a legible sense of stereo space. The scope is not decoration behind an application. The scope is the application.

The interface has two materials:

- **Sharp stone:** controls, sheets, labels, frames, settings, and structural chrome. Stone is deliberate, stable, readable, and restrained.
- **Living signal:** the beam and effects that are causally derived from real audio, a real gesture, a real connection, or a real state transition. Signal moves because something happened. It does not move to pretend the app is alive.

The resulting character is fast, fluid, trustworthy, and expressive. Every gesture should feel directly connected to the hand. Every transition should cover its distance with intention. The vectorscope remains authoritative even when Phosphor grows into richer visual forms.

## What Phosphor is for

Phosphor exists to make music and audio physically legible through a beautiful, high-fidelity scope. It must remain useful as an instrument and delightful as a visualizer.

Its core views include:

1. The classic oscilloscope and vectorscope family already present in the app.
2. A **ProjectM blended scope view** that combines a ProjectM music-visualizer field with Phosphor's vectorscope rendering engine in a distinct, coherent instrument.
3. Future scope views that preserve the same source truth, timing truth, silence truth, and readable beam.

A scope view is not a theme. A theme changes the room around an instrument. A scope view changes how the instrument interprets and presents audio.

## The ProjectM blended view

The ProjectM view must contain three separable and inspectable layers:

1. **True beam:** the real stereo vectorscope trace. It remains legible at every supported blend.
2. **Geometry effects:** deterministic Phosphor geometry derived from named audio features and state.
3. **ProjectM field:** the expressive music-visualizer field, labeled and never represented as the literal audio trace.

All three layers use one audio source and one monotonic clock. Silence makes the field honestly rest. Under thermal or GPU pressure, the ProjectM field loses quality or cadence before the true beam does. The beam is the instrument's last thing standing.

## Phosphor and Nexidex

Phosphor and Nexidex are kin, not the same body.

- Phosphor is stone and beam.
- Nexidex is stone and honey.
- They share causal truth, agent conventions, provenance, security, and an ability to understand each other.
- They retain distinct visual identities.

Nexidex must be able to observe and comprehensively drive Phosphor through first-party, typed surfaces. The human interface and the agent interface are equal readers and writers of one state system. Neither gets a private approximation.

Nexidex presence is visible but honest:

- **Observe:** an open-eye presence only while a real session is attached and observing.
- **Drive:** a hand presence only while Nexus is actually holding or changing a control.
- Presence decays immediately when the cause ends.
- No ambient glow implies awareness when there is no attached session.

Every mutation has one provenance stamp, authored when the mutation is accepted. UI state, agent deltas, command acknowledgements, and receipts all read that exact stamp. They never reconstruct it later.

## Capability without deception

Phosphor has two legitimate distribution identities:

- **Play:** policy-safe, Google Play signed, purchasable, supportable, and honest about Android capture limits. Its first public release exports no first-party agent entry.
- **Fortress:** Ben-signed developer build with the full Nexidex harness built now, tailnet tooling, ADB/Shizuku experiments, receipts, and elevated local capabilities where the device can legitimately provide them.

The Play build is not a crippled lie. It is the complete product within Play and Android's public capability boundary. The Fortress build is the laboratory and first-party agent instrument. Shared state/action schemas keep future compatibility, but elevated implementation and the initial agent transports must be absent from the first Play artifact, not merely hidden behind a runtime flag.

## Permission philosophy

The user remains sovereign.

Phosphor keeps a **YOLO permissions master control** for people who want the application to do everything it can, while also exposing every subordinate capability and behavior independently. Every row tells the truth about whether the capability is available, granted, active, system-managed, Play-unavailable, or waiting for user action.

The app may guide the user to a system panel or request a permission. It may never claim to have overridden Android when it has not. System authority is visible, not treated as an error.

## Audio philosophy

The highest-priority engineering objective is the broadest truthful audio capture the device can support.

The source state must distinguish:

1. No capability or permission.
2. Capability present, but the source is silent or has opted out.
3. Source connected with no current signal.
4. Live audio flowing.
5. Remote link lost or recovering.

Phosphor never freezes a live-looking trace over dead input. It never labels policy-limited silence as an app crash. It never promises all-app capture in the Play build when Android cannot provide it.

The Fortress build must investigate the newly viable shell-process route. On the S25, `com.android.shell` already holds the privileged audio-output permissions and the remote-submix path exists. A Shizuku UserService or ADB sidecar running as shell may therefore capture output that a normal app cannot. This is a mandatory controlled experiment, not yet a proven shipped feature.

## Interaction philosophy

- Direct manipulation tracks the finger one-to-one while held.
- Physics begins after release, not between the finger and the surface.
- One pointer sequence has one owner.
- Windows enter and leave from the edge that spatially owns them.
- Landscape settings always use the screen-right edge, independent of physical rotation side.
- Android system rotation lock has final authority and is represented explicitly.
- Motion is abundant where it communicates distance, state, energy, or causality. It is absent where it would become theater.
- Reduced motion removes travel and flourish, not information.

## Themes

Phosphor ships a focused six-room shelf and supports unlimited user and agent-authored theme packs.

Themes are inert, versioned data. They can be expressive, dimensional, animated within declared rails, and fully capable of using the phone UI's design vocabulary. They cannot execute code, fetch resources, hide safety disclosures, forge capability state, or introduce unbounded work.

AI assistance is a first-class theme-authoring path. The app explains this with a clear symbol and a button to the authoring guide. Nexidex can validate, install, preview, keep, revert, export, and remove themes through the same state system the human interface uses.

## Sound and teaching

The app has subtle, optional UI sounds with master and category controls. Sounds confirm real actions and never become ambient theater or contaminate the scoped audio.

The first launch is a short, skippable demonstration. In less than 30 seconds it teaches what left and right audio do to a vectorscope, plays a safe bundled stereo example, gives two or three basic gestures, and offers a deeper tour. The tutorial is always relaunchable.

## Commercial and privacy promise

The Play product remains:

- free to install;
- fully usable for seven days;
- $3.99 once for Pro after the trial;
- no subscription;
- no automatic charge;
- no advertisements;
- no required Phosphor account;
- minimal, transparent, user-controlled diagnostics.

Commerce, policy work, and telemetry must not move the instrument's purpose or visual center.

## Non-negotiable invariants

1. The vectorscope remains an instrument, not wallpaper.
2. The beam remains readable in every scope view.
3. Human and agent surfaces share one causal state.
4. Every agent mutation is visibly attributable.
5. Phosphor never network-listens. It dials one trusted gate. Same-phone Binder is not a network gate.
6. Play and Fortress capabilities are separated at compilation and signing identity.
7. Permission and capture state are honest at all times.
8. The UI is sharp stone. Living motion comes from signal, gesture, or real state.
9. No Python is shipped, required, or introduced into this project.
10. Performance and beam fidelity outrank decorative layers.
