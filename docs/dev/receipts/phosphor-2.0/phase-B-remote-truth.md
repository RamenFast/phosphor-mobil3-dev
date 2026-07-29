# Phase B receipt: remote connection-state truth (acceptance L-04)

**Date:** 2026-07-28
**Method:** read-only source audit (Opus 5, high effort), spot-verified by me against the
code before acceptance. Every claim below cites a file:line I re-read myself.

Acceptance `spec/ACCEPTANCE.md` L-04 asks that the human HUD and the agent snapshot agree
on `dialing / authenticated / connected / silent / stalled / backoff / error`.

## Verdict: 7/7 cannot be met as written, and that is the criterion's fault, not the code's

Two of the seven states are **not real** on this transport. Writing code to display them
would be inventing status, which is worse than showing less. The honest count is **5
states that exist**, of which **3 are currently reported truthfully**.

## The table

| state | exists in engine? | in `remoteStatus()` JSON? | shown to user? | verdict |
|---|---|---|---|---|
| dialing | yes, `ST_CONNECTING` `remote.rs:855` | `"connecting"` | `remote · connecting…` `MainActivity.kt:691` | **works** |
| authenticated | **no such thing** | — | — | **not applicable** |
| connected | yes, `ST_STREAMING` `remote.rs:1318` | `"streaming"` | `remote · <label>` | **partial, see below** |
| silent | **no** | no RMS anywhere on the remote path | nothing | **absent** |
| stalled | yes, `ST_STALLED` `remote.rs:1172` | `"stalled"` | **mislabelled** as `reconnecting…` `PlaybackService.kt:793` | **partial** |
| backoff | yes, ladder `remote.rs:814-829` | only `"reconnecting"`, no timer | `remote · reconnecting…` | **partial** |
| error | yes, `ST_FAILED` `remote.rs:807` | `"failed"` + `last_error{error,fix}` | `remote · unreachable`, **`fix` never rendered** | **partial** |

## The three findings that matter

**1. "Connected" is really "greeted".** `ST_STREAMING` is stored the moment the relay's
`W` welcome frame arrives (`remote.rs:1318`), *before a single audio frame*. So the band
claims a live link during a window where no media has flowed. The honest discriminator
already exists and is already published: `rx_a` / `rx_g` (`remote.rs:713`). Nothing reads
them.

**2. "Stalled" is computed and then thrown away.** The engine correctly distinguishes a
frozen-but-alive link (`ST_STALLED` at >3 s without frames) from a dropped one. The
service collapses both into `Conn.LOST` (`PlaybackService.kt:793`), so the user is told
"reconnecting" while the socket is fine and nothing is reconnecting. This is the
frozen-live-trace failure that acceptance H-04 explicitly forbids.

**3. Every error carries a `fix`, and no user ever sees it.** The engine populates
`last_error = {error, fix}` (`remote.rs:744-748,807-812`) and the relay guarantees a `fix`
on every `E` frame (`docs/BRIDGE.md:34`). The phone folds it into a `PlaybackException`
message (`PlaybackService.kt:815`) that no listener observes. The repo's own house rule
is that errors carry a usable fix; here the fix is manufactured and discarded.

## Why "authenticated" and "silent" are not real

**authenticated:** protocol v2 has **no authentication**. The relay greets on accept
(`docs/BRIDGE.md:19-20`); there is no credential, challenge, or token on this lane. The
word in L-04 appears to have been borrowed from the *Nexus* session plane
(`SessionState.AUTHENTICATING`, `StatePrimitives.kt:56`), which is a different transport
entirely and is not shipping. **Ruling: L-04 conflates two planes.** For the relay link,
"authenticated" should be struck or renamed to `greeted` (welcome received, no media yet),
which is a real and useful distinction.

**silent:** there is no RMS anywhere on the remote path. Worse, `NO_SIGNAL` cannot fire
during remote audio at all: the oboe callback pushes buffers unconditionally once started,
so the "empty samples for 2 s" precondition never becomes true. A silent desktop source
and a healthy loud one are indistinguishable in the app today. Making this real needs a
new measurement (cheapest: an RMS field on the relay's existing `K` frame, since the relay
already computes RMS for `probe --rms`, `relay/src/cli.rs:339-351`).

## Forcing actions, agreed in advance

A state counts as proven only if a named action produces it and two surfaces agree.

| state | forcing action | provable? |
|---|---|---|
| dialing | `iptables -I INPUT -p tcp --dport 45777 -j DROP` (DROP, not REJECT) then connect; 4 s `connect_timeout` `remote.rs:865` | yes |
| connected | connect and play; `rx_a` climbs ~100/s | yes |
| stalled | `kill -STOP` the relay pid, observe 3-10 s, `kill -CONT` before 10 s | yes, narrow window |
| backoff | stop the relay **and** DROP the port; rungs 1,2,4,8 s | rungs 1-8 only; the 15 s cap is unreachable because the service gives up at 60 s (`PlaybackService.kt:797`) |
| error | hold DROP past 60 s (practical path), or a v1-shaped first frame (the only `ST_FAILED` writer) | yes |
| silent | pause the desktop source; `probe --rms` shows rms≈0 with `a_per_sec` ~100 | **provable on the wire, invisible in the app** |
| authenticated | none, nothing to authenticate | **no** |

## What I am doing about it, and what I am not

**Doing now (small, honest, no new protocol):**
1. Stop lying about stalled: give `ST_STALLED` its own user-visible state.
2. Make "connected" mean media is flowing, using the already-published `rx_a`/`rx_g`;
   report the pre-media window as `greeted`.
3. Surface `last_error.fix` where the user can read it.

**Explicitly deferred, with the gate written down:**
4. **`silent` needs a relay protocol change** (an RMS field on `K`). That touches the
   wire contract and both deployed relays. It is not in scope for the Fortress
   prerelease. **Gate: implement RMS on the `K` frame before claiming L-04 `silent`.**
5. **The 15 s backoff rung is unobservable** under the 60 s give-up. Either extend the
   give-up or document the cap as unreachable. **Gate: decide before claiming L-05.**
6. **L-04 itself should be amended** to drop `authenticated` from the relay lane or
   rename it `greeted`. Recorded as an ask for Ben rather than silently reinterpreted.

## Live device results, 2026-07-28

Run against Fortress 2.0.0 release (`e4d14ce2...`) on the S25 at `100.102.2.83:5555`,
relay `interserve-linux` 100.114.165.77 running phosphor-relay 2.2.0.

| what | result | evidence |
|---|---|---|
| Fortress cold start | pass | `v2/fortress-2.0.0-cold-start.png`: grid, resting beam dot, `src · no source`. No FATAL/ANR in logcat. |
| Seeded hosts appear | pass | `v2/remote-sheet-with-add-relay.png`: REMOTE lists `thinkcenter` and `interserve-linux`. |
| **ADD RELAY control exists** | pass | Same shot: `+ ADD RELAY` renders in the sheet language. This is the control the Play build previously had no way to reach. |
| Connect to a relay | pass | `v2/remote-connected-interserve.png`: row checked green, band reads `src · remote · interserve-linux`, `MUSIC · on`. |
| Relay serving frames | pass | `phosphor-relay probe --host 100.114.165.77` → `a_per_sec: 97.67`, 293 A-frames in 3 s. |
| Link survives sheet dismiss | pass | `v2/remote-beam-live.png` and the later scroll shot both still show `src · remote · interserve-linux`. |
| v1.0.7 untouched | pass | `firstInstallTime` and the `shared_prefs` listing hash are byte-identical before and after install. Both packages coexist. |

### The "silent" gap, demonstrated rather than argued

The beam drew nothing while the link was healthy. That is not a defect, and the live
numbers say exactly why:

```
phosphor-relay probe --host 100.114.165.77 --rms
  → a_per_sec: 97.67, rms: 0.0, rms_peak: 0.0
```

The desktop was silent. The relay faithfully streamed 97 frames/sec of silence, and the
scope correctly drew nothing. This is precisely the case the audit predicted: **the app
cannot tell "connected and silent" from "connected and loud"**, because there is no RMS
anywhere on the remote path. A user seeing a black screen here has no way to know whether
the link is fine or broken.

That makes the deferred `silent` work concrete rather than theoretical. **This is now
closed** (commit `a7beb5f`), see below.

## `silent` closed, 2026-07-29

The gap above was fixed rather than filed, because it is the one a user actually hits.

**Relay:** the `K` frame carries `rms` and `rms_peak` for the window since the previous
`K`. Measured in the writer thread, so the number describes bytes that genuinely left the
machine. Each `K` drains its window, so a source that just went quiet reads quiet
immediately rather than being masked by a session-long average. Both fields are optional,
so older clients skip them.

**Engine:** `remote.rs` folds them into atomics and `remoteStatus()` reports
`remote_rms` / `remote_rms_peak` as **null**, not `0.0`, when the relay never sent them.
"Cannot tell" must stay distinct from "measured silence".

**App:** `RemoteLinkTruth` gains `SILENT`; the band reads `remote · <host> · no sound`.
A silent link stays `STATE_READY` and never counts against the give-up clock, because it
is healthy and simply has nothing to draw.

Measured against a real relay on loopback:

| source | rms | peak |
|---|---|---|
| silence | 0.0 | 0.0 |
| `Front_Center.wav` | 0.00393 | 0.026245 |
| 440 Hz sine | 0.565686 | 0.800018 |

The sine is the proof the maths is right: a 0.8-amplitude sine has a theoretical RMS of
`0.8/√2 = 0.565685`, and the wire reported `0.565686`.

Twelve tests cover it, six in the relay and six in the app, including a quiet passage that
must **not** be called silent, an older relay with no field, and an explicit null. Both
suites were verified failable by breaking the code and watching the right test go red.

**Deployment, 2026-07-29:** `interserve-linux` (100.114.165.77) is **rebuilt and live**
with the new relay, verified by reading raw K frames off port 45777:

```
{"ts_ms":...,"tx_a":91,"tx_g":0,"dropped_a":0,"rms":0.237353,"rms_peak":0.800018}
{"ts_ms":...,"tx_a":192,"tx_g":0,"dropped_a":0,"rms":0.0,"rms_peak":0.0}
```

`thinkcenter` (100.66.109.56) is reachable but still runs the older relay, and its K
frames confirm it:

```
{"ts_ms":...,"tx_a":89,"tx_g":0,"dropped_a":0}
```

That is the backward-compatibility path proven in production rather than only in tests:
the field is simply absent, so the app reports STREAMING and never claims silence it
cannot measure. thinkcenter picks the feature up whenever it is next rebuilt, and nothing
breaks in the meantime.

### Live verification of the SILENT rule, 2026-07-29

`interserve-linux` (100.114.165.77) now runs the new build via
`scripts/relay-install.sh`, with its systemd user service active. Checked against the
live production relay rather than a test port, using the app's exact threshold:

| desktop | live `K.rms` | app rule resolves to |
|---|---|---|
| silent | `0.0` | **SILENT** → `remote · <host> · no sound` |
| 440 Hz sine | `0.565686` | **STREAMING** → `remote · <host>` |

Not confirmed on the phone's own screen: the S25 sat behind its PIN lock for this run and
I will not guess or ask for it. What remains unverified is the pixel rendering of a
string, not the logic that chooses it.

### Still unproven live

The forcing actions for `stalled`, `backoff`, and `error` need `iptables` or `SIGSTOP`
against a relay while the phone watches. `interserve-linux` is this workstation, so the
shell is available, but deliberately breaking the relay Ben uses is not something to do
while he may be listening. They pass as host-side unit tests (`RemoteLinkTruthTest`) and
are **not** proven end to end on device. Recorded as unproven rather than assumed.
