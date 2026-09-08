# Section 5 correction 04: requested LIVE versus completed present

## Context

Round3 identified an old candidate that can first submit after the CPU resume or retirement request. Its retained History commit is correctly rejected. Moving another token check before submission cannot eliminate the gap without synchronizing with the driver. This correction adds an observable asynchronous completion boundary, not a physical-scanout or captured-source-age claim.

## Required behavior

1. Resume and source retirement publish a new exact FrameToken and mark the new application image pending. They remain nonblocking CPU operations. Audio transport, source stop and visual ingress fences keep their current order.
2. The sole render owner acknowledges that exact token only through the existing successful current-token History commit, after reset, composition, submit and return from application `present()`. A failed acquire, retained allocation failure, paused commit, old token or superseded token cannot acknowledge it.
3. Already admitted old rendering may finish while the request is pending. It cannot clear pending or become the acknowledged image. Once the exact current token is acknowledged, the single render owner cannot later first-submit an earlier candidate. GPU queue ordering is not a physical panel scanout claim.
4. Display state exposes pending independently of desired HOLD/LIVE and actual audio transport. The Activity status and HUD say `waiting for new frame` until the current application-present acknowledgement. Controls remain usable. Hidden or failed surfaces may remain pending without a fabricated timeout success or driver wait on the UI thread.
5. A new HOLD before completion pins the actual last committed application image. It does not pretend the pending LIVE request completed. Retained identity, BLACK, source-live suffix, destination opacity and inspection keep their existing behavior.
6. No GPU, queue, surface or driver operation moves under DISPLAY or the ring/meter locks. No extra render thread, input queue, recorder, network endpoint or source command is added.

## Checks

Production History tests hold an old admitted token behind a bounded barrier, request resume or retirement, and then release it. Pending must remain set after that stale completion. Only a successful new-token commit clears pending. Repeat with a second boundary, failed acquisition represented by no commit, and an intervening HOLD. UI policy tests distinguish pending, held, black and source activity without changing audio glyphs. Inspect actual render ordering and run the native, Kotlin/Android, lint, dual-APK and existing GPU energy-retirement checks on frozen source.

This closes an application-present completion report only if its state is wired through the actual render owner and visible UI. It does not prove Android scheduling, fresh source timestamps or physical scanout. The buffered-source-time marker experiment remains required, and R13 is not accepted by this contract alone.
