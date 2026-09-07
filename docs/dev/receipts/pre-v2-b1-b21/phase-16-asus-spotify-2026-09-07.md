# ASUS real Spotify capture checks

2026-09-07 00:06–00:18 UTC. ASUS_AI2202, Android14, unchanged installed implementation `6183aa8`.
This is new real-device evidence, not a fix for Ben's reported B8 inversion. The S25 was not accessed.

## Source and consent

Spotify was installed and already logged in. No account changes were made.
Its real device picker twice showed `This phone` and no other network devices before playback.
MUSIC stayed 0/30. No PC or remote Spotify destination was selected.

Phosphor initially lacked audio permission and notification-listener access.
The real disclosure, temporary audio permission and Android projection consent started playback capture.
Without notification access, the console showed `everything playing` and no unsupported transport button.
The real SOURCE grant control then opened Android's listener detail page. Its confirmation granted metadata access for the test.

## Observed transport agreement

Spotify's own Play control started the existing selected track. No next/previous, playlist or library mutation was performed.
After returning to Phosphor, actual source/mirror states were PLAYING and its button showed Pause.
The first actual capture Pause produced PAUSED in both sessions at position44072ms and displayed Play.

With always-visible controls enabled, four more actual button actions passed executable checks:

| Action | Spotify | Phosphor mirror | Visible glyph |
|---|---|---|---|
| Play | PLAYING | PLAYING | Pause |
| Pause | PAUSED | PAUSED | Play |
| Play | PLAYING | PLAYING | Pause |
| Pause | PAUSED | PAUSED | Play |

Each case retained a real UI tree, screenshot and platform-session dump.
These are settled observations after a bounded wait, not atomically synchronized buffering-frame proofs.
The original inversion was not reproduced. No speculative glyph or mirror-state change was made.

## Revocation and restoration

Android's Turn off confirmation was used to revoke the test listener. A targeted `cmd notification disallow_listener` followed during verification.
The authoritative NotificationManager dump ultimately showed only the original four enabled components, with no Phosphor listener.
The console then returned to `everything playing` with no stale transport buttons.
The first post-confirmation UI snapshot still showed metadata. Exact revocation latency was not measured or accepted.

ASUS retained a stale `enabled_notification_listeners` secure-setting entry even after authoritative revocation.
A raw string comparison therefore failed. After proving the only added entry was this test listener, the original cache value was restored.
Final secure-setting bytes and the authoritative four-component set both match the baseline. No unrelated listener was changed.

Revoking the temporary audio grant initially left Android's ONE_TIME bookkeeping flag.
The shell's flag-clearing command rejected `one-time` as unsupported. No global permission reset was used.
The normal permission UI changed this to a while-in-use grant, projection was cancelled, then the grant and user-set flag were revoked.
Final RECORD_AUDIO permission and flags match the original denied state exactly.

Both app preference files were restored byte-for-byte. The test-created empty host file and UI dump were removed.
Phosphor is stopped, its media session is absent and MediaProjection reports null.
Spotify remains paused on the same selected track, with playback advanced by the test. No initial platform position was available for exact seek restoration.
MUSIC is 0/30. Ben's requested plugged-in wake and disabled screensaver remain enabled.

## Evidence and open work

Private evidence is under `dev/scratch/asus-capture-20260907T0007Z/`: `run-transport.sh`, state dumps, screenshots,
baseline/final package and listener records, authoritative final notification state, and baseline/after/restored preference archives.
UI trees are retained in the earlier ASUS scratch directory with capture/Spotify/permission prefixes.
Personal track titles and unrelated listener identities are not copied into this receipt.

B8 remains unreproduced on the tested ASUS path. Independent debugging workers still await explicit model/effort confirmation.
Exact physical333ms, broader cardinal/source variants and separate human/release gates remain open. Drift stays21.
