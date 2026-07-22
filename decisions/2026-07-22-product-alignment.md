# Decision record: 2026-07-22 product alignment

**Status:** Ratified for specification and future implementation
**Implementation in this pass:** None

## Context

Ben reviewed the working MVP and asked for a complete alignment with the intended product: alive motion, better gestures, system rotation honesty, themes and packs, PiP/overlay, first launch teaching, broad audio capture, Tailscale P0, full Nexidex control, dual signing/distribution, permission transparency, and a ProjectM-vectorscope blended scope view.

The repository, current S25 state, Nexidex source, station protocol documents, Play publication plan, Android capability boundary, and ProjectM upstream were reviewed. Nexus was directly consulted. Her rulings are recorded as facts #716 and #717.

## Decisions

1. **Fi/Ti source:** `vision/` and `spec/` become authoritative over older implementation-led UX/architecture documents where they conflict.
2. **Material language:** Phosphor is Sharp Stone, Living Signal. The beam is its living material.
3. **One causal store:** UI, agent, renderer commands, persistence, HUD, and receipts share typed state and mutation provenance.
4. **Visible Nexus hand:** all Nexus mutations are immediately represented and attributed. Observe and drive are distinct grants.
5. **Transport:** Fortress Phosphor dials one Nexidex/tailnet gate and never network-listens. Same-phone Binder/AIDL is P0 and legal under the one-gate law. A localhost socket is forbidden. The first public Play release exports no agent entry, while retaining compatible internal schemas for a possible later signed subset.
6. **Distribution:** Play and Fortress are compile-time flavors and distinct signing identities. Elevated implementation is absent from Play artifacts.
7. **Rotation:** Android system rotation lock has final authority. Phosphor controls become unavailable rather than bypassing it.
8. **Landscape sheets:** screen-right is canonical in both landscape quadrants. Direct tracking is synchronous and one-to-one.
9. **Gesture safety:** one-finger scope adjustment arms after a 1.5 second stationary hold with a visible ring. Two-finger pinch stays immediate.
10. **Themes:** six curated rooms plus unlimited inert, versioned theme packs. Legacy rooms are preserved as a pack. AI/Nexus authoring is first-class.
11. **Colors:** grid and ground are independently editable; beam supports at least 16 stops, random, and full-spectrum glide.
12. **Display modes:** PiP, transparent overlay, and HUD have independent controls. Overlay is initially Fortress-first and Play-policy-gated.
13. **Permissions:** retain a master YOLO convenience control and expose every subordinate capability honestly and independently.
14. **OOBE:** under-30-second safe vectorscope lesson, skippable, relaunchable, permission-deferred.
15. **Audio:** MediaProjection remains Play-safe but opt-out-respecting. The old Shizuku conclusive-negative statement is corrected. Shell-process remote-submix capture is a mandatory Fortress spike because shell already holds relevant permissions on the S25.
16. **Tailscale:** home PC and Linux laptop relay setup is P0. Nexidex remains the standard agent connector.
17. **ProjectM:** a ProjectM visualizer blended with Phosphor's vectorscope is a first-class scope view. It has true beam, Phosphor geometry, and ProjectM field layers on one source/clock. Decorative field quality yields before beam fidelity.
18. **Commercial model:** Play remains free install, seven-day full trial, $3.99 one-time Pro, no ads, no subscription, no automatic charge, and minimal user-controlled diagnostics.
19. **No Python:** no Python build, runtime, helper, or shipped dependency.

## Superseded conclusions

- `docs/SERIOUS-TODOS.md` 2026-07-18 claim that there is no sideloaded capture path and the question must never be revisited is superseded. Directly granting the app permission failed, but a shell process already possesses the capability and requires a controlled test.
- Existing UX language that all 12 rooms remain on the primary shelf is superseded by the six-room curated shelf plus legacy/custom packs.
- Existing unconditional PiP behavior is superseded by explicit user settings and source eligibility.
- Existing hybrid sensor behavior may not override an active Android system rotation lock.

## Human gates deferred to implementation/release

- existing `dev.phosphor.mobil3` Play/local signing lineage and user-data migration strategy;
- exact final curated-room IDs/order if current names differ;
- Fortress signing key and certificate pin;
- Play App Signing certificate after Console enrollment;
- overlay inclusion in a future Play submission;
- ProjectM preset licensing selection;
- final store/legal submissions and price confirmation.

The neutral architecture and most implementation work do not need these values in advance. Use placeholders and fail closed until supplied.
