# R06 brightness and archive compatibility: ASUS checkpoint

2026-09-09. Parent Prime integration; Astra medium implementation; Grok4.6 high code review. Design is not scored by Grok.

## Changes and tests

Persisted default-off full-app brightness pin; event-driven focus/lifecycle/PiP/HUD restoration; source wake remains independent. Schema/2 portable Boolean, inert runtime state. Added deterministic Android-compatible checksum quoting after actual SAF found host/Android JSONObject.quote divergence. Existing Android archive hashes preserved, strict verification unchanged, no fallback.

- Review attempts:1 build-blocked/unscored;2=8/10;3=8/10 after checksum fix. All reports retained unchanged in critiques/brightness-grok-attempt-*.
- Final Android gate:921 tests across77 suites, no failures/errors/skips; lint, debug app+androidTest APKs, checkEngine passed.86 focused tests passed. Source-r3.sha25616 entries match after build. Source boundary12 checks passed.
- Installed/readback app SHA256 `ba47d3118f0182cdfcc2c1fef0d580338d51950195881a0a3509e5dd58aff0a4`; signer `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.
- Companion retained SHA256 `b0cc12eb14551530cfe0f3e3aabfbdf5238d01bbf5515a48a94f5b5c1b27553b`; built, not instrumentation-run.

## ASUS observations

Dedicated non-root ASUS_AI2202 serial NAAIB70036673ZC. Actual UI toggle set the foreground WindowManager brightness override to1.0 and added screen-bright wake bits with no source. Display pause retained it. Home/focus loss, pinned PiP and actual FloatingHudService presentation released it (PowerManager reportsNaN for no override); full-app return restored1.0. Toggle-off removed it. Process stop/recreation released/restored the persisted choice. These lifecycle checks were on prior retained R06 APK2518e34; R06 runtime code hashes are unchanged in final APK.

Final APK real SAF imports from repaired native exports applied on->1.0 and off->NaN. Earlier full Android export imported successfully before fix and decoded natively after fix with unchanged checksum. A checksum-only diagnostic matched the hypothesized slash spelling before production repair. Original failures and vectors remain evidence, not erased.

Global settings were never written: auto mode1, timeout600000, plugged wake7 and screensaver0 remained. Brightness numeric value was60 during active pin checks and58 at final cleanup while auto mode stayed1; not claimed byte-identical, not forcibly restored. Physical luminance/thermal limits are not measured. Existing plugged-in keep-awake masks an unattended sleep test; wake-bit observations are not a30-minute unplugged awake claim.

Final cleanup: both original preference XML files restored byte-for-byte; pin disabled by restoring baseline; app stopped/no services; overlay app-op restored to default; all owned SAF files and temporary UI/restore files removed. MUSIC remained1/30. No S25 operation or PC audio playback.

## Evidence, limits, continuation

Exact commands/logs/source freezes, power dumps, screenshots, native fixtures and retained APKs: `dev/scratch/brightness-pin-20260909/`. Exact prior preferences/rollback APK remain under `dev/scratch/root-stub-20260909/asus-baseline/`. Visible build backups: `/media/ben/Mass storage/agenticTinkering/claude/phosphor-mobile/brightness-pin-20260909/`, hashes verified.

R06 is implemented with bounded source/device evidence. Broader large-font/design/release/soak gates remain on the full baseline checklist; no physical HDR or total expansion acceptance. The manual's historical device-pending availability wording needs the next documentation/UI refresh. Next unit: tactile vector no-track console from independent Muse/DeepSeek brief with source corrections. Mic/mixer,HDR,startup and final regression remain required. Jcode candidate has38 passing BashTool tests and built; independent review pending; maintainer feedback queued only.
