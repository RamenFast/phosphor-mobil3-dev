# Addendum: native policy mix format gate and owner lifetime

Date: 2026-09-08. Worker: hog, configured GPT Astra/high. Separate parent-authorized follow-up, starting04:49:33Z. Limit: eight minutes and eight new primary documents. This does not revise the confirmed manual-tee blocker in `REPORT.md`.

## Decision

**The16k mono restriction is Java-path-specific in the inspected AOSP16 registration chain, not a universal native fidelity cap.** Native AudioPolicyService registration checks privileged media permission but does not repeat that format cap. A full-stereo native mix is therefore a source-grounded format possibility, not a proven working capture method.

**Native direct registration remains blocked by missing established mix Binder-death ownership.** The registered AudioPolicyServiceClient death path does not unregister policy mixes. The Java `updateMixingRules` alternative updates native criteria only. It does not activate a new privileged flag on the registered native mix.

No Binder transaction, device action, build, implementation, repository edit, Git operation, playback or service change occurred. Public pinned source was decoded and excerpted in memory under the parent's prior curl approval. Exactly eight new documents were inspected. No further searches followed the parent's04:53:53Z stop-search instruction.

## Findings table

| Question | State | Exact evidence |
|---|---|---|
| Does native registration enforce privileged16k/mono? | Resolved for inspected chain: no such check | AudioPolicyInterfaceImpl2010–2066 checks permissions, not rate/channel cap. PolicyAidlConversion227–247 copies format and flag. APM4127–4198 uses supplied format and stereo profiles. |
| Is root automatically admitted through every relevant check? | Partly resolved | APS1342–1406 uses isServiceUid, which admits root. Legacy media-permission helper explicitly admits audioserver/root. New permission-provider branch and Samsung flag state were not independently audited. |
| Does registerClient create a real Binder death link? | Resolved: yes | APS375–400 keys a notification client by UID/PID and links its Binder to death. AudioSystem1037–1042 performs registration. |
| Does this client death unregister its policy mixes? | Proven no in that cleanup branch | APS432–454 only calls releaseResourcesForUid after the last same-UID notification client dies. APM5977–5982 only clears audio sources, patches and session routes. |
| Does the AudioMix Binder token prove an independent death owner? | Unestablished | AIDL describes an owner token. Conversion copies it. Inspected registration/unregistration uses token equality, not a death lease. Inline AudioPolicyMix constructor/header internals were outside the eight-document budget. |
| Does Java updateMixingRules change privileged capture natively? | Resolved: no | JNI2455–2489 sends the old mix plus only new criteria. APM updateMix ultimately assigns only registeredMix->mCriteria. |
| Does Java update revalidate the cap? | Not in inspected update method | AudioService13784–13810 enforces routing permission and registered policy. It does not call the initial format-cap check. The native operation still only changes criteria. |
| Does Java cache retain the whole new rule? | Resolved: yes | AudioService14667–14677 replaces its matching cached mix rule after native success. AudioPolicy459–466 updates its local config. |
| Is replaying that changed cache an approved safe method? | No | Cache/native divergence exists. No owner-death serialization or in-flight-registration proof was established. No replay trick is proposed. |
| Does privileged capture include BY_NONE or universal SoundCloud? | No source basis | AudioPolicyMix439–449 rejects NO_SYSTEM_CAPTURE regardless of privileged flag. Actual SoundCloud runtime flags remain unmeasured. |

## 1. Native format and permission chain

The exact AIDL operation is `android.media.IAudioPolicyService.registerPolicyMixes(in AudioMix[] mixes, boolean registration)` at273. Client notification registration is a separate method at261. There is no policy callback parameter on the registration method itself.

`AudioSystem::registerPolicyMixes` at2113–2123 bounds the count, converts each AudioMix, and forwards it. `PolicyAidlConversion::aidl2legacy_AudioMix` at227–247 converts format independently and copies `allowPrivilegedMediaPlaybackCapture`, voice permission marker, Binder token and virtual-device ID. It imposes no privileged-rate/channel cap.

AudioPolicyService's transaction wrapper admits these methods only from service UIDs (`AudioPolicyService.cpp:1342–1406`). Root satisfies that coarse predicate, but the method has additional permission checks:

- A non-combined route requires MODIFY_AUDIO_ROUTING.
- Any privileged-media mix requires CAPTURE_MEDIA_OUTPUT.
- Voice capture has its separate permission check. It is not part of this proposed media-only path.
- Both registration and unregistration pass through this permission logic before APM dispatch.

These checks are explicit in `AudioPolicyInterfaceImpl.cpp:2010–2066`. The method caps list size but does not inspect a16k maximum or mono channel count. With the legacy permission helper, `captureMediaOutputAllowed` explicitly accepts audioserver/root (`ServiceUtilities.cpp:292–300`). `getCallingAttributionSource` takes raw Binder calling UID/PID (`461–469`), not the app's previously measured Java framework attribution UID1000. When `audioserver_permissions()` is enabled, the method uses its permission-provider branch. This audit does not claim device-specific authorization through that branch.

APM registration requires a PLAYERS mix for combined routing, resolves the remote-submix backend, registers the mix, copies the requested format, and creates stereo input/output profiles (`AudioPolicyManager.cpp:4127–4198`). It does not condition those profiles on a privileged16k/mono restriction. Supported hardware formats and actual output negotiation still constrain the result.

In contrast, Java AudioMix constants are16000Hz, one channel and two bytes/sample (`AudioMix.java:165–171`). Builder.build validates them for privileged capture (`596–601`), and initial AudioService registration repeats the check (`AudioService.java:13516–13527`). AudioMix.CREATOR rebuilds through Builder.build (`338–350`), so ordinary parceling of an already-mutated high-quality privileged Java AudioMix encounters the validation again. No malformed parcel or validation exploit was attempted or proposed.

## 2. Native Binder death does not clean up policy mixes through registerClient

AudioPolicyService creates a NotificationClient for calling UID/PID and links the supplied client Binder to death (`AudioPolicyService.cpp:375–400`). NotificationClient.binderDied calls removeNotificationClient with those identities (`692–699`).

Removal waits until no other notification client shares the UID before calling APM.releaseResourcesForUid (`432–454`). Therefore even the proven UID-resource cleanup is not an individual-helper lease when several processes share a UID.

The complete releaseResourcesForUid body is only:

- clearAudioSources(uid)
- clearAudioPatches(uid)
- clearSessionRoutes(uid)

It does not call unregisterPolicyMixes or remove policy mix profiles (`AudioPolicyManager.cpp:5977–5982`). The downstream functions clear source objects, UID-owned audio patches and preferred routes (`5984–5992,6030–6085`), not the dynamic mix collection.

AudioMix.AIDL29–46 includes a Binder token described as identifying the policy owner. That description is not a promise to unregister on death. The conversion copies the token, and AudioPolicyMixCollection registration/unregistration compares it when the ownership feature flag is enabled (`AudioPolicyMix.cpp:184–245`). No token death registration was found in the inspected path.

**Evidence boundary:** AudioPolicyMix's inline constructor and the native AudioMix header were not inspected because the eight-new-document cap was reached. Thus the complete absence of every possible token-specific inline callback is not independently proven here. The specific proposed registerClient-to-UID-cleanup ownership mechanism is disproven. Native mix lifetime remains unestablished, which is enough to block the safe probe. Parent was informed of this residual header check before directing research to stop.

Normal explicit unregister could remove a known private mix. That does not establish helper-crash cleanup or an atomic death-versus-registration contract. A userspace supervisor is not a substitute for those unproven guarantees. No live native registration is recommended from this evidence.

## 3. Java update is criteria-only, despite whole-rule cache replacement

AudioPolicy.updateMixingRules sends the existing AudioMix array, a new AudioMixingRule array, and the registered policy callback (`AudioPolicy.java:450–472`). AudioService checks MODIFY_AUDIO_ROUTING, array lengths and that policy callback (`13784–13810`). Its proxy invokes the native update while holding its mix lock and then replaces the whole cached Java rule on success (`14651–14681`).

The JNI payload makes the mismatch explicit. It converts the existing mix into the first part of an update and converts the new rule into a vector of AudioMixMatchCriterion only (`android_media_AudioSystem.cpp:2455–2489`). `convertAudioMixingRuleToNative` reads mCriteria, not privileged or voice booleans (`2201–2259`). AudioSystem2144–2163 forwards the old mix plus newCriteria.

The native service passes this to APM.updatePolicyMix (`AudioPolicyInterfaceImpl.cpp:2085–2099`). APM4372–4381 delegates to AudioPolicyMixCollection.updateMix. The final assignment is only `registeredMix->mCriteria = updatedCriteria` (`AudioPolicyMix.cpp:247–269`). It leaves the registered format and privileged flag unchanged.

A successful update can therefore leave Java's cached rule privileged=true while the native registered mix remains privileged=false. `AudioMix.setAudioMixingRule` checks only target mix type before assignment (`AudioMix.java:208–214`). Java-side readback is not native capture-policy evidence.

AudioService.connectMixes unregisters and registers its stored mixes (`14637–14647`), and inspected call sites include policy construction and audioserver-death recovery (`14515–14519,2123–2132`). Replaying divergent state is not an established safe method. Audioserver restart is outside authorization. Owner-death ordering, in-flight registration and complete rollback would require separate proof. This report supplies no reconnect, re-register, cache-mutation or parcel recipe.

## 4. Smallest remaining resolution and limits

The native16k/mono distinction is now source-backed. The next ownership-only source check, if parent elects to do it, is AudioPolicyMix.h's constructor/destructor and native AudioPolicy.h's token declarations. A token-specific death hook would need to show exact mix removal and serialization with registration. If absent, a platform-owned lease or another already-authorized ownership mechanism is required before a native probe.

Java's immediate criteria-only result needs no device experiment. Native flags cannot be inferred from a successful Java rule update. A full-quality record result, SoundCloud capture, crash cleanup and latency are all device unknowns. Native privileged capture still excludes NO_SYSTEM_CAPTURE. Nothing here promises BY_NONE capture, bit-perfect samples, realtime display alignment or universal offload.

## Pinned new-source receipts

Release: `android-16.0.0_r1`. Blob IDs came from Gitiles JSON metadata. These are the eight new distinct primary documents in this follow-up.

1. [AudioPolicyInterfaceImpl.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/services/audiopolicy/service/AudioPolicyInterfaceImpl.cpp#2010), blob `40899002dd367d419400e30b333c28a00e8ae327`. Register2010–2066 and update2085–2099.
2. [AudioPolicyService.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/services/audiopolicy/service/AudioPolicyService.cpp#375), blob `663e0d69000328dd6dcd6a1a6e18eacbe14b78b3`. Register375–400, UID cleanup432–454, Binder death692–699, transaction gate1342–1406.
3. [AudioPolicy.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/media/libaudioclient/AudioPolicy.cpp), blob `1b9936fe181fbf3d4a32d20149789a2aebf5d9e7`. Legacy AudioMix parcel methods88–145. Not mistaken for the AIDL conversion.
4. [android_media_AudioSystem.cpp](https://android.googlesource.com/platform/frameworks/base/+/android-16.0.0_r1/core/jni/android_media_AudioSystem.cpp#2455), blob `1bbf811dc373b3018e786a974f7d8e3dc23fb3c3`. Criteria conversion2201–2259, mix conversion2347–2384, register2386–2425, update2455–2492.
5. [PolicyAidlConversion.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/media/libaudioclient/PolicyAidlConversion.cpp#227), blob `163a359a8aef4b6124dae4bfb1f4bb86987f5c67`. Native mix conversion227–272.
6. [IAudioPolicyService.aidl](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/media/libaudioclient/aidl/android/media/IAudioPolicyService.aidl#261), blob `590679183fb5814ea832079e5ae3e5025e85bcc7`. Methods261,273,277.
7. [AudioSystem.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/media/libaudioclient/AudioSystem.cpp#2113), blob `3ef92255200b23d81947a8f081e1996af30de428`. Client registration1037–1042, native registration2113–2123, update2144–2163.
8. [AudioMix.aidl](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/media/libaudioclient/aidl/android/media/AudioMix.aidl#29), blob `bb8537d334a2c371ea4a2a727c838e11cde4aad2`. Entire parcel29–46.

## Reused source receipts

- [AudioPolicyManager.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/services/audiopolicy/managerdefault/AudioPolicyManager.cpp#5977), blob `ac8062e5c6d27be2ed501dab35d6b1653b5d0c50`. Register4127–4279, update4372–4381, cleanup5977–6085.
- [AudioPolicyMix.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/services/audiopolicy/common/managerdefinitions/src/AudioPolicyMix.cpp#247), blob `ea78a5dfb8da3a69a27d85f1dabe2516c50d29dc`. Token equality184–245, criteria update247–269, capture flags439–449.
- [ServiceUtilities.cpp](https://android.googlesource.com/platform/frameworks/av/+/android-16.0.0_r1/media/utils/ServiceUtilities.cpp#292), blob `81662f1ad4e4a7a03e656e67e5b09e5a674be9b9`. Legacy root permission292–300, calling attribution461–469.
- [AudioPolicy.java](https://android.googlesource.com/platform/frameworks/base/+/android-16.0.0_r1/media/java/android/media/audiopolicy/AudioPolicy.java#450), blob `57f5f52c40f9327043b5cf7734e059f0360bbfee`. Update450–472.
- [AudioService.java](https://android.googlesource.com/platform/frameworks/base/+/android-16.0.0_r1/services/core/java/com/android/server/audio/AudioService.java#14651), blob `b1acfe830eeda3b35e4ab759f4768dbb701bbe97`. Initial cap13516–13527, update13784–13810 and14651–14681, death/release14522–14579, reconnect14637–14647 and2123–2132.
- [AudioMix.java](https://android.googlesource.com/platform/frameworks/base/+/android-16.0.0_r1/media/java/android/media/audiopolicy/AudioMix.java#338), blob `e4eaaa317b3df30bae3ab1243946a1d69a9e20c8`. Constants165–171, setter208–214, CREATOR338–350, builder cap596–601.
