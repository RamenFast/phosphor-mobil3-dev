# ASUS Activity sensor retirement

2026-09-07. Ben confirmed the ASUS is a dedicated test phone, authorized autonomous takeover,
and restarted it. Boot completed, launcher was unlocked, MUSIC remained1/30, stay-awake7,
screensaver0 and unplugged timeout600000. S25 was not accessed.

## Reproduced failure

Exact source18569be was installed with matching hash and signer. Same-process Activity replacements
used explicit NEW_TASK|CLEAR_TASK launches. The original implementation retained one, two, then three
active accelerometer connections. A second replay waited for UI settling after each launch and
confirmed those counts with unchanged PID21987. This is persistent retention, not only pending teardown.

## Correction

Activity destruction marks the owner retired, clears its listener reference, then unregisters that listener.
Rotation mutations and registration reject stale task/Activity owners. A queued sensor callback also
checks exact listener identity before changing filtered gravity or routing native orientation.
No filter, detent, lock choice or UI design changed.

## Checks

- Full Android gate passed:440 tests, lint, debug assembly and checkEngine.
- Two new source-only tests bind teardown order and callback/registration guards. They do not execute Android sensors.
- Candidate APK SHA256:010a75f0e4510aa17511c87466dbbec94e987471a1cf80a8ab9e10cf75608cfe.
- Installed bytes and debug signer were verified by dev/pm3.
- Three same-process replacements retained exactly one settled active listener with PID22262.
- A previous fixed replay also passed three replacements with PID20968.
- Current app focus and no-source console remained usable after replacement.

The first fixed check sampled immediately after am start -W and saw two listeners before old-Activity
destruction completed. Its surrounding tee pipeline hid the failure status. No success was claimed
from that pipeline. The check now uses pipefail and a bounded20-attempt settling check, at100ms intervals.
The old implementation was replayed after this discovery and still retained three listeners after UI settling.

Private evidence is in dev/scratch/asus-timeline-20260907T0113Z/: sensor-old-settled-*.txt,
sensor-fixed-*.txt, check-sensor-fixed.sh, install JSONs and sensor-retirement-build.log.
The physical replay proves connection cleanup and current-owner survival, not every gravity pose or
visible stale-callback beam artifact. Broader rotation/import and source acceptance remain separate.
