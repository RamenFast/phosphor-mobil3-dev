# Phase 8: gesture ownership and double-tap setting

Status: **VERIFY**. Source, offline gates and the exact retained debug package passed. Installation and real Compose/phone acceptance remain open.

## Implemented contract

The existing stage arbiter now checks all pressed pointers against actual console, sheet and overflow card bounds. Compose maps attached corners into the unrotated screen reference. Margins are 24dp. Opening, movement and dismissal block scope mutation through 333ms, with 334ms eligible only after other guards clear. Exiting cards retain their geometry and timing.

The physical 88dp bottom band latches scope rejection for the sequence, including overshoot and later pointers. The existing armed one-finger upward console pull remains available. A second pointer cancels that pull and retires the sequence. Blocked scope motion rebases before resuming, rather than applying accumulated deltas. Stable geometry does not restart settling on every recomposition.

The dedicated `double_tap_playback` setting defaults true and uses the existing typed save/load/archive/action chain. Disabled state supplies a literal null double-tap handler. The actual console, sheet and overflow owners remain separate from the stage.

## Exact source evidence

The implementation worker froze eleven files on mobile HEAD `37351ae52b9de835e207c5e14778955d3298f3b9`. Its owned manifest SHA-256 is `c5dcedf770699f95e9696a7576435d75a25b569a459102b68cff8e3c51e32987`.

Focused tests passed 37/37. The full wrapper gate passed 271 tests across 26 suites, with zero failures, errors or skips. Lint, `checkEngine` and `cargoBuildDebug` passed. All four complete 811-entry before/after manifests matched, SHA-256 `1ca89e944697f4e36d9e94bba98d00cf01b7502d6ce404d7a5357af0f531c68c`. Lint retained 15 existing warnings and zero errors, not a warning-free claim.

Independent frozen review found no concrete source defect. Its complete requirement map covers geometry producers, transformed coordinates, timing, mode-6 ownership, rebasing, null-handler behavior and the real settings action chain. The reviewer independently verified current hashes and terminal XML/log receipts. Existing Phase 7 RemoteFlow bytes were unchanged.

Production numeric policy tests and source-wiring assertions do not execute LayoutCoordinates, Compose animations, actual pointer consumption or Android navigation. B1, B18 and B19 therefore remain open pending the exact installed matrix.

## Separate live-discovered paused seek correction

The installed Phase 7 acceptance window exposed stale paused timeline publication despite successful native decoder seeks. A follow-up reproduced native offset 144.007 seconds while the paused MediaSession remained at 1320ms and the app later displayed 0:00.

The correction stays in the existing service/player path. Only a successful current native seek on the main-thread publication callback reports the actual native position. The local player emits a one-shot Media3 SEEK discontinuity and clears it on consumption, queue replacement and stop. The requested position is not published optimistically. Failed and superseded paths do not acknowledge completion.

Media3 also emits a command-time seek acknowledgement. This adds a completed-native-seek publication, not a promise of one total listener event per user action. Version-matched API/bytecode inspection and separate read-only review cover that distinction.

Root full gate `rootseek1` passed 272 tests with zero failures, errors or skips, plus lint, JNI and engine checks. Its full source inventory was unchanged. The additional regression is source-wiring coverage, not executed Android Media3 behavior. Exact installed paused-seek recovery is still required.

## Private evidence and next check

Evidence is retained under `dev/scratch/pre-v2-20260829T072841Z/phase-08/` and `phone-live-20260905/`. Historical attempts and the installed Phase 7 rollback remain intact. No worker installed, assembled, launched, operated the phone, changed shared engine code or created a new gesture recognizer.

Next: package the exact committed sources, verify installed readback and preserved preferences, then check actual paused-seek publication and transformed gesture/settings behavior. Later controls/render/settings phases and combined regression remain separate.

## Exact retained artifact, 2026-09-06 00:01 UTC verification

The sole clean-snapshot build completed at 2026-09-05 23:46:53 UTC with exit 0. Mobile source is `0d47f0d21037adebd3d55f89e9f97e51798f7cf8`. Consumed shared source is `4dc0f2c3ec27c560b497b887f2a8e9c9031a967f`. Original working trees were not used as moving build inputs.

The retained `phase-08-0d47f0d21037.apk` SHA-256 is `4c2e6fa312e328189e2a4deb3b5ef692b8cc74d7ade4c687c75fb7cdba3647b7`. The debug signing certificate SHA-256 is `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`, matching the prior installed debug lineage.

Root independently ran `sha256sum -c SHA256SUMS` in the retained candidate directory. All six entries passed: APK, build log, source identity, test summary, signer and artifact boundary receipt. The exact build passed 272 tests with zero failures, errors or skips. The boundary receipt passed 11 source and 5 artifact checks, with one documented trusted-runtime exemption.

| Retained evidence | SHA-256 |
|---|---|
| Build log | `d0887ee3b93533265c9c04aab6c8cee6fddd44ccfaf162df2dbd8b8ad719a98f` |
| Source identity | `1886e9199f7af33a943586bf1c5f2c539f8ab1afae0921b24f2ccf569f3bf227` |
| Test summary | `76627e22c872b4007e19da165f1958173a1f993198f3bdab2d356a28802b4edf` |
| Signer | `5cd4660fc96362205bbaa03078d98b001a8ed33a18e1e32a904cff46f9ddd49a` |
| Artifact boundary | `79bbfb7d57b957e1c74eea5d8db305a328f4f6c5fbcb6c8277d4cb7203ba9bcd` |

No Phase 8 upgrade occurred. The next USB preflight found that route absent. A bounded read-only wireless probe verified the same S25 identity and found another media app in the foreground. Root sent no input, playback or package command. A compound state check exited before the preference backup commands, so no fresh Phase 8 preference backup is claimed. Earlier verified backups remain intact.

The next live window still requires a fresh stable preference backup, exact `dev/pm3` upgrade and installed readback, then paused repeated seeks/resume and the transformed gesture/settings matrix. Sequential Phase 9 source work may proceed without closing these gates. B1, B18, B19 and drift 21 remain open.
