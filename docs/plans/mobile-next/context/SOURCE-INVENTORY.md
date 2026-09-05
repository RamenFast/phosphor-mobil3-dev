# Source inventory

Checked against mobile `98116556ade7dc9ccaf2265d718d2dc294cf4e4c` and engine `2a45b0f4d05696efe98970f51ac5358c052b565f` on 2026-09-05.
The mobile behavior bytes are from `47ff7cdc71c3b8e4315d265844fdfbfcfeb2136c`.
Two isolated readers supplied anchors. Root rehashed every listed file against their recorded identity.
These are planning facts, not future acceptance. Phase 00 recompiles this inventory after the inherited repairs.

Exact path aliases, also defined by EXECUTION.md: `M=/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3`, `E=/home/ben/Dev/ClaudeWorkspace/phosphor`.
Expand `M/` and `E/` literally. The adjacent manifest uses absolute paths for local checking.
No proposed file is claimed to exist in this table.

| Existing owner | SHA-256 | Checked anchor |
|---|---|---|
| `M/PRE-V2-B1-B21-EXECUTION-PLAN.md` | `e12042be43a3ee450c3b7bb6ef34befa55db18bea819db59c91f6e2faa9357f0` | `5:### Execution update, 2026-09-05` |
| `M/app/build.gradle.kts` | `7b767b0c9a2bad23c2bc9c82eba29692a456150fbc3610ae05365e73664530af` | `349:tasks.register<Exec>("checkEngine")` |
| `M/app/src/debug/kotlin/dev/phosphor/mobil3/SelfTestReceiver.kt` | `bdc2022989869433aa1173bd4c3362857f303b27cf62ec586d751f0f23251a5b` | `onReceive` runs native self-test asynchronously |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/CaptureService.kt` | `eaa50c40cba5792bd39a1ba8f0313ab50edd1720955a56871a407cafd7c505be` | `238:private fun finishCapture(` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/DocumentTreeSource.kt` | `ee9c0f70647a6c5316bbfd6ef1efbe8d6463daca269c383fbbe7dacb93338ea6` | `9:internal class DocumentTreeSource` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/LatestRequestSlot.kt` | `e735b0cb686b6abf1e08fa0c8d2600f6e03bed509d3123ae2046011261a8a832` | `10:internal class LatestRequestSlot` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/LocalPlaybackPolicy.kt` | `83fb7b960ecbd932125506f69dfa249403b9f77e24960f4eff09bef0fb40f9d8` | `27:internal class LocalSourcePublication` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt` | `7b453db4fc7beec10bf8bf81c8c0d8e570e9d1ca82c55c985e6230366bbaacc6` | `614:private fun syncSessionFace(` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/MicController.kt` | `c8141d712b12e489f346cbce61d3efd4c87344e9cf3244435cf66eee76982b91` | `23:fun start(onStarted:` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/MicHandoffPolicy.kt` | `0e2bcbd986a9d17917026c41bf17cc2373e067ab31ad6b3d48e67bd52e4afa3b` | `10:internal class MicHandoffPolicy` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/PhosphorApplication.kt` | `702605193e08bce1147c6ced97cbf52863044ca0822e0a50e31a184a6a4923aa` | `25:private fun migrateLegacyRuntimePreferences` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/PhosphorNative.kt` | `0bb7d868e7dc7862918bf8d93b719fe8cc54f24a4f3be9c1ffc497cfbd81ec78` | `7:object PhosphorNative` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/PhosphorPlayer.kt` | `362cada790ad6af839aea3a473b158c7016d1f008886b1579f9a5ef2b45281e7` | `19:class PhosphorPlayer` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/PlaybackService.kt` | `f4a015adaaba4ba7121086db15f7c21738d384e9598a4ae3033dfd10c975e3c2` | `98:private val localDeckExecutor` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/ReaderStop.kt` | `5869483f47ea5657c9fa4b625d1fc22e387ebb69ad41bb196098031758f856d1` | `13:internal object ReaderStop` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/RemoteLinkTruth.kt` | `473a44643ac225fb06c5671b12d493d6f9ac43bb24cbfc8ba1e7015cbb701d91` | `72:fun read(status: JSONObject)` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/RemotePlayer.kt` | `a33272178702b5b3959f9bb86fc9480a7b3d2c03e7ce9a54739eb38c04f70d21` | `28:class RemotePlayer` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/settings/LegacySettingsMigration.kt` | `2cf67920d942e43fbfae28c5e9921b368f0ba79db39cbf78b66486df88339d84` | `8:internal object LegacySettingsMigration` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/settings/SettingsArchive.kt` | `6d379dd5fa421248352272f90f3924bda0fda18b4487b49474a15dfb07689f98` | `60:private val specs:` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/ui/Console.kt` | `3d26decd958daff696060d2b3b2eb9a0b124d43f03911381be0e067ea9856b43` | `SeekRule:125`, `StatusBand:72`, `OverflowPopout:345` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/ui/Controls.kt` | `e0957cc46f5bcbf9827c199f575940bbcba75e1a74a36cf9c6caa3c43c7bc485` | `StoneKey`, `StoneToggle`, custom drawn clickable controls |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/ui/Motion.kt` | `14f52e5d1eecbf0d8d2b927c685966e0e71961056883e7d0845b653febc662a5` | `readReducedMotion:269`, `LocalReducedMotion`, `motionSpec` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/ui/Palette.kt` | `8b0d95978d46d3ea0f9e95f33f0fdac5ea54d547ea88c81a70d524a63846fa3e` | `Palette.liveAccent`, `withBeam`, `lerpTo` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/ui/PhosphorScreen.kt` | `5a6d8c6aa83a1c68fb0078807939a749496a79aafb96d8365bc0ae93d2998e47` | `PhosphorScreen`, `ScopeActions.makeSurface`, AndroidView at 399 |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/ui/ScopeUiState.kt` | `45df1201add84298bec09982597f42d82309c79e13252bf8e5e835f1adfa0917` | `11:class ScopeUiState` |
| `M/app/src/main/kotlin/dev/phosphor/mobil3/ui/Sheets.kt` | `1b82b75b4cea924bcd6bb85fbdff6c69b1d1e3ece19fa5c800c782b23e899427` | `1428:fun RemoteFlow(` |
| `M/app/src/test/kotlin/dev/phosphor/mobil3/ReaderStopTest.kt` | `867b8b240171bca791b55a33b832261113dae7476c511eb9acf8677606928865` | `66:fun successRequiresJoinedReaderAndCompletedCleanup()` |
| `M/dev/pm3` | `b6a2cb14cdd8da5aa150d4d2727bcb5b24087a4f35faaa499597006dce3395c6` | `schema:188`, `install:363`, `smoke:495` |
| `M/docs/SERIOUS-TODOS.md` | `2e0f8fbd779dcfd5260c3e8bfc2955ddec71b7a2e7db123caec85d7777952be4` | `42:## Post-v2 structure debt` |
| `M/docs/dev/receipts/pre-v2-b1-b21/phase-02-b9-seek-stress.md` | `ebe0d2c5e0880fc8605f8725761a4ef35d51592a7ea4fa0aac552d4e8cf13094` | `40:## Separate observations` |
| `M/docs/dev/receipts/pre-v2-b1-b21/phase-03-b4-folder-tree.md` | `1b859264333e8552aa9e7d60750cb5c2869acc0f572e5e707eefc4e0a0cefb85` | `23:## Committed shared change` |
| `M/docs/dev/receipts/pre-v2-b1-b21/phase-04-b2-mic.md` | `98ecce3f946eaab846194da0b8b4c9a751793708c39e6fbe2582d5190b9abef3` | `4:Status: B2 OPEN` |
| `M/relay/src/session.rs` | `3de1c2a7341258711b8fc85b7960a0ef5bdc2d07209ed8c150df63b528718b2f` | `893:pub fn serve_client(` |
| `M/rust/Cargo.toml` | `41307781dc028ffb57409a3a5fb871a3345ffe0ab4da119ad0bd6581e63000dc` | `18:phosphor-audio =` |
| `M/rust/src/bridge_core.rs` | `2d1604ca7defa189722f67e992e93bbc4d4814c850901af852c1aef67ba2d43a` | `88:pub fn bounded_join(` |
| `M/rust/src/deck.rs` | `ad50a2ea551db8965ec315cfc88303a669e350a9820895fa766fe416b9fda547` | `86:_events_rx:` |
| `M/rust/src/deck_activation.rs` | `24959c61cf6f4d7a819088df93f39f3e2c2e96825b432bb3cea1390c52744728` | `8:pub(crate) struct DeckActivation` |
| `M/rust/src/deck_close.rs` | `0983cb87cae86446a03411bf73a2404fa0fc7d513e371af578f44d49645adec6` | `3:pub(crate) fn close_session` |
| `M/rust/src/engine.rs` | `4b077498d5fadb348a83382732b188f9c535e7ef42a7aeeb27ab1bbaad277467` | `compute_scope_frame`, `apply_geom_fx`, `AutoGain` tests |
| `M/rust/src/jni_glue.rs` | `182c8362ee31cdba2d9bc222f64cff83101adbbb161783716674d73ee5c9220d` | `69:fn Java_dev_phosphor_mobil3_PhosphorNative_surfaceDestroyed` |
| `M/rust/src/lib.rs` | `aefae57c770caa114061947d2706a56618f38136a85277139ce16dd0228473d5` | `15:pub mod deck;` |
| `M/rust/src/remote.rs` | `344c7cdd12a95e8289c8fcdb1159ed569b3c923a3f3f89edc3128bcf3f415fd7` | `1222:fn teardown_session(` |
| `M/rust/src/render.rs` | `676446c482f468e7d9710e8fb7b2275f5a1d03ab5d0fa698201643710c5fd1fb` | `141:pub fn sender()` |
| `M/rust/src/selftest.rs` | `97d953d060c3bf31101857a12d424d351d5ef2b277ed3b2c784f34489f97da1c` | fixed 512px Lissajous, eight GPU advances, lit count and FNV |
| `M/scripts/check-play-boundary.sh` | `8798d4948b9a2d353c91374fea6f286996e5c35d78abcef16f8d703e6015c65f` | `8:MODE="all"` |
| `M/scripts/package-release.sh` | `31d4917c8ef3bbcb246eb4d56955b953231de1e6f87bb436c0c150606b346a56` | exact-tag archives 201-211, build manifest/checksums 230-251 |
| `M/scripts/publish-public.sh` | `004b9c6ac5b31ba39faaa21da077f223b04bbcf9b1d7eae480e85cc9aac41e3c` | separate public export path, current allowlist 27-42 |
| `M/scripts/ship-check.sh` | `b858d91c8ef280a9a7025cdfd32c68dbb0cde6d69c30ba7ccf0b580a05610916` | `12:MODE="run"` |
| `M/scripts/test-pm3.sh` | `f686b711f580afd2fc1b91e2767c95fcc5a21220759ca6d82df1f394ff784f16` | `12:ORIGINAL=` |
| `M/scripts/test-release-gates.sh` | `a8530cd6e420ff35241ab172bba337d6c309b50df61e25ce84e0caf5d500abc9` | fixture-only provenance and missing-signer rejection |
| `M/spec/ACCEPTANCE.md` | `e1fb37421bb1cf64befcfbe91db73a688fff843c5f04578fbf488028c92a5ada` | `97:## L. Pre-v2 B1-B21 lived-repair matrix` |
| `M/spec/AUDIO-AND-CONNECTIVITY.md` | `d8f6f96113e27871cbafb507956f591ae546a7f319ab8f6e8d5d1313b69fe106` | `96:## 8. Source shutdown` |
| `M/spec/EXPERIENCE.md` | `c721d6e2596e4ead89b0033e74e52fd0c55430337b0fc348f65fce28c1fbaee5` | 97-139 chrome versus beam, a11y, immediacy |
| `M/spec/PRODUCT.md` | `27297672435728426b3281941a75f4437fa507da1bc2993ad8b7d79d94818fd2` | `40:## 4. State and persistence` |
| `M/spec/README.md` | `2ac16ef0ee8de0fd194bec62979e2aee3eac7b3d5e9f425b269c8df6dcddc58f` | `5:## Authority order` |
| `M/vision/PHOSPHOR-LIVING-INSTRUMENT.md` | `9252ce6e9666a43cf3b7a680776b736590cee4b6e501ce8abe4e6b97b75bdd3e` | `59:## Non-negotiable invariants` |
| `E/crates/phosphor-app/src/feed.rs` | `f8f0f30296db5fdafb918f2c045c77c445a68b11543749fe12f05c66cdf28ced` | `209:while let Ok(event) = events.try_recv()` |
| `E/crates/phosphor-app/src/shell.rs` | `e36ddbfee1963587018968563011343e61909bec0ade6df2679ea622c873832a` | `2608:AudioEvent::PlaybackEnded =>` |
| `E/crates/phosphor-audio/src/events.rs` | `c6bb7bab3938096332f2399dcd0f7142b24297c6b731961f3900788cfd57cce8` | `8:pub enum AudioEvent` |
| `E/crates/phosphor-audio/src/playback.rs` | `3c054e80a55b2467ee04e56dedc0ddf9e098f3fa1ef182a35b677b603ea76efb` | `731:fn push_chunk(` |
| `E/crates/phosphor-dsp/tests/golden_replay.rs` | `3bb2a9a412022c85422653b64f477a3c56f838ce48261288274c6183bab6e388` | recorded chunk boundaries, coordinate/intensity tolerances |
| `E/crates/phosphor-render-cpu/tests/timing.rs` | `69f91238e9279531296c86845efc9bd514050be6a1e4703319f3ecc8135d42ec` | `timing_noise_workloads`, release timings, sanity ceiling only |
| `E/crates/phosphor-render-gpu/tests/cross_snapshot.rs` | `8b941c3dcd5d7919bef779e53f19520dd1c34a523c73ad1d5f056ab2fd4bff6e` | `gpu_or_skip`, CPU/GPU luminance and sharpness comparisons |

## Current identity check

```bash
cd /home/ben/Dev/ClaudeWorkspace/phosphor-mobil3
sha256sum -c docs/plans/mobile-next/context/SOURCE-SHA256SUMS
```

Expected at this planning snapshot: all 63 files match.
Later accepted B repairs will change hashes. That is an expected rebase trigger, not permission to erase their changes.
Recompile the inventory and affected tasks before implementation. Preserve this historical manifest in Git.
