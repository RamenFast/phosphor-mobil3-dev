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
