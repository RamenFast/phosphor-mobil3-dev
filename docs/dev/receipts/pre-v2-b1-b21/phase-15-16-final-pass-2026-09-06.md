# S25 final bounded test pass, 2026-09-06 18:53–19:28 UTC

This supplements the [earlier continuation](phase-15-16-continuation-2026-09-06.md). It records completed testing, not blanket B1–B21 acceptance. Delivered implementation stays **63fc7ef**, shared **297e88b**, installed APK SHA256 **f5afd99617e7a1abf21246c612590212adeed3a82b588c6235e8448a68b48834**. No additional product patch, push, release or desktop service change occurred. Drift remains 21.

## Capture transport and B8 disposition

A real Spotify next-track action produced 80 high-cadence platform-session snapshots. Sample 3 observed **both Spotify and Phosphor BUFFERING**, followed by both PLAYING in sample 4. Metadata changed to the real next item. The visible transport showed Pause through the captured transition and settled playback, then actual Pause produced Play with matching PAUSED states and position 68526ms in both sessions.

Evidence: `b8-next-transition-sessions.txt`, `b8-next-transition.png`, `b8-next-stable`, `b8-next-paused` in the private session scratch directory. The screenshot is not synchronized tightly enough to prove the glyph at the exact buffering sample. Buffering propagation is physically observed; its glyph decision is also covered by the existing host policy tests. A brief new-title/old-duration transition is visible before the normal settled metadata update.

**Ben's reported inversion was not reproduced and is not declared fixed.** No speculative glyph change was made. Later Spotify had no platform session at all, and capture correctly showed generic `everything playing` rather than stale transport. Its session was already absent before the later linger test. This does not establish why Spotify retired its session.

## Newly exercised controls and settings

- **B18:** supported shell taps with a 90ms sleep and observed approximately 140ms separation toggled actual Spotify capture play/pause. With always-visible controls OFF, disabling double-tap preserved PAUSED at 83990ms. Reenabling it produced matching source/mirror PLAYING, then PAUSED at 85381ms. See `doubletap-shell-*`, `b18-disabled-*`, `b18-reenabled-*`. Exact single-tap latency was not measured.
- **B14:** actual taps 40 physical pixels above the gain line and below the focus line changed gain 1.94→4.39 and focus 0.30→2.13. A tap 40 pixels below the range line changed the beam range 6–20→8–20. These are interior samples of the 44dp lane, not exhaustive every-edge coverage. Screenshots and UI trees: `lanes-settings-before`, `lane-gain-upper`, `lane-focus-lower`, `lane-range-lower`.
- **B17:** Android's real export picker wrote a 42-key archive. After visible gain/focus mutation, the real import picker restored all **42 exported keys**, individually compared against actual Android preferences. The console returned to Xy45, gain 1.83 with auto indication. A deliberately invalid checksum was then rejected with an actionable message, and preferences stayed **byte-identical** to the successful imported state. Evidence: `settings-roundtrip.phossettings`, `imported-prefs.xml`, `import-restored-console`, `reject-result.xml`, `rejected-import-prefs.xml`.
- Export provider detail: Android appended `.json` to the requested `.phossettings` filename because the current CreateDocument MIME is application/json. Both export and import worked. This is a filename-contract follow-up, not data loss and not a delivered change.

## B21: all four source ownership paths

| Source | Linger ON, actual recents removal | Reopen and linger OFF | Evidence |
|---|---|---|---|
| Existing relay | PlaybackService survived. Reopen showed connected/no sound and continuing approximately 1.6 Mb/s traffic. No remote file or PC sound was started. | Disable linger, remove the new task: PlaybackService gone. Reopen no source. | `linger-relay-*`, `relay-off-*` |
| Microphone | Despite linger, the Activity-owned recorder stopped. The app's active recording row disappeared from AudioFlinger. | Reopen no source. Earlier OFF case already passed. | `mic-linger-record-before/after.txt`, `mic-linger-removed/return` |
| Capture | CaptureService and PlaybackService survived, with the app's projection retained. | Reopen capture without new consent. Disable linger and remove: both services gone and projection null. | `capture-linger-*`, `capture-off-removed-final` |
| Local file | Playback remained PLAYING around 30120ms after removal. Reopen showed the same fixture advancing around 32 seconds. | Disable linger and remove before the 90-second fixture ended: PlaybackService gone. | `local-linger-*` |

Every removal used a fresh Samsung recents tree and the identified Phosphor card at the right edge. No central Spotify card or Close all was used. The capture projection dump printed the retained projection and then hit a Samsung system dump NullPointerException. That dump failure is preserved and is not an app crash or a fully clean diagnostic dump. No delayed-callback race timing or new full liblog window is claimed.

## B5: measured trace, not just settings labels

A deterministic 90-second stereo 440Hz quadrature circle supplied fixed geometry. Phone output was muted for this measurement. Device brightness was pinned at 30 with automatic mode off in an EXIT-trapped scope. Gain settled at 6 and the actual settings screen retained nondefault BEAM approximately 16 and focus 0.30.

Six screenshots each were taken before opening settings, after full modal dismissal, and after PiP→fullscreen surface reconfiguration. The ROI was 1000×1400 at (40,300), excluding console/HUD. Red-channel pixels above 50 percent exclude the dim grid and count the bright deposited trace. Mean bright fractions were **0.007484165**, **0.007018215** and **0.007330358**. Post-dismiss and post-reconfiguration samples all fell within the baseline's observed range 0.006785–0.007875. Mean differences were −6.23 percent and −2.06 percent, so this is measured overlapping variation, not a claim of identical pixels. Evidence: `b5-{base,dismiss,reconfigure}-[1-6].png`, `b5-trace-stability.json`, `b5-settings-energy.xml`.

This checks modal and surface-size lifecycle stability. It does not prove a complete Surface destruction/recreation sequence, calibrated panel luminance, or Ben's visual acceptance.

## Exact remaining limits

- B8 reported inversion remains unconfirmed. Do not claim a fix based only on these successful observed transitions.
- A finite Java multi-pointer driver built through the Gradle wrapper, but shell `app_process` was killed with exit 137 before any event acknowledgment, including a read-only dex retry. Root cause was not diagnosed. B1/B19 exact physical two-pointer/333ms tests remain unaccepted. Existing host boundary tests remain host evidence only.
- No emulator was running. The existing Phase12 receipt already documents five native-renderer failures with the translated x86_64 emulator and partial 17-key default observations. This pass did not repeat those failed renderer setups or claim clean-install visual acceptance.
- Fresh-Linux B3 remains outside this bounded phone/existing-relay pass. Full surface recreation, all LEG/default/export-value combinations, exhaustive mode/rotation and every source/item auto-gain-reset combination are not silently marked passed.
- The remaining release and visual-acceptance gates stay in the issue ledger and handoff. This is the end of a bounded test pass, not release signoff.

## Final restoration

Every additional phone fixture/archive and the finite input-driver jar was removed only after device and retained-host hashes matched. Original tuning XML and saved hosts were compared byte-for-byte after restoration. The observed enabled grid remains retained as before. Final readback: brightness mode 1, timeout 1800000, original PiP app-op deny, MUSIC 2/15. The luminance test used MUSIC 0/15, restored only after stopping playback. Final UI: no source, Xy45, gain 1.83 auto, no volume row. Then Home. No live microphone, projection or test playback was left running. Host receipts and test fixtures remain private under ignored `dev/scratch/phone-acceptance-20260906T1720Z/`.
