# Handoff — next session starts here

## Where we are (2026-07-18 night, after the COMPLETION session): the queue is EMPTY of buildables

One Fable session (this one) executed the entire three-lane plan with six codex
gpt-5.6-sol workers on disjoint file sets (orchestrate → merge → build → on-device
receipts → commit). Three commits: `5668a92` (relay 2.2.0 + icon + wave-1 receipts),
`abc0fb2` (the big app wave), `6314211` (bloom + A/V sync + icons + fullscreen).
**Everything below is INSTALLED on the S25 and the relay is DEPLOYED on both machines.**

### What shipped (all receipts in docs/dev/receipts/)
- **Corner-clip root fix** — chromeSafeDrawingInsets (ui/Insets.kt): RoundedCorner radii
  → per-callsite sagitta clearance ∪ safeDrawing; every room inherits. Receipts: glass+amber.
- **Accuracy root fix (Ben's live report)** — "2-3 circles out of sync" + "doesn't look
  120fps" = the old beam-rate control SLICED one drained window into N separately decayed
  deposits + froze decay on empty ticks. Now: real polyphase reconstruction (48k→96k/192k
  inside Computer), ONE deposit per display frame, decay advances on empty windows.
  Regression test in engine.rs; on-device receipt `accuracy-circle-after.png` — ONE
  phase-locked circle from a quadrature tone. BEAM RATE labels are honest now (·kHz).
- **Remote A/V sync** — the oboe callback publishes its exact played samples via a
  lock-free slot ring; scope draws what the EAR hears (was leading by the whole jitter
  buffer). scope_drops stat in remoteStatus. Ear/camera verify still pending (Ben's gate).
- **Adaptive latency (Lane 1.2)** — tight 80ms / balanced 150ms / safe (= shipped shape
  bit-for-bit, DEFAULT); underruns widen 40ms→cap, 2 clean min shrink 20ms; mid-stream
  mode changes; SETTINGS → REMOTE → LATENCY; HUD line2 shows `tgt N ms · und N`.
- **Network selector (Lane 1.3)** — NETWORK auto/wifi/mobile; process-bind before rust
  connect, reconnect on change; honest no-multipath prose; manifest permissions added.
  HONEST LIMIT untested: forced wifi/mobile bind may bypass the VPN → tailnet 100.x could
  be unreachable on forced routes; prove on S25 before trusting (report-uiwiring.md §2).
- **Bug #5 (relay 2.2.0, BOTH machines)** — monitor-choose = real desktop output switch
  (wpctl set-default + pactl move-sink-input, cancellable) + honest S echo + transactional
  capture swap + revert-on-fail. Live round-trip receipt HDMI↔analog on interserve.
  Bonus root-caused: pw-dump 247KB overfilled the runner pipe → empty source list (fixed +
  regression). BUGLOG #7.
- **Art parity** — desired art id from M (was R-slot: fresh connects could never fetch);
  stale-art impossible at every boundary; R responses id-checked. Receipts: notification
  card + in-app deck across track changes (remote path). Local-file path code-fixed but
  not receipt-verified (needs a local track on device).
- **RANDOM ⚄** — real-mode rolls, band shows `radial ⚄` truth, track-change + armed-tap
  re-rolls, persisted. NOTE: persistence rides saveTuning = onStop only — adb force-stop
  skips it (not a bug; HOME first when testing).
- **Bottom-pull bloom** — engine-path (deposit-energy lift pre-advance, P7 layers own the
  decay); Amber 7 detents / Glass underdamped+breath / Void cut / Blossom eased; sheets
  rubber-band ≤28dp; stage arms only in bottom 88dp with console away. FEEL VERDICT IS
  BEN'S — no capture clip yet (he took the phone back mid-session).
- **Settings icons** — 8 hairline vectors, one geometry + ChromeCharacter wrapper
  (engraved/carved/annotated/glass). Glass receipt done; amber variant receipt pending.
- **Launcher icon** — hero untouched + 3 quieter closed-figure traces; monochrome matched;
  PNG mipmaps regenerated (rsvg-convert, all densities). On-launcher eyeball = Ben.
- **Swipe-up on play bar → SETTINGS** (verified in glass; in amber the cold-start timing
  beat my adb swipe — likely fine by thumb, verify by hand) · **FULLSCREEN toggle**
  (DISPLAY chip; off-state receipt pending) · **HUD on/auto/off** (auto rides console
  timer; pref migration) · **AUTO-GAIN** (desktop autosize law verbatim, host-tested;
  remote = passthrough; pinch disarms).
- Desktop repo: build-deb.sh now derives version from Cargo.toml (`99264f5` there).

### The one live process learning
Ben's phone is BEN'S: mid-session he took it back (Claude mobile foreground) — blind adb
taps landed in HIS app. LAW for next hands: before ANY input tap, check
`dumpsys window | grep mCurrentFocus` == dev.phosphor.mobil3; if not, hands off. Prefs
snapshot/restore protocol worked (his glass/×1.92 state restored at close; app left
stopped). His live pinches mid-session are HIS — stop "fixing" his gain.

## Next session queue
1. **Ben's verdicts (the gates):** full-song listen per LATENCY mode (safe default still
   right? tight on LAN?), bloom feel in Amber vs Glass, launcher icon on-launcher, amber
   icons variant, fullscreen-off, swipe-up by thumb in every room. Camera rig for the
   A/V-sync receipt (film both screens, frame-step).
2. **Lane 1.1 tethered ground truth (GATED ON BEN):** USB-tether → iperf3/ping matrix
   tether vs wifi vs mobile → receipts table; then LATENCY tight verdict on the best path.
   Also prove NETWORK forced-wifi/mobile keeps tailnet reachable (see honest limit above).
3. **Lane 2 phone terminal (GATED ON BEN's hands):** mosh roam receipt, termux-am
   side-channel test, rig docs.
4. **Fidelity map** (docs/SERIOUS-TODOS.md, from the accuracy audit): geometry-mode
   64-seg decimation drawn as trace (first suspect if circles ever split in VISUALIZER
   mode), s16 transport quantization, focus default 0.3 vs desktop 1.6 (ask Ben), JNI
   ingest alloc, local deck mutex ring → SPSC.
5. **Carry-over:** beam-cycle parity in LIGHT (task exists), Act X polish (shuffle/repeat
   → REMOTE HOSTS editor → long-press+hints → LIGHT undo/redo → ember → volume poll),
   glass glyph-tint pass, amber POST verify-by-eye, doze torture, codex re-audit
   (pre-v1.0.0), M5 kits/compose/postcards, M6 prep (Ben gates), desktop backports
   (LiquidGlass room, SPSC adoption, consumer-side tap idea).

## Field notes (new ones only — prior notes in git history still hold)
- codex workers write compile-clean rust but NOT compile-clean Compose: this session's
  breaks were suspend-in-restricted-pointer-scope (Animatable calls must hop onto a real
  scope) and cubicTo≠curveTo (PathBuilder). Budget an integration-fix pass after every
  Kotlin wave; rust waves came in green every time.
- The MODE/SRC/… console buttons: chain summon-tap + action-tap inside one adb shell
  (`input tap ...; sleep 0.4; input tap ...`) — the console auto-hides in ~4s and cold
  start swallows early input for ~1.2s (warm-up).
- An AskUserQuestion dialog EATS Ben's typed text if the session moves on — he flagged it
  ("it deleted my feedback"). Ask again immediately when that happens; better, keep
  questions terse and singular while he's semi-AFK on Telegram.
- Relay deploys: relay-install.sh local + `--host thinkcenter` both fine; the LOCAL
  service does NOT restart on install — `systemctl --user restart phosphor-relay`
  explicitly (thinkcenter's did restart via the script's remote path).
- paplay + ffmpeg lavfi quadrature (sin|cos) through the default sink = instant clean-
  circle ground truth for any future accuracy claim. 12s tone, screenshot at 5s.

## Decisions & honest limits (standing + new)
- All prior standing decisions hold. Latency POLICY changed: safe (≈today) remains
  default; tight/balanced exist but are unverified by ear — Ben's verdict gates any
  default change.
- A/V sync: trace now matches the callback-era audio exactly; remaining lead = BT/route
  latency, unmeasurable without an output-timestamp/acoustic loop (documented in
  report-avsync.md). No promise made in UI about absolute sync.
- Scope accuracy: audio-path fixed and receipt-proven; GEOMETRY mode (VISUALIZER
  streaming) still draws decimated midpoints as a trace — known, mapped, unfixed.
- Auto-gain constants are desktop-verbatim (0.999 release / 0.92 headroom / 0.05 glide);
  any retune is desktop-first (engine source-of-truth law).

## Later that night — Ben's live batches 1+2 (appended 2026-07-18 ~22:00)

Ben stayed on the line and fed two more batches; ALL shipped (commits 7142242, 4e28d25,
0e4fdde; installed on the S25; desktop 4.7.2 both machines):
- **Card chrome approved** → extended: animated centered popout above the play card,
  swipe-down closes sheets (nested-scroll at top), finger-tracked + flick-aware sheet
  opening (40% settle / 920dp·s⁻¹), S9 tap/pull dual-affordance menu + new glyph.
- **Landscape + portrait** with SCOPE ROTATION and UI PLACEMENT locks (honest
  one-Activity limit in the sheet prose); four-corner inset model.
- **Bottom-edge arbitration**: bottom-band swipe-up only summons controls — never
  gain/seek (Ben's live bug).
- **Grid zooms with gain; gain to ×7** everywhere incl. desktop 4.7.2 remote (auto-gain
  target stays 6, desktop-verbatim).
- **Art mystery SOLVED — ops not code**: thinkcenter's relay SERVICE was never restarted
  onto 2.2.0 (ran the stalled pw-dump loop since 16:18; audio thread fine, M/art
  starved). LAW: relay-install does NOT restart the local service — always
  `systemctl --user restart phosphor-relay` and verify the RUNNING version, both
  machines. Ben's eyes still owe the final art receipt.
- **LOCAL ANDROID CAPTURE WORKS (Spotify allows capture now — Ben's discovery)** → built
  capture-source metadata: active-MediaSession title/art/transport while capturing, via
  a notification-listener gate + honest grant… affordance. VERIFY ON DEVICE: grant flow,
  metadata display, transport routing, no-permission path.
- **Persistence audit**: only gap was custom light (colors/cycle) — now persisted.
- **CODEX IS OUT until Jul 24** (usage limit, died mid-task; Fable finished by hand).
  Until then: subagent work is Fable/Opus or by hand — plan fleet size accordingly.
- Icon supports grew + amber band crosses the hero (receipt = launcher eyeball).
- NEW Ben-verify queue on top of the earlier one: popout/S9 feel per room, finger-track +
  flick, swipe-down dismiss, rotation matrix (the report's on-device matrix in
  report-rotation.md), capture metadata flow, art after the thinkcenter restart,
  grid-zoom feel, ×7 headroom.

## Pass 3 (appended ~22:25) — storage, quick settings, beam-to-gravity
Commit e0c76e2, installed. Storage leak (staged audio, 441MB) fixed + LIVE-verified
(431MB→347KB on Ben's app open; sweeps on service create/destroy/track-open, ≤2 staged
tracks ever). Popout drags closed + carries FPS/HUD/GRID quick toggles (3 new glyphs).
Auto-gain now LOCKS the viewport (ribbon "auto · view locked"; SETTINGS GAIN rule =
manual takeover). grant… deep-links to the notification-ACCESS toggle (≠ "allow
notifications"). UI PLACEMENT locked = Activity PINNED + OrientationEventListener
rotates the BEAM to gravity (Cmd::SetViewRotation, quarter-turn maps, DSP path only —
remote geometry keeps its frame) + FlatKey labels/quick toggles counter-rotate
(LocalUiUpright). ⚠ VERIFY WITH BEN: rotation quadrant SIGNS (flip = one line in
MainActivity's sensor or rust rot()); popout drag-close feel; quick-toggle icons in
amber/void; capture metadata after granting access.

## Pass 5 — the finish (appended ~23:10)
Rotation matrix COMPLETE (Opus subagent through Claude Code — codex still walled):
one rotate-layout primitive (Modifier.uprightRotate/UprightCell: swapped-constraint
measure + bbox-sized cell + centered placeWithLayer — nothing clips, hit-testing intact),
four-mode routing (free+follow rotates activity · free+UIlocked pins + beam-to-gravity ·
locked+follow pins + CHROME-to-gravity container · both-locked pins + elements-upright),
mutually-exclusive quadrants, ±30° cardinal hysteresis. TOP lockstep toggle in quick
settings (band+HUD as one switch) — RECEIPT toptext-off-receipt.png: pure scope, the ×2
bug closed. Popout receipt: icons on all rows + quick toggles live + upright rendering.
Notification access granted (adb allow_listener) + capture transport verified E2E
(MEDIA_NEXT advanced SoundCloud, mirror followed). BEN VERIFIES BY HAND: the four
rotation modes physically, chrome-to-gravity hit-testing, insets in rotated chrome
(honest approximation — top inset not permuted), sensor feel. His HUD was auto — one
tap on the SETTINGS HUD chip restores the fade (TOP toggle left both on).

## Pass 6 + the close (appended ~23:35) — "let's get everything done this session"
Commit below = the finish. Sign flip per Ben's receipt (deviceQ−displayQ — if anything
still turns wrong, flip THAT line). Pre-surface settings-drop CLASS fixed in render.rs
(grid/glow/focus/beam-theme now applied at renderer creation from mirrors — the
"toggle-it-twice-after-update" family is dead). Popout = dense 2×2 grid + rule + even
toggles (RECEIPTED live in landscape with capture metadata showing Braden Ross — one
frame proving popout density, category icons, landscape console, seek rule, capture
mirror). Settings = real two-column landscape anchored per rotation side. LIGHT beam
cycle SHIPPED (slots/ring/LEG/TIMER-TRACK, photosensitivity-gated, persistence already
wired). TOP lockstep receipted (pure scope). Opus subagents went 5-for-5 on first-try
compiles. REMAINS FOR BEN'S HANDS: physical rotation matrix re-verify post-sign-flip,
beam-cycle eyeball, latency/full-song verdicts, lanes 1.1/2 (tether ground truth, mosh
roam, termux-am) — all gated on his body, not on code. Codex returns Jul 24.

---

## Appendix: v1 night (2026-07-19, the close-out batch)

Ben's batch: no-Python verify · GitHub public v1 w/ screenshots + APK · desktop sync ·
beam/glow ⚄ randomizers w/ range · ban faces from mode-⚄ · "MANIPULATE GEOMETRY" · agent-CLI JSON.

**All shipped.**
- **Python: verified clean** — zero authored/tracked Python (cargo-ndk via plain Gradle Exec; NDK's .py gitignored).
- **Features** (commit 0395482): `RangeDragRule` two-thumb primitive + ⚄ dice on BEAM/GLOW (tap=arm+roll in range, per-track re-roll, manual drag disarms) · BAN FACES editor in MODE·AUTOMATIC (≥2 guard, CSV pref) · GEOMETRY FX stage in rust render (kaleido/spin/tunnel/pulse, pre-deposit/pre-rotation, loop-local state = pre-surface-safe, never mirrored to desktop) + 5 unit tests (32 green). Live receipts: geomfx-kaleido-live.png (4-fold mandala on Ben's Spotify), ban-faces-receipt.png, randrange-rules-receipt.png. Device driven with Spotify playing; state restored exactly (Spotify forward, screen off, prefs intact).
- **Public view** (commit b73d6d8): private repo renamed **phosphor-mobil3-dev** (remote URL updated); public **RamenFast/phosphor-mobil3** created from `scripts/publish-public.sh` (agent-CLI JSON; allowlist rsync + sanitization gate: tailnet IPs/hostnames/ecosystem names abort the publish; independently re-grepped clean). Hosts left source → BuildConfig via gitignored local.properties (`phosphor.remoteHosts=…`; Ben's file seeded, his installed debug build carries his hosts). README rewritten w/ 4 screenshots; docs/REMOTE.md = generalized bridge knowledge.
- **Release v1.0.0** (both repos tagged): signed APK (keystore `~/.secrets/phosphor-mobil3-release.jks` + `.pass`, NEVER commit; cert sha256 e4d14ce2…) built with EMPTY hosts (dex grepped clean), SHA256SUMS, download+checksum round-trip verified. **Ben's phone keeps the debug install** — release-over-debug = uninstall = wiped prefs; adopt deliberately if wanted.
- **Desktop sync**: 4.7.2 deb built; interserve = deb 4.7.2 ✓; thinkcenter = ~/.local/bin/phosphor now the true 4.7.2 deb payload (session PATH puts ~/.local/bin first — verified via systemd user env). /usr/bin trued up to 4.7.2 (Ben authorized live) and the overlay retired — deb is the single source of truth on both machines. v4.7.2 GitHub release cut w/ deb. Relay 2.2.0 active both machines.

**Ben-verify list**: geometry FX by eye (tunnel breathes ~17s — stills undersell), dice feel, ban-roll behavior across tracks, the public repo front page reads right. Old gated lanes #11/#12 still parked.

**Ben's verdict on the 1.0.6 batch: "Wonderful update. Truly."** — view-lock takeover and
beam-tinted chrome move to shipped on his word. Rotation flatness-gate stays on his
physical-verify list (tilt can't be faked over adb). Session tally: v1.0.0 → v1.0.6,
seven public releases in one night, the bestiary sleeps in the manual.
