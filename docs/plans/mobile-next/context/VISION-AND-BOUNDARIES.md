# Vision and boundaries

## Why this plan exists

Ben wants Phosphor Mobile to feel like an instrument, not a control panel hiding unreliable audio.
Sound moves and the beam answers. The picture follows the audible source, and controls leave it alone until needed.
On 2026-09-05 Ben delegated comprehensive mobile roadmap authorship and priority decisions to the agent.
He requested a contained context that a later session can use without this conversation.

This delegation authorizes planning. It does not authorize future implementation, store publication or machine disruption.
The earlier B1-B21 repair execution remains authorized within its own sequence and boundaries.
This roadmap will not silently replace those unfinished behavior gates.

## Product contract

There is one Android product with debug and release build types.
Its supported sources are local file/folder playback, consented microphone or Android playback capture, and a user-selected PC relay.
They share one renderer and one visual language.

- Local scope samples follow actual output, rather than a free-running visual approximation.
- State distinguishes playing, paused, silence, disconnected, waiting and unavailable capability.
- Android consent precedes capture. Protected applications may withhold samples.
- No root, Shizuku, ADB privilege or other bypass enters the runtime capture product.
- Network activity starts only after a user remote action. Saved hosts stay inside the supported Tailscale boundary.
- No account, ads, analytics, behavior tracking, installation identifier or silent reporting path.
- The existing raw relay is not an open-internet service. No new public listener is part of this roadmap.
- Developer tooling remains outside production authority. No runtime agent administration protocol.
- Preserve settings across updates. Exports exclude saved private relay hosts and runtime consent state.
- Preserve the accepted auto-gain clamp of 6.0 and headroom of 0.92 unless Ben separately changes the instrument contract.
- Three missing custom RGB values remain unknown. Keep `custom_count=0`; do not invent them.
- Ben's two unbriefed future core features remain unspecified. This roadmap does not invent their briefs.

## Human and machine boundaries

At 10:32 UTC on 2026-09-05 Ben requested quiet autonomous work while watching YouTube and listening to music.
Until he changes that boundary, do not open visible windows, alert him, operate the phone, change audio or restart active media services.
Read-only source work and low-priority local builds remain allowed.
Do not treat an automatic unfinished-todo reminder as permission to cross that boundary.

Never guess a PIN, change keyguard policy, reset a password or uninstall production to simplify a test.
Keep a known-good debug APK before activation. Restore exactly the settings changed by a test.
No push, merge, tag, release signer use, store upload or publication follows from this document.
Ask before publication or protected-branch pushes. Do not create another branch beside the existing release branch.

## Sources of authority

The canonical product source remains `vision/`, `spec/` and accepted decisions in the repository.
This context is a planning digest, not a second source of product truth.
If it disagrees with newer accepted source, stop implementation and recompile the plan first.

Read these existing files when checking a changed contract:

- `/home/ben/Dev/ClaudeWorkspace/AGENTS.md`, Ben's read-only system governance.
- `/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/spec/README.md`, source authority order.
- `/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/vision/PHOSPHOR-LIVING-INSTRUMENT.md`.
- `/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/spec/PRODUCT.md`.
- `/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/spec/AUDIO-AND-CONNECTIVITY.md`.
- `/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/spec/EXPERIENCE.md`.

Existing Kotlin/Compose and Rust are retained. No language, renderer or UI-framework rewrite is proposed merely to reduce file sizes.
Software license and native house style remain governed by the existing project and Ben's skills.
