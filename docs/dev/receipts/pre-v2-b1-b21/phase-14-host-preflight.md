# Phase 14 host preflight: partial validation, not release acceptance

Date: 2026-09-06 UTC. Refs #6 and #7.

**Status: BLOCKED for the complete Phase14 gate.** This preflight does not bypass the preceding physical behavior requirements or authorize publication.

Baseline mobile commit: `2161e19bd6a127e68d7bd77769a82a1075971fb4`. Shared: `aa09b8e14f8b8912b125e3312b4ddeec09404089`. Both were clean. The preflight captured825 tracked source/document blobs before and after with identical hashes.

## Observed checks

Root task812005cyk8 ran the existing broader host commands, retaining each command and result rather than treating selected tests as the full suite.

| Check | Observed result |
|---|---|
| Gradle test/lint/assembly/checkEngine | Exit0 |
| Full mobile-native tests | 73 passed, zero failures or ignored cases. Includes local socket tests excluded from earlier selected gates. No Android callback execution. |
| Full relay tests | 43 passed, zero failures or ignored cases. Host fixtures are not a live user relay matrix. |
| Native formatting | Exit0 |
| Native strict Clippy | Exit101 on `payload.len() % 4 == 0`, manual_is_multiple_of. Corrected below. |
| Relay formatting and strict Clippy | Both exit0 |
| Shellcheck | Exit0 for dev/pm3 and all existing scripts |
| pm3, boundary and release fixtures | Each exit0. pm3 uses fake adb and restores its temporary build-output fixture. No physical device action. |
| Existing relay installer fixture | Exit0 inside its pinned disposable confinement. No real installer/service was invoked. |
| Source privacy boundary | Exit0 |
| Shared whole-workspace strict Clippy | Exit0 |
| Shared whole-workspace formatting | Exit1 across53 distinct files,1045 reported diff sections. Existing hand-wrapped desktop/audio/DSP/render source was not reformatted. |
| Public publisher fixture | Missing `scripts/test-publish-public.sh`, recorded as unavailable127. No publisher or public checkout was invoked. |

Overall preflight exit1 is preserved. It is not a full gate pass. Shared whole-workspace tests and the complete ship scoreboard are not established by this receipt.

## One-expression native lint correction

The only production change after preflight replaces the nonempty stereo-payload guard's `% 4 == 0` with `is_multiple_of(4)`. The divisor is the same nonzero constant and the independent nonempty guard remains. Root reconstructed the exact replacement from HEAD and proved there was no second source change.

Fresh task0838389mt0 passed strict native Clippy with `-D warnings`, native formatting, all73 native tests and actual Android rebuild. Root independently parsed420 JVM cases in34 suites, zero failures/errors/skips. The825-input correction inventory rehashed after execution. The unchanged parser tests still cover malformed/partial and valid silent stereo payloads. No lint suppression or protocol change was introduced.

This mechanical correction was root-reviewed and executed, not represented as another independent source/intent review. The previously reviewed rotation implementation remains byte-identical. The earlier exact rotation APK remains retained, but does not contain this later expression change.

Evidence under ignored `dev/scratch/pre-v2-20260829T072841Z/phase-14/` includes preflight results, logs, exact commands and correction results. `root-host-evidence01.sha256` binds81 files, SHA256 `942469c7f9feb1763cb54b9b5487c6a0c186fab505ad20cab7ebfb4e9e1d39dd`.

## Remaining requirements

- Complete the prior S25 and clean-default runtime acceptance before treating Phase13's optional cleanup as eligible. The phone's foreground use was preserved.
- Resolve the shared formatter scope explicitly rather than silently changing53 unrelated files or exempting a required check.
- Implement and independently validate the required publisher confinement/sanitization fixture in its planned phase. The current publisher copies broad scripts/dev paths and has not proved private scratch or estate-only omission. Do not invoke it against the real public checkout.
- Run the whole required scoreboard with zero skips and only the three allowed external release reds. Existing partial checks do not prove that result.
- Keep exact artifacts, settings preservation, physical behavior receipts and the user visibility gate open. No push, tag, release signing or store submission occurred.

Rollback is a new commit reverting only the expression change if needed. No shared source, protected archive, device setting or private backup was changed by this preflight.

## Full scoreboard and test-text clarification, 12:02 UTC

Task357680b2vz built the exact debug artifact from mobile `89367f4d653429831914c37ef601dfa8615f65e5` and the shared commit above. Its six-entry artifact manifest reverified successfully. APK SHA256: `104dc6ad671fdb76fadfdd1379b7c87844b352ecc49790a0960a747d2dcb604a`. This APK is retained, not installed on the S25.

The first full scoreboard reported 17 green, four red and zero skipped. Its unexpected privacy failure matched only two test-text lines: a remote gain-status comment and the letters `sentry` spanning words in a dismissal test method name. Root inspected both exact matches. The test comment now names remote gain status, and the test name describes following the chosen edge. Assertions, production code, dependencies and the privacy scanner are unchanged.

Fresh task992854niea verified exactly these two substitutions, froze all tracked inputs in both repositories, and ran the complete existing scoreboard. Result: **18 green, three external reds, zero skipped**. The red set is exactly `provenance.release`, `release.bundle`, and `signing.release`. The scoreboard correctly returns exit2 for those reds. The validating wrapper returns exit0. Root independently parsed 420 JVM cases with zero failures, errors or skips, including the renamed test. Every frozen input rehashed after execution.

Release signing credentials were unset during the debug-only check. No release signing, publisher, device activation or external publication occurred. The earlier four-red result remains preserved. `root-privacy-evidence01.sha256` binds 45 retained files, SHA256 `ab888905371d52216c1a621c8dc791ec20470d2f13cc7eb6da70e96e61ec21fa`.

This closes the unexpected privacy marker failure and establishes the required scoreboard result for this test-text snapshot. It does not close the separate shared formatting, missing publisher fixture, whole shared-workspace tests or Android acceptance requirements.
