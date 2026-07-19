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

## Next session queue (FULL PLAN, written at Ben's ask 2026-07-18 late — three lanes)

**Read NEXUS-FEEDBACK.md** (8 compiled items + my ack appendix at its foot answering the
verify-questions). **Test bench moves to interserve-linux** (Ben's word); Spotify will be
open on BOTH machines — drive play per-machine via relay transport/playerctl.

### LANE 1 — the latency hunt, instrumented (Ben: "audio still lagging behind")
Facts in hand: tailscale phone path is DIRECT (IPv6, no DERP — verified); Ben's wifi signal
79%; current design is DELIBERATELY 250–350 ms behind (safe constants after the skip fix);
his A/B is phone-buds vs laptop-speakers side by side, so ALL buffering is audible as lag.
1. **Tethered ground truth** (Ben's protocol): USB-tether phone→interserve; measure
   speed/latency/loss per transport — termux `pkg install iperf3` + iperf3 to the PC, ping
   -c100 jitter/loss over tether vs wifi vs mobile; receipts table into docs/dev/receipts/.
2. **Adaptive latency** (the real fix): replace fixed catch-up constants with an adaptive
   jitter buffer — start tight (~80 ms), WIDEN on observed underruns (count zero-fill
   events in the RT callback — atomic, RT-safe), SHRINK after clean minutes; SETTINGS →
   REMOTE → `LATENCY · tight / balanced / safe` (tight for tether/LAN, safe = today's
   shape). HUD buf-ms is the live receipt; Ben's camera rig (films both screens) gives the
   VISIBLE end-to-end offset receipt — frame-step the clip.
3. **Network selector (Ben's feature ask)**: SETTINGS → REMOTE → `NETWORK · auto / wifi /
   mobile` — ConnectivityManager.requestNetwork(TRANSPORT_WIFI|CELLULAR) + bind (process or
   per-socket) before session connect; reconnect on change; persist. HONEST LIMIT: one TCP
   stream cannot true-multipath — offer auto (system) instead of "both" and say why in the
   sheet prose. Verify tailscale stays direct per-transport (status receipt each).
4. Re-run Ben's full-song verdict per transport+mode; his ears remain the gate.

### LANE 2 — the phone becomes a station terminal (termux + mosh + rmux)
Ben's flow (his words): **phone mosh → interserve-linux or thinkcenter → rmux attach.**
VERIFIED already in place (2026-07-18 late): mosh-server on BOTH machines (/usr/bin);
rmux on BOTH (PC /usr/bin/rmux, laptop ~/.local/bin/rmux — non-login ssh PATH misses the
laptop's, use the explicit path or fix PATH); Termux on the phone (com.termux) with keys
per Ben, phone has mosh. rmux = github.com/Helvesec/rmux (rmux.io) — Rust tmux-compatible
multiplexer with a TYPED SDK to drive CLI/TUI from code; sessions persist on the machine.
Remaining next session: (1) verify phone→both-machines mosh over tailnet + one
roam-survival receipt (wifi→mobile mid-session, rmux session lives); (2) `pkg install
iperf3` in termux for lane 1; (3) the side-channel: termux `am` firing phosphor-mobil3
intents (REMOTE_CONNECT…) WITHOUT adb — backup control path, test + document; (4) document
the rig (docs/ + concourse node if it earns one); rmux's typed SDK is a future
agent-drives-the-phone lever.

### LANE 3 — the Nexus batch (order = Nexus's read + my root-cause; ack at file foot)
1. **#2/#2b corner-clip ROOT FIX** — CONFIRMED root cause: chrome pads by
   WindowInsets.safeDrawing, which does NOT include the physical corner radius; with
   immersive bars hidden the bottom inset ≈ 0 so every room draws into the S25's clipped
   curve. Fix ONCE at the chrome root: read the Android 12+ RoundedCorner API
   (rootWindowInsets.getRoundedCorner(BOTTOM_*) radius) → min-inset the console/sheet
   bottoms; per-room rounding stays cosmetic. Screenshot sweep across a box room + glass.
2. **#3 bottom-pull phosphor bloom** — the creative budget item: overscroll at bottom =
   beam blooms proportional to pull (engine brightness path if reachable, else chrome
   overlay), rubber-band, spring-back HONORING each room's MotionFeel (Nexus's stake:
   never average the rooms). Borrow the real P7 two-layer decay from ../phosphor render.
3. **#1 RANDOM ⚄ mode** — picker entry; rolls a REAL mode (band shows `xy45 ⚄`, never
   "random"); re-roll on track change + tap-to-reroll. App-side roll over the 11-mode pool.
4. **#4 notification/lock-screen art parity** — feed the SAME resolved bitmap the in-app
   UI uses into MediaMetadata (remote/Drive art included; never stale, blank when absent).
5. **#5 thinkcenter output-switch bug — GET REPRO FROM BEN FIRST** (which UI: desktop
   phosphor vs phone SOURCE picker? exact steps?) — fix differs (desktop sink re-bind vs
   picker echo). Likely relay S-frame echo vs pw sink move; do not build blind.
6. **#6 settings parity diff + HUD auto-hide + auto-gain setting** — diff desktop Settings
   vs mobile groups; HUD auto-hide rides the console timer pattern; auto-gain = decide
   local-engine port vs desktop passthrough after the diff (band `·a` tag exists).
7. **#7 custom settings icons** — ImageVector set in phosphor's own language (beam/knob/
   sink/decay-curve), room-ChromeCharacter-aware (engraved/carved/annotated variants).
8. **#8 launcher icon: more waveforms, keep the hero** — layer supporting traces behind
   the anchor figure; phosphor-icon skill laws (closed figures, guard bands, verify
   on-launcher).

### Carry-over (after the lanes)
- Act X polish: shuffle/repeat → REMOTE HOSTS editor → long-press+hints → LIGHT undo/redo
  → ember → volume poll; glass glyph-tint pass; amber POST verify-by-eye.
- Deferred receipts: doze torture (USB adb ✓ now — udev rule installed), codex re-audit
  (optional pre-v1.0.0 ceremony).
- M5 kits/compose/postcards; M6 prep (Ben gates); desktop backports (LiquidGlass room,
  SPSC adoption).

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
