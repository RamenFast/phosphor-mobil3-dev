# Handoff — v2 is installable; two things wait on Ben

**State:** `release/phosphor-2.0.0` at `586f5c7`, working tree clean, 13/13 gates green.
**Base for rollback:** `62d7d59`, tag `checkpoint/phosphor-2.0.0-phase-05b2`.

Run `scripts/ship-check.sh` before trusting anything below. It re-establishes all 13
gates from scratch and takes about a minute.

## What Ben can do right now

Install the prerelease:

```bash
adb -s 100.102.2.83:5555 install -r \
  "/media/ben/Mass storage/agenticTinkering/Codex/phosphor-2.0/v2.0.0-fortress-prerelease/phosphor-fortress-2.0.0.apk"
```

It is already installed as of this session. It sits beside v1.0.7 as a separate package,
and v1.0.7's `firstInstallTime` was verified byte-identical through every install cycle.

Both estate relays are live and carrying the new loudness field:
`interserve-linux` 100.114.165.77 and `thinkcenter` 100.66.109.56.

## The two open items, and why they are open

**1. Device-proving `stalled` / `backoff` / `error`.**
Not blocked on access. Both relay hosts are reachable, and the forcing actions are written
down in `docs/dev/receipts/phosphor-2.0/phase-B-remote-truth.md`: `iptables -j DROP` for
dialing and backoff, `kill -STOP` for stalled, a v1-shaped first frame for error.

It is blocked on **judgement**. Each one means deliberately breaking a relay Ben may be
listening to at that moment, and the payoff is a receipt for logic that already has host
tests behind it. Worth doing when he says the relays are free, not unannounced.

**2. Play commerce.**
Blocked on two things only Ben can produce:

- a Play upload keystore (`~/.secrets/` holds only the Fortress JKS)
- a published HTTPS privacy policy URL

The entitlement machine, billing wiring, trial storage and paywall are specified and
costed in `docs/plans/V2-FEATURE-LEDGER.md`. None of it can be validated end to end
without a Play Console entry, so building it first would be building blind.

## Where the truth lives

| question | file |
|---|---|
| what actually works, with the command that proves each row | `docs/plans/V2-FEATURE-LEDGER.md` |
| the relay link's honest state model | `docs/dev/receipts/phosphor-2.0/phase-B-remote-truth.md` |
| what Play still needs | `docs/dev/GOOGLE-PLAY-PUBLISHING-PLAN.md` §4.2 |
| the wire contract, including `K.rms` | `docs/BRIDGE.md` |
| the way back to v1 | `.../phosphor-2.0/v1-rollback-safety-net/RESTORE.md` |

## Two habits worth keeping

**Docs drift, and stale docs cost more than missing ones.** The publishing plan listed
three blockers that were already fixed and omitted two that were real. Every row in the
ledger now cites a re-runnable command precisely so the next reader can re-measure rather
than inherit an old belief.

**Twice this session I wrote down a blocker that was really an untested assumption**, and
both times the error pointed toward doing less work: "no shell on interserve-linux" (it is
this workstation) and "thinkcenter unreachable" (an unaccepted host key). Both are
corrected in the commit history. When a blocker appears, test it before recording it.
