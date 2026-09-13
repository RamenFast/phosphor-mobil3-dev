# Unit 1 independent source review

## Outcome

**Two source blockers remain before Unit 1 acceptance installation.** R1 loses adaptation time between input batches. R2 does not complete the remembered-framing failure/restart contract. R3 is a narrower provenance correction. The implementation otherwise follows the approved ownership and stereo-first direction.

Compared the working changes and new files against `bf851082d97f3fbef00229b9ecec8d8688843e31`. Read `docs/AGENTS.md`, the Unit 1 spec, stereo decision, and all four implementation/audio/gesture/stereo reports. The reports informed navigation, not acceptance. Reviewed the native render loop and shared DSP gain paths, not only the controller. Also reviewed root's compile corrections in `deck_events.rs`, the `modeIndex` trace correction, and the added spectrum warm-up fixture.

This review ran no build, test, device, network, or Git mutation. Build results belong to root. The only write is this assigned report. Source ownership is released to root.

## R1 · Blocker · Empty render drains discard upward adaptation time

**Locations:** `rust/src/render.rs:780-809`, `rust/src/engine.rs:763-790`, `app/src/main/kotlin/dev/phosphor/mobil3/CaptureMixSession.kt:23-35`. The misleading coverage is `rust/src/engine.rs:1177-1203`.

The microphone/mixed publisher produces 480 stereo frames every 10 ms. The renderer drains once per rendered frame. At 120 Hz, and especially unlimited rendering, some render iterations necessarily have no new PCM. `auto_gain_last` advances on every iteration. The controller releases its protection envelope on empty input, but it raises gain only on structured nonempty input. Therefore empty iterations permanently discard their elapsed time from the rise calculation.

For a steady tone with 100 nonempty batches per second and regular 120 Hz rendering, only about 100/120 seconds reaches the rise branch per second. At higher unlimited cadence, the fraction is smaller. This is not a DSP-cost hypothesis. It follows from the producer schedule and update branches. Real scheduling determines its size. The capped render limiter at `render.rs:1042-1051` also makes an ideal fixed-cadence assumption unsafe. This review does not ask to rewrite that older limiter.

The current rate-equivalence fixture supplies a fresh 480-frame tone on **every** 60/90/120 Hz call. It proves the exponential formula under continuous updates, not the real producer/drain integration. The older sparse-drain fixture does not include AutoGain.

**Smallest fix:** Keep protection release on render elapsed time. Account separately for bounded elapsed time represented by the next admitted nonempty input window. Empty render ticks must not raise gain, but they must not discard continuous-source adaptation time. Reset/rebase any pending rise time across source ownership changes, HOLD/surface suspension, manual mode, and actual silence/invalid/unstructured input. Do not borrow a long pause into a later tone or add a second ring drain.

**Required regression:** Drive one 100 Hz producer through 60/90/120 Hz and a faster empty-heavy render schedule. Include batch jitter, quiet-loud-quiet, silence, and starvation. Compare gain at equal wall times. Check the first loud batch before its DSP call. Preserve no gain rise on empty ticks.

## R2 · Blocker · Framing persistence has an unreported loss window and no write-failure owner

**Locations:** `app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt:183-185,1069-1088,1097-1102,2064-2109,2408-2419`. Coverage: `ui/AutoFrameWiringTest.kt:39-64` and `ui/AutoFramePreferenceTest.kt:7-20`.

A gesture or RESET changes UI/native state immediately, then schedules a preference write 250 ms later. That runnable uses the default `SharedPreferences.edit { ... }`, which calls `apply()`. There is no disk-write result, dirty state, failure message, or retry action. `saveTuning()` also uses `apply()`.

A normal Activity stop calls `saveTuning()`, so graceful recreation has a real save path. That does **not** cover process termination immediately after a completed gesture/reset, before the delayed runnable and without `onStop`. Nor does it cover a failed disk write. After such failure the UI continues to present the value as remembered, while a later process restart can restore the old value. The tests only inspect source strings or read a map. They do not exercise persistence completion or failure.

**Smallest fix:** Give this one global preference a persistence owner with a checked commit result and explicit pending/failed state. Flush a completed gesture and RESET without the trailing 250 ms loss window. Preserve the last durable value on failure and offer a small retry path. Coordinate lifecycle, import, and pending writes so an older completion cannot replace a newer value. Avoid fixing this by doing an unchecked synchronous disk write on every pointer sample, which can cause touch/render stalls.

**Required regression:** Complete a gesture, restart before the old debounce would fire, and restore the accepted value. Cover RESET, ordinary recreation, rapid successive edits, commit failure, retry, and a failed write followed by lifecycle autosave. State clearly when a live edit is not yet durable.

## R3 · Medium · Paired measurements are owner-local, but not format-generation-local

**Locations:** `app/src/main/kotlin/dev/phosphor/mobil3/MicController.kt:94-95,149-153,162-173,198-200`; `SignalAggregate.kt:26-37,95-97`; `SignalPresentation.kt:56-58,74-86`. Shared recorder reporting also reaches `CaptureService.kt:269-272,296-303`.

The new paired aggregate resets with its recorder owner or its 500 ms time window. `observe(rec)` can replace the client/device format descriptor during that window without resetting or tagging the aggregate. A platform recording reconfiguration on the same recorder can therefore show the newly observed device format beside paired measurements collected partly under the previous format. The existing tests cover owner expiry, not same-owner format changes.

This does not falsely prove physical independence, because the UI explicitly disclaims it. It does weaken the requested route/format provenance of a stereo receipt.

**Smallest fix:** Publish a format/route generation with the descriptor and aggregate, or invalidate the aggregate on an observed identity change and begin a fresh window on the reader thread. Keep the single-writer meter contract. Do not reset for a mere observation timestamp change. When a descriptor changes during an in-flight read, report unavailable until a fresh matching window exists.

**Check:** Same recorder owner, changed platform device format, and a queued read must not display old paired data as a fresh measurement of the new format. Treat unobserved OEM behavior as unknown, not proof that a transition cannot happen.

## Source-backed checks that are sound

- `MicrophoneRoutePolicy.kt:31-39` groups channel count before rate/encoding. At most eight stereo tuples precede mono within twelve opens. Unknown capabilities retain mono PCM16. Advertised-only unusual rates and mono-only cases remain bounded. The actual `AudioRecord` client channel count must match the attempted count before acceptance (`MicController.kt:79-83`). A mono fallback still exists.
- Selected-device checks, `setPreferredDevice(device)`, `AudioSource.MIC`, route-loss rejection, and cleanup remain in their original owner. Retries do not select an unrelated input device.
- Client format and platform device format are separate. Mono-client duplication is named. Identical non-silent pairs and difference RMS do not claim physical stereo. Stereo format counts, silence, and correlation are not independence proof.
- Raw stereo ingress retains owner/visual-epoch fences. The one render drain remains before visual gain. AutoGain reads samples and updates `computer.gain`; it does not rewrite PCM or add an audible microphone output. The Kotlin stereo fixtures cover both mic-only and mixed render at 44.1/48 kHz. The native unequal-pair ingress fence remains present.
- Current loud input enters the peak envelope before effective gain is selected and before `compute_scope_frame` (`render.rs:801-809,954`). This repairs the old downward glide for the current raw window. Actual mode geometry still needs the checks below.
- AUTO pinch and one-finger drag share `StageZoomPolicy`. They edit the global preference, not manual gain or AUTO. The manual slider retains explicit takeover. Reset changes only the preference. Remote geometry excludes local framing. HOLD inspection, 3D orbit/dolly, VIEW LOCK, mode/glow arbitration, physical edge bands, and chrome rebase keep their earlier ordering.
- Local malformed/missing framing reads use 1.0. Archive schema 2 adds the bounded float; old partial imports omit it. Schema-1 instrument capture, codec, publication, and preference allowlist exclude it. Applying a preset does not intentionally rewrite global framing.
- The large changes in `deck.rs`, `light_cycle.rs`, `remote.rs`, and `surface_policy.rs` are formatting, not a new source/audio/light policy.

## Focused evidence gaps, not new product scope

1. **Useful geometry:** The new controller test asserts numeric gain above 20, while the DSP test asserts finite/nonempty/bounded-count output. Neither measures actual XY or waveform fill. Source math is promising: plain XY uses `gain * min(width,height) * 0.45`; waveform uses `gain * height * 0.21` around two baselines. Add a controlled 0.005-peak controller-to-DSP fixture that checks actual coordinate extents at default/min/max framing. Include the first loud frame and both channels. Run the all-mode finite check at supported reconstruction factors and with history populated. Do not equate an arbitrary 256x fixture with a 90-percent viewport guarantee in every mode.
2. **Noise evidence:** The noise test repeats one selected 480-frame pseudorandom window for ten seconds. Its measured lag correlation is explicitly below the gate. It does not establish behavior for changing windows, short windows, colored microphone noise, or coherent hum. Test multiple windows and minimum sample lengths. With only two nonzero samples in a channel, normalized one-lag correlation can be 1 even for random input. The lag gate is a structure hint, not semantic classification of birds, voice, music, or unwanted noise. The spec states that limit honestly.
3. **Archive failure test:** `SettingsArchiveTest.kt:525-539` tests numeric invalid values through **export**, not valid-checksum import. Its destination map never reaches the failing operation, so unchanged-map equality is not an atomic-import test. The production decoder does call the shared float validator before publication. Extend `rejectsBoth`/the actual import seam rather than claiming that the current map assertion proves atomic mutation behavior.
4. **Boundary fixtures:** The new framing tests do not directly cover the 0.0005 boundary, exact 0.02 crossing, irregular time steps, or a stale-reader rejection connected to controller state. Existing epoch tests protect ingress, but are not a full controller integration proof. Add only the cases needed to close the stated Unit 1 contract.
5. **Gesture tests:** Policy and string-wiring tests do not execute Compose gestures. Keep slow/ordinary pinch, upward/downward single-finger zoom, slider takeover, dead zones, HOLD, and both 3D modes in the phone card. They cannot be accepted from test names alone.

## Remaining ASUS and human evidence

After root closes the blockers and runs its gates, the phone card still owns:

- Exact installed APK/hash/signer and same-package settings retention.
- Actual selected/routed built-in mic, client/device formats, and a bounded non-root spatial fixture for channel-specific response. No saved PCM. Stereo-open success is not physical independence.
- Real quiet structure, ordinary/loud transitions, recovery time, actual trace extent, noise behavior, and real display cadence. Measure geometry and raw/effective/preferred values together.
- Completed-gesture/reset immediate restart, manual mode, source switches, VIEW LOCK/chrome/edge exclusions, HOLD and 3D owners.
- Unchanged audio/system levels and no standalone microphone speaker monitoring. Existing permission grants do not prove fresh consent UI.
- Ben's preferred feel and usefulness. Synthetic fixtures, screenshots, microphone measurements, and Ben's feedback are distinct evidence.

No unrelated backlog is reopened. This is not a public release review. Root retains all build/install/device/Git authority.

## Reviewed snapshot hashes

- `rust/src/engine.rs`: `082abc40c4f70d67411b520ecf779cc07fea174a7d5d84646dc926442fc1d394`
- `rust/src/render.rs`: `1eefa1df80d3e531e671a12c9b3a6556eb9cc426e68d05eb7ad4b110b2c88f85`
- `app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt`: `34bb5bc41cf8f14a5caabf5a163a11ef0f759823e94f4fed1ab7ffe6a5ae5662`
- `app/src/main/kotlin/dev/phosphor/mobil3/MicController.kt`: `72b310cd0d62e897f0c182a9454649261d281d2c8585eee0e37c1f5d4a8176ab`
- `app/src/main/kotlin/dev/phosphor/mobil3/SignalAggregate.kt`: `0880f2f8ce738169832da3a55b494dac22eda9839769a5c28d20ad0d9ac04d95`
