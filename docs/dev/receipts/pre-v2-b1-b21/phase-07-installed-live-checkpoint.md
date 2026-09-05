# Exact Phase 7 installation and partial live regression

Observed 2026-09-05, 22:58 through 23:16 UTC. Status: **VERIFY**, not final B1-B21 acceptance.

Ben authorized the connected phone at 22:36 UTC. The root used one explicit USB device and preserved unrelated phone content. The installed package is `dev.phosphor.mobil3.debug`, version `2.0.0-debug`, code `2000000`, on the Galaxy S25 with Android 16/API 36.

## Exact artifact and upgrade

- Mobile behavior commit: `6b24eade7490bf219a1004f0d7c605db31df54e4`.
- Shared engine commit: `4dc0f2c3ec27c560b497b887f2a8e9c9031a967f`.
- APK SHA-256: `d1c3649b358b44c9e43f50e21bd1b8d5cc2a00ea66cda95e8607c5a27c33fd10`.
- Signing certificate SHA-256: `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.

Committed-input gate 4 and the separate clean detached artifact build passed. The artifact build ran 246 Android tests with zero failures, errors or skips, plus lint, JNI, engine and artifact boundary checks. Six retained artifact checksums were independently verified before installation.

Public `dev/pm3 install` installed that exact APK, pulled the installed base APK, and verified equal hash and signer. The previous installed Phase 4 APK and app preferences were backed up first. Preference XML checksums before upgrade and immediately after installation, before launch, were byte-identical. This proves preservation for this update, not every future version or archive/default requirement.

## Observed user workflows

| Requirement | Actual observation | Limit |
|---|---|---|
| B2 capture to built-in mic | Accepted normal Android capture consent, then selected the actual built-in mic row. Projection became null. AudioFlinger replaced the capture record with one active, unsilenced MIC source. Frames advanced from 110400 to 1516800. Screenshot shows a lit amber beam. | One current-candidate handoff supplements the earlier five-cycle Phase 4 receipt. |
| B6 local metadata | A tagged MP3 opened through Android's direct file picker. The app displayed `Over the Horizon` and `Samsung`, matching file tags and its actual MediaSession metadata. | Physical audible output was not asserted in this quiet run. |
| B4 direct/folder agreement | The identical MP3 then opened through the real folder picker and explicit folder grant. Both paths produced lit scope traces, the same title/artist and a playing session. | This small repeat complements, not replaces, Phase 3 recursive/invalid/large-provider acceptance. |
| B9 local pause and seek recovery | Actual pause held position 57192 ms across two snapshots. A real slider drag reopened the decoder at 167.629 seconds. Resume progressed from 167688 to 170688 ms. A backward drag reopened at 29.169 seconds. The source sheet and folder picker remained responsive without restart. | Immediate paused MediaSession snapshots retained the prior position. Paused seek publication still needs investigation before claiming complete timeline truth. |
| B21 microphone removal | Swiped only Phosphor from actual Android Recents. The mic record was removed, projection was null, and PlaybackService/CaptureService were absent. | PlaybackService existed before removal. This does not prove the mic-only/no-service case. |
| B21 local removal | Swiped the playing folder session's Phosphor task away. PlaybackService and its MediaSession disappeared. | The system-bound notification listener and cached process remain. This is source cleanup, not a promise that Android kills the process. Linger-on/capture/relay cases remain open. |
| Recovery | Reopening after mic task removal showed no owned source and accepted the normal local picker workflow. | No forced crash or reader timeout was manufactured. |
| B3 reachability | Two ICMP packets in each direction between this phone and this PC succeeded over Tailscale. | This is basic bidirectional reachability, not relay playback or Linux deployment acceptance. |

Capture UI also surfaced the paused external session's title, artist, duration and play glyph. External play/pause/seek commands were not exercised in this window, so B7/B8 remain open.

## Complete local-workflow logging

The phone's existing `/system/bin/logcat` does not currently expose the Android logging CLI. No system executable, privilege or module was changed. The root reused the previously reviewed, task-owned log reader instead.

Its first self-test failed because its relative temporary file was attempted from an unwritable working directory. Running the identical binary from its private writable directory passed all 275 checks. The initial incorrect `--self-test` invocation was a usage error. Neither failed attempt produced acceptance evidence.

The corrected all-PID main/system/crash reader completed its 420-second deadline with 58579 records. Start/end markers bracket the local picker, metadata, pause, forward/backward seek, folder playback and local task removal. Raw replay produced all 58579 records and matched the entire captured text byte-for-byte. Fatal exception, ANR, native panic and log-drop/chatty candidate scans found zero matches in this window. This does not retroactively cover the earlier mic handoff or prove that Android can never drop a log silently.

## Evidence and remaining work

Private evidence is under `dev/scratch/pre-v2-20260829T072841Z/phone-live-20260905/`. Exact artifact evidence is under `phase-07/candidate-6b24eade7490-isolated/` in the same ignored run tree. Raw screenshots, serials, addresses, device dumps and user metadata remain private.

Paused seek timeline publication, complete capture transport, capture/relay/linger lifecycle, canonical Linux relay activation, later gesture/UI/render/settings phases and combined regression remain open. The task-owned media fixture and diagnostic directory remain available for continuation and must be removed at final restoration. No desktop sound, volume change, display setting change, release signing or publication occurred.
