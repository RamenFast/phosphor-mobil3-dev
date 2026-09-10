# R09 standard microphone and visualization mixer contract

Ratified for implementation by the sole R09 writer under the coordinator assignment, 2026-09-10.
Canonical authority: MOBILE-EXPANSION-PLAN.md section 3 and its deferred-root override.
The source-grounded preparation is retained in ignored dev/scratch/r09-current-handoff.md.
This contract makes its proposed decisions explicit. It does not claim hardware acceptance.

## Complete vertical scope

API29–36, target36. Non-root ASUS Android14 is the physical acceptance target. S25 is excluded.
Keep root policy/helpers inert. No root-plus-mic startup, network/scanning/location, global mute,
speaker monitoring, system writes or parallel capture framework. Existing local/relay playback stays unchanged.

A nonexported MicCaptureService owns the recorder, foreground notification with Stop, route lease,
reader stop completion and wake. MicController becomes its recorder adapter. Activity owns only
visible permission/admission requests and observations. Established mic survives view recreation,
PiP/HUD and eligible linger. Pending starts do not survive retired tasks. START_NOT_STICKY.
API29 uses base foreground service;30+ microphone type;34+ microphone FGS permission and
while-in-use eligibility. RECORD_AUDIO always applies. No overlay-based background mic restart.

CaptureService owns standard projection and its CaptureMixSession. Mic-only service owns a
one-input session. A session has one ring owner and one visualization publisher. Optional attachment
never restarts projection, changes whole-source selection, rearms the ring or clears HOLD.
Mic-only/source replacement still uses request-correlated MicHandoffPolicy and SourceRetirement.
Whole-source retirement waits for readers, routing and publisher cleanup. Failed cleanup blocks
replacement; a join timeout is not a total Android stop deadline or successful retirement.

## Route and permission policy

Enumerate AudioManager source endpoints: built-in, wired headset, USB device/accessory/headset,
SCO, and31+BLE headset. Default is an explicitly verified built-in input. Device loss reports
selected-input unavailable, drops that input and requires explicit retry. Never silently clear preference.
Persist only a private descriptor in excluded phosphor.runtime. IDs are connection-local. Resolve a
unique type/address/name descriptor or require reselection, never choose among ambiguous matches.

Finite format candidates: mono/stereo PCM_FLOAT/PCM16 at advertised supported rates bounded to
8–192kHz. Empty capability arrays permit the declared48k/44.1k/16k/8k fallback trials.
At most12 candidates. Reject nonpositive minimum buffers. Keep buffers/reads frame-aligned.
Only mono/stereo client formats are accepted. Android may negotiate a multichannel device format;
report it separately. No undocumented channel discard or manufactured stereo.
Register routing and recording callbacks before start. Verify actual route after start and before
publication, including all routed devices on36. Requested preference is not proof. Observe client
and device format and silencing separately. Callback lag cannot establish sample-exact provenance.

Prefer recorder device selection. A Bluetooth first-use explanation precedes activation and explains
quality/output changes. No Nearby request for enumeration. No Bluetooth profile/identity APIs,
therefore no BLUETOOTH_CONNECT request in this implementation. Handle platform permission
failures explicitly. MODIFY_AUDIO_SETTINGS supports only the owned communication/SCO lease.
If direct Bluetooth routing fails,31+ selects an available communication output and verifies paired
input;29–30 starts SCO with receiver-first sticky-state handling. Bound establishment to5s per
routing phase. Do not seize an existing non-normal mode. No MODE_IN_CALL or global mode snapshot
restore. Release only owned communication selection/SCO requests and callbacks on every exit.

## PCM, clocks, bounds and clipping

Only CaptureMixSession publishes48kHz stereo float480-frame blocks. Raw readers never append
independently. Blocks retain source generation, attachment generation and the visual epoch sampled
BEFORE read. Epoch change clears queued audio, filter history and pending output. Never relabel an
old block at dequeue. Native pushCaptureRead performs the final original-owner/epoch check.

Queue cap200ms per input plus32-frame FIR support; maximum rate192kHz, mono/stereo only.
Output clock: BOOTTIME, target50ms behind current time. Missing data contributes zero at deadline,
never stale replay. Overflow discards oldest frames with an explicit discontinuity. Long stalls join
current time, with no unlimited catch-up. Input anchors use AudioRecord TIMEBASE_BOOTTIME.
Read-return time is estimated alignment only, labeled as such. Invalid/reset/backwards anchors reset
continuity. Valid anchors estimate rate drift, bounded±1000ppm and50ppm/s slew. Outliers reset,
not unbounded correction. Test60s at±500ppm and bounded jitter; hardware latency remains unmeasured.

Rate conversion uses a32-tap windowed-sinc low-pass interpolator, fractional frame positions,
cutoff0.45*min(1,output/input rate), normalized coefficients. Preserve stereo, duplicate mono with
honest provenance. Tests cover split continuity, alias rejection, bounded filter support and resets.

Independent finite gains0..1, default1. Include off initially. Smooth gain changes over20ms.
Output=(gp*p+gm*m)/max(1,gp+gm), using currently available contributions; unavailable mic does
not halve continuing playback. Sanitize nonfinite input and clamp[-1,1]. Input/output meters are
bounded aggregates. Display auto-gain is not clipping protection. Mixed samples never reach audio
output. Electronic monitoring is absent; acoustic speaker pickup can still duplicate visible signals.

## Failure, settings and UI

Mic failure invalidates attachment before cleanup. Independently healthy standard playback continues
with explicit mic unavailable. Shared RECORD_AUDIO revocation may end both. Projection end stops
its whole composite, never silently turns it into mic-only. Stale timers, status, notification Stop,
route callbacks and stop completions cannot mutate replacement generations. Cleanup retries retain
real dependencies. Task removal with linger off stops both; linger on keeps only established owners.

SourceSheet retains existing design: microphone picker and selected-route status, include mic and
playback/mic visualization levels beside everything-playing, Bluetooth explanation, retry and stop.
Signal check shows each input, negotiated format, route, silence/silenced/mute/stall/unavailable,
timestamp validity and mixer bounds independently. It never requests permissions or starts capture.
HUD transfer is allowed for established service-owned mic, never pending startup.

Portable schema/2 keys: capture_include_mic Boolean=false, capture_playback_level finite Float0..1=1,
capture_mic_level finite Float0..1=1. Strict decode rejects malformed values. Missing keys preserve
existing values. Import is inert even while capture runs: store intent only; explicit local action
applies runtime inclusion/levels. Device identity, explanation acknowledgement, grants and sessions
stay in excluded runtime storage or memory and never in instrument presets or portable archives.

## Acceptance and valid incomplete outcome

Implement/tests cover actual route policy, service admission/retirement, bounded converter/mixer,
pre-read visual epochs, source handoffs, partial failure, stop ordering, permissions, settings and UI
adapters. Preserve existing ownership/wake/native/HOLD/local/relay tests and exact manifest scanners.
Coordinator owns full Gradle/native/boundary gates, independent code review and exact ASUS install.
Record source identities and focused test logs before release. No compile-only hardware claims.
Physical acceptance requires built-in plus each available accessory, two distinguishable inputs,
actual route/format, unplug/retry, mode restoration, PiP/HUD/linger/recreation, clipping/drift/soak.
Other API runtime coverage and absent accessories remain explicit gaps. No S25 substitution.
Blocked outcome names exact failing seam, tested evidence, useful partial result and next action.
