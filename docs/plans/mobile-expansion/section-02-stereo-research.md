# R01 stereo and opted-out capture paths, source-only

Date: 2026-09-08. Research worker: stallion, GPT Astra/high.
Report: `/home/ben/.jcode/scratch/root-audio-stereo-20260908/REPORT.md`.

## Decision

**The any-app stereo goal remains open. SoundCloud is not established as blocked.**

The 16 kHz mono limit belongs to `allowPrivilegedPlaybackCapture(true)`, not every root-accessible audio path. Removing that flag permits fuller formats, but does not remove the opt-out checks from `LOOP_BACK | RENDER`.

The most practical next controlled candidate is **a registered, UID-scoped `LOOP_BACK`-only MEDIA/GAME mix, privileged flag false, 48 kHz PCM16 stereo, with a distinct-attribution-UID AudioTrack monitor**. AOSP excludes neither capture flag at this primary-routing gate. This diverts original playback and re-renders it. It is not transparent duplication. It needs measured stereo, feedback exclusion, gain, audibility and recovery acceptance before product use.

A stronger but less mature native-duplication lead exists: **root-accessible AudioFlinger `updateSecondaryOutputs`**. The inspected Android16 service gate admits root, and its per-track tee copies source buffers downstream of policy matching without rechecking capture flags. This is a Binder operation, not injection or hooking. However, a safe native interface, exact track identity, preservation of existing tee outputs, policy-manager synchronization, and crash cleanup are not established. Do not execute it from this report.

No system/vendor/boot writes, remount, module, replacement platform binary, audioserver restart, injection, hook or DRM operation is required by the scoped re-render hypothesis. None was attempted.

## Evidence boundary

Read Ben context standards, machine AGENTS, project docs/AGENTS, canonical `MOBILE-EXPANSION-PLAN.md`, current R01 product contract, and the prior private AOSP report. No root AGENTS exists in this repository. The newer user clarification supersedes any mono-only completion interpretation in those documents.

Inspected 20 distinct primary-source documents, listed below. Targeted re-excerpts use the same documents. Android capture-gate comparison directly covers API29 and API36 release endpoints. The earlier report contains API30–35 Java compatibility checks. This pass does not establish native behavioral equivalence for every intermediate version or Samsung firmware.

Actual validation: source inspection and cross-file tracing only. No compilation, unit test, Android execution, ADB, GUI, playback, service changes, Git mutation, Python, installation or repository edit occurred. Public implementation text was inspected, not copied into a runnable artifact. Only this report is newly written.

Coordinator-supplied observations, not independently verified by this worker:
- Standard capture displays SoundCloud silence while Spotify works.
- Installed SoundCloud is 2026.08.13-release, code368060, target36. Effective player flags remain unresolved.
- The S25 bare-root framework attribution is UID1000/package `com.samsung.InputEventApp`, not raw UID0.
- Existing 48/96/192 kHz selector controls 1x/2x/4x beam reconstruction over a fixed48k input. It is not a capture-rate selector.

## Exact flag and routing facts

### What the attributes mean

`AudioAttributes.capturePolicyToFlags` on Android16 maps:

| Player policy | Effective flags from this conversion |
|---|---|
| BY_ALL | Clears NO_SYSTEM_CAPTURE and NO_MEDIA_PROJECTION |
| BY_SYSTEM | Sets NO_MEDIA_PROJECTION, clears NO_SYSTEM_CAPTURE |
| BY_NONE | Sets both flags |

`AudioPolicyManager::getOutputForAttrInt` ORs the UID-level allowed-capture policy into the result attributes before mix selection. A permissive new AudioTrack cannot override a more restrictive process policy through this path. Per-player and process policy need separate fixture controls. Manifest opt-out is a distinct effective-policy source, not evidence that an app uses BY_NONE. SoundCloud's actual player flags must be observed rather than guessed from silence or target SDK.

### Where exclusions actually apply

Android10 `AudioPolicyMix.cpp:208–228` and Android16 `:424–452` put these checks inside `is_mix_loopback_render(mix->mRouteFlags)`:

1. NO_SYSTEM_CAPTURE rejects the match unconditionally.
2. For MEDIA/GAME, NO_MEDIA_PROJECTION rejects unless the mix's privileged media flag is true.
3. Android10 allows UNKNOWN/MEDIA/GAME at this gate. Android16 additionally has a separately authorized VOICE_COMMUNICATION branch.

Changing caller UID to root does not bypass these conditions. There is no caller-privilege exception in this matching code.

Crucially, **LOOP_BACK without RENDER does not enter this block**. It still must satisfy the configured usage/UID criteria and routing/format requirements. This is the difference between a privileged rerouting policy and the restricted secondary playback-capture policy.

`getOutputForAttr` classifies combined-route mixes as secondary outputs. LOOP_BACK-only is a primary-output candidate. On Android16 an explicit requested device has higher priority than dynamic primary routing. Therefore a player with explicit routing can be another exception. Root does not make this branch a universal capture guarantee.

### Fidelity is negotiated, not inferred

`AudioMix.canBeUsedForPrivilegedMediaCapture` and AudioService registration enforce at most16k, one channel and at most2bytes/sample when privileged capture is enabled. Disabling that flag avoids this particular cap.

Android16 `AudioPolicyManager::registerPolicyMixes` creates input/output submix profiles using the requested mix rate/format and stereo channel masks. It explicitly says both LOOP_BACK and combined routes share the submix backend.

The inspected legacy Android16 submix HAL supports mono/stereo masks, defaults to48k PCM16, and sanitizes format to PCM16. Its rate list is 8k,11.025k,12k,16k,22.05k,24k,32k,44.1k,48k,192k. The192k entry is commented for IEC61937 E-AC-3 encapsulation. This is not evidence of192k music fidelity. 96k is absent. Samsung may use a different AIDL/HIDL/vendor backend.

48k stereo PCM16 is a grounded first request. 44.1k is a separate candidate. Neither proves bit-perfect source fidelity, original hardware rate, float precision or preservation of effects. A 48k source can be mixed/resampled even when the returned AudioRecord says48k. Report requested format, actual record format, actual thread/HAL format if observable, and measured channel content separately.

## Candidate comparison

| Candidate | BY_SYSTEM / BY_NONE | Stereo/rate | Phone playback | Conclusion |
|---|---|---|---|---|
| Combined route, privileged flag true | SYSTEM eligible, NONE excluded | At most16k mono | Native primary remains | Not the stereo solution |
| Combined route, privileged flag false, real root privileges | Both excluded by matching gate | Fuller requested formats possible | Native primary remains | Useful BY_ALL stereo control, not an opted-out solution |
| LOOP_BACK-only, privileged flag false, source UID + MEDIA/GAME | Both pass this flag gate |48k stereo grounded, actual negotiation unknown | Diverted until separate monitor renders | Best bounded next probe |
| Bare REMOTE_SUBMIX address0 | Not the combined-policy flag gate | scrcpy requests48k stereo | Normally diverted | Too broad and easy to feed back, not first choice |
| Call-redirection markers | No media-only bypass established | Irrelevant to the stated goal | Call route semantics | Reject as a MEDIA/GAME technique |
| Direct AudioFlinger secondary tee | No NO_* recheck in inspected tee functions | Patch uses source format before sink mixing | Existing primary can remain | Strong lead, unsafe to claim ready |
| Live hooks/injection/platform replacement | Could change where PCM is intercepted | Implementation-dependent | Implementation-dependent | Outside authorization, not proposed for execution |

### Why call redirection does not solve this

Android16 `AudioMixingRule.isForCallRedirection` requires an attribute marked for redirection plus a VOICE_COMMUNICATION/VOICE_COMMUNICATION_SIGNALLING usage rule, or VOICE_COMMUNICATION capture-preset rule. A marked MEDIA rule is not sufficient. AudioService then requires CALL_AUDIO_INTERCEPTION. This alters authorization, not the native MEDIA/GAME flag test. Adding voice rules to unlock permission changes scope and is not a permissible media-only shortcut. Keep all voice, signalling and call-redirection fields unset.

### scrcpy's two different paths

Pinned scrcpy v3.3.1 `AudioPlaybackCapture` uses a hidden AudioPolicy, MEDIA usage and no privileged-media flag. `--audio-dup` selects combined routing. Without duplication its playback source selects LOOP_BACK only. Its API33 floor is scrcpy implementation compatibility, not proof that AOSP privileged AudioPolicy starts at33.

`AudioDirectCapture` configures a bare source, with compatibility workarounds and an API30 floor. For output, scrcpy documentation maps that source to REMOTE_SUBMIX and says device playback is disabled. `AudioConfig` requests48k, stereo PCM16. A request is not measured fidelity.

The playback implementation calls a voice builder method after building the rule. Do not cargo-cult this call. It supplies no media-only bypass and is not needed for the proposed mix. scrcpy's broad docs warning that apps can opt out must not replace the route-specific AOSP analysis above.

### Existing root implementation and its trap

`cUDGk/aroute` commit66811dd0a1d8863a8a9f4b887f7a396decf48249 opens bare REMOTE_SUBMIX at48k **mono** and can feed an AudioTrack. Its source explicitly warns that its STREAM_MUSIC monitor feeds itself back through the same submix. It does not prove the vectorscope outcome and should not be copied.

The non-obvious improvement is not a more elaborate global submix. It is a tagged policy with bounded MEDIA/GAME selection and the monitor's actual attribution UID excluded, so the monitor never enters the captured mix.

## Proposed feedback-free scoped re-render design

This is a proposal, not implemented or validated behavior.

1. Use the existing fixed helper/supervisor identity and bounded ownership protocol.
2. Resolve actual framework attribution and actual resulting monitor-track UID before admitting capture.
3. For the fixture, include only the controlled source UID10401 plus MEDIA/GAME usage.
4. Require the monitor's actual track UID to differ from10401. Do not infer it from Process.myUid().
5. For a future product mix, use MEDIA/GAME with one exclusion for the verified monitor UID.
6. Do not combine include/exclude rules within the same UID dimension. Use the controlled include or product exclusion variant.
7. Keep privileged-media, voice and call-redirection flags false. Use LOOP_BACK only, not address0.
8. Create the tagged AudioRecord through the registered policy at48k PCM16 stereo.
9. Create a separate monitor AudioTrack at the same PCM format with unit per-track gain.
10. Route the monitor to the observed original physical endpoint. Verify its actual routed device after playback starts.
11. Preserve the existing physical stream volume. Do not raise the phone volume or replay via a call/alarm stream.
12. Use one bounded PCM read. Feed the unchanged frames to the monitor and instrument without a second drain.
13. Keep monitor backpressure bounded. Any stall, route mismatch or potential feedback retires the session.
14. Observe route changes. Retire and restore before admitting another endpoint rather than guessing Bluetooth behavior.

A fixed exclusion of UID1000 can omit MEDIA/GAME produced by other UID1000 components. A monitor in the app UID can omit that app's own sources. This is a real coverage tradeoff. The current all-app aspiration stays open until an identity design covers it honestly.

### Gain and effects

`createAudioRecordSink` adds the fixed-submix-volume tag. AudioService's `forceRemoteSubmixFullVolume` maintains a reference-counted, Binder-death-backed full/fixed-volume state for the REMOTE_SUBMIX device type. It is not an instruction to change physical speaker volume. The service has an exception when a legacy submix is active, so overlapping address0 capture is a reason to reject the probe.

Inference: original per-player gain is applied when the source is mixed into the primary submix. Full submix stream gain should avoid applying the user's physical media volume twice, and the monitor should apply physical media volume once. This is not yet an observed Samsung gain contract. Do not apply arbitrary compensation or automatically force monitor volume higher.

Require a separate volume observation: source gain1.0 versus0.5 should produce the expected6.02dB capture ratio. Monitor gain stays1.0. Compare baseline and monitored physical media-volume indices, route gain and audibility. At two user-approved nonzero media-volume levels, compare baseline versus monitored acoustic/output level before claiming no double attenuation. Player gain testing alone cannot prove this.

Per-session effects, spatialization, ducking, device DSP and Bluetooth absolute volume can move or be reapplied after rerouting. Raw stereo preservation is not equivalent to exact audible post-DSP mix preservation.

### Offload and preexisting tracks

Combined-route secondary capture rejects non-linear PCM or compressed-offload requests in the inspected manager. The tee strips FAST, DIRECT and COMPRESS_OFFLOAD flags from its patch track. Neither fact proves a decoded PCM copy exists for every offloaded source.

The manager comments describe transferring existing playback after track invalidation when capture starts. An app may reopen in PCM, remain explicitly routed, fail, or retain an unsupported vendor path. Probe fresh controlled PCM first. Then test already-playing ordinary media and SoundCloud separately. Never infer DRM from silence, and never promise protected content or decrypt anything.

## Exact finite two-tone proposal

Coordinator executes only after incorporating the route and monitor prerequisites. No device actions are authorized to this worker.

### Fixtures and matrix

Use a recreated, non-offloaded MODE_STREAM stereo AudioTrack, usage MEDIA, source process policy BY_ALL, fixture UID10401. Save the previous fixture-process policy and restore it in finally. Per-track capture policy varies. Exclude GAME from the first fixture's usage, then repeat one passing case as GAME if MEDIA succeeds.

For exactly5seconds at48000frames/s, generate L=0.125*sin(2*pi*997*n/48000), R=0.125*sin(2*pi*1499*n/48000), converted to PCM16. Apply10ms start/end ramps. Both tones must exist simultaneously during the steady section. Preserve actual source write counts and playback-head/timestamp advancement.

Run these fixed cases, one owner at a time:

| Case | Route / flag | Track policy | Prediction |
|---|---|---|---|
| A | Combined / false /48k stereo | BY_ALL | Two independent tones, original physical playback |
| B | Combined / false /48k stereo | BY_SYSTEM | Excluded despite progressing source |
| C | Combined / true /16k mono | BY_SYSTEM | Optional already-established low-fidelity control, never stereo acceptance |
| D | LOOP_BACK / false /48k stereo + monitor | BY_SYSTEM | Independent tones and audible monitor |
| E | LOOP_BACK / false /48k stereo + monitor | BY_NONE | Same, because this matching gate lacks both exclusions |
| F | LOOP_BACK / false /48k stereo + monitor | BY_SYSTEM, original player gain0.5 | Capture tones6.02dB below D within1dB |

A and B can reuse existing verified evidence if it truly matches these exact conditions. Case C need not be rerun merely to reproduce known mono. D and E are the decisive new tests. Limit each setup to10seconds, tone playback to5seconds, acquisition including tail to5.5seconds, and cleanup to8seconds. Abort the matrix on routing, feedback, physical-audibility or cleanup failure. Do not run more than these six cases automatically.

Measure the middle3seconds after settling. Retain aggregate measurements only, not PCM recordings:

- Actual input frames, read-progress sequence, negative errors, timestamps, actual rate, channels and encoding.
- Independently compute power at997Hz and1499Hz for each channel using a Hann-windowed frequency estimator.
- Require intended-channel tone power at least30dB above the same tone in the opposite channel.
- Require both channel RMS values above0.001 full scale and no clipping. Do not require L==R or normalize channels together.
- Require absolute interchannel correlation below0.1 over the aligned steady window.
- Require frame/timestamp progression consistent with48000frames/s within1percent after accounting for measured startup/drain boundaries.
- Require the post-JNI instrument input retains separate L/R. Reuse its one ring drain, not a competing observer.
- Reject duplicated mono even if the API reports two channels. Reject a fabricated48k rate derived from16k upsampling.
- Verify physical-route playback with monitor timestamp progress plus independent audibility observation. A successful AudioTrack write is not proof of audible sound.

If a separate44.1k capture option is later added, repeat only D and E at44100 using freshly generated source samples and separate rate receipts. The current48/96/192 beam setting must not silently change these input facts. 96/192 capture claims require a new backend capability investigation.

### Feedback, stop and restoration observations

Before registration, record existing route, media-volume index, active relevant mixes, source owner, and actual source/monitor identities. Reject communication mode, calls, concurrent legacy submix, other capture owners, and unresolved cleanup.

Stop the fixture at its5second deadline. Continue bounded measurement for at most500ms with no new source frames. Repeated tone energy, growth, or monitor-to-submix routing is failure. One finite buffered tail is not by itself feedback.

For normal stop, invalidate the publication generation first. Stop and flush the monitor before restoring original routing, so queued monitor audio cannot overlap resumed original playback. Stop/release the AudioRecord, release monitor resources, synchronously unregister the policy in finally, close IPC and join the bounded helper threads. The exact disconnect timing must be observed because stopping the record can itself trigger rerouting.

Require the tagged policy and recorder to disappear, original physical routing to return, full/fixed-submix-volume reference state to return to baseline, and no owned root/ART process or PCM publication to remain. Confirm original playback resumes without new volume settings or an audioserver restart. Do not treat a killed helper as clean shutdown evidence.

After D/E pass, run three separate finite lifecycle trials with BY_SYSTEM: normal stop while the source continues, owned-helper death while the source continues, and app-owner loss. Each has at most5seconds of capture and8seconds recovery observation. The death tests must signal only the owned process. Failure latches replacement closed. No root-manager profile, SELinux or service restart is a recovery step.

Only after those pass, use already-installed SoundCloud as the meaningful media smoke test, with explicit coordinator ownership of playback. Observe actual effective flags if available, real L/R PCM and source rate, audible output and restoration. SoundCloud silence under standard capture is the reason to test this path, not proof it will fail.

## Native tee lead, facts versus unknowns

Android16 AudioFlinger facts:
- `onTransactWrapper` places UPDATE_SECONDARY_OUTPUTS in its system-component gate, not the list of policy-manager-only transactions rejected over Binder.
- `isServiceUid(uid)` is `multiuser_get_app_id(uid) < AID_APP_START`, which includes UID0.
- `updateSecondaryOutputs` finds a live playback track and delegates to `updateSecondaryOutputsForTrack_l`.
- The latter creates a PatchRecord/PatchTrack with the source track's sample rate, channel mask and format.
- `Track::releaseBuffer` invokes `interceptBuffer`, which copies frames into those patches without NO_SYSTEM_CAPTURE or NO_MEDIA_PROJECTION checks.
- `setTeePatchesToUpdate_l` replaces the tee list. Updates destroy earlier patch tracks. This is shared state, not an additive owner-scoped tap.

Inference: a valid, root-originated Binder update to an owned submix output could capture opted-out PCM while leaving its original physical playback thread active. It is a better fidelity/audibility architecture than user-space re-render if it can be owned safely.

Unresolved blockers: exact device-native interface and identifier semantics, reliable UID/usage ownership of discovered tracks, safe sink creation, unrelated secondary-output preservation, APM reconciliation, source replacement races, no Binder-death ownership for the manual attachment established here, and actual fast/direct/offload behavior. No API29–35 direct-tee compatibility claim is made. No ready-to-run command is supplied.

Do not present direct attachment as already safe, and do not open or close global AudioFlinger outputs directly. The same wrapper explicitly rejects several such policy-manager-only transactions regardless of root.

## Owned combined-route sink plus manual tee: cleanup audit

Coordinator asked whether an ordinary full-stereo combined-route policy could own the sink while a direct root update attaches only a controlled opted-out track. **Architecturally plausible, but unavailable-for-safe-probe on this evidence.**

The owned-sink half has a concrete lifecycle: Android16 AudioService `AudioPolicyProxy.binderDied` calls `release`. Release removes the registered mixes. `AudioPolicyManager::unregisterPolicyMixes`4282–4335 removes that mix, disconnects both addressed remote-submix devices, and removes its input/output profiles. These operations concern the policy's private address, not a global address0 capture.

If that causes the sink output to close, AudioFlinger `closeOutput_nonvirtual`3256–3346 removes its playback thread, sends OUTPUT_CLOSED, calls `mPatchPanel->notifyStreamClosed`, exits the thread and clears its output. This is evidence for sink closure. It is **not proof of detaching a manual tee held by a different, still-playing primary track**.

The inspected close-output function does not walk the other primary tracks and clear their `mTeePatches`. Manual tee construction calls `secondaryThread->addPatchTrack` and stores strong PatchRecord/PatchTrack references on the source track. The source track's `destroy` path explicitly destroys its tee patch tracks, and tee-list replacement also destroys old patch tracks. Those are proven teardown paths. Whether sink thread exit or PatchPanel notification also reconciles this manually introduced cross-thread ownership is not established by the inspected functions. Treat orphaned references and continued interception/drop work as unresolved, not inevitable and not disproven.

Identity is also not interchangeable. `TrackBase` initializes `mId` from `nextTrackId`, `mPortId` from the policy port argument, and `mThreadIoHandle` from the thread id. The update map is passed to `getTrackById_l`, whose matching implementation was outside this20-document read budget. The sink vector is demonstrably audio I/O handles because `checkPlaybackThread_l` indexes playback threads. Do not pass an AudioTrack session ID, policy port ID, registration string or physical device ID as if it were that output handle. A private registration address must be reliably resolved to its exact live output handle, and the precise source identifier must be verified against the device-version interface.

Preservation has no established atomic contract. `updateSecondaryOutputsForTrack_l` constructs a complete replacement list. No compare-and-swap or owner token is present in the inspected call. Reading a dump and later overwriting its tee list can race APM updates, another capture owner or source replacement. Limiting the fixture to a freshly owned app track reduces risk but does not prove all these conditions.

A follow-up may audit the exact native interface, `getTrackById_l`, playback-thread exit/patch cleanup and PatchPanel notification. Do not execute direct updates until identity, unrelated-output preservation and helper-death cleanup all have a source-backed ownership strategy. This worker added no new primary documents for this addendum and performed no runtime actions.

## Honest completion state

**Blocked for product acceptance:** no Samsung stereo BY_SYSTEM/BY_NONE capture, re-render gain, physical audibility or crash restoration was executed by this research. No universal any-app path is established.

**Best current result:** source-backed primary-routing and downstream-tee alternatives show why the low-fidelity privileged flag is not a rooted-device impossibility proof. Scoped LOOP_BACK plus a verified distinct-UID monitor has a finite, reversible next test.

**Next step:** coordinator validates D/E and restoration with the fixed fixture, then tests installed SoundCloud. Keep actual source rate and L/R separate from beam reconstruction. Investigate the direct tee safety blockers as a separate bounded task if transparent original playback becomes necessary.

## Pinned primary-source receipts

The list contains exactly20 distinct primary documents. Re-excerpts did not add documents.

1. [scrcpy v3.3.1 audio documentation](https://github.com/Genymobile/scrcpy/blob/v3.3.1/doc/audio.md), Sources and Duplication.
2. [AOSP Android16 AudioPolicyMix.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/services/audiopolicy/common/managerdefinitions/src/AudioPolicyMix.cpp), `getOutputForAttr`, `mixMatch`, lines328–485.
3. [AOSP Android10 AudioPolicyMix.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-10.0.0_r1/services/audiopolicy/common/managerdefinitions/src/AudioPolicyMix.cpp), lines148–228.
4. [scrcpy AudioPlaybackCapture.java](https://github.com/Genymobile/scrcpy/blob/v3.3.1/server/src/main/java/com/genymobile/scrcpy/audio/AudioPlaybackCapture.java).
5. [scrcpy AudioDirectCapture.java](https://github.com/Genymobile/scrcpy/blob/v3.3.1/server/src/main/java/com/genymobile/scrcpy/audio/AudioDirectCapture.java).
6. [AOSP Android16 AudioMix.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/media/java/android/media/audiopolicy/AudioMix.java), format constants and `canBeUsedForPrivilegedMediaCapture`, lines253–274.
7. [AOSP Android16 AudioMixingRule.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/media/java/android/media/audiopolicy/AudioMixingRule.java), call predicate337–352 and privileged flag561–579.
8. [AOSP Android16 AudioService.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/services/core/java/com/android/server/audio/AudioService.java), `isPolicyRegisterAllowed`13500–13603, `forceRemoteSubmixFullVolume`5618–5655 and legacy-submix exception15366–15380.
9. [AOSP Android16 AudioAttributes.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/media/java/android/media/AudioAttributes.java), `capturePolicyToFlags`1822–1837.
10. [AOSP Android16 AudioPolicyManager.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/services/audiopolicy/managerdefault/AudioPolicyManager.cpp), blob ac8062e5c6d27be2ed501dab35d6b1653b5d0c50, `getOutputForAttrInt`1296–1370, secondary output selection1569–1579, registration4103–4198.
11. [scrcpy AudioConfig.java](https://github.com/Genymobile/scrcpy/blob/v3.3.1/server/src/main/java/com/genymobile/scrcpy/audio/AudioConfig.java).
12. [AOSP Android16 AudioFlinger.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/services/audioflinger/AudioFlinger.cpp), `updateSecondaryOutputs`369–387, tee4018–4119, Binder gates5175–5269.
13. [AOSP Android16 AudioPolicy.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/media/java/android/media/audiopolicy/AudioPolicy.java), `policyReadyToUse`763–837 and sink986–1021.
14. [aroute exact upstream commit receipt](https://github.com/cUDGk/aroute/commit/66811dd0a1d8863a8a9f4b887f7a396decf48249), resolved through GitHub API before source read.
15. [AOSP Android16 legacy remote submix HAL](https://android.googlesource.com/platform/hardware/libhardware/+/android-16.0.0_r1/modules/audio_remote_submix/audio_hw.cpp), rate list199–209, channel masks223–264, sanitization488–495.
16. [AOSP Android16 ServiceUtilities.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/media/utils/ServiceUtilities.cpp), recording permission and attribution helper use.
17. [AOSP Android16 default policy Engine.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/services/audiopolicy/enginedefault/src/Engine.cpp), media strategy address0 remote-submix preference403–418.
18. [aroute AudioRouter.java at exact commit](https://github.com/cUDGk/aroute/blob/66811dd0a1d8863a8a9f4b887f7a396decf48249/com/agapkit/aroute/AudioRouter.java), mono capture and explicit route feedback warning.
19. [AOSP Android16 ServiceUtilities.h](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/media/utils/include/mediautils/ServiceUtilities.h), `isServiceUid`45–50.
20. [AOSP Android16 Tracks.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/services/audioflinger/Tracks.cpp), `releaseBuffer` and `interceptBuffer`1274–1314, tee replacement1836–1855.
