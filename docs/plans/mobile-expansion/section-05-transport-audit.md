# R13 bounded audit

Read-only, Astra/high. Source analysis stopped 2026-09-08 07:15:11Z, within eight minutes. No R15 score or implementation approval.

## Evidence
Mobile a3223b87c8d15fc0da691cfcfe0963ba464d1e92 and shared 7729990bb29f0167ef906d0fbdb44e1e91206955 both resolved as commit objects. Product reads used git show/grep only. Read machine/repo governance, ben-context-standards, folder preferences, and pinned section-05-hold-research.md. No changing implementation, tests, compilation, device, runtime APIs, network, Git mutation, or workers. A computed scratch write was refused before execution. This report is the only successful file write.

Aliases: D=rust/src/deck.rs, E=engine.rs, N=remote.rs, R=render.rs. Kotlin files under app/src/main/kotlin/dev/phosphor/mobil3/: P=PlaybackService.kt, L=PhosphorPlayer.kt, RP=RemotePlayer.kt, LP=LocalPlaybackPolicy.kt, CP=CaptureMirrorPolicy.kt, B=BackgroundLifecyclePolicy.kt. Shared A=crates/phosphor-audio/src/playback.rs. All lines refer to the stated objects, not the older mobile hash in prior research.

## Pause/stop entry map and pin boundary
- Notification/controller transport: P260–266 creates the single MediaSession. Common handlers are L191–196, RP269–279, P1682–1688. Earbud dispatch through Android framework is inferred, not device-proved.
- Noisy: P177–183 records intent then activePlayer.playWhenReady=false. Focus: P187–205 does the same for permanent/transient/duck loss. Gain resumes only with matching transport revision.
- Local: L191–196 calls deckSetPaused. D233–237 changes activation only, no ring clear. Native toggle D294–301 is another entry. L38–41 publication and L140–145 terminal reporting also pause, but must not all manufacture intentional HOLD.
- Remote: RP269–278 dispatches transport, sets optimistic playing, then locally mutes. N641–643 sets mute. N451–470 keeps draining and feeds post-mute zeros to scope. Pin before command/mute, not after the UI callback.
- Capture: P668–686 checks controller/state/actions before direct pause or media-button toggle. P1710–1712 STOP directly invokes pause router, bypassing handleSetPlayWhenReady. Common validated router is the pin seam before P678/P680. Capture STOP is external pause, not projection retirement.
- Local STOP: L216–223 clears face, P756–760 queues Close, P809–812 closes native, D312–319 retires deck. Remote STOP: RP303–307/P1196–1202 disconnect. N607–624 bumps generation, trips scope and clears geometry. N699–700 stop_file is a distinct outgoing file-stop command.
- Task retirement: P1370–1412 and B45–49 separate linger from shutdown. Selection/task revision alone does not prove source replacement.

R684–784 advances before R788 acquisition. R814–815 submits/presents. Current energy is not necessarily last submitted image. Recommended pin is a short CPU ownership transaction on an already committed immutable image record, before transport mutation. Render publication must obey the same boundary, including an in-flight candidate. No GPU/surface/fence wait may occur while holding that ownership lock. Merely queueing a render command before audio pause is not a barrier.

Urgent audible pause must never wait for GPU or render-thread acknowledgement. If an immediate CPU pin cannot be established, silence first and expose the weaker observation boundary or no held frame. The pinned source has no such committed-image primitive today.

External pause can precede P481–488 callback, then P590–610/P1667–1679 publish observation. Earliest current-binding observation is the available seam, not proof of pre-external-stop image. present() does not establish physical scanout.

## Ingress freshness and lock order
E100–107 locks ring then RAW_STEREO. R553–563 drains and meters inside that order. D38–40 capture ingress takes ring alone. D42–49 set_ring_active replaces meter and changes source active, so it is not display-resume.

E120–170 RemoteScopeLease uses per-attempt Arc owner. E175–188 retires it. N198–208 checks cancel/quit/link generation during ingest. Reconnect can reuse link generation. Replacing StereoWindow on visual resume destroys remote_boundary and rejects later valid remote input. Preserve lease and DECK_ACTIVE while clearing visual pending samples/peaks.

Concrete races:
1. N1166 scope worker pops old chunk. Resume clears ring. N1175/E166–170 publishes old chunk after clear because source identity still matches.
2. N1398 checks cancel before N1404 decode. Receive-only audio N1421 then ingests without a display epoch. Old decoded data can cross same-source resume.
3. N1435–1441 queues untagged geometry. R89–93 contains no session/epoch, R539–540 accepts unconditionally. Delayed decoded G can cross resume or retirement after GeometryActive(false) already cleared latest geometry.
4. R167 unbounded mpsc and R292–305 idle-one/active-drain-all processing mean latest-wins R540 does not bound queued memory or frozen source-driven wakes.

N1132 audible queue has 32 packets. N305–306 visual SPSC config is 64 chunks of 16,384 samples. Callback drains under mute N428–470. Bounds alone do not prove freshness. Do not flush audible queues or alter jitter for display-only resume.

Recommendation: separate visual epoch from source identity. Capture epoch before producer work crosses resume, carry through packet/chunk publication, validate under ring/geometry lock, atomically bump and clear at resume. Geometry needs exact SessionShared identity as well as visual epoch and bounded latest storage. Stamping current epoch after decoding old data fails.

D38 samples-only capture API has no producer timestamp/epoch. An old Kotlin AudioRecord buffer arriving after resume cannot be distinguished here. Strict source-time freshness needs producer read boundary or protocol timestamp/fence, not just receipt-time tagging. Those producers were outside allowed scope.

## Local sample-locked invariants
D1–5 declares pause as stop popping, stream emits silence. D73–88 delegates to DeckTerminal.pop_output and zero-fills. D233–237 does not clear/open/restart. Activation and terminal helper internals are outside the allowed read set, so exact callback atomic interleaving remains unverified.

A72–91 blocks writes when audible buffer fills. A96–109 pops/wakes producer. A749–754 feeds scope only after audible push completes. Pause freezes decode through backpressure after spare queue room fills, not necessarily immediately at request time. Never replace this with audible clear, wall-clock catch-up, restart, or seek-to-now. A695–699 position follows produced frames, not physical callback consumption.

D256–267 preserves was_paused across offset reopen. D164 validates before close, D215–218 clears pending on open. P1026–1047 same-target seek checks path, queue object, index, publishes new native open with newItem=false, then deckPublish(!current playWhenReady). P1075–1092 distinguishes a real new item. Changed open ID alone does not imply changed item.

LP3–24 transport/focus revisions, P752 snapshot, L127–131 publication preserve pause-during-staging against old autoplay. LP38 selected advances revision without changing source. P1016–1025 preflight and P886–901 retention distinguish rejected choice from source loss. Do not invalidate HOLD on selection alone.

## Identity and bounded follow-up
Local D124–136 provides open ID plus published/terminal guard. Pair with seek-vs-item reason. Remote E113–170 lease and N181–208 session identity are stronger than metadata labels. N229–236 GeometryMode additionally checks exact current SessionShared pointer. CP36–45 capture binding identity+epoch rejects stale callbacks even after rebinding same controller, applied P474–488. B98–106 capture-owner token identifies projection, not external playback truth.

N527–546 enqueue may fail. N660–670 ignores Boolean. RP275 is optimistic, RP239 reconciles M metadata. No inspected per-command acknowledgement exists. RP224–231 track metadata key is not a transport fence.

Blocked facts: JNI prepared-open/seek mapping, DeckActivation/DeckTerminal callback synchronization, SampleRing internals/capacity, AudioRecord producer boundaries, framework earbud dispatch, strict source-time freshness. Smallest follow-up: authorize only those pinned boundary implementations. Runtime remains root-owned. Two early seam reports delivered. No claim assesses hatchling implementation. Source-audit ownership released at final report.
