# R13 HOLD / BLACK source contract

## Vision and provenance

Ben pauses the image he just saw, not a later empty or decayed substitute. Inspection changes only a transient view of that image.

Source bases: mobile `a3223b87c8d15fc0da691cfcfe0963ba464d1e92`, shared renderer `7729990bb29f0167ef906d0fbdb44e1e91206955`. This contract follows canonical section 5 and the immutable section-05-hold-research audit. It does not amend that audit or claim runtime acceptance.

## Retention mechanism

The additive public shared `RetainedFrame` API owns a GPU energy snapshot and every composite uniform used for one application present submission. Copy the exact same-format energy texture in the present encoder. No CPU readback, screenshot, audio recording, sample replay or geometry reconstruction participates.

Acquire first before changing retained history. A failed acquire leaves the previous committed frame unchanged. Publish the snapshot identity only after `present()` returns. The identity means last completed application present submission. Neither wgpu nor the inspected Android APIs establish physical panel scanout.

A short CPU ownership lock pins the committed snapshot before controllable transport pause. No GPU allocation, submission, polling, driver wait or transport action runs under that lock. A concurrently completing present cannot replace the already pinned image. An in-flight image can briefly appear before the pinned image is redrawn. This boundary is explicit rather than a physical-scanout claim.

Retained resources live outside `Active` and survive destructive renderer resize and owner handoff. Bound snapshot ownership to committed, candidate and a concurrently pinned frame. Each snapshot validates nonzero dimensions, checked byte arithmetic, device texture limits and a 128 MiB limit. Allocation failure preserves valid history and reports failure. No history means opaque black and `no held frame`.

## Appearance and destination policy

Freeze energy, beam/flash/grid/theme colors, grid geometry, supersampling and original image extent. Inspection samples this fixed image coordinate system with aspect-preserving fit, zoom 1–8 and bounded normalized pan. Reset returns to fit. Live tuning changes apply only after resume.

Destination owns scope background opacity. SOLID uses scope_alpha=1. TRANSPARENT uses the accepted R02 premultiplied surface and scope_alpha=0 with the original shared composite equation. This recomputes alpha from frozen emission rather than fading an opaque retained rectangle. Frozen theme background color remains part of that equation. The alternative, preserving captured opaque background pixels, conflicts with truthful transparent handoff and is not implemented silently.

The named `RetainedTransfer` seam records SDR hardware-sRGB versus manual-SDR encoding. R05 still requires explicit linear-scRGB formats, conversion and compositor validation. This work neither implements nor claims HDR.

BLACK uses RGBA=(0,0,0,1), without beam or grid composition, including transparent HUD. HOLD/BLACK switches retain identity and transform and never command transport.

## Three authorities and ordering

Lifecycle suspension only controls visible rendering. Display pause owns a pinned frame. Transport state remains the actual source observation.

App, MediaSession notification and earbud controllable pause pin before local audible pause or external command dispatch. Urgent audible pause must not wait for GPU work. External commands retain existing capability and acknowledgement policy. A display pause request does not prove external audio stopped.

Unsolicited external PAUSED pins at the earliest authoritative callback. The source may already have stopped. Without a shared stop fence, this cannot prove retention before the unseen external stop. Silence, buffering, unavailable input, errors and transport initialization do not create intentional pause.

Live uncontrollable sources offer display pause and report `display held · source live`. Resume clears visual pending ingress and geometry only, rebases display timing and does not seek or flush audible local playback. Source replacement and explicit stop invalidate both committed and pinned history. Lifecycle-only suspension does not.

Display-only controls use HOLD/LIVE labels. Transport controls retain observed play/pause labels. Debug transport-glyph receipts never substitute display pause for observed playback. A `source live` suffix requires observed playback or an active reader, not merely selected capture metadata. A display-only action leaves audio transport unchanged even when no reader is active.

## Event and resource discipline

No visible surface means no GPU work. HOLD/BLACK branches before meter-driven geometry, DSP, decay, deposit, resting beam and cycle evolution. Unchanged pause blocks for actual damage, inspection, mode, surface or lifecycle events. Geometry ingress uses bounded latest storage and does not wake paused presentation per packet. Surface failure gets bounded retries, then waits for a real event.

R02 generation ownership, cancellation publication and retirement acknowledgement stay unchanged. Images and transforms are memory-only. Portable `pause_display` accepts HOLD or BLACK and defaults HOLD when absent or invalid.

## Acceptance map and handoff boundary

| Requirement | Source/host check | Coordinator runtime check |
|---|---|---|
| Committed identity and failed acquire | Retention state tests and present-order review | Present A, failed B, pin A |
| Frozen appearance and true alpha | Frozen uniform/copy review, shader validation | Solid to transparent over contrasting content |
| BLACK and mode recovery | Mode state tests and clear-only branch review | HOLD A, BLACK, HOLD A |
| Transport ordering/truth | Common-owner call order review | App, notification, earbuds, unsolicited pause, rejected command |
| Inspection isolation | Bounds tests, gesture dispatch review | Pan/zoom/reset and settings gesture priority |
| Recreation and retirement | Ownership diff review | Rotation, PiP/HUD transfer, stale callbacks |
| Fresh resume | Visual-only flush and ingress review | Old/new timeline markers, local audible position unchanged |
| Bounded idle work/resources | Count/byte checks and mailbox review | Stationary pause under source flood, memory and driver behavior |
| Defaults/archive | Portable key review | Clean install, migration, export/import |

Host tests cannot validate Android-only render/JNI code or driver behavior. The final source manifest and report distinguish checks run from tests authored and requirements still blocked.

## Bounded source handoff limitations

At the 2026-09-08 source handoff, coordinator approved destination-owned opacity at 07:05 UTC. Additional allowed paths were RemotePlayer.kt at 07:11 UTC and visual ScopeChunkRing epoch metadata in rust/src/spsc.rs at 07:17 UTC.

Remote visual callback chunks carry an epoch captured before callback work. Receive-side audio and geometry capture it before decode. Scope publication checks epoch under the existing ring/lease guard. Geometry uses a bounded latest mailbox plus a weak exact session owner and validates both at publication and consumption. Visual resume preserves RemoteScopeLease.remote_boundary and never changes audible queues or jitter.

Strict capture source-time freshness remains blocked. CaptureService, MicController and root PCM producers supply samples without a read epoch or capture timestamp. The source handoff clears pending visual ingress but cannot distinguish a pre-resume AudioRecord buffer delivered afterward. The coordinator retains that producer-fence follow-up. Network data already buffered before the receive/decode fence also has no claimed zero-age source timestamp.

No Android build, JNI type-check, Kotlin compilation, GPU/device test, physical scanout check or external transport acceptance ran in this writer window. Pure host checks cover ownership, epoch metadata, preserved remote lease, bounds and WGSL validation only. Allocation/present failure, driver behavior, stable pixels, actual premultiplied handoff, gestures, notifications and earbuds need coordinator acceptance. R13 is not declared complete by this source handoff.

## Capture producer read fence follow-up

The app capture and mic reader sample `VISUAL_EPOCH` immediately before each AudioRecord read. The whole returned batch retains that value. The reader also retains its native producer token from activation, never a replacement token sampled at publication. Native publication checks both values and active source state while holding the same visual ring and meter locks used by resume. No check-to-push gap permits resume to clear the ring and then accept an old batch.

Capture activation installs a runtime-only producer token under the existing source publication lock. Generic source replacement resets the meter owner and invalidates the token. Visual resume clears measurements but preserves the producer token. Publication cannot activate a source. Read epoch 4 followed by resume epoch 5 accepts zero samples from the held epoch-4 read. A subsequent epoch-5 read is accepted only for that same active producer. Current-epoch input with no active owner is rejected.

Preserve reader stop, join, release and mic handoff order. Do not create another recorder, capture thread or runtime control endpoint. The fence changes only visual ingress. Audible queues, local position and remote jitter remain unchanged.

This runtime epoch proves ordering at the app read call, not the age of samples already buffered by AudioFlinger. Root `AudioPolicyMain` reads before emitting kind-15 PCM. `Protocol.pcm` includes identity, sequence, format and count, but no producer read epoch. A future root fence requires a fixed visual-epoch control, producer acknowledgement and epoch-bearing PCM across Java, supervisor and app validation. This bounded follow-up does not change that protocol or label pipe receipt time as source-time freshness. Network data buffered before its existing receive fence has the same explicit source-time limitation.

Required host evidence exercises the production native acceptance primitive with held-read/resume and source-replacement races, plus current inactive rejection. A cached pure JVM fixture exercises the production reader loop with an actual held read. Android/JNI compilation, device behavior and canonical whole R13 acceptance remain coordinator gates.

At 07:51 UTC this follow-up passed 87 locked/offline native host tests, including four production capture-fence tests, and four cached pure Kotlin/JVM reader tests. The existing WakeOwnershipTest adapters were updated but that Android-dependent suite was not run here. The private handoff records exact source hashes, commands and logs. No helper protocol, root session, audible queue, recorder lifecycle, Gradle or device change belongs to this follow-up.
