# Handoff — next session starts here

## Where we are (2026-07-18 evening, after the SERVICE-BENCH session): ship-safe + four souls

One Fable session (plan: `~/.claude/plans/spicy-bubbling-whale.md`) closed the ENTIRE codex
audit — all 13 findings fixed with on-device receipts (resolution table at the foot of
`docs/dev/codex-bridge-audit-2026-07-18.md`; per-finding invariants in docs/BRIDGE.md
"Lifecycle & hardening" — read that before touching remote.rs/session.rs, the orderings are
load-bearing). Phone: single control thread owns sessions (overlap impossible), writer
thread + fail-fast JNI (zero ANRs through a blackhole probe), Session RAII with ordered
teardown, token-cancelled oboe supervisor + reopen ladder (a real BT flap receipt), lock-free
SPSC ring (rust/src/spsc.rs — first impl of desktop's SPSC-RING-DESIGN) with SUSTAINED
catch-up (field-retuned after Ben's ears caught the first cut skipping in normal jitter:
400 ms elastic, skip only after 250 ms continuously >350 ms). Relay 2.1.0 BOTH machines:
every external deadline+cancellable, browse/Drive-fetch are supersede-tokened jobs, pump-id
EOFs, Drop-RAII everywhere, monotonic clocks. BUGLOG #5 found live (stale mute across
sessions — buds silent on a healthy stream) and fixed.

**Remote scope control is LIVE (Ben's ask):** the phone drives the DESKTOP scope while
VISUALIZER streams — MODE taps / LIGHT presets / pinch-gain send V frames; relay speaks
phosphor's ctl socket (status) + CLI (commands); K carries scope truth; the band shows
`xy45 · auto ×1.38 · pc` honesty. Desktop phosphor grew `ctl gain <v|auto>` → **4.7.1 on
master (its repo), deb on the PC, ~/.local/bin overlay on the laptop (running instance
relaunched onto its display)**. The laptop /usr/bin is still the 4.6.2→4.7.0-era deb —
`sudo dpkg -i /tmp/phosphor_4.7.1_amd64.deb` when Ben's at it. NOTE: packaging/build-deb.sh
HARDCODES its version (env PHOSPHOR_DEB_VERSION overrides) — I clobbered the local 4.7.0
dist deb before catching it (removed; canonical lives on the GH release). Fix the script.

**Four souls + the chrome feel:** RoomStyle framework (ChromeCharacter × MotionFeel per
room, LocalRoomStyle, crossfade-aware) absorbed the amoled discriminators; the 240 ms
whole-chrome crossfade finally exists (alpha-aware Palette.lerpTo). CRT Amber service bench
(designators S1/V2/J1/S9, dotted leaders, detented motion, mono prose, BenchPost cold-start
POST, CAL stamp). Liquid Glass 13th room (Ben's ask — glacial, translucent slabs with
specular rims + iOS-6 sheen, springy sheets, room-scoped 12dp rounding, follows-beam).
Custom style editor in the ROOM sheet (FEEL/MOTION/CORNERS/LABELS overrides, persisted).
Self-portrait tiles (§2.6 complete). Fullscreen immersive by default (+ focus re-assert),
BAND on/auto/off, expressive dismiss (sheets ACTUALLY animate out now — visible=true had
made exits impossible), instant press tints. Nerd HUD line 2 = bridge health
(buf ms/skips/drops/leaks).

**The dev loop:** `source scripts/env.sh` then `dev/pm3 <verb>` — EVERY Bash call re-sources
(shell state does not persist; half this session's noise was forgetting). **USB adb works
now** (udev rule /etc/udev/rules.d/51-android.rules) + wireless re-paired; prefer USB.
Drive the app: `am start-service -n dev.phosphor.mobil3/.PlaybackService -a
dev.phosphor.mobil3.REMOTE_CONNECT --es host <ip> --ei port 45777 --es label <name>`;
keyevent 126=play 85=toggle; room via `run-as dev.phosphor.mobil3 sed -i s,old,new,
shared_prefs/phosphor.prefs.xml` (bare-word sed — quotes die in the shell layers) +
force-stop + run. Blind stage taps register as GESTURES (a tap dragged Ben's gain to 6.0).

## Next session queue, in order

1. **Act X polish remainder** (untouched this session, all located): shuffle/repeat on the
   deck queue (DeckSheet.kt:99-128 absent) → REMOTE HOSTS add/edit UI (MainActivity
   remoteHosts hardcode) → long-press 450 ms context popout + first-entry hints (Gestures.kt)
   → LIGHT undo/redo (SheetHost header) → local auto-gain (fresh engine-side port; the
   band's `·a` tag exists) → ember auto-dim (brightness-budget verb in render.rs) → volume
   rule live-poll. Also: glass glyph tint pass (dark glyph on glass slab — Ben may want
   accent), amber POST is hard to screenshot but plays for humans (verify by eye).
2. **Deferred receipts:** doze force-idle torture (needs USB adb — wireless strands the
   phone), the two-screen camera latency clip (Ben's rig films laptop scope + phone scope;
   camera was busy tonight), codex re-audit of the hardened bridge (optional ceremony
   before v1.0.0).
3. **M5 remainder: kits · compose · postcards** (unchanged from before).
4. **M6 prep** (keystore, release.sh, README screenshots from the CURRENT four-soul build,
   concourse pm3 registration) — Ben's polish round still gates any release.
5. **Desktop backports queued as asks:** LiquidGlass room; SPSC ring adoption
   (phosphor-audio still Mutex+Condvar — mobile's spsc.rs is the reference impl).

## Field notes (so they don't bite twice)

- Ben's live feedback drove two mid-session fixes: stale-mute (BUGLOG #5) and the catch-up
  retune — HIS EARS ARE THE ACCEPTANCE GATE for audio; ask for a full-song verdict after
  any latency change. (Tonight's verdict on the retune was still pending at close.)
- Ben lives in the Void room (amoled); receipts left him in glass — he can walk back via
  ROOM (which crossfades now). His gain was left at 6.0 by my stray tap — one pinch fixes.
- relay-install.sh over ssh: fine. Desktop phosphor over ssh: non-login PATH resolves
  /usr/bin FIRST — use explicit ~/.local/bin/phosphor for the overlay instance.
- `adb mdns` can advertise a stale connect port after wireless-debug wedges; a phone-side
  toggle mints a new port; pairing needs the dialog OPEN (adb-tls-pairing service).
- grep -c returning 0 exits 1 and kills && chains; `sed s,a,b,` bare-word survives adb
  shell quoting layers, anything with quotes/>| dies.
- The narration rig (kitty right-panel + timestamps + amber ANSI + intercom TTS + memes)
  is session-local — pattern: scratchpad narrate.sh {say,win,ouch,meme,act}; Ben resized
  the panel once (respawn on glitch) and asked for timestamps+color (keep).
- codex gpt-5.6-sol (high) one-shotted the desktop gain verb across 5 files incl. laws I
  didn't brief (FEEDBACK.md SOP, schema-coverage test) — 145 tests green. Ben's standing
  directive: route subagent work through codex; spare his Claude quota.

## Decisions & honest limits (standing + new)

- All prior standing decisions hold (>120 fps, DRM/bridge, Spotify Connect, geometry needs
  desktop phosphor, one media session, AGP9/pins, oboe shared-stdcxx).
- Audio latency policy: ~250-350 ms behind the laptop by design (listenability > tightness);
  the HUD shows live buf ms; constants in remote.rs are the tuning surface.
- Glass = specular translucency, NOT true backdrop refraction (Compose can't blur a sibling
  SurfaceView; would need the TextureView experiment — only if Ben asks).
- Rounded corners exist ONLY inside the glass room / user override (Ben's explicit ask);
  every other room stays sharp (house law intact).
- ctl gain drives desktop settings.gain (persisted there via SaveSettings) — the phone is
  genuinely reconfiguring the desktop app, as Ben asked ("change the remote render").
