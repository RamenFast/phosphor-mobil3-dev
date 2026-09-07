# ASUS local gain, rotation import and installed checkpoint

2026-09-07. Root-only continuation of the approved phone acceptance plan.
Ben confirmed exclusive ASUS test ownership and autonomous takeover. S25 remained excluded.

## Local playback acceptance

Three owned 30-second stereo WAV fixtures used48kHz PCM16:220Hz sine at0.8 and0.1 peak, then silence.
The real SOURCE folder picker granted only the owned fixture directory. The app played its actual three-item queue.

| Requirement | Observed result | Evidence |
|---|---|---|
| New quiet item resets the prior loud peak history | Loud item settled at1.15×. Next selected quiet item showed4.51× shortly after opening and settled at6.00×. | gain-loud-stable.xml, gain-quiet-start.xml, gain-quiet-stable.xml and media-session dump |
| Silence holds auto-gain | Actual silent item advanced from0:00 to0:02 with PLAYING state and gain6.00× throughout. It ended at0:30 with Play displayed. | gain-silence-start.xml, gain-silence-held.xml, gain-silence-media.txt, gain-final-state.xml |
| Paused seek updates without playing | Quiet item paused near22s. Actual seek-rail swipe moved to9000ms. Two successive system media-session dumps retained PAUSED, speed0, position9000 and the same update timestamp770212. UI retained0:09, Play and6.00×. | paused-seek-first/held XML and media dumps |

This exercises actual decode, renderer gain, queue ownership, console controls and platform session publication.
It does not claim sample-exact gain latency or every source/error combination. No gain production code changed during these checks.
MUSIC stayed1/30. No PC audio or Linux deployment was used.

## Android rotation authority and import

With Android accelerometer_rotation=0, both dependent rotation controls were disabled in real accessibility semantics.
Tapping their displayed controls left scope locked and UI placement follow unchanged. The existing explanation was visible.

A synthetic five-setting archive requested both app locks and saved landscape orientations.
The first modified fixture retained an old checksum and was rejected with checksum_mismatch. This was a fixture error, not a successful import.
A self-consistent fixture used the existing documented canonical hash contract. No application integrity gate was changed or bypassed.
The real picker imported all five settings. Actual preferences matched each requested value.
The Activity still requested SCREEN_ORIENTATION_LOCKED and remained1080×2400 portrait.

Changing Android auto-rotate to1 let the foreground authority refresh apply the saved choices without new gravity motion.
The Activity requested SCREEN_ORIENTATION_LANDSCAPE and the actual UI/screenshot became2400×1080.
The settings sheet remained usable. The original Android rotation settings0/0 were restored afterward.
This verifies system hold, rejected controls, import and unlock. It does not simulate physical cardinal poses or establish every beam orientation.

## Additional gesture checks and limits

A free pinch after SOURCE dismissal changed gain1.83× to2.03×, but began after the settle deadline.
Immediate close-plus-pinch input kept gain2.03×. A synchronized live reader observed no stage gesture decisions for that sequence.
This is consistent with the exiting sheet owning those pointers, not proof of the exact sheet333ms boundary.
One earlier split logger/action attempt missed its recording window. It is excluded from timing evidence.
A later actual VIEW LOCK pinch left gain1.83× unchanged. The earlier eight overflow runs remain the measured333ms evidence.
No blanket all-card/orientation or panel-scanout claim is made.

## Exact installed build

Implementation commit:743d8bb, including capture truth61e0630 and slow pinch18569be.
Retained APK:dev/scratch/asus-timeline-20260907T0113Z/final-743d8bb.apk.
SHA256:eba2a0bc433055dec0a01650e49e5df0c4e8318fc1b227d4c50f8d933a3b57e5.
Debug signer:f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d.
Package dev.phosphor.mobil3.debug, version2.0.0-debug.

The exact source build passed440 Android tests, lint, assembly and checkEngine.
dev/pm3 read back identical installed bytes and signer. A real cold launch showed no source and gain1.83× auto.
One active gravity listener was present. Projection was null and MUSIC remained1/30.
Earlier host-native73, relay43, mocked pm3, privacy and isolated release-gate fixtures passed.
No release-signing, tag, push, publication or service deployment occurred.

## Restoration

- Both original preference files were restored byte-for-byte against baseline-prefs.tar after the final smoke launch.
- RECORD_AUDIO remains denied with exactly the baseline flags.
- The authoritative four notification-listener components and secure cache match the baseline.
- Original rotation settings0/0 and empty PhosphorAcceptance log property were restored.
- Requested stay-awake7, screensaver0 and600000ms unplugged timeout were retained. MUSIC remains1/30, not the old muted baseline.
- Only the owned fixture tree's persisted read grant was released. Three older app grants remained present.
- Android's uri_grants service dump was empty on this phone. Two attempted file comparisons therefore provided no proof. The actual Activity permission dump confirmed the owned grant absent and the three older entries retained.
- A finite, hardcoded app-UID cleanup fixture performed that one release. Gradle compiled it outside production sources. Its temporary code-cache JAR was gone after installation. No runtime app endpoint was added.
- Owned WAV/archive files, phone recordings, XML and gesture JAR were removed. The empty owned folder was removed without recursive deletion.
- The test-generated empty remote_hosts.xml was removed. Only the two baseline preference files remain.
- The app is stopped, its source services are absent and projection is null. Private host evidence and rollback APKs remain retained.

All named evidence is under ignored dev/scratch/asus-timeline-20260907T0113Z/.
This checkpoint closes the named measured workflows, not all B1–B21 acceptance. Real capture buffering/sustained variants,
physical gravity poses, exact sheet/cardinal gesture timing, human visual approval and approved Linux/release work remain separate gates.
