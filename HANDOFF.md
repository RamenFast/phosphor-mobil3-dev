# Handoff — next session starts here

## Where we are (2026-07-18, after the Fable session): the app is REAL

One giant Fable session shipped Acts 0–V of the plan (`~/.claude/plans/scalable-sparking-planet.md`)
plus the first Act-VI soul. All on master, pushed. The phone now has: the full Fable UI redo
(12 rooms, real fonts, carved stone, engraved mode glyphs, LIGHT custom editor + photosensitivity
guard, gestures incl. pinch-gain + 2-finger mode-step/glow, resting beam, tube-flip, thermionic
warm-up), **bridge v2 on BOTH machines** (protocol W/H/K, 10 ms audio framing — the burstiness
bug is dead, per-app source picker, library browser incl. **Google Drive via rclone**), **native
audio residency** (phosphor owns the media-button session; earbuds drive the source machine over
the bridge; lock-screen card; background-mute bug dead), **deck sheet with a real Media3 queue**
(folder → gapless, cover art, seek, cubic volume), Nerd HUD, PiP auto-enter, tuning persistence,
the adaptive launcher icon (multi-figure), and the AMOLED Void soul (engraved keys on true black).

**The dev loop** (unchanged): `source scripts/env.sh` then `dev/pm3 <verb>`. Wireless adb drops
often — reconnect: `adb connect "$(adb mdns services|awk '/_adb-tls-connect/{print $3;exit}')"`.
**Machines: thinkcenter = Ben's LAPTOP (100.66.109.56) · interserve-linux = the PC/dev box
(100.114.165.77).** Both run `phosphor-relay` 2.0.0 as systemd user services (reboot-proof;
`scripts/relay-install.sh [--host <h>]` upgrades). PC relay roots: Music, Mass storage,
google drive (rclone remote `gdrive:`, read-only OAuth done). Protocol reference: docs/BRIDGE.md.

## Next session queue, in order

### 1. FIRST: bridge concurrency hardening (M6 release blocker)
`docs/dev/codex-bridge-audit-2026-07-18.md` — GPT-5.6-SOL xhigh audit, 13 findings, verdict
"not ship-safe yet" (happy path is fine for daily driving; the blockers are edge-case lifecycle).
Blockers 1–4: phone cross-generation socket race (make sessions RAII + generation-owned);
`send_frame` blocking under the writer mutex can ANR main-thread JNI callers (dedicated writer
thread + bounded queue + write timeout); oboe restart supervisor leak + late-install race
(cancellation token + recv_timeout + post-open generation check); relay watchdog starvation
behind blocking rclone/ffmpeg handlers (cancellable worker jobs + per-command deadlines).
Then 5–10 (audio-writer join, pump-id-tagged EOF events, oboe reopen retry, relay panic RAII,
half-session on oboe-fail, lock-free SPSC ring in the callback). Finding 11 fixed this session.
Fresh context strongly advised; the audit doc is the spec.

### 2. Act VI remainder: the CRT Amber service-bench soul + room tiles
Blossom Dark (carved, default) and AMOLED Void (engraved-on-black) exist. Build the third
distinct personality: **CRT Amber "service bench"** — annotation-dense like a Tektronix service
manual: engraved part-number labels (`V2 · MODE`), dotted leader lines, stepped/mechanical motion
(detented, no easing), all-mono discipline; skeuomorphism-free. Introduce the real `RoomStyle`
bundle (control character/density/motion feel per room) rather than the `p.id == "amoled"`
discriminator now in Controls.kt (works, but the framework should absorb it). Then upgrade the
ROOM sheet to spec §2.6 self-portrait tiles + the 240 ms whole-chrome crossfade (lerp across
Palette). `ben-ui-design` loaded before touching any of it.

### 3. Act V polish remainder
Long-press (450 ms) context popout on the stage + first-entry gesture hints (retire after two
sightings) + return-to-auto gain popout; LIGHT sheet header undo/redo (5 deep); auto-gain
(engine-side breathing + `· auto` tag — desktop semantics); ember auto-dim (needs a brightness-
budget envelope verb); shuffle/repeat on the deck queue; volume rule live-tracking (poll while
sheet open); settings REMOTE HOSTS add/edit UI (list is hardcoded in MainActivity.remoteHosts).

### 4. Remaining M5 soul: kits · compose · postcards
`.phoskit` browser/editor (formats in phosphor-proto, desktop kit.rs is the reference), compose
mode (draw → WAV, dsp/compose.rs port), `.phos` postcards + share-sheet registration. Snapshot/
clip exports (engine offscreen render exists — selftest.rs shows the path).

### 5. M6 prep (NO ship without Ben's polish round)
Keystore staged (~/.android-keys/phosphor-mobil3.jks, NEVER in git), release.sh (apksigner
verify + 16 KB llvm-readelf + SHA256SUMS), README screenshots from current build, concourse
registration of pm3, THEN Ben's UX round gates v1.0.0 + repo-public flip.

## Field notes from this session (so they don't bite twice)

- **Ben's live feedback applied:** focus defaults 0.3 (max sharp, persisted); earbud-skip title
  staleness fixed (initial metadata sync on controller reconnect); gain clamp 0.1–6.0 parity.
- Sheet coordinates for adb-driving change with orientation; the console auto-hide (4 s) races
  multi-command tap sequences — normalize (tap-wait-tap) or drive fresh-launch intents.
- `pkill -f phosphor-relay` over ssh kills the ssh session itself (cmdline match) — use `-x`.
- PULSE_LATENCY_MSEC=20 was the v1 burstiness hotfix (superseded by v2 framing, keep for lore).
- rclone shared client_id retires during 2026 (SERIOUS-TODOS) — mint our own before it breaks.
- The scratchpad narration rig (kitty right-panel + intercom TTS + ASCII memes) is session-local;
  Ben loves it — rebuild on request (narrate.sh pattern in the session scratchpad).
- Blind taps on the console can hit ▸▸ and skip Ben's real music — compute coords from a fresh
  screenshot, same orientation.

## Decisions & honest limits (standing)
- >120 fps display impossible on Android (vsync-locked); beam-rate oversampling is the honest lever.
- Spotify/DRM can't be captured on-device (role-managed) — the bridge IS the answer; Shizuku
  conclusively negative (do not revisit).
- Spotify Connect steals playback to the phone when buds connect to it — keep Spotify's device
  = the source machine (documented in the SOURCE sheet prose).
- Geometry (VISUALIZER) mode needs desktop phosphor running on the source host
  (`phosphor --background`); caps.geometry=false when absent, E+fix when it dies.
- One media session ever; the loaded deck owns the transport. RemotePlayer ghost-playlist
  pattern defeats the SimpleBasePlayer seekToNext trap — don't "simplify" it away.
- AGP 9 built-in Kotlin (never apply kotlin-android); core-ktx/lifecycle pinned (compileSdk 37
  unpublished); oboe needs shared-stdcxx + the libc++_shared copy step.
