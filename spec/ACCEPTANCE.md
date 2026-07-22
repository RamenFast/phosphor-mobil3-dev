# Acceptance matrix

**Status:** Binding release gates

Every test produces a named receipt under a future `docs/dev/receipts/` run directory. Tests marked S25 require the physical Galaxy S25. Tests marked BOTH run against Play and Fortress artifacts.

## A. Specification and build separation

- **A-01 BOTH:** Build `playRelease` and `fortressRelease`; install both concurrently; package identities and labels are distinct.
- **A-02 PLAY:** AAB scan finds no Shizuku, ADB sidecar, shell-capture, or Fortress-only component/dependency/string marker.
- **A-03 FORTRESS:** Elevated features fail closed with fix-bearing state when Shizuku/ADB is absent.
- **A-04:** Repository scan finds no tracked authored `.py`, Python runtime dependency, or Python invocation in build/run paths introduced by this work.

## B. One causal store and agent parity

- **B-01:** For every visible settings control, schema lists exactly one corresponding typed action or an explicit non-agent exception.
- **B-02:** Human changes HUD mode; UI, snapshot, delta, persistence, and receipt share one revision and provenance stamp.
- **B-03:** Nexus changes theme; UI animates immediately, shows `NEXUS`, and acknowledgement contains the exact same provenance object.
- **B-04:** Agent no-op returns `changed:false` and does not advance revision.
- **B-05:** Human and Nexus contend for one slider; latest accepted revision wins and both actions remain in audit.
- **B-06:** Revoke an active session; Binder/remote control stops within one heartbeat and presence disappears.

## C. Binder and signing

- **C-01:** Untrusted app with same requested Binder action cannot bind or invoke.
- **C-02:** Package-name spoof signed by another cert fails both walls.
- **C-03:** Allowed Nexidex cert with observe only can read but cannot mutate.
- **C-04:** Play, Fortress, and local-dev identities render distinctly.
- **C-05:** Same-phone Fortress control uses Binder/AIDL. Network inspection finds no localhost listener.
- **C-06 PLAY:** First public Play manifest and runtime expose no Nexidex Binder service or remote agent-control entry, while shared state/schema code remains harmlessly internal.

## D. Rotation

- **D-01 S25:** With Android rotation unlocked, free/free follows all four physical quadrants.
- **D-02 S25:** Lock Android rotation; current orientation freezes and Phosphor rotation controls become disabled with an accurate explanation.
- **D-03:** Nexus rotation action while system lock is active returns `system_rotation_locked` and a fix, with no sensor bypass.
- **D-04 S25:** Unlock Android rotation; controls reactivate and retained desired preferences become available.
- **D-05 S25:** Both landscape quadrants place settings on screen right with identical safe-inset spacing.

## E. Direct manipulation and gestures

- **E-01:** Slow 200 px sheet drag renders 200 px plus or minus 2 px each sampled frame.
- **E-02:** Drag trace shows no per-delta coroutine backlog.
- **E-03:** Landscape sheet enters and exits horizontally from screen right.
- **E-04:** One-finger contact held 1499 ms makes no gain/orbit mutation.
- **E-05:** At arm completion, ring and haptic occur; subsequent drag changes the correct parameter.
- **E-06:** Move beyond slop before arm; adjustment cancels and does not mutate.
- **E-07:** Add second pointer during candidate; no one-finger mutation occurs and pinch owns the sequence.
- **E-08:** Active sheet receives all touches and stage state stays unchanged.
- **E-09:** Reduced-motion keeps one-to-one drag and cuts settle within 80 ms.

## F. Themes

- **F-01:** Fresh install primary shelf has six curated rooms in declared order.
- **F-02:** Upgrade from a legacy selected room preserves the room through the Legacy Rooms pack migration.
- **F-03:** Install 25 valid user rooms; all remain selectable and restart-persistent.
- **F-04:** Malicious traversal, oversized asset, executable payload, and unknown major schema each refuse with a specific fix.
- **F-05:** Theme preview updates open sheets and stage immediately; `KEEP` persists, `REVERT` restores.
- **F-06:** Unkept preview reverts after background/resume or session loss.
- **F-07:** `pm3 theme schema` is sufficient for an agent to author a valid pack without source inspection.
- **F-08:** `pm3 theme list` count and IDs equal UI pack tiles.
- **F-09:** Safety, permission, commerce, and provenance text remains visible under hostile theme tokens.
- **F-10:** Nexus-created pack displays honest author/source attribution.

## G. Beam and grid

- **G-01 S25:** Black/AMOLED default grid is visibly legible in a dark room and subordinate to the beam.
- **G-02:** Grid and ground color/alpha change independently and round-trip through restart and agent state.
- **G-03:** Add at least 16 beam stops, reorder, persist, and render without truncation.
- **G-04:** Random mode performs 1000 rolls, never chooses black or banned/out-of-range values.
- **G-05:** Full-spectrum glide traverses hue continuously and differs measurably from direct interpolation.
- **G-06:** v1 three-stop settings migrate losslessly.

## H. Liveness and HUD

- **H-01:** Flowing audio reports/render `live`.
- **H-02:** Pause reports `hold`, freezes breathing, and holds last meaningful tint.
- **H-03:** Prolonged silence reports `no_signal`, shows centered resting beam and source label, drains chrome.
- **H-04:** Relay stall transitions `hold -> stalled/backoff -> no_signal` without frozen-live trace.
- **H-05:** HUD off hides human HUD but agent snapshot remains truthful.
- **H-06:** Every HUD field and agent field tested against the same store object.

## I. UI sounds and OOBE

- **I-01:** App idle for 60 seconds produces zero UI sounds.
- **I-02:** Each enabled category produces only its declared sound on a real user action.
- **I-03:** Master/category disable, system volume zero, and reduced-sound mode suppress correctly.
- **I-04:** UI sounds are absent from captured PCM, clips, relay, and ProjectM input.
- **I-05 S25:** Fresh full OOBE reaches interactive stage within 30 seconds.
- **I-06:** OOBE works in airplane mode and requests no capture/overlay permission until feature selection.
- **I-07:** Safe stereo example is low level, band-limited, and visibly explains L/R vectorscope behavior.
- **I-08:** Skip works on every beat; Settings relaunches tutorial; completion does not nag on restart.
- **I-09:** Captioned narration and stereo example remain under 30 seconds, start at a conservative level, mute immediately, and keep beam geometry synchronized with the explained L/R behavior.

## J. PiP and overlay

- **J-01:** PiP off prevents auto-entry; PiP on plus eligible live state enters according to setting.
- **J-02:** PiP shows live trace, play/pause where supported, and no-signal face on silence.
- **J-03:** Overlay denial degrades calmly to PiP and does not reprompt-loop.
- **J-04 FORTRESS:** Overlay outside region is truly transparent; center color/alpha/fade match controls.
- **J-05:** Interactive and click-through modes are recoverable through notification actions.
- **J-06:** PiP/overlay uses one capture session and one authoritative render/audio pipeline.
- **J-07:** Overlay quiet state obeys frame/burn-in budget.

## K. Audio capture

- **K-01 BOTH:** MediaProjection capture starts only after explicit source choice and valid consent.
- **K-02:** Active valid projection does not reprompt; invalidated/stopped projection asks again honestly.
- **K-03:** Compatibility matrix covers Spotify, SoundCloud app/browser, YouTube Music, browser media, local deck, microphone, and relay hosts.
- **K-04:** Silent opted-out source uses `present_silent_or_opted_out` when evidence supports it, never fake `flowing`.
- **K-05 FORTRESS SPIKE:** scrcpy/shell remote-submix captures or conclusively fails with dated PCM receipts on S25.
- **K-06 FORTRESS SPIKE:** If K-05 succeeds, Shizuku UserService captures stereo PCM as UID 2000 or documents the exact blocker.
- **K-07:** Shizuku/ADB loss stops privileged capture and updates UI/Nexus within one heartbeat.
- **K-08:** Route changes, screen lock, reboot, app force-stop, BT, speaker, and USB behaviors are recorded.

## L. Tailscale and relay

- **L-01:** Fresh user can reach test-link action using concise in-app instructions plus manual link.
- **L-02:** Both home PC and Linux laptop endpoints connect over tailnet and stream.
- **L-03:** Forced network modes explicitly expose tailnet-unreachable failures with auto-mode fix.
- **L-04:** Human HUD and Nexus snapshot agree on dialing/authenticated/connected/silent/stalled/backoff/error.
- **L-05:** Link drop reconnects with bounded backoff and truthful countdown.

## M. ProjectM scope view

- **M-01:** Actual libprojectM Android spike renders a field on arm64 or produces a decision record selecting the native alternative.
- **M-02:** True beam remains above the declared visibility floor at maximum field blend.
- **M-03:** Beam and ProjectM consume one source ID and clock ID; sync receipt proves bounded drift.
- **M-04:** Silence causes the field to settle and true beam to show no-signal truth.
- **M-05:** Thermal ladder reduces field resolution/cadence before beam fidelity.
- **M-06:** ProjectM context loss falls back to true beam without process crash.
- **M-07:** UI sounds do not affect the field.
- **M-08:** Preset selector exposes source/license; Play bundle contains only cleared presets.
- **M-09:** Nexus snapshot reports view, preset, blend, quality, source, clock, and drops.

## N. Play commerce and privacy

- **N-01 PLAY:** Trial starts only on explicit action, grants all features for seven days, and never requests a card.
- **N-02 PLAY:** Expiry does not delete settings/themes and does not auto-charge.
- **N-03 PLAY:** $3.99 non-consumable purchase, acknowledgement, restore, pending, cancellation, refund, and revocation paths pass licensed tests.
- **N-04 PLAY:** No ad SDK, ad identifier, behavioral analytics, or required Phosphor account.
- **N-05 PLAY:** Diagnostics first-run choice and Settings toggle match actual network behavior.
- **N-06 PLAY:** Manifest includes only intended permissions, including a reviewed need for `CHANGE_NETWORK_STATE`.
- **N-07 PLAY:** Data Safety is checked against the exact AAB, backend behavior, purchase verification path, and diagnostics path.

## O. Performance and reliability

- **O-01 S25:** 120 Hz beam target remains achievable in classic scope modes within prior baseline noise.
- **O-02:** Macrobenchmark jank is under the release threshold for sheet drag, settings, theme preview, and Nexus-driven control.
- **O-03:** No per-frame JNI added.
- **O-04:** No additional render/capture thread exists solely for PiP/overlay.
- **O-05:** 30-minute thermal soak in ProjectM auto quality preserves beam and avoids crash/ANR.
- **O-06:** Surface loss/background/foreground/rotation tests pass for classic, PiP, overlay, and ProjectM views.

## Release gate

A release candidate cannot be called complete while any applicable MUST acceptance test lacks a receipt. A failed privileged-audio SPIKE may close with a conclusive report and honest fallback, but it cannot be silently marked implemented.
