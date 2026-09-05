# Spotify capture: permission recheck

- Date: 2026-09-05, 00:20 through 00:22, America/Los_Angeles.
- Status: targeted reproduction and permission recovery verified. Phase 5 remains open.
- B IDs: B6, B7, B8. Private issue: #2.
- Device: S25, SM-S931U, Android 16, API 36, build `S931USQSBCZF5_OYNBCZF5`.
- ADB serial: [redacted], wireless.
- Package: `dev.phosphor.mobil3.debug`, `2.0.0-debug`, code `2000000`.
- Mobile source: `e289a74c02b7e757f480ffc8636a4d0583ac6f37`.
- Sibling source: `c0cf967c4afa0aa7bf907dee915480aed8bd0530`.
- Local and installed APK SHA-256: `f953f1b61469500d3a3c01366f23c73d8c5db65c430a8d68f69f9f17b2de76dd`.
- Signer SHA-256: `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.

## Before the permission change

The executor approved Android's full-display capture prompt and played a Spotify track.
Phosphor was absent from the enabled notification-listener list.
Spotify's MediaSession reported PLAYING while Phosphor's mirror reported PAUSED.
Phosphor showed `everything playing`, a play glyph, and no seek control or track title.
Tapping Phosphor's play glyph paused Spotify but changed Phosphor's mirror to PLAYING.
This directly reproduced the inverted state. The local-file B9 test is not evidence for this path.

## Permission recovery

Ben approved the app's permission prompts during this test session.
The executor opened Android notification-access settings and approved Phosphor's access through the visible confirmation dialog.
The enabled listener became `dev.phosphor.mobil3.debug/dev.phosphor.mobil3.CaptureNotificationListenerService`.

Without a code change or rebuild:

1. Phosphor displayed the captured track title, artist, duration, and seek bar.
2. Its pause glyph matched Spotify's PLAYING state.
3. Tapping pause in Phosphor left both sessions PAUSED at position 40191 ms.
4. Resuming and dragging Phosphor's seek bar moved Spotify to 140307 ms and the mirror to 140309 ms.
5. A screenshot showed a visible captured trace and the correct title, artist, seek position, and pause glyph.

Missing notification access explains this reproduction. It does not prove every planned capture-state defect is absent.
The no-permission fallback still reports an incorrect state and needs honest unavailable-state behavior.
Reattachment, buffering, unsupported seek, tagged local metadata, and the full Phase 5 matrix remain unverified.

## Evidence and retained state

Raw evidence stays under `dev/scratch/pre-v2-20260829T072841Z/phase-02/s25-20260905/`.
Commands used explicit-device `dumpsys media_session`, `settings get secure enabled_notification_listeners`, UI dumps, taps, and a seek drag.

| Artifact | SHA-256 |
|---|---|
| `spotify-playing-media.txt` | `1ca60be323259f27365d7c80b84da9de8a9965fcf942203b7ae72e9478388833` |
| `capture-toggle-media.txt` | `b1bb09c2d24eecee2e8ff33c9186f9f6c43ac98283cbdcdfb4459e4b46470189` |
| `capture-after-grant-media.txt` | `c05b5f4709cd81dbb706fd006982b857684ffeff5446f7aa0262c553b4de6239` |
| `spotify-pause-verified.txt` | `529f020d6b625a92c848be5f2f260ce8750fba992d796d9da3763b33666be97a` |
| `spotify-seek-verified.txt` | `0be34c49fc484affc2fce962bf96f3d2f47471562187e7d638e03848b6a53a13` |
| `spotify-capture-beam.png` | `a5da3dfada3ff623d1bd51ea1a30e00b33625422eb624b30510450ed55b70f24` |

The approved notification permission remains enabled. Spotify was paused before returning to local queue tests.
The executor did not change saved library membership, downloads, shuffle, repeat, screen timeout, or lock policy.
No code, release artifact, or public claim changed. This receipt omits private media and device identifiers.
