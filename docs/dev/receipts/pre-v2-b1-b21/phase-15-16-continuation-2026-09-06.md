# S25 continuation, 2026-09-06 18:17–18:49 UTC

Status: partial acceptance, not release-ready. This supplements `phase-15-16-s25-2026-09-06.md`. It does not close all B cards or reduce drift 21.

## Exact delivered build

- Mobile implementation: `63fc7ef`, following `6565585`. Shared renderer remains `297e88b`.
- Debug package `dev.phosphor.mobil3.debug`, 2.0.0-debug, version code 2000000.
- Retained APK: ignored `dev/scratch/phone-acceptance-20260906T1720Z/replay-committed.apk`.
- Local and installed SHA256: `f5afd99617e7a1abf21246c612590212adeed3a82b588c6235e8448a68b48834`.
- Signer SHA256: `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.
- `dev/pm3 install` read-back proof: `replay-committed-install.json` and later `pip-control-reinstall.json`.
- No release, push, tag, signing change, desktop service action or PC audio occurred.

## Fourth installed correction: final-item replay

The prior APK reproduced the failure with the owned 15-second right-only fixture. The duration watcher paused the final item before native drain, leaving output READY. Play resumed the remaining tail and the watcher paused it again. `63fc7ef` retains early successor selection but lets the final item drain and report its native terminal state. The existing ended replay path then seeks to zero.

Evidence: `replay-old-ended`, `replay-old-retry`, `replay-new-ended`, `replay-new-retry` screenshots, UI trees and media-session dumps. Corrected end reported STOPPED at 15000ms. Actual Play then showed the pause glyph, position near the beginning, and a moving sample count. This is a narrow demonstrated fix, not complete B9 acceptance. The precommit candidate hash was `d78b5c7b8d6b4d4a926892b82c456d0c2087981b8e9323006b4ae419ea1f5aa9`; the committed build was subsequently installed and used for live playback and PiP tests.

419 JVM cases, lint, engine check and assembly passed (`replay-build.txt`). A source regression assertion prevents restoring the premature final-item pause. No new shared renderer code changed in this continuation.

## Additional physical coverage

| Requirement | New observation | Evidence and limits |
|---|---|---|
| B8 capture glyph | Relaunching Spotify restored its external session. Settled real Phosphor Play/Pause taps matched Spotify and mirrored platform states. Three timed transitions were PAUSED, PLAYING, PAUSED. | `resume-play-settled`, `resume-pause-settled`, `resume-timed-[1-3]`. No inversion reproduced. Buffering remains untested. Immediate taps during hiding did not activate the button and are excluded. No glyph patch. |
| B9 paused seeking | Three paused scrubs published 3456, 13104 and 6000ms while staying PAUSED. A separate seek reached 9744ms. | `paused-seek-330/820/460`, `replay-paused-seek`. Prior intermittent zero-position observation remains open rather than inferred fixed. |
| B15 right meter | Actual right-only playback showed L 0.000 / negative infinity and R 0.100 / -20.0 dBFS. | `right-meter-final`. Complements earlier actual left and stereo readings. |
| B16 mode grid | Clear, explicitly dismissed-sheet captures now exist for XY, swirl, dots and Xy45 with mode text verified. | `grid-{xy,swirl,dots,xy45}-clear-resume`. Full device CPU/GPU switching and every mode remain untested. |
| B10 style | FEEL changed preview to Carved, MOTION to Cut, CORNERS to 8dp and LABELS to part-nos while open. Reopening preserved all four. Actual screenshot showed rounded preview and part-number labels. | `style-before`, `style-motion`, `style-corners`, `style-labels`, `style-all-changed`, `style-reopened`. Motion animation timing itself was not measured. Original style restored. |
| B12 PiP controls | Quick AUTO PiP off was reflected in full settings. Manual entry worked when off. Home with off did not pin. Full setting on enabled both manual and Home entry. | `pip-full-off/on`, `pip-manual-off/on-activities.txt`, `pip-auto-off/on-activities.txt`. Android app-op was temporarily allowed and restored to its original deny. |
| B11 live wake | With timeout temporarily 30000ms, a 90-second local fixture kept main and actual pinned PiP awake across separate 35-second untouched intervals. | `wake-main-power/window`, `wake-pip-power/activities`. Both power snapshots Awake. Original timeout 1800000 restored by EXIT trap. Stopped state has no app hold-screen window. This proves accelerated local wake, not a 15-minute soak or every source. |
| B21 local removal | Fresh recents tree identified only Phosphor at right edge. Removing that card released PlaybackService, leaving only the normal notification-listener service. | `removal-recents`, `local-recents-removed-resume`. This supplements earlier capture and mic removal. Relay and linger variants remain open. |

## Rejected PiP hypothesis and test limitations

After PiP return, late UI dumps and screenshots showed no chrome while Android reported fullscreen. An experimental resume/focus reconciliation candidate was built and installed, but a matched control using the unchanged `63fc7ef` also recovered controls when tap and snapshot timing were explicit: wait for the four-second auto-hide interval, tap, then allow 0.6 seconds for the tap/animation before inspecting. Both no-source and sustained live PiP control runs passed (`pip-control-timed`, `pip-control-live-timed`). The candidate was discarded completely and the verified committed APK reinstalled. No PiP repair is claimed. The initial duplicate focus-method compile failure is retained in `pip-recovery-build.txt`; the second experimental build passed. Those experiments are not delivered changes.

The scratch `source_sheet` helper remains timing-sensitive with auto-hide and double-tap enabled. A missing SRC target is not sufficient evidence of a broken app. Covered sheets, nonclickable text targets and immediate pre-animation taps likewise do not prove acceptance failures. A final double-tap experiment did not produce a reliable paired transition and is not counted as B18 success. Exact multi-pointer injection and 333ms timing remain unperformed.

No new complete liblog diagnostic window was recorded during this continuation. Earlier crash-free windows must not be extrapolated over these later actions.

## Restoration and next work

The two additional fixture files were removed only after matching their device hashes to retained host originals. All test style, gain and display preferences were restored to the existing `restore-tuning.tar`, which preserves original tuning except the previously observed enabled grid. Exact XML comparison passed. Saved hosts still match the original archive byte-for-byte. Restored app-op deny and timeout 1800000 were re-read. Phone MUSIC remained 2/15, approximately 13.3 percent. Final UI was no-source, original Xy45 and gain 1.83, with no volume row; then Home. No testing volume above the ceiling was used.

Continue from the original phase 15–17 matrix. Priority gaps: B8 reported inversion and buffering, precise B1/B19 pointer timing, full B5 luminance recreation, all B14 interaction lanes, clean-emulator/import B17, reliable enabled/disabled B18 timing, B20 full item/source reset matrix, relay/linger B21, and fresh-Linux B3. Do not call this comprehensive acceptance complete or claim Ben's visual approval.
