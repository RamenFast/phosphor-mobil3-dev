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

## Honest status of this receipt

States proven live on device: **0 of 5** so far. The forcing actions above are agreed but
not yet executed, because Ben is out with the phone in use and device work waits for an
idle handset. This receipt records the plan and the audit; the live column gets filled in
when the phone is free.
