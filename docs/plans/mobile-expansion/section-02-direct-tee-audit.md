# Android16 manual secondary tee: identity and lifetime audit

Date: 2026-09-08. Worker: hog, configured GPT Astra/high. Source-only follow-up to `/home/ben/.jcode/scratch/root-audio-stereo-20260908/REPORT.md`.

## Decision

**Blocked: an owned registered sink does not confer reversible ownership of a manual foreign-track tee.** The exact Android16 source identifier and AIDL interface are resolved. Sink closure invalidates the downstream patch and prevents subsequent successful buffer acquisition. It does **not** retire the surviving source's tee list, patch references, or interception attempts. The setter offers no owner token, expected version, additive operation, or conditional detach.

This is stronger than the predecessor's unresolved cleanup concern. There is no qualifying owner-lifetime design in this inspected API under the current restrictions. Do not execute a manual tee from this report.

## Scope and checks

Read ben-context-standards first, machine AGENTS, docs/AGENTS, and the complete predecessor report. Did not read the concurrent writer's implementation. No repository edit, Git command, build, worker spawn, device action, media action, service change, Binder transaction, injection, or system modification occurred.

Inspected exactly **10 new distinct primary-source documents**, plus four documents already inspected by the predecessor. All are pinned to AOSP `android-16.0.0_r1`. Gitiles JSON supplied each blob identity. Native webfetch truncated large files before the required branches. Parent approved memory-only public-source curl retrieval at 04:43:20Z. Registered curl through integration_tools, then decoded and excerpted pinned source in memory. No source cache or implementation was written. This private report is the sole new artifact.

Validation is cross-file source tracing, not Samsung runtime verification. One malformed grep was corrected. A nonexistent Tracks.h request returned404 and supplied no source. No product acceptance or R15 scored review was performed.

## Blocker table

| Question | State | Evidence and consequence |
|---|---|---|
| Source map key is AudioFlinger display track ID? | Resolved: **no** | `Threads.cpp:3777–3785` compares `mTracks[i]->portId()` to `audio_port_handle_t trackPortId`. Use of `trackId` in the outer function is misleading. |
| Exact AOSP16 Binder interface | Resolved | `android.media.IAudioFlingerService`, synchronous void `updateSecondaryOutputs(in TrackSecondaryOutputInfo[])`, AIDL231–232. Parcelable24–27 contains only `int portId` and `int[] secondaryOutputIds`. |
| Sink identifier | Resolved | Sink values are audio I/O handles, resolved with `checkPlaybackThread_l`, not mix addresses, registration strings, device IDs, sessions, or policy port IDs. |
| Actual Samsung native ABI and active track identity | Unresolved on device | AOSP pin is exact source evidence, not a firmware ABI receipt. No device transaction or identity discovery occurred. |
| Sink closure stops successful new copies | Resolved, conditional on actual output closing | Sink exit invalidates PatchTrack. ClientProxy rejects CBLK_INVALID. After invalidation is observed, buffer acquisition fails before memcpy. A concurrently acquired buffer may complete. |
| Sink closure removes foreign source tee references and interception work | Proven blocker | Sink exit and PatchPanel notification do not edit foreign `mTeePatches`. Source still takes tee references and attempts writes. Failures produce dropped-frame warning work. |
| Preserve all existing APM/other-owner tees atomically | Proven blocker | Setter constructs and publishes a complete replacement vector. Neither AIDL nor implementation accepts expected list/version, owner, or an additive delta. |
| Source UID/usage scope adds ownership or comparison | Proven blocker | Setter has no expected UID, usage, source generation, or source Binder argument. Source selection reduces scope but does not add server-enforced ownership. |
| Exact registered mix token adds tee ownership | Proven blocker | `audio_mix_ownership` can match mix tokens during register/unregister. No token reaches the tee setter or TeePatch. |
| APM reconciliation guarantees manual-tee cleanup | Proven blocker | APM compares its cached secondary descriptors against policy matching. A manual AudioFlinger update does not update that cache. Equal expected lists cause no update. |
| Minimal physical latency or realtime capture proved | Unresolved on device | Zero-timeout tee writes avoid intentional waits, but source copying, logging, sink buffering and capture scheduling remain. Startup threshold is two sink buffers plus one source buffer. |
| Universal original PCM, offload or bit-perfect fidelity | Unresolved, not promised | Patch format follows source track but downstream mixing remains. FAST/DIRECT/COMPRESS_OFFLOAD are stripped from patch output flags. No Samsung SoundCloud track flags or PCM path were measured. |

## Exact identifier and interface semantics

`AudioFlinger::updateSecondaryOutputs` holds the AudioFlinger mutex, searches current playback threads, holds each thread mutex, and looks up `getTrackById_l`. It passes the selected track and full sink vector to `updateSecondaryOutputsForTrack_l` (`AudioFlinger.cpp:369–387`).

The lookup compares **policy track port ID**. It does not compare `TrackBase::mId`, which is separately allocated from `nextTrackId`, or the session ID (`Tracks.cpp:109–151`). The dump's `Id` and `Port Id` columns are distinct. The predecessor's warning against assuming a port ID was appropriate while lookup semantics were unknown. This follow-up resolves that uncertainty in favor of the port ID.

The AIDL parcel contains no owner Binder, lease, UID, usage, expected old list, epoch, or result array. `IAudioFlinger.cpp:765–773,1366–1373` converts between this parcel and the native map. `AidlConversion.cpp:789–810` maps `portId` to `audio_port_handle_t` and each secondary ID through the audio I/O handle conversion. Its intermediate vector spelling uses `audio_port_handle_t`, but the conversion and consumer both establish sink I/O semantics.

The service wrapper admits UPDATE_SECONDARY_OUTPUTS under the serviceUID predicate (`AudioFlinger.cpp:5226–5257`). Root admission does not create ownership. Several global output and patch operations are separately rejected over this wrapper (`5175–5224`). No transaction number, parcel construction, or runnable native recipe is supplied here.

Return success is weak evidence. A missing source only logs a warning, and the function still returns NO_ERROR. Invalid sinks or patch initialization failures are skipped while building the replacement vector. An invalid-only sink vector can therefore become an empty replacement. Successful Binder return is not proof of attachment, preservation, publication, or capture.

## Concrete reference graph

For a surviving source track S and owned sink playback thread T:

- S owns `mTeePatches` and optional `mTeePatchesToUpdate` (`PlaybackTracks.h:403–405`).
- Each TeePatch contains strong `sp<IAfPatchRecord>` and `sp<IAfPatchTrack>` (`IAfTrack.h`, TeePatch declaration).
- Tee PatchRecord has **no record thread**. Its constructor receives nullptr (`AudioFlinger.cpp:4066–4074`). It is not the helper's AudioRecord or a record-thread-owned track.
- T's `mTracks` also holds PatchTrack after `addPatchTrack` (`Threads.cpp:5087–5091`).
- PatchTrack holds PatchRecord strongly through `setPeerProxy(..., true)`.
- PatchRecord holds only a raw peer interface pointer to PatchTrack through `setPeerProxy(..., false)`.
- TrackBase holds its thread weakly (`TrackBase.h:343`). Peer storage and reference policy are explicit at422–443.

This is not a circular strong-reference leak by itself. It is retention by the still-live source. The sink container may retire while S keeps both patch objects and their buffers alive. Losing the helper's Binder does not release S.

## Exact sink-close branch

The predecessor establishes AudioService policy-Binder death calling release and unregistering the private mixes. Rechecked `AudioPolicyManager.cpp:4282–4329`: unregister disconnects the addressed input/output remote-submix devices and removes the addressed profiles. That is real sink-resource ownership. Actual Samsung closure success and timing remain device unknowns.

Assuming that removal closes T:

1. AudioFlinger removes T from `mPlaybackThreads`, sends OUTPUT_CLOSED, and calls PatchPanel notification (`AudioFlinger.cpp:3281,3326–3327`).
2. `PatchPanel::notifyStreamClosed` only erases the stream from each inserted-module `streams` set (`PatchPanel.cpp:929–934`). It does not remove tees, walk source tracks, release patch endpoints, or edit `mPatches`.
3. T exits. `PlaybackThread::threadLoop_exit` invalidates tracks in **T's own** `mTracks` and clears its active-track list (`Threads.cpp:3679–3693`).
4. `clearOutput` clears output and sink references (`Threads.cpp:3499–3510`). `closeOutputFinish` deletes the output stream (`AudioFlinger.cpp:3348–3354`). Neither visits S.
5. PatchTrack invalidation sets CBLK_INVALID (`Tracks.cpp:2043–2068`). PatchTrack's client proxy uses that same control block (`2664–2673`).
6. S continues `interceptBuffer`: it copies its tee vector and invokes each PatchRecord's `writeFrames` (`Tracks.cpp:1284–1313`). There is no invalid-sink filter or list removal here.
7. PatchRecord asks its peer PatchTrack for a writable buffer (`3449–3468`). PatchTrack delegates to its proxy (`2751–2765`). ClientProxy sees CBLK_INVALID, returns DEAD_OBJECT, and clears the requested frame count (`AudioTrackShared.cpp:188–195,365–370`).
8. The PatchRecord wrapper consequently returns WOULD_BLOCK on zero frames. `writeFramesHelper` logs failure before memcpy, returns zero, and the source logs dropped frames (`Tracks.cpp:3415–3430,1303–1306`).

**Precise inference:** once sink invalidation is visible, new successful copies into the tee stop. Existing patch buffers and repeated source interception attempts do not retire. This is not continued successful recording after death, but it also is not clean teardown. In-flight acquisition before invalidation can finish, so this is not an instantaneous revocation guarantee.

PatchPanel's separate software-bridge cleanup is not a hidden solution. Its `Patch::clearConnections_l` stops its own endpoints and clears its peer cycle (`PatchPanel.cpp`, that function). Manual tee creation in AudioFlinger does not register a PatchPanel Patch or return a patch handle for owner-scoped release.

## Replacement, preservation, and APM races

`updateSecondaryOutputsForTrack_l` starts a new empty TeePatches vector and constructs a new pair for every supplied sink (`AudioFlinger.cpp:4018–4117`). `Track::setTeePatchesToUpdate_l` overwrites the pending vector and warns when one was already pending (`Tracks.cpp:1851–1855`). Source thread processing publishes it later (`Threads.cpp:4251–4255`). Publication destroys the old list's patch tracks, replaces the entire live vector, and starts the new tracks when active (`Tracks.cpp:1836–1848`).

Internal mutexes serialize operations. They do not implement an external compare-and-swap contract. A dump read followed by an overwrite can erase another owner's newer tee, resurrect a retired sink, or replace a newer pending policy vector. Even unchanged sink IDs reconstruct patches rather than preserve existing patch object continuity. A helper replacement generation does not bind server state to that helper generation.

Source port ID selection is more precise than UID-wide selection, but the method carries no source generation or expected owner check. It searches the live state at execution. A vanished source is not a hard error. Freshly owned fixture tracks reduce the impact of a mistake, but do not eliminate competing APM updates or prove safe foreign-source cleanup.

`AudioPolicyManager::checkSecondaryOutputs` compares expected policy-derived sinks with its **own cached** descriptors (`AudioPolicyManager.cpp:7714–7769`). It sends a full replacement only when those differ. For a source excluded from the combined mix, both expected and cached lists can remain empty before and after private-sink removal. The manual tee is absent from both lists. Therefore unregistering the sink need not trigger any source-tee replacement at all.

When `audio_mix_ownership` is enabled, registration rejects duplicate tokens and unregister matches the token (`AudioPolicyMix.cpp:184–245`). With the flag disabled, unregister matches device type and address. This protects the registered mix, not the independently edited source tee. Neither token nor registration identity appears in TeePatch or the AIDL setter. Device flag state is unmeasured and cannot rescue this missing link.

## Is there a qualifying owning-lifetime design?

**No, not through this API with a surviving foreign source and the stated constraints.**

The existing clean native architecture is APM-managed secondary selection: APM owns the complete expected list. Its combined-route capture matching retains the capture-policy exclusions described by the predecessor. Manual attachment bypasses that selection but also bypasses the owner's bookkeeping.

A helper that owns both source and sink can terminate its own source before death and thereby retire source-held tees. That does not solve SoundCloud, whose source survives independently. A supervisor that later writes a saved list still lacks atomic comparison and still modifies surviving shared tee state. Private sink registration alone cannot bridge the gap.

A qualifying platform primitive would need an owner Binder, source-instance binding, additive attach/owner-only detach, preservation of APM entries, sink-death cleanup, and publication acknowledgement. That describes the missing contract, not an implementation proposal. Adding it would require platform changes or equivalent internal integration outside this task's authorization.

## Latency implications

This tee leaves the original physical path in place in the source architecture. It does not place a userspace monitor in series with audible playback. However, interception still runs on the source data path, and each tee uses a zero timeout rather than a zero processing cost.

The exact capture startup threshold is `2 * sinkFrameCount + sourceFrameCount`, all converted to source-track frames (`AudioFlinger.cpp:4030–4056`). Thus the threshold duration is two sink periods plus one source period. If each period were20ms, this threshold alone would be60ms. That is an arithmetic example, **not a device measurement**. The allocated capacity is larger than the start threshold. FAST/DIRECT/COMPRESS_OFFLOAD flags are explicitly removed from the patch output.

Consequently, transparent physical playback is not proof of a realtime vectorscope or the minimum possible capture lag. Source comments about average interception cost are not S25 timing evidence. This report does not promise bit-perfect fidelity, zero added latency, or universal offload support.

## Smallest useful next resolution

No direct native tee test on the phone is needed to establish this AOSP blocker. A successful sound sample would not establish ownership or cleanup.

If a device-specific extension is claimed, the smallest prerequisite is an exact firmware-matched source or interface receipt showing owner-scoped atomic attachment and foreign-source cleanup. Only such new evidence could justify a separate finite fixture test. Without it, retain the direct-tee blocked state rather than testing dump-read overwrite recovery.

An isolated platform test could confirm the source inference: keep a controlled source alive, close only its test sink, then inspect retained tee references and failed writes. This would require a separate approved host/platform test environment. It is not necessary for the present source decision and was not built or supplied as executable instructions.

Parent owns the separately implemented LOOP_BACK-only finite stereo probe and its timing measurements. Its physical playback and scope lag must be measured separately. No new device action is requested by this report.

## Pinned source receipts

Every link is the release pin. Blob identities below came directly from Gitiles `?format=JSON`. Line numbers come from decoded source, not rendered page line wrapping.

### Ten new documents

1. [Threads.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/services/audioflinger/Threads.cpp#3777), blob `2f5c872aaa912509b88708a7e560529d59f347b2`. Identity3777–3785, exit3679–3693, clearOutput3499–3510, publication4251–4255, patch insertion5087–5091.
2. [IAudioFlingerService.aidl](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/media/libaudioclient/aidl/android/media/IAudioFlingerService.aidl#231), blob `474ab11a3e66a2bce4ba27c58ca2393d8d3abe8c`. Method229–232.
3. [TrackSecondaryOutputInfo.aidl](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/media/libaudioclient/aidl/android/media/TrackSecondaryOutputInfo.aidl#24), blob `113328ea8eb9949dd1c7aed1c3a7f590c4fb548e`. Entire parcel24–27.
4. [PatchPanel.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/services/audioflinger/PatchPanel.cpp#929), blob `be59299aef69cf0a70b1e07251dbb39a3231cb76`. notifyStreamClosed929–934, separately owned software-bridge create/clear functions.
5. [IAfTrack.h](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/services/audioflinger/IAfTrack.h), blob `ad5ccc6cd56212dd454e60510449d26b76d717c3`. TeePatch strong references and patch interface declarations.
6. [TrackBase.h](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/services/audioflinger/TrackBase.h#422), blob `6dea78682729f3ee9b664b3a689b97f65fd97c7b`. Invalidation74–78, weak thread343, peer ownership422–443.
7. [IAudioFlinger.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/media/libaudioclient/IAudioFlinger.cpp#1366), blob `152360770f05bd4328053f4335f5a829248a2da7`. Client765–773 and server1366–1373 adapters.
8. [AudioTrackShared.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/media/libaudioclient/AudioTrackShared.cpp#188), blob `359f3c1ff21d14970c728ccb43e80bfd5d3e6c92`. Invalid proxy188–195, failed buffer365–370.
9. [AidlConversion.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/media/libaudioclient/AidlConversion.cpp#789), blob `2377fc813192875b4a65891e53fda124d3341663`. Secondary pair conversions789–810.
10. [PlaybackTracks.h](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/services/audioflinger/PlaybackTracks.h#403), blob `dac5959e89d33e2e58354a91c067c23c543f756f`. Tee iteration364–369, live/pending storage403–405.

### Four predecessor documents re-excerpted

11. [AudioFlinger.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/services/audioflinger/AudioFlinger.cpp#4018), blob `a4b06ee141e33ac3e8d18d11731b4ac3442761d3`. Setter369–387, close3256–3354, construction4018–4117, wrapper5175–5257.
12. [Tracks.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/services/audioflinger/Tracks.cpp#1284), blob `9046859c0ee1afad253483d3269eff182b563845`. IDs109–151, destroy1078–1107, interception1274–1313, replacement1836–1855, invalidation2043–2068, proxy2664–2673 and2751–2765, write3415–3468.
13. [AudioPolicyManager.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/services/audiopolicy/managerdefault/AudioPolicyManager.cpp#7714), blob `ac8062e5c6d27be2ed501dab35d6b1653b5d0c50`. Unregister4282–4329, reconciliation7714–7769.
14. [AudioPolicyMix.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/services/audiopolicy/common/managerdefinitions/src/AudioPolicyMix.cpp#184), blob `ea78a5dfb8da3a69a27d85f1dabe2516c50d29dc`. Mix token scope184–245.

## Device facts supplied by parent, not remeasured

SoundCloud's installed manifest has `allowAudioPlaybackCapture=false`. Runtime player flags and offload remain unmeasured. Actual root framework attribution is UID1000, while raw UID is0. Those facts do not establish native tee lifetime or firmware ABI equivalence. No protected-content or call path was investigated.
