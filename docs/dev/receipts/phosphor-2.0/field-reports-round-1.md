# Phosphor 2.0.0 — Ben's field reports, worked

Nine reports from testing the first 2.0.0 build. Each one below says what was actually
wrong, what changed, and how it was checked. Three of them needed no code change, and
saying so is the point.

## 1. Spotify — not a code regression

**Cause:** notification access is a *per-package* permission.
`dev.phosphor.mobil3` (v1) had it; `dev.phosphor.mobil3.fortress` is a different package
and started with none, so track names and art were unavailable.

```
adb shell settings get secure enabled_notification_listeners
  → dev.phosphor.mobil3/...CaptureNotificationListenerService   (v1 only)
```

Once granted, the app read the live title immediately:

```
dumpsys media_session → description=wrist w/ gash & cld (pk & brayam), uxie
```

**The real defect underneath, now fixed:** `startCapture()` called
`createScreenCaptureIntent()` with no config, so Android 14+ preselected **"Share one
app"** while the row that launches it says *"everything playing"*. The dialog
contradicted its own label. Now passes
`MediaProjectionConfig.createConfigForDefaultDisplay()`. Verified present in the shipped
dex.

## 2 + 5. Landscape cards came from the wrong place

Two reports, one wrong rule. The console is always `BottomCenter` at 620dp
(`Console.kt`), but the sheet flew to a side edge at 600dp (`Sheets.kt`), and the reveal
always animated `translationY`.

| mode | before | now |
|---|---|---|
| locked landscape | rose from the screen bottom, side flipped with rotation | slides in horizontally from one fixed anchored edge |
| unlocked landscape | arrived from the right edge in both rotations | rises from the centre, at the console's own 620dp |
| portrait | rises | unchanged |

The rule lives in `SheetEntryPolicy` so it can be tested without a Compose tree. 5 tests;
making entry depend on `landscape` alone (the old behaviour) fails three of them.

## 3. Landscape side flipped per rotation

`ROTATION_270` anchored left, everything else right, so the same gesture behaved
differently depending which way the phone had been turned. One edge now.

## 6. Rotation had no detent

**Ben's correction, which was right:** not a time delay. A delay makes a *correct* turn
feel laggy. He wanted angular commitment — "you have to really rotate it and then it's
set".

Before: a single symmetric ±30° window, so the chrome flipped the instant the phone
crossed 45°.

Now asymmetric, in `RotationDetent`:

- **hold** 38° — a hand-held phone at an untidy angle keeps its orientation
- **commit** 18° — taking a *new* orientation needs the phone genuinely turned

The gap between them is the detent. 9 tests, including a slow 0→90° sweep that must
commit exactly once and a wobble around 45° that must not oscillate. Restoring the
symmetric window fails two of them.

## 4a. Symlinked music was invisible

`read_dir`'s `file_type()` does **not** follow symlinks, so a symlinked folder was
neither dir nor audio file and silently vanished. Ben's real
`~/Music/WAV versions → /media/ben/Mass storage/…` never appeared.

Uses `metadata()` now, which follows. Entering it also had to work: the old
`canonicalize()` + `starts_with(base)` guard resolved the link and then rejected its real
location. **Per Ben's decision (A)**, a symlink the user placed in their own library is
deliberate and is served. The escape guard now rejects `..` components on the *requested*
path, which is the actual attack and a different question entirely.

Tested against the **real `~/Music`**, not only a fixture, skipping cleanly on machines
without it. Both that and the fixture fail if `file_type()` is restored.

## 4b. Only the first library root was reachable

The relay already served three (Music, Mass storage, gdrive) but the phone hardcoded
`libs.getJSONObject(0)`. A root picker now appears when there is more than one.

Adding a drive no longer means editing JSON:

```
phosphor-relay library list
phosphor-relay library add --path "/media/ben/Other drive" --label "Other"
phosphor-relay library remove --id lib0
```

Envelope + exit codes per the workspace CLI standard: duplicate, bad path, unknown action
and missing id each exit 3 with a `fix`. Ben's own `config.json` is byte-identical after
an add/remove round trip.

## 7. Latency modes — verified, not changed

Asked to confirm tight/balanced/safe are three things rather than three labels. **They
are:**

| mode | target floor |
|---|---|
| tight | 80 ms |
| balanced | 150 ms |
| safe | 250 ms |

And safe is deliberately **frozen** — `observe_underrun` and `observe_clean` are both
no-ops for it, while the other two adapt. That is what makes it *safe* rather than merely
*slower*. No behaviour change; two tests now pin it so a refactor cannot quietly collapse
them.

## 8. Capture re-auth — partly fixable, and honestly so

**Fixed:** tapping the capture row during a live session used to tear down a working
projection and re-prompt to rebuild an identical one. It returns early now.

**Cannot be fixed:** `MediaProjection` consent is single-use *by design*. There is no
persistent grant on Android 16 — no `media_projection_allowlist`, no
`cmd media_projection`. The prompt per capture **session** stays. The sheet says so rather
than implying persistence.

## 9. Sleep / dimming — already correct, now proven

`FLAG_KEEP_SCREEN_ON` was already set. On the S25:

```
SCREEN_BRIGHT_WAKE_LOCK  ws=WorkSource{10435 dev.phosphor.mobil3.fortress}
am get-standby-bucket    → 10   (ACTIVE)
foreground service       → running
```

Doze and App Standby only bite once the screen is **off**, which the flag prevents. So no
battery-optimisation exemption is requested: it would buy nothing and is a Play-policy
liability. That reasoning is a code comment now rather than something to rediscover.

## Totals

- **13/13** ship-check gates
- **560** app tests · **34** engine · **27** relay
- every new test verified failable by breaking the code first
- v1.0.7's `firstInstallTime` unchanged through every install this session

## Found while verifying: pm3 refused on a cold process

Not one of the nine. Found while trying to read UI state *without* touching Ben's
screen, which is the only reason it surfaced at all.

```
pm3 state-get → provider_refused
                "lateinit property causalStore has not been initialized"
```

A ContentProvider's `onCreate` runs **before** `Application.onCreate`.
`Pm3AdminProvider` reaches for `app.causalStore`, which was a `lateinit` assigned in
`onCreate`, so any pm3 call that *woke* the process rather than finding it already
running hit an unset field. It looked like a broken provider; it was an early one.

`causalStore` is `by lazy` now — synchronised by default, so the one-store-per-process
invariant holds under a race — and `onCreate` still touches it so a normal launch pays
the migration cost up front exactly as before.

Verified on the S25: force-stop the package, then `pm3 state-get` answers from cold. It
failed the same way every time before the change.

This would have made operator tooling look unreliable for reasons nobody could reproduce
while the app happened to be open.

## What is NOT proven

The landscape animation was not filmed mid-gesture. Ben picked the phone up during
verification and was still using it, and driving his UI further would have been rude for
a screenshot. The geometry is proven by `SheetEntryPolicy`'s tests, which fail on the old
behaviour; what remains unwitnessed is the animation itself on his screen.

Everything else above was checked on the device or by a command quoted with it.

---

# Correction (same night): the detent was never running

Ben, after installing: *"rotation is still super sensitive, and ui has exact same
issues, what even changed???"*

He was right, and the tests were not lying — they were measuring the wrong thing.

`RotationDetent`'s arithmetic was correct and had 9 passing tests. But the gravity
sensor that feeds it was registered only when a **scope-rotation or UI-placement lock**
was on:

```kotlin
val needed = scopeRotationLockState || uiPlacementLockState   // ← the bug
```

and `routeOrientation` began with a matching early return. With no lock the Activity was
`SCREEN_ORIENTATION_UNSPECIFIED`, which hands rotation wholesale to Android's own very
twitchy logic.

**No lock is the default.** So on his phone none of the new code ran, and the build
behaved exactly like the one before it. The same gating hid the landscape work, since
the sheet change was correct for the unlocked case but unobservable while rotation
itself was misbehaving.

## What this cost, and what it teaches

Unit tests proved the arithmetic. **They could not prove the arithmetic was reachable.**
I verified `RotationDetent` in isolation, watched 9 tests pass, and reported the feature
as done without ever checking that the code path executed in the configuration the user
actually runs.

The right check existed and I skipped it: *is this reachable from the default state?*

## The fix

- the gravity sensor always runs
- `routeOrientation` handles the no-lock case instead of returning early
- the Activity is pinned to the orientation the detent commits to
- the **system** auto-rotate setting is read first, so a user who told their phone not to
  rotate is honoured rather than overridden — that would be a worse bug than the original

## Proof it is live this time

Not a passing test. The Activity's own requested orientation, read off the phone:

```
topResumedActivity  dev.phosphor.mobil3.fortress/.MainActivity
requestedOrientation=SCREEN_ORIENTATION_PORTRAIT
```

Before this change Fortress requested `UNSPECIFIED` — it never asked for an orientation
at all, because it had delegated the decision. Requesting `PORTRAIT` is only possible if
the detent decided it. Stable across 8 seconds, zero FATAL/ANR.

`RotationDetentReachabilityTest` now fails if the sensor is gated behind a lock, or if
`routeOrientation` early-returns without one — which is exactly the code I shipped an
hour earlier.
