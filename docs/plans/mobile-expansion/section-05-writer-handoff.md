# R13 bounded source handoff

Status: source released, acceptance blocked. This is not an R13 completion or R15 approval claim.

## Provenance and ownership

Runtime bases were mobile a3223b87c8d15fc0da691cfcfe0963ba464d1e92 and shared 7729990bb29f0167ef906d0fbdb44e1e91206955. Source release arrived at 07:02 UTC on 2026-09-08. The writer stayed read-only beforehand. Coordinator later committed only its own docs/ledger work. The writer made no Git mutation, worker spawn, network request, Android build, device, GUI, service or playback action.

Read canonical section 5 and defaults, R13 spec, immutable hold audit, machine cabinet, mobile docs/AGENTS.md, shared AGENTS.md and required skills. Mobile has no root AGENTS.md. Read Wyvern's fixed-baseline audit at /home/ben/.jcode/scratch/R13-bounded-audit-20260908-071537-REPORT.md. Neither audit nor critic report was edited.

The contract and R13-only spec refinement preceded runtime code. Coordinator approved destination-owned opacity at 07:05 UTC, RemotePlayer.kt at 07:11 UTC and visual-only spsc.rs epoch metadata at 07:17 UTC.

All 25 manifest paths are released to coordinator integration. No writer-owned background task remains. Coordinator's new shared tests/retained_frame.rs is excluded and was not edited. The private target and logs may be retained as receipts. No installed artifact changed.

## Source result

- Additive shared RetainedFrame snapshots exact same-format GPU energy plus all appearance uniforms. COPY_SRC/COPY_DST/TEXTURE_BINDING capabilities are explicitly checked. Live shader remains byte-identical. Private immutable copies survive renderer resize and Active replacement.
- RetainedPresenter maps nearest original image pixel centers for bounded fit/pan/zoom. It recomposes frozen emission with destination-owned scope_alpha, not opacity-faded captured backgrounds. RetainedTransfer names hardware-sRGB/manual-SDR provenance, not HDR support.
- CPU History pins committed application-present identity before common controllable pause. Driver work stays outside the ownership lock. Present failure does not publish a candidate. Pinned ownership prevents later queue work from reusing that texture. Resources are bounded to committed/candidate/pinned with checked 128 MiB maximum per snapshot.
- Native HOLD/BLACK branches before DSP/decay/deposit. BLACK is a clear-only opaque black draw. Dirty events drive paused presentation. Geometry now uses a bounded latest mailbox rather than one queued wake per packet. Hidden source retirement also drops spare history.
- Source generation fences stale commits. Remote callback chunks carry producer epochs captured before callback work. Receive-side geometry/audio capture epochs before decode and validate under publication/consumption guards. Resume clears visual ingress and DSP history, preserving remote_boundary, audible queues and jitter. Same-item seeks preserve held identity.
- Common local/capture/remote pause hooks cover app, MediaSession notification and inferred earbud paths. Capture callbacks pin before metadata work. Native accepted M packets provide earlier remote authoritative observation. Silence/errors/buffering do not invoke these intentional pause hooks.
- Portable pause_display defaults HOLD, accepts BLACK and exports only that preference. Image and inspection are transient. App and HUD expose pause/resume and inspection/reset without live gain/camera/remote controls. Existing settings gesture arbitration precedes inspection.

## Checks actually run

1. `CARGO_TARGET_DIR=/home/ben/.jcode/scratch/r13-private-target cargo +1.96.0 test --manifest-path rust/Cargo.toml --locked --offline --lib`: final 83 passed, 0 failed. Log: /home/ben/.jcode/scratch/r13-host-tests.log.
2. `CARGO_TARGET_DIR=/home/ben/.jcode/scratch/r13-private-target cargo +1.96.0 test --manifest-path ../phosphor/Cargo.toml -p phosphor-render-gpu --locked --offline --lib retained_frame::`: 4 passed, 0 failed, 2 GPU-using existing tests filtered out. Log: /home/ben/.jcode/scratch/r13-shared-tests.log.
3. New executed tests: two committed/pinned generation tests, one visual chunk producer-epoch test, one preserved remote lease/old-epoch rejection test, shared checked allocation bounds, inspection finite/bounds, additive shader entry anchor, and Naga parsing/validation of retained plus unchanged live WGSL. These use no GPU device.
4. Rustfmt applied to the changed native source and new shared module. JNI preexisting lifetime formatting was restored to avoid unrelated changes. Both repository `git diff --check` commands passed.
5. R02 SurfaceHost.kt, surface_lifecycle.rs and surface_policy.rs plus the original shared shaders.wgsl compared byte-identically with frozen runtime bases. Native retirement logic was not weakened.
6. `sha256sum -c /home/ben/.jcode/scratch/r13-source-sha256.txt`: all 25 owned source files matched. Receipt: /home/ben/.jcode/scratch/r13-manifest-check.log.

Earlier source-anchor tests failed after the epoch argument addition. The three bounded adapter assertions were updated to match the stronger production call, not removed. Subsequent 83-test suite passed. An orphan enum doc comment caused rustfmt parsing failure and was removed. A Ruby script syntax error caused no edits before its corrected run.

No Kotlin fixture was authored or executed by this writer. No Gradle, Android/JNI compilation, GPU pixel/readback test, device/driver/transport test, or independent source approval ran. The coordinator authored its separate offscreen fixture, which remains unrun at this handoff.

## Requirement-to-check mapping

| Requirement | Evidence obtained | Acceptance still needed |
|---|---|---|
| Committed image, failed acquire, frozen history | History pin/generation tests, immutable copy and present-order source review | Real acquire/allocation failures, in-flight present ordering and exact pixels |
| Transparency, BLACK, HDR seam | Naga validates additive/live shader, original live shader unchanged, clear-only BLACK branch | Transparent HUD over contrasting content, output formats/alpha, future HDR separately |
| Surface survival and bounded resources | Device-owned snapshots, checked byte/extent tests, unchanged R02 barriers | Rotation/PiP/HUD handoff, driver allocation failure, memory/thermal behavior |
| Default HOLD and portable BLACK | Typed archive key/default and settings source review | Android clean-install, migration and archive round-trip |
| Audible local and honest external pause | Common-owner pre-pin source ordering and earliest callback/M observation | Real notification/earbud/focus/noisy/capture rejection/remote acknowledgement |
| Live display-only pause and status | Independent display state and UI labels in source | UI state and actual source-live status across controllers |
| Inspection and settings gestures | Finite/bounded transform test, retained pixel-center sampling, settings-door-first dispatch | App/HUD pan/zoom/reset, gesture cancellation, tuning-write spies, accessibility |
| Fresh resume and source invalidation | Epoch metadata and remote lease tests, bounded mailbox, same-item seek branch | Capture producer read fence, source replacement/reconnect races, real timeline markers |
| Hidden/event-only GPU work | Branch ordering/mailbox source review and original lifetime barriers | Stationary HOLD under source flood, visible/hidden driver counters |

## Blocked outcome and smallest next step

Strict capture source-time freshness is unclosed: samples-only CaptureService/MicController/root PCM ingress has no producer read epoch or timestamp. Pending visual buffers are cleared, but a pre-resume read delivered afterward is indistinguishable. Coordinator explicitly owns that follow-up. Network bytes already buffered before the decode fence similarly do not prove zero-age source time.

Remote wire commands retain existing optimistic playing/mute behavior and later M reconciliation. There is no new per-command protocol acknowledgement. An unsupported or rejected external action must not be interpreted as proven audible pause from the display state. Unsolicited pause may already have occurred before the authoritative callback. The snapshot identifies the last completed application present call, not physical scanout.

Android-only render/JNI and Kotlin source have not been type-checked or exercised here. Host green does not establish their correctness. A driver can still stall a present or allocation outside the short pin lock. In-flight surface submission can briefly precede the pinned redraw. No device-loss recovery guarantee or HDR conversion was added.

Best current result is this reversible, additive source handoff, with exact manifest and executable host coverage. Smallest next steps are coordinator Android compilation plus its synthetic GPU fixture, independent source/intent review, capture producer-fence integration, then permitted device acceptance. Do not mark R13 complete until those requirements close.

## SHA256 manifest

```text
85f93c40e38814a854eb62eb72c4b2c59334f93b2a34a2c80ab2e0cfa90030fd  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/app/src/main/kotlin/dev/phosphor/mobil3/FloatingHudService.kt
2f9795291ff5656167a7788d84ac02e4615a6cbc0ff335507a0c097c4efc3190  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt
74125d25a3dc65a1ec8e947e9cd360fb80109959575670f21f68820a5d5e15f0  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/app/src/main/kotlin/dev/phosphor/mobil3/PhosphorNative.kt
b487e58ba59d8f7579241e0db5ade11b4e563a2f03d9d2d2d65bb8025ea61400  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/app/src/main/kotlin/dev/phosphor/mobil3/PhosphorPlayer.kt
f32f4fb0e176e33ce7ddf700f8880e44abdbe29987b62d635dd8e85660641266  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/app/src/main/kotlin/dev/phosphor/mobil3/PlaybackService.kt
5028b281771d83d001dd025fb4d451500a0cd7b5d22f102048c257787088fd05  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/app/src/main/kotlin/dev/phosphor/mobil3/RemotePlayer.kt
de3f9422cfe48d428995d2b5c28ad53589f220b592d056e5d7bcd7a6188eb824  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/app/src/main/kotlin/dev/phosphor/mobil3/settings/SettingsArchive.kt
15446e46a5b3ace20d6e8497e9f76f8d93cc39dc221049073498e9c4c40dd4d0  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/app/src/main/kotlin/dev/phosphor/mobil3/ui/Console.kt
63fd52db4b1af67ff825aaf615eda07afd9ff8a712f6d2073e09307bfe0dc403  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/app/src/main/kotlin/dev/phosphor/mobil3/ui/Gestures.kt
c0d6c7081e8ee0f88072da43d6c548f32b7e607816413ddf500fc0a7b9483d95  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/app/src/main/kotlin/dev/phosphor/mobil3/ui/PhosphorScreen.kt
b3e22823b986a89106cf63cdb484eb63806996f088725d8a59e68dadf9176b72  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/app/src/main/kotlin/dev/phosphor/mobil3/ui/ScopeUiState.kt
80658a30a5a29a1316db0e71dd375cb3411ce850714aea675cb4e2185031138f  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/app/src/main/kotlin/dev/phosphor/mobil3/ui/Sheets.kt
1ac919b5ae6a3930e4b35092783956b272a1d584176f15743542bb797d703b64  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/docs/plans/mobile-expansion/section-05-hold-contract.md
a20c0a44149768cd14d8a09e34039ceff65522bbe9891ce83a9b29bc5e878a6f  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/rust/src/deck.rs
d1a87353493f007cf61c34fcfa367c0534729a60b22ab1ca00d923cbc8d0c68c  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/rust/src/engine.rs
ebbc910a700afae7c07b5806e8ef983621c2d15340cbe486544a7ca8fdb2468b  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/rust/src/jni_glue.rs
8176c0c2881000f77d037689a2bb597fd370635070507008d85b140e383d1f59  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/rust/src/lib.rs
0261ba33a55c3180e4a51ab685ae4748b28db46d28e80f42e27249159cce0d68  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/rust/src/pause.rs
e994aa8de7ecbaa94a8b4acbae4e1d8f35ee277737eec934316d4f7fe3b9cb10  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/rust/src/remote.rs
719bca4c611a4ae15e947fe5a9b0ac1b3be9460ddfe970cbb84c6b77888e432c  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/rust/src/render.rs
fd1bc125eb7ce0a7529615ee0e81aed46621764f2723173e7a8dfdaffe30e8ec  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/rust/src/spsc.rs
f9753da58080eae94382092641f20dfee95299c052e3d73df302ac06e4a6f273  /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3/spec/EXPANSION.md
1b336df3ad2e27ed14593ec3eace47f35edc9b139ef7e550f1f60382ed4ca98f  /home/ben/Dev/ClaudeWorkspace/phosphor/crates/phosphor-render-gpu/src/lib.rs
b7b246cf4b490fa3f628ce02a9766c3189a3962477a3855f678bb513f544b4b9  /home/ben/Dev/ClaudeWorkspace/phosphor/crates/phosphor-render-gpu/src/retained_frame.rs
27bceaa96ea2a0fef7de4f037daf71c873423dcf6e75dcd9fa3d5dcb4d206b75  /home/ben/Dev/ClaudeWorkspace/phosphor/crates/phosphor-render-gpu/src/retained_frame.wgsl
```
