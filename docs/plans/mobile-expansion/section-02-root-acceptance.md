# Section 2 product acceptance procedure

This is a coordinator checklist, not a passed receipt. Follow the [product contract](section-02-root-product.md) and [canonical R01 plan](../../../MOBILE-EXPANSION-PLAN.md). Protocol1 feasibility receipts do not accept protocol2 product behavior.

## Ownership and entry gate

1. Obtain the implementation writer's explicit source and build-slot release.
2. Inspect the diff and run host tests against that exact source.
3. Run Android unit, lint, engine and both debug APK build tasks together.
4. Review each required behavior against a named test, not an aggregate count.
5. Commit only the reviewed source, then rebuild both APKs from the clean commit.
6. Hash the source archive, app APK and companion test APK.
7. Record package, version, signer, helper content identity and embedded DEX digest.
8. Verify production variant packaging separately without release signing or publication claims.

Use the Gradle wrapper and pinned environment. Only one Gradle task graph may run. No worker runs Gradle or device commands without an explicit slot transfer. Installed proof remains tied to the exact APK, not the newest documentation commit.

## Device preflight

- Use USB serial `R3CY90HEZ3M` explicitly. Do not operate wireless concurrently.
- Save fresh preferences, process, audio, policy, service and projection observations in the ignored recovery directory.
- Compare exact installed package/signer and the original recovery hashes before replacing anything.
- Refuse a fixture if a source, helper, unresolved retirement, unrelated started player or communication mode is active.
- Recheck current output route and volume. Do not restore a historical route or level.
- Keep `/system`, `/vendor`, boot/vbmeta and raw aliases read-only. No remount or root-profile/security change.
- Install the exact same-package app through `dev/pm3` and verify its installed SHA-256 and signer.

Raw system dumps, private device identities and audio data are not public receipts. Keep aggregate measurements only. No global logcat replacement or persistent diagnostic process is needed.

## Requirement-linked checks

| ID | Action and observation | Pass condition |
|---|---|---|
| P01 | Cold-launch with local root switch off. Inspect process, service, policy and new private helper publication. | No root probe, grant request, helper, root service or capture consent. Ordinary UI works. |
| P02 | Import a valid portable archive while root is off, then while already locally configured. Compare the two exact local root fields. | Import changes neither field and starts no authorization, source, helper or permission dialog. |
| P03 | Use the existing five-tap bestiary reveal and initial root-enable flow. Inspect the finite authorization result and all process UIDs. | Clear explanation, genuine existing grant, unprivileged app, no audio helper/policy for authorization-only. No claimed manager prompt or profile change. |
| P04 | Start Everything playing through the real UI with authorized root enabled and no player started. | Root specialUse foreground owner, no MediaProjection or redundant recording dialog, one helper. Idle/no-input is not an authorization error. |
| P05 | Run the reserved fixed `ROOT_CAPTURE_SYSTEM_TEST` through the DUMP-protected debug receiver after idle preflight. | Own-UID BY_SYSTEM tone is actually started and its playback head advances. Product service and private decoder deliver actual 16kHz mono PCM. Post-JNI ring has fresh 48kHz duplicated-mono 997Hz aggregate evidence. |
| P06 | Run reserved fixed `ROOT_CAPTURE_NONE_TEST`, then rerun P05. | BY_NONE remains excluded while actual fixture playback advances. Fresh ring window is silent, not old history. Subsequent positive passes. No unsupported universal-capture claim. |
| P07 | Observe the live root policy, AudioTrack route, AudioFlinger and service while P05 runs. | Tagged player mix has LOOP_BACK and RENDER. User output stays routed and advances. No projection token or app recorder is falsely reported. Physical audibility is separate. |
| P08 | After the real UI starts root, recreate the activity and enter/leave PiP without selecting a new source. | Source, metadata and helper owner IDs stay stable. No second helper/reader, permission chain or premature ring-off occurs. |
| P09 | Exercise root to mic/local/standard, then reverse, using controlled sources and normal stop rendezvous. | Old helper, policy and reader retire before the new producer publishes. No late callback changes the new ring, metadata or wake owner. |
| P10 | Turn root off during authorization, STARTING, streaming and idle. Exercise explicit standard fallback after an observed root error. | Pending callbacks cannot re-enable root. Actual root cleanup completes. Standard consent occurs only after explicit choice. |
| P11 | Remove the task with linger off, then repeat with linger on using temporary owned settings. | Off retires source and policy. On retains only the real source and rebinds without duplication. Restore only the temporary changes after checking current ownership. |
| P12 | Kill only the owned app process during a controlled active product fixture, then retry on the same installed artifact. | Both privileged descendants disappear, policy is removed, fresh process recovers. Killing the whole package cgroup is not parent-death proof. |
| P13 | Exercise fixed host protocol faults and bounded helper-stop failures. Distinguish synthetic faults from actual device faults. | Partial/oversize/stale/duplicate/sequence/lease failures reject. Cleanup uncertainty prevents replacement. No stale PID is signaled. |
| P14 | Inspect root-on silence, EOF, terminal-frame/child-exit races and repeated stop. | Silence remains a truthful usable state. Stop is idempotent. Final status cannot outrun cleanup, and helpers do not leak. |
| P15 | Compare preferences, current route/volume, owned processes, service, policy and projection after each test. | No unowned state changes or owned helper residue. Record any intended local root choice separately from preserved existing settings. |
| P16 | Run distinguishable controlled left/right signals through the full-fidelity replacement, then SoundCloud through the normal root UI. | Real independent channels reach the scope. Duplicated mono fails. SoundCloud has actual moving input while audible playback remains usable. The prior mono fixtures remain limited checkpoint evidence. |
| P17 | Compare actual recorder, private PCM, native input and selected beam-reconstruction rates. Exercise the existing48/96/192kHz options. | Each rate is named honestly, channel distinction survives conversion, and existing reconstruction works. Negotiated hardware/OEM limits are explicit. Upsampling does not claim recovered source bandwidth. |
| P18 | Measure normal playback and the candidate on the same physical output. Retain source/capture/monitor frame-clock pairs, buffer occupancy, underruns and presentation timing. Optimize the largest measured added delay, then repeat stereo and cleanup checks. | Added software delay, route latency and end-to-end audible/beam alignment are reported separately. A timestamp estimate is not acoustic proof. No invented latency number, growing queue, channel loss or sample dropping qualifies as realtime acceptance. |

The two reserved developer policy-test action names become executable only after their implementation and exact-artifact checks pass. They accept no extras, share the existing single-flight lock and test only controlled app-UID audio. Their service, decoder, normalizer and native ingress must be the product path, not a copied substitute. Do not infer post-JNI success merely from a Java push counter. Existing `scopeStats()` requires renderer activity, so a no-surface test needs a bounded read-only native aggregate or a separately verified visible surface.

The separately reserved `ROOT_CAPTURE_TONE` action exists only for real UI acceptance. It plays the same fixed own-UID tone for five seconds while the normal UI's root owner is already active. It creates no helper, session or service, changes no source or setting, accepts no extras, and uses the same DUMP protection and fixture lock. Its receipt must prove playback progress and cleanup. The coordinator first starts root through the actual UI, then injects this playback-only fixture. Running `ROOT_AUDIO_PROBE` concurrently would create another helper and invalidate that check.

## Failure coverage without changing root configuration

Denial, provider mismatch, malformed identity, missing SELECT, stale generation, invalid PCM size/sequence, heartbeat loss, helper progress loss, output backpressure and direct-child reap rules need independent host cases. Actual provider revocation would require changing an existing grant and is outside the current no-profile-change boundary. Do not claim a mocked revocation as a live revocation pass. Real owner death, explicit stop and recovery remain required device checks.

A no-player session and zero nonblocking reads can be legitimate. Helper-loop liveness must not be equated with nonzero PCM or user playback. A stuck Binder/read must still expire within the documented bound. The watchdog cannot rely only on healthy app heartbeats.

## Closure

- Map each P-row and each R01 contract clause to observed evidence or an explicit unresolved limit.
- Retain exact failures before corrections and identify every rebuilt artifact separately.
- Run a separate Astra/high scored R01 critique after implementation and coordinator acceptance checks.
- Apply the canonical maximum-four-round rule and stop early at a score of at least8.
- A score, source gate or production manifest check does not imply release signing, store readiness or physical audibility.
- Continue later approved sections even when a hardware-only observation remains explicitly unresolved.

Ben's04:21:19 stereo requirement supersedes any interpretation that the16kHz duplicated-mono checkpoint completes R01. P16 andP17 are required, not optional polish. SoundCloud's standard-capture failure is Ben's observation until independently reproduced. The coordinator has not yet tested root capture from that app.

Ben added minimal added latency at04:38:43. P18 is required. For a monitored path, match each accepted source-frame range to the same monitor-frame range across partial writes. Exclude setup silence and timestamp reset periods. Record actual buffers and the maximum pending frame count. Preserve uncertainty when Bluetooth timestamps do not establish physical presentation.

Measure rather than choose a buffer by intuition. Start with the smallest bounded stable configuration, observe underruns and queue delay, and change one buffer or scheduling parameter at a time. Compare the same output route without changing the user's volume. A direct-copy alternative may avoid re-render delay, but cannot bypass the existing ownership and cleanup gates.
