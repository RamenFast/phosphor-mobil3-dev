# Serious todos

## Primetime cleanup

- [x] Preserve the dirty starting material and create a rollback tag.
- [x] Reset active vision, specification, and decision authority.
- [x] Remove the dormant runtime administration and audit graph.
- [x] Preserve the user-facing HUD value, then scrub obsolete private state.
- [x] Collapse Android packaging to one debug/release product.
- [x] Make the Gradle wrapper the sole Android build authority.
- [x] Set the compatibility floor to Android 10, API 29.
- [x] Request microphone permission before projection when playback capture needs it.
- [x] Request full-display projection by default on Android 14 and newer.
- [x] Limit saved PC relay hosts to Tailscale endpoints.
- [x] Remove process-wide Wi-Fi/mobile routing that could bypass Tailscale.
- [x] Add and expose a privacy policy.
- [x] Make dirty, untagged, mismatched, and unknown release provenance fail closed.
- [x] Add canonical APK/AAB, bundletool, signer, 16 KiB, source, manifest, and checksum gates.
- [x] Make device installation verify exact APK bytes and signer.
- [x] Finish comment cleanup and active-document reconciliation.
- [x] Run all Android, Rust, relay, CLI, boundary, and shell gates.
- [x] Build and install the exact current implementation APK on the Galaxy S25 and verify installed bytes and signer.
- [x] Complete the S25 capture, denial, process-death, rotation, true multi-window, relay, disconnect, and dormant-network matrix.
- [x] Reconcile local branch ancestry without pushing protected branches.
- [ ] Exercise the true screen-lock callback after explicit approval to manipulate the PIN-protected keyguard state.
- [ ] Provision the approved direct-APK and Play-upload signers and build the final APK/AAB.
- [ ] Verify the production signer, manifest, archive boundary, and 16 KiB native alignment. The same flow passes with an ephemeral fixture signer.
- [ ] Install the exact packaged production APK on the phone with `dev/pm3 --profile release --serial <serial> install <APK>`.
- [ ] Preserve wanted Fortress settings and obtain explicit approval before uninstalling `dev.phosphor.mobil3.fortress`.

## Pre-v2 B1-B21 lived repairs

- [ ] Close the B1-B21 lived-repair matrix before the v2 release path continues. Every card needs its active-spec contract, automated gate, honest live receipt, ledger update, and rollback point. Keep `spec-version: pre-v2-b1-b21` and `drift: 21` until all 21 receipts pass. Track the umbrella in [issue #7](https://github.com/RamenFast/phosphor-mobil3-dev/issues/7).

## Deferred networking work

- [ ] Bind the PC relay to an explicit Tailscale interface or address.
- [ ] Add protocol-level peer authentication only if Tailscale identity is no longer the boundary.
- [ ] Polish discovery, connection recovery, latency, and long-session behavior.
- [ ] Re-run remote artwork, output switching, file playback, and link-state receipts.

## Post-v2 structure debt

These items remain outside the B1-B21 behavior scope:

- [ ] Reconcile capture-reader lifecycle and raw reader-thread ownership beyond the narrow B2 microphone handoff.
- [ ] Split oversized Activity, playback/capture service, and RemoteFlow responsibilities only with a separate accepted plan.
- [ ] Reconcile render, session, and serve-client teardown ownership.
- [ ] Rework release scoreboard and envelope debt without weakening the exact release reds or CLI contract.
- [ ] Define the full native playback-event ownership policy after the narrow B6 metadata consumer lands.
- [ ] Remove duplicated default authority and wake ownership after behavior receipts prove the current contracts.

## Deferred product work

The next two major core features are intentionally absent from this backlog until Ben provides the feature brief. Historical plans are not active todos.

## 2026-09-05 device findings

- [ ] Phase 6 task 6.3: fix the reproduced `Missing implementation to handle COMMAND_RELEASE` service-shutdown crash. The B9 receipt contains its DropBox evidence.
- [ ] Phase 5: retain the notification-access recovery and stop publishing an inverted capture state when that permission is absent.
- [ ] Check the capture-to-local queue transition: the band can say `no source` while the local queue plays and the trace is lit.
- [ ] Check paused local seek position reporting: the mirror reads zero until playback resumes at the requested destination.
- [x] Resolve the S25 logcat evidence gap. A temporary read-only logd client captured the complete B9 rerun. Raw replay matched exactly, and the helper was removed. No system file or mount changed. See the Phase 2 receipt addendum.
- [ ] Investigate oversized-provider cancellation separately from the passed representative B4 path. A 20,001-file stress fixture caused an external-storage-provider ANR before app grant. Mic eventually won without stale local publication, but started about ten seconds after selection. The fixture was removed and normal picker recovery passed. Before another large stress run, assess cancellable provider queries and a bounded fixture. See the Phase 3 receipt.
