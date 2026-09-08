# Section 04: coordinator validation [R02]

## Current result

The floating HUD implementation passes Android compilation, unit tests, lint, native integration, and the production source boundary. It is not installed or hardware-accepted. At06:54 UTC the S25 still had unrelated active MEDIA playback. The coordinator performed read-only observations and left that playback untouched.

The source provides explicit opt-in activation, solid or premultiplied transparent presentation, one shared surface lease, existing-session transport controls, and independent teardown. It refuses activity-owned microphone transfer without stopping the reader. Imports remain inert.

## Reviews and corrections

- [Round 1](critiques/section-04-round-01.md), SHA256 `308852afaa8ee8950f7d85c11483abeccca8fb196c5dd78ef839c9d5cf53c3c5`, scored6/10. Its exact baseline remains preserved.
- [Round 2](critiques/section-04-round-02.md), SHA256 `ccab0099329b0413afcc883045a5803a6874cb1fb32e3282b9f650202ecebd6b`, scored8/10 for corrected source. It accepted actual native drop ordering, local metadata identity, and retained controller-future ownership. It separately identified an uncaught callback failure.
- The coordinator then added a production callback adapter. A lost native acknowledgement remains a sticky ownership failure. Surface and visibility callbacks report restart guidance without throwing through Android. Later teardown still runs. HUD close preserves the failure instead of publishing successful retirement.
- Two focused Kotlin tests exercise this actual adapter with the actual lease owner, including an error in the status observer. These are host unit tests, not Android SurfaceHolder fault injection.
- The immutable [callback addendum](critiques/section-04-callback-addendum-03.md), SHA256 `b29898c686f129dfa568244ca9007871e580313a30a753ee3febbb42785d6d22`, closes that source defect. Its six observed source hashes match the gated implementation. The prior8/10 score remains unchanged. Corrective source review stops here. Runtime acceptance remains open.

The original writer's75 native tests grew to79 after native retirement corrections. Writer-local Kotlin checks covered13 HUD cases. The coordinator's results below supersede those partial build checks for current source only.

## Exact frozen integration gate

Task `329223ctm4` ran06:52–06:54 UTC. Source hashes were captured before the command and verified afterward. Both exit codes were0.

```sh
source scripts/env.sh
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug \
  :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:checkEngine :app:buildReleaseRootAudioLauncher
```

| Evidence | Observed result |
|---|---|
| Android unit results | 505 tests, zero failures, zero errors |
| Android lint | Passed, existing warnings retained |
| App and companion Android-test APKs | Both assembled in the same frozen-source command |
| Native engine and helper variants | Android integration checks passed |
| Source before/after | Every recorded source hash matched |
| Production source boundary | Passed12 checks at06:55 UTC |
| Independent coordinator native host suite | Cargo1.96.0, locked/offline,79 tests passed |

Private evidence lives under `dev/scratch/mobile-expansion-20260908T001819Z/`:

| File | SHA256 |
|---|---|
| `hud-callback-gate-0652-source.sha256` | `204d097f9f8af1155dae616a6f5c0fb6285ef766fdf0aebbabe700d89f941bce` |
| `hud-callback-gate-0652-build.log` | `f4c28626aeb9e1ec39524ed49b40f4aba1dc3dee6ab50f6f3ab3ea5af27f7a1a` |
| `hud-callback-gate-0652-source-check.log` | `ddb61e236e03ea5397d995170fc1c6d5b4faa3c90d3c909ca4999403cf3689d1` |

This gate validated a frozen working tree, not a clean committed installation artifact. A subsequent clean dual-APK freeze must establish the install candidate and its hashes.

## Remaining acceptance

Use the [contract verification map](section-04-hud-contract.md) and round-one finite acceptance procedures. Each remains open until observed on the exact installed artifact:

1. Real transparent pixels over colored content, solid fallback, and no whole-window alpha imitation.
2. Outside touches,48dp controls, drag/resize bounds, rotation, and display-inset changes.
3. Repeated app/PiP/HUD handoffs with one source reader and unchanged tuning/audio.
4. Permission denial/revocation, lock, close, task removal, failed window addition, and process recovery.
5. Native delayed retirement and lost-channel callback recovery, without early window release.
6. Real pending Media3 binding disposal and local metadata enrichment without duplicate cycle steps.
7. Inert enabled/transparent import and explicit activity-microphone refusal.
8. No native frames or hidden chrome/sensor heartbeat without a visible presentation owner.

The native resource barrier can wait indefinitely for a stalled GPU driver. Source inspection and host tests establish ownership order, not bounded GPU completion or phone responsiveness under a driver stall. HOLD retention, HDR output, and background microphone ownership remain separate expansion sections.

## Clean committed dual-artifact freeze, 07:01 UTC

Task `810364pegz` built both APKs together from clean mobile commit `a3223b87c8d15fc0da691cfcfe0963ba464d1e92` and clean engine `7729990bb29f0167ef906d0fbdb44e1e91206955`. Both source manifests matched after the full gate. The same505 Android tests passed with no failures or errors. Both decoded BuildConfig classes report `a3223b87c8d1`, without a dirty suffix. The packaged HUD service is nonexported with specialUse and its declared subtype.

| Frozen item | SHA256 |
|---|---|
| Mobile source archive | `c320f5bd74421afd8147b0255e19d10b63a5f40c103245a6480ceafc9122ada2` |
| Mobile source manifest | `842bfad2483cd86ed73ce4ba6e05eb5bc1bb90fe8b39fc2cbdead005e02486dd` |
| Engine source manifest | `8e7a32ac43b2fa668f2d53f6fe0642650c5b334dd53997366d97006a13b81ffe` |
| App APK | `265509b1e28eb30e8f4d2e32f86649215d053bddedb0dce9df3b5aabf7fc7cf3` |
| Android-test APK | `04b94fb3e2447acb18b8d490bac5d53744816d7360164e9edafabb8b52ff2a85` |
| Debug signer | `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d` |
| Actual decoded packaged manifest | `be023f7771c1e31363e3a61d7c04d7934b73a45f523325b664c901bb75bb6c8e` |
| Packaged helper DEX jar | `ba6cdea7e73e412c702d37652d1bb766fee2fef86fa411fc0fba26e7cf7ffc2a` |
| Clean build log | `1b5d7e18020a8d7e462ffb4e388bfcdec51bf5ee14c696ddbe4838f271905059` |
| Freeze JSON | `c51fe41d1250fb6affd3c7754c145e29274a2dc163a6410387dbdd177bf3af24` |

The app's helper content identity is `db99e0398505d83ecd024c80ced96bf2a3c378e592b52bdd2e2382f090003be4`. The packaged digest matches the actual jar. The retained prefix is `dev/scratch/mobile-expansion-20260908T001819Z/hud-a3223b8`, with `.apk`, `-androidTest.apk`, and `-freeze.json` files. Offline guards verified the source archive and both artifact hashes. Neither APK is installed. The installed06f root checkpoint remains unchanged. R13 source work started only after retention and source/build release, so it is outside this freeze.
