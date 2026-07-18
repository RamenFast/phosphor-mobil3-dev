# Handoff — next session starts here

## Where we are (2026-07-18): M0–M5(pass 1) + a working Tailscale bridge, all on GitHub

phosphor-mobil3 is a usable Android scope-music app on Ben's S25. Both repos are on GitHub
under RamenFast (phosphor public, **phosphor-mobil3 private**), single branch `master`, pushed.
Toolchain is in-repo at `.toolchain/` (Ben's ask — no home clutter; `scripts/env.sh` self-locates).

**The dev loop** (memorize this): `source scripts/env.sh` then `dev/pm3 <verb>`
(doctor/build/install/run/logcat/screenshot/record/media/fps/smoke). Device serial rotates —
always resolve it: `D="$(adb devices|awk 'NR>1&&$2=="device"{print $1;exit}')"`. If the phone
drops off wireless adb: `adb connect "$(adb mdns services|awk '/_adb-tls-connect/{print $3;exit}')"`.
Pairing (needs Ben): he opens Settings → Developer options → Wireless debugging → Pair with code,
sends the ip:port + 6-digit code; `adb pair <ip:port> <code>` (LAN 192.168.1.x works; the
pairing dialog mints a NEW port each time it opens — `adb mdns services` discovers it).

### Milestones shipped (commits on master)
- **M0** scaffold + toolchain + device (2db590b, ebd53b0)
- **M1** wgpu beam at 120 Hz + SELFTEST/smoke (919fd0b, 20caa45)
- **M2** oboe deck, sample-locked (a24aba1; upstream phosphor gate b72154c pushed)
- **M3** Media3 lock-screen deck (cf5c56a)
- **M4** capture + mic + Shizuku spike closed NEGATIVE (ea05427, bae1163)
- **toolchain move** to .toolchain/ (ef23967)
- **M5 pass 1** Compose chrome — console, 4 sheets, mode/beam/room control, SAF file open,
  MediaController sync (02a102f)
- **M5 beam-rate oversampling** — the honest "beyond 120" (7861910)
- **M5 Tailscale bridge** — Spotify on the phone via desktop proxy (ca02a66)

### The bridge (works, proven) — see docs/BRIDGE.md
Laptop (`thinkcenter`, tailscale 100.66.109.56) runs `relay/` (built here, scp'd to
`/tmp/phosphor-relay`, restart: `ssh thinkcenter 'nohup setsid /tmp/phosphor-relay --port 45777
</dev/null >/tmp/relay.log 2>&1 & disown'`). Phone connects (SOURCE → "remote · desktop", or
`am start -n dev.phosphor.mobil3/.MainActivity --ez remote true` for testing), plays PCM via
oboe + scopes it + drives transport. Proven: a laptop test tone drew 960 segs on the phone.
Host is hardcoded `100.66.109.56` in MainActivity — needs a host field (below).

## Where we're going — the queue, in priority order

### 0. FIRST: full Fable quality-pass / redo of the ENTIRE M5 UI (Ben's directive, 2026-07-18)
Ben observed the model may have switched off Fable ~1/4 into M5 and wants the whole UI
build-out revisited with Fable's strength. **Treat M5 pass 1 (commit 02a102f + follow-ups) as
a DRAFT to review and rebuild where it falls short of the UX-SPEC and the house design
language (`ben-ui-design` skill).** Go surface by surface against `docs/UX-SPEC.md`: the
stage/console, all four sheets (SOURCE/MODE/LIGHT/SETTINGS), the transport, typography,
spacing, motion (the 80–200 ms eased transitions, the tube-flip, thermionic warm-up), the
carved-stone dimensionality, sharp corners / hairlines / mono discipline. Keep what's good
(Ben liked the look), sharpen the rest, and only then extend into the un-built M5 pieces
(deck sheet, gestures, rooms, kits, compose, postcards — section B). Do this on FRESH context
with `ben-ui-design` loaded before touching any UI. The engine/JNI/bridge below M5 are solid
— this pass is about the Compose chrome quality, not the Rust.


### A. Bridge: two stream toggles + source selection (Ben's live asks — do first)
Full design in **docs/BRIDGE.md → "Planned next"**. Short version:
1. Config frame on connect `{audio, geometry}`; relay conditionally streams PCM (`A`) and/or
   `phosphor tap` polyline (`G`); phone draws geometry when on (bypass DSP via
   `GpuRenderer::advance`), else local-scopes the audio. Two toggles in the remote UI.
2. Source picker: relay enumerates `pactl` sinks + sink-inputs; phone picks primary-output or
   a specific app; relay captures it **read-only** (no vacuum, don't move the app's stream).
   Per-app solo = port desktop phosphor's mirror/targets node-linking. `phosphor tap` frame
   format is captured in BRIDGE.md.
3. Add a host field (settings) so it's not hardcoded to the laptop IP.
Also: set parec low-latency (`--latency-msec`) so streamed audio arrives in steady small
chunks (currently bursty → the scope draws ~400 segs then 0 between chunks; visible but not
smooth). And the status band showed "no source" once when connect raced — verify the
`ui.sourceLabel="remote"` update always lands.

**FIRST THING to revalidate next session (Ben, 2026-07-18):** the remote scope was still
black in real use AND phone audio needs a re-check (it worked on the phone's speakers earlier
this session). The black scope was almost certainly because **Spotify Connect moved playback
to the phone the moment AirPods connected** — so the laptop source went silent and the bridge
captured silence (proven: a laptop test *tone* drew 960 segs fine). Revalidate by putting real
audio on the SOURCE machine (Spotify device = laptop, or play a local file on the laptop) and
confirm: (1) the remote scope draws, (2) audio plays on the phone. If the phone audio is
genuinely muted, check Android's "background playback would be muted" hardening — the remote
playback may need a foreground service like the deck's PlaybackService (currently oboe runs
without one for the remote source).

### A″. Native audio residency for the remote source (Ben's ask, 2026-07-18)
Make the Tailscale audio play **as if native** — its own media-notification + lock-screen
presence so Ben can route it to earbuds or the phone speaker like any media app. Today the
remote oboe output runs with NO foreground service (why Android logs "background playback would
be muted"). Fix: route remote playback through a **MediaSessionService** (extend/reuse the
deck's `PlaybackService` + `PhosphorPlayer` `SimpleBasePlayer` pattern), FGS type
`mediaPlayback`, so:
- The remote track shows in the system media notification + lock screen, metadata (title/
  artist/album/art) fed from the relay's `M` frames.
- Notification/lock-screen transport (prev/play/next) drives the LAPTOP via the bridge
  (`remoteTransport`) — the loaded-deck-owns-the-transport law, remote edition.
- Audio focus + `BECOMING_NOISY` + `AudioDeviceCallback` handled → clean earbud↔speaker
  switching, no ducking surprises. This is the fix for the "revalidate phone audio" item.
- Net effect: the remote source is indistinguishable from a local media app to the OS.

### A′. Remote music file-browser (Ben's ask, 2026-07-18) — bundle with the bridge work
Browse the source PC's music library over the bridge, pick a file, stream + scope it — a
"remote library" mode distinct from live-output capture.
- Relay gains `list <dir>` (JSON: dirs + audio files) and `play-file <path>` (decode the file
  → stream as `A` PCM, reusing the audio path — e.g. `ffmpeg -i <path> -f s16le -ar 48000 -ac 2 -`
  piped into the frame writer). Roots to expose: **the PC's main-drive music dir** (confirm
  path — likely `~/Music` / `~/Music/WAV versions`, Ben's scope-music WAVs live there) **and
  Mass storage music** (`/media/ben/Mass storage/…` — that drive is on the DESKTOP
  `interserve-linux`, NOT the laptop, so the relay may need to run on the desktop, or expose
  both machines' roots). **Confirm with Ben which machine(s) host the music + exact paths.**
- Phone: the remote SOURCE flow gets a "browse library" path (folder/file list from the relay)
  alongside "live output" and the app/source picker.

### B. Finish M5 UX (per docs/UX-SPEC.md)
- **Deck sheet**: now-playing (cover art via MediaController artworkData), seek bar (scrub →
  controller.seekTo), queue from a folder (SAF OpenDocumentTree → list audio). Makes local
  playback feel real. (Ben: "get local and web playback looking amazing.")
- **Gestures** (the instrument feel): 1-finger drag = GAIN (2D) / ORBIT (3D), pinch =
  gain/dolly, 2-finger horizontal swipe = mode step, 2-finger vertical = glow. Needs engine
  verbs SetGain/SetGlow/SetCamera + a GestureArbiter in Compose. Haptics on detents.
- **Resting-beam dot + `no signal · <source>`** when silent (currently black — a
  SERIOUS-TODO).
- **Full settings port** (Ben's ask, 2026-07-18): port the desktop's whole settings surface
  (phosphor-proto `Settings` + the desktop panel — RENDERER/SCOPE/APPEARANCE/SIGNAL-KIT/
  PERFORMANCE): Focus (beam px), scope sample rate, glow/persistence, gain + auto-gain, grid,
  glass, color cycle (1–3 colors, timer/per-track) + the **photosensitivity guard**, kit path,
  GPU quality, max-fps — plus a **host field** for the bridge. Mobile currently exposes only
  frame-rate / beam-rate / room / beam-color; this is the rest, laid out thumb-friendly.
- **Themes — 3 of VERY DIFFERENT UI STYLE, all mobile-friendly** (Ben's ask, 2026-07-18): NOT
  just palette swaps. Design three distinct-personality UIs — different control character,
  density, motion feel, dimensionality — each still obeying the house non-negotiables
  (`ben-ui-design`: sharp corners, hairlines, mono data, Obsidian-dismiss). Think e.g. a warm
  carved/dimensional room (Blossom-family), a stark true-black minimalist room (AMOLED-family,
  flatter/quieter), and a third with its own clear identity. Elevate 3 rooms from the 12 to
  full distinct-style treatments rather than porting all 12 as palette-only. This belongs with
  the §0 M5 UI redo — theme character is core UI, do it with `ben-ui-design` loaded.
- **Remaining rooms** (palette-only), **kits** browser/editor, **compose** mode (finger-draw →
  WAV), **.phos postcards** (share-sheet), snapshot/clip exports.

### B′. App icon (Ben's ask, 2026-07-18)
The app ships with the default Android icon — M0 removed the missing `@mipmap/ic_launcher`
reference from AndroidManifest.xml (add it back once the icon exists). Make a proper **Android
adaptive icon**: `mipmap-anydpi-v26/ic_launcher.xml` (+ round) pointing at a foreground +
background layer, with PNG fallbacks across densities (mdpi→xxxhdpi). Derive it from the
desktop scope identity — `phosphor` repo's `packaging/phosphor4-scope.svg` (the 4-panel scope
glyph) — and honor the **`phosphor-icon` skill's laws** (closed-figure traces, compose-on-
transparency-first verification, guard-band scans, stale icon-cache gotchas). AMOLED-friendly:
the beam/scope figure on a true-black or plane-dark background reads best on the S25. Then set
`android:icon`/`roundIcon` in the manifest and rebuild.

### C. M6 — PiP + polish + v1.0.0 release
PiP auto-enter (setAutoEnterEnabled), ember auto-dim, thermal pass, keystore
(`~/.android-keys/phosphor-mobil3.jks`, NEVER in git — back it up), release.sh (apksigner
verify + 16 KB llvm-readelf check + SHA256SUMS + `gh release create --notes-file`), README
screenshots, concourse registration of pm3, flip the GitHub repo public for the APK release.

## Decisions & honest limits (don't relitigate)
- **>120 fps display is impossible on Android** (compositor vsync-locks all surfaces; caps =
  [Mailbox,Fifo], no Immediate). Shipped 60/90/120/uncapped + the genuine "beyond 120" =
  beam-rate oversampling (120/240/480 substeps, dt-correct decay).
- **Spotify/DRM apps can't be captured on-device** (CAPTURE_AUDIO_OUTPUT is role-managed;
  Shizuku can't help — tested). The bridge is the answer for Spotify.
- Zenfone 9s (Ben has spares) = possible later port target, not now.
- Non-DRM apps that DO capture on-device: browsers (verified: Chrome web audio → 960 segs),
  games, local/open players (NewPipe likely). Spotify/YT Music/Netflix opt out.

## Gotchas that bit (so they don't again)
- AGP 9 has built-in Kotlin — do NOT apply the kotlin-android plugin.
- core-ktx ≥1.19 / lifecycle ≥2.11 demand compileSdk 37 (unpublished) — pinned 1.17.0/2.10.0.
- oboe needs `shared-stdcxx` + libc++_shared.so copied by the Gradle cargo task.
- Media3: `addSession()` explicitly when no controller connects; COMMAND_GET_TIMELINE or no
  notification. One UI media card = expanded Quick Settings, not the shade.
- Compose over SurfaceView: the stage tap-catcher must sit BELOW the console (drawn earlier)
  so buttons win taps; a full-screen detector on the root eats them.
- `push_interleaved_le_bytes` expects **f32** bytes; the relay sends **s16** — convert to f32
  once and use `push_interleaved`. (This was the black-remote-scope bug.)
- On-device WAV staging for tests: `adb shell 'cat /sdcard/x.wav | run-as dev.phosphor.mobil3
  sh -c "cat > files/x.wav"'` (shell can't write Android/data; apps can't read /data/local/tmp).
- Blind adb taps on the console can hit ▸▸ and skip Ben's real Spotify — be careful.
