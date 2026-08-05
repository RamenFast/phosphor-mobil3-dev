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
- [ ] Build and install the current debug APK on the Galaxy S25.
- [ ] Complete the on-device capture, denial, lifecycle, rotation, multi-window, and relay matrix.
- [ ] Reconcile local branch ancestry without pushing protected branches.
- [ ] Provision the approved direct-APK and Play-upload signers and build the final APK/AAB.
- [ ] Verify the production signer, manifest, archive boundary, and 16 KiB native alignment. The same flow passes with an ephemeral fixture signer.
- [ ] Install the exact packaged production APK on the phone with `dev/pm3 --profile release --serial <serial> install <APK>`.
- [ ] Preserve wanted Fortress settings and obtain explicit approval before uninstalling `dev.phosphor.mobil3.fortress`.

## Deferred networking work

- [ ] Bind the PC relay to an explicit Tailscale interface or address.
- [ ] Add protocol-level peer authentication only if Tailscale identity is no longer the boundary.
- [ ] Polish discovery, connection recovery, latency, and long-session behavior.
- [ ] Re-run remote artwork, output switching, file playback, and link-state receipts.

## Deferred product work

The next two major core features are intentionally absent from this backlog until Ben provides the feature brief. Historical plans are not active todos.
