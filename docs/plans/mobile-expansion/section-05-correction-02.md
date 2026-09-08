# R13 correction 02: atomic visual revision

This separate correction follows the immutable35d8/0ffd round2 review. Do not alter correction01 or the original review verdict.

## Confirmed interleaving

The first correction publishes History LIVE before setting the separate VISUAL_FRESH flag. The renderer can observe LIVE, miss that flag, advance old live energy, and commit it under the unchanged source generation. A quick later pause can pin that stale resumed frame. Invalidation also publishes its reset separately from History replacement. Sequential GPU clear tests do not establish the missing transaction.

## Required transaction

History owns a visual revision distinct from source generation. Resume increments visual revision. Source retirement increments both. Under the existing ring, meter, then History locks, publish the new History state, update the producer epoch, and clear only visual samples/measurement. Preserve capture producer ownership during resume. Drop retired image ownership after unlocking. No driver or GPU call belongs in this transaction.

Every live render snapshots a frame token containing source generation and visual revision with its display state. Before advancing live energy, compare the token with the renderer's last cleared token. A changed token clears both GPU energy sides before processing samples and then records the cleared token. Do not depend on a delayed separate boolean signal. Retained-frame commit validates both token fields under History's lock. Work already in flight under an old token cannot become the new committed HOLD image. Physical scanout may already contain one in-flight present, as previously disclosed.

Replace the unused split fresh/reset helpers after checking all callers. The bounded geometry mailbox still validates the producer visual epoch before use. No audible ring, source control, socket or audio service changes are part of this repair.

## Checks

Use the actual host-testable History and render-reset token primitive. A barrier holds a render after its old snapshot, then resume/retire changes History before release. The old commit must be rejected. The first new-token frame must require a clear, and subsequent same-token frames must not. Test repeated pause/resume, generation replacement, initial observations and rejected remote observations. Retain the actual GPU pixel test proving both live sides clear while pinned pixels stay unchanged. Source wiring tests connect token snapshot, clear-before-advance and token-checked commit. Android callbacks, concurrent physical presentation and root source-time freshness remain explicitly unverified.

Local decoder retirement has a second confirmed ordering requirement. `close_session` stops and joins the producing thread. Move the final History invalidation after that join and `set_ring_state(false)`, retaining existing audible-stop ordering. Same-item seek still uses its explicit preserve-history path. A source-wiring assertion checks this final retirement boundary against the existing production join fixture.

## Capture-controller replacement baseline

A capture service can replace its mirrored MediaSession without retiring the audio reader. Native History therefore cannot infer that controller B's initial PAUSED snapshot is not an edge from controller A's PLAYING state. Bind a pure pause-observation adapter alongside the existing capture controller identity. Each bind resets only the observation baseline, not display or audio state. Reject observations from any other controller. Seed the first known PLAYING/PAUSED value without an action, ignore duplicates and unrelated states, then forward subsequent real edges to `setDisplayPaused`. Keep observation before metadata publication on the callback path. Controller loss and failed binding clear this adapter together with the existing callback owner. This is no new native API, source switch or transport command. Test A PLAYING, destruction, B initial PAUSED, B PLAYING then PAUSED, stale A callback, duplicates, and initial unknown states.

## Scoped observations before integration

Task0647564sdz tested an immutable private Git-archive snapshot of1dbb3a9 plus the three owned Rust corrections. Locked/offline Rust1.96.0 passed94 tests, including two threaded in-flight render barriers and the repeated reset-token test. This did not compile Android-only renderer/deck callbacks. A separate cached Kotlin2.4.10/JUnit4.13.2 run passed eight tests against the actual pure capture-observation adapter and fixed source-wiring fixtures. Evidence is under `/home/ben/.jcode/scratch/r13-revision-20260908T0810/`, with source receipt, native.log, run-jvm.sh and jvm.log. The live six-color writer was not compiled. Full Android integration and independent correction acceptance are still open.

## 08:21 UTC: exact committed integration gate

Task514919zi9y passed in172.1 seconds from independent clean clones of mobile `0591d7345cdbc701dd2549ef62037959c1fbdaca` and shared `0ffd658d7f19e68180c2720e0500b23644619e90`. The live color writer was not part of this freeze. All526 JVM tests in46 suites passed with zero failures, errors or skips, counted from the actual Gradle XML. All94 native tests and three actual offscreen retained-frame GPU tests passed. Gradle unit/lint, both debug APKs together, checkEngine and release-helper build passed. The production source boundary passed. Complete source manifests and file sets were identical before and after, and both clone working trees remained clean.

Evidence directory: `/home/ben/.jcode/scratch/hold-freeze-0591d73-0817/`.

| Evidence | SHA256 |
|---|---|
| Mobile source archive | `382cbcc3284a924602f9caf23bc3bc57fc0cc676928efe463d4576ecb7d58234` |
| Shared source archive | `c8a35001b41537def1b7d3777045ebe197526c73e423c1897d54c2ed9724915f` |
| App debug APK | `dee32897b98e61f23d995b345d38bd35ab893b6ee963bb4050a2966ae4fe9977` |
| Companion androidTest APK | `f0f741092e6a077f949ddc4a56d12e2ed9162257fb6867968be1b46806527a1d` |
| Mobile full source manifest | `afdba83fc77181d1c79a56dfacf1cdd8d9b802a461136be9b32b7e24d926a119` |
| Shared full source manifest | `581cd08f0a1fedcb8470e6848957edcec1017012bb772462e01280bc1d30acb9` |
| Bounded runner | `ffe265f06436751632ddbc545dad457ff62e47ad219f27a2b9687ac1f4aa8ceb` |
| Result envelope | `6805e9963034df28e98f2ca0400af12d00cce7d874eacc85b4ca1734797e194b` |
| Native log | `9197946d2b0aab9814453b735eac588e6745d46f29743f555250b7ab13cc3e28` |
| GPU log | `9e873c960d0b858dcd7dcfbabc8c05fed7ebe029d3f8bcc264bdab7b6f645daf` |
| Gradle log | `f23170a8f01fabaa505dec449b6e02dfaa44dacad69a3c46a7bac350e8851bc0` |

This is a clean host integration and artifact receipt, not installation, signing/publication acceptance, or Android behavior. The offscreen GPU tests ran on the host, not the S25 compositor. Root producer/source-time freshness, physical scanout, real callback interleavings and device acceptance remain open. Independent round3 reviews the exact0591/0ffd objects. The prior [round2 report](critiques/section-05-round-02.md) remains unchanged at7/10 for35d8/0ffd, SHA256 `e0d96fcbc7556c20528074112adc01903a7341f8947153bf47db16689c285f3b`.
