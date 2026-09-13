# Unit 1 execution checkpoint · 2026-09-13

Ben approved continued Unit 1 work and Git/GitHub handling in Prime session
`01a09987-2c87-776d-aac8-f9a9d8765b01`. Stop at his feedback test card, not the whole roadmap.

## Preserved baseline

- Mobile source before implementation: `bf851082d97f3fbef00229b9ecec8d8688843e31`.
- Sibling engine: `3171e0f3bb99bfcb881f17cc034e8794c9e249e4`, clean at intake.
- Recovery documents committed and pushed to the existing private `RamenFast/phosphor-mobil3-dev` master.
- ASUS serial `NAAIB70036673ZC`, model ASUS_AI2202, Android 14.
- Installed debug APK SHA256: `2bb7b9830f5bd1dbd10256e4dffc6faa9a3ebebff3784e8d2419ac2335e1882b`.
- Signer SHA256: `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.
- `RECORD_AUDIO` was already granted. No permission was revoked or granted during intake.
- MUSIC speaker volume was 1/30. No volume or system setting was changed.
- Private baseline APK, readable app-state tar, device settings and protected hashes are in
  `dev/scratch/recovery-unit1-20260913/`.
- Hash-matched APK/app-state copies are on the mounted Mass storage drive under
  `agenticTinkering/claude/phosphor-mobile-recovery/unit1-20260913/`.

## Source findings

The old AUTO controller caps at 6x and freezes below raw peak 0.02.
Its 0.999 peak release and 0.05 gain glide depend on render-frame count.
Both 2D stage zoom gestures currently call manual gain and disable AUTO.
Reports are retained in ignored `build/recovery-unit1-baseline/audio.md` and `gestures.md`.
The active implementation contract is `spec/UNIT1-AUTO-FRAMING.md`.

## Bounded ASUS baseline

The real Sources action started selected built-in microphone 19 with the existing grant.
The app displayed active built-in input, device 48000 Hz, one channel, encoding 2.
Numeric route/status UI and service dump were retained. These are not physical stereo or fresh-consent proof.
A screenshot captured during sheet dismissal is not trace-geometry acceptance.
The bounded baseline session ended by stopping this debug package. No PCM was saved.
Current temporary source/runtime state will be restored after final testing without reverting the new app.

## Current ownership and next action

Root owns Git, all builds, ASUS operations and these receipts.
`unit1-implementation` is the sole source writer for app/, rust/ and the exact policy section of the unit spec.
`unit1-device-proof` is read-only and prepares reuse of existing physical-input/test helpers.
Neither worker owns devices, builds, Git or the sibling engine. No S25 operation is authorized.

After source release: root inspection, project tests, independent review, exact build/install,
ASUS acceptance and a three-action Ben test card. No application acceptance is claimed yet.

## Stereo correction added during implementation

Ben explicitly added stereo-first defaults. The canonical decision is `decisions/2026-09-13-stereo-first-input.md`. Root must integrate this after the sole writer releases. The current candidate loop actually tries mono first. ASUS advertises two-channel masks, but the baseline recording was mono. Independent stereo source review is read-only. Do not claim stereo from duplicate channels or advertised masks.

## Source integration verification

Initial native compile caught two old AutoGain test calls in deck_events.rs. Initial Android compile caught the root debug trace using ui.mode instead of ui.modeIndex. Both were corrected without weakening assertions. Run2 is in progress. Source review is independent on current dedicated Astra. Stereo-first negotiation and separate client/device/paired-window diagnostics are now integrated. No candidate APK installed yet.

## Independent review corrections

Review found lost rise time on empty render drains, unchecked delayed preference writes, and same-owner format changes mixing diagnostic windows. Root added bounded pending time with rebases, a checked completed-gesture/reset save with pending/error/retry, and format-local microphone windows rejecting old read tokens. Tests now cover actual producer/render cadence, controller-to-DSP extent, short/changing noise, and checksum-valid import failures. Correction review and fresh native/Android runs are in progress. Old review findings remain in ignored review.md and will be summarized with final outcomes.

## Verified checks before final APK

132 locked mobile native tests pass. 43 locked relay tests pass. Android run3 passed979tests with zero failures/errors/skips and debug lint. All pm3 fixtures,107manifest-policy checks, real APK/AAB boundary fixtures and release-rejection fixtures pass. A final persistence correction adds process-scoped failed-save ownership and cache/disk regression; Android run4 and its focused review are pending. No candidate installed. The original settings archive was exported through the real UI and retained as before.phossettings; Android added a .json filename suffix on the phone.

## Source gate cleared

Final Android run4:980tests, zero failures/errors/skips; debug lint passed. Native run4:132tests passed. Relay:43passed. Independent [source](reviews/unit1-source-review.md), [correction](reviews/unit1-corrections-review.md), and [final persistence](reviews/unit1-persistence-review.md) reviews are retained. Final R2 is source-clear. A pure cache/disk model and wiring tests do not replace actual ASUS restart proof. Root now seals source and builds the exact debug artifact. Phone acceptance and Ben feedback remain pending.

## First exact ASUS candidate

Installed source583f4af APK SHA256d312c99130a829bafa3bcc423e514967d0eef2645d67c153cd9517754319b05e through pm3. Installed readback and signer matched. Actual built-in19 opened device48000Hz stereo PCM16. Live mic trace showed about45x at119fps. Signal Check exposed a pre-existing startup-only cached MicCaptureService getter: zero reads and old route timestamp while the trace moved. Root is correcting only this read path to fetch the current recorder snapshot while preserving terminal/retirement evidence. This candidate is not final acceptance.

## Physical framing margin correction

On exact23541ca, raw0.005 stereo fixture grew from14x14px to1054x1054px in XY45 at about160x. This proved enlargement but left insufficient rotation margin. Root added1/sqrt2 AUTO headroom for actual XY45/swirl only. The independent runtime review caught update-before-animated-mode-landing order. The single AUTO update now follows actual mode landing and precedes grid/DSP. A first-switched-frame regression checks ordering, margin and upward return glide. Native tests rerun before the final APK. Manual gain, PCM and shared engine remain unchanged.
