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
