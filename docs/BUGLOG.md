# BUGLOG

Root-caused bugs live here, numbered, with receipts.

## #1 · 2026-07-18 · remote scope "black in real use" = parec burstiness

With real audio on the laptop (Spotify + WAV both feeding the default sink; monitor name
verified matching the relay's), the phone logs `remote connected` then a
`0 · 0 · 1920 · 0 · 0…` segs-per-frame pattern — audio arrives in ~15-frame bursts, so
~14 of 15 frames draw nothing and only GPU decay keeps a ghost visible. The earlier
"AirPods → Spotify Connect silences the laptop" trap is real but separate.
Receipt: `docs/dev/receipts/screen-20260718-152749.png` (live trace, `src · remote`) +
logcat pattern above. **Fix owned by bridge v2 D5:** capture children at
`--latency 20ms` + fixed 1920 B (10 ms) A-frames + relay-side TCP_NODELAY.
Status: root-caused → fix lands in bridge v2 (this session).

## #2 · 2026-07-18 · v1 relay leaks per-client threads/sockets

Phone force-stops leave half-open TCP; relay log showed 3 "connected" clients with 1
live phone; no liveness detection in v1. **Fix designed:** v2 `K` heartbeats both ways +
8 s client-silence teardown (kills capture children too). Status: fix lands in bridge v2.

## #3 · 2026-07-18 · silent connect failure path in startRemote

First app instance (launched without the extra) never logged `remote connected` after a
later `--ez remote true` delivery via onNewIntent, and no failure surfaced anywhere
(startRemote has no failure feedback). Clean relaunch with the extra connected
instantly. Status: superseded by the bridge-v2 client state machine
(CONNECTING/FAILED + fix strings surfaced in the status band).

## #4 · 2026-07-18 · crash-loop on launch: PiP params without the manifest flag

`setPictureInPictureParams` in onCreate without `android:supportsPictureInPicture="true"`
→ IllegalStateException before the first app log line; Samsung's error dialog ate the
launch. ~10 min window in the field (Ben caught it). Root process failure: that one
install went out WITHOUT a launch verification — the only unverified install of the
session, and the one that bit. Fix: manifest flag added; launch now verified by pid +
120 fps log before commit. Law reaffirmed: every install gets a boot receipt.

## #5 · 2026-07-18 · stale mute outlives its session — healthy stream, silent buds

Field report (Ben, live): scope drawing real laptop frames, earbuds silent, A2DP route
active and healthy. Root cause: `Link.muted` is a global the service asserts on
pause/focus events, but nothing reset it at session boundaries — a mute set while one
session died silently inherited into the next session's oboe stream. The watchdog's
muted-mirror then faithfully muted a perfectly healthy stream forever (callback drains
the ring, emits silence). Made worse by bench chaos: media-key toggles during testing
left the flag down. Fix: `connect()` resets `muted=false` — mute is per-playback POLICY,
re-asserted by the service, never link state. Status: fixed (bridge C4 commit); unmute
receipt = play resumed + Ben's ears.

## #6 — 2026-07-18 · "a clean circle draws as 2-3 circles out of sync" (+ "doesn't look 120 fps")
Reported by Ben live, phone beside the desktop scope. Root cause (codex accuracy hunt,
receipts in the wave commit): the BEAM RATE control was renderer-side window SLICING, not
oversampling — one drained capture batch was split into N slices, each getting its own
compute + persistence-adjusted GPU deposit, so N differently-aged partial traversals
coexisted on screen (the 2-3 circles). Bonus defect: a 120 Hz display tick landing between
100 Hz capture chunks did NO advance at all — decay froze, motion stepped at chunk cadence
(the "not 120 fps" feel). At 1× neither fired; Ben's persisted 2× ran both every frame.
Fix: real polyphase reconstruction (desktop DSP `set_sample_rate(48k, factor)` — the
16-tap windowed-sinc streaming upsampler with carried tail), ONE compute + ONE deposit per
display frame, empty windows still advance decay. Regression: engine.rs
`pure_circle_render_path_reconstructs_at_selected_rate` — 440 Hz quadrature through the
real SampleRing at phone cadence; failed before (479 vs 1919 segs, 10/60 frozen frames),
green after. On-device receipt: docs/dev/receipts/accuracy-circle-after.png — one
phase-locked circle. Status: fixed, shipped in the wave commit.

## #7 — 2026-07-18 · SOURCE-picker output switch: stale checkmark + silent stream (NEXUS #5)
Ben's repro: phone SOURCE▸REMOTE picker, pick a different OUT on the ThinkCentre.
Two root causes in relay on_choose: (1) no S-frame echo after C — the picker polls cached
S for its checkmark, so it could never move; (2) choosing a monitor captured that sink's
monitor while the desktop player kept playing into the OLD sink — the new monitor carries
silence. Fix (relay 2.2.0): monitor-choose genuinely switches the desktop output (wpctl
set-default + pactl move-sink-input, deadline+cancellable), every C ends in an honest S
echo, capture swap transactional, failure reverts + best-effort restores routing. Found
live during the fix: pw-dump's 247 KB overflowed the command-runner's un-drained pipe →
empty source inventory (5 s deadline kill); runner now drains concurrently (+256 KiB
regression test). Verified live on interserve: HDMI↔analog round trip, checkmark followed,
spotify sink-input moved both ways, stream stayed up (receipts bug5-*.png + relay journal).
Status: fixed, deployed both machines.
