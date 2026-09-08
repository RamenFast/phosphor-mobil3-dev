# Producing-read epoch integration evidence

## Scope

The worker handoff remains byte-identical in section-05-root-epoch-handoff.md, SHA256 `4d54d98815176802a44830055c0542c8c55f6e36d905bbca66711d3a7fdb351e`. The coordinator verified all 15 released file hashes before integration and stopped the worker at09:18 UTC. Color checkpoint1e68 was unchanged during this gate. Shared source remains0ffd658d7f19e68180c2720e0500b23644619e90.

## Observed gates

- Coordinator strict Java compilation passed. Original436 assertions and32 producing-read assertions passed.
- Coordinator locked/offline Rust helper tests passed37, including Java-produced binary vectors consumed by the production parser.
- First combined gate126901z5n2 failed Android unit-test compilation because `ClassLoader.getPlatformClassLoader` is absent from Android's compile API. Native107, GPU3, source boundary and both source manifests passed. This was not an accepted full gate.
- The coordinator changed only the test adapter. It loads the actual RootHelperLease class bytes in an isolated child classloader, delegates other dependencies, and retains every sticky-cleanup assertion. No production compatibility fallback was added.
- Corrected gate320828q14x passed at09:22:44 UTC in43.46 seconds. Actual XML reports contain555 JVM tests across50 suites, with zero failures, errors or skips. Native107 and three offscreen GPU tests passed. Android lint, both debug APKs, engine integration, release helper packaging and source boundary passed.
- Both complete source file lists and hashes remained unchanged before and after the corrected gate. This was a released working-source gate, not yet a clean committed artifact freeze.

## Exact evidence

Prefix: `dev/scratch/mobile-expansion-20260908T001819Z/color-root-integration-0922`.

| Evidence | SHA256 |
|---|---|
| Runner `run-color-root-integration-0922.sh` | `49c8ead1185572eac23be328e334baeff7ce7cc10ec7c3fad4cfd3ec5179a602` |
| Mobile source manifest | `25adbd2130832c88a727c55455a26abcbcc38623e85c5628e8a31ece049de3db` |
| Shared source manifest | `581cd08f0a1fedcb8470e6848957edcec1017012bb772462e01280bc1d30acb9` |
| Gate result JSON | `4a4e27d389ae7374f0276fb56525d17f71b94fbeb5dd28ac7826e8bb83e102b1` |
| App APK retained as prefix`.apk` | `54e3e560fb986df0d37cb0dc235a9d06fcc82b86962e922ea56111a492e2732a` |
| Test APK retained as prefix`-androidTest.apk` | `caa0fef92708ef35008256ba45619a014b76b7a8e88c50a412bf404751b23d15` |

Independent coordinator host outputs are under `/home/ben/.jcode/scratch/root-epoch-coordinator-20260908T0919/` and background task126901z5n2. The original failed0919 runner and logs are preserved.

## Acceptance limits

No APK was installed. Neither Android callbacks nor actual AudioRecord delivery, root grants, cleanup, physical audibility or latency were exercised. Producing-read tags reject stale queued reads and preserve original-owner native admission. They do not establish the age of samples already buffered inside AudioRecord. The finite source-time marker experiment remains open. R13 post-boundary application submission also remains open. Section6's independent inactive-owner findings remain open despite this passing integration gate. No new section-complete or R15 score claim follows.
