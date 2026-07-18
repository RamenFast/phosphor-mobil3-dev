# phosphor-mobil3 — agent interface

## The conforming surface is `dev/pm3`, not the APK

Ruling (2026-07-18): an Android APK has no argv and no stdout — the app itself **cannot**
implement the agent-first CLI standard, and pretending otherwise would be registry rot.
Precedent: blossom-chat's GUI is non-conformant and its bridge is the declared surface.

`dev/pm3` (bash) is the project's declared, conforming agent surface: envelope
`{status,tool,version,ts}` on one-shots, errors carry `fix`, exits 0/2/3/4, `pm3 schema`
self-describes, isatty auto-switch, `logcat` is the one declared raw stream.

Debug builds additionally carry an on-device receipts hatch (SELFTEST broadcast, lands
with M1): `adb shell am broadcast -a dev.phosphor.mobil3.SELFTEST` → offscreen render →
`selftest.json` + `selftest.png` pulled by `pm3 smoke`. Release builds honestly don't
have it (`pm3 smoke` exits 2 on a release install).

## Laws inherited from the workspace

- Engine source of truth is the sibling `../phosphor` checkout (path deps). Never fork
  engine code into this repo; upstream changes go through the phosphor repo.
- Receipts: every milestone's "done" is a `pm3` command output pasted into the commit.
- One branch besides master at a time; push to master only on Ben's word.
- No authored Python anywhere, build tooling included.
