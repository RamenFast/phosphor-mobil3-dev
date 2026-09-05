# Risks, deferred designs and external owners

These are valid stopping points with evidence and a smallest resolving action, not hidden implementation guesses.

| Risk / decision | Evidence at planning | Owner and next action |
|---|---|---|
| Current repair program incomplete | B2 retained APK is not installed. Later B phases and final regression remain open. | Root completes existing sequence when device work is permitted. Phase 00 rejects premature entry. |
| Future source will differ | B6/B8/B17/B21 will change the exact owners this plan inspected. | Root planner recompiles affected tasks and hashes before implementation. Executor stops on unexplained drift. |
| Native SurfaceDestroyed timeout does not prove safe window release | `jni_glue.rs:69-86` returns after two seconds. `render.rs::Active` owns surface/window. | Separate source-lifetime design required before changing this policy. Phase 07 exposes ordinary failures, not arbitrary wedged-GPU safety. |
| Stale native write/clear may need stronger identity | B2 tests prove stop/publication order, not every JNI interleaving. | Phase 03 production-adapter tests establish whether a narrow paired Kotlin/Rust ownership migration is needed. No blanket migration now. |
| Decoder failure and EOF may still share a path | Shared AudioEvent lacks a decoder-error variant in the planning snapshot. | Recheck accepted B6 outcome in Phase 00. If still a product-truth gap, compile a separate mobile/shared/desktop event change before claiming failure distinction. |
| Provider cancellation cannot prevent every provider ANR | Separate 20,001-file stress reached Android provider ANR. | Keep representative tree cases and recorded scale. A CancellationSignal task needs its own API/cursor/late-result checks if cancellation is measured deficient. |
| Relay teardown may join before socket shutdown | Reliability reader inspected `relay/src/session.rs::serve_client`. This is a source risk, not a reproduced hang here. | Task 03.5 blocked-worker tests establish ordering. Fix only the reproduced shutdown seam with protocol regression. No live relay restart during quiet work. |
| Instrumentation dependency compatibility is unbuilt | Google Maven lists runner 1.7.0 and ext-junit 1.3.0. Existing Compose BOM is 2026.06.01. | Phase 01 test-only compile, manifest/dependency boundary and real harness gate. |
| Same-package downgrade may be rejected | Android signer/version constraints and settings compatibility apply. | Root retains exact APK/export and uses a compatible forward fix when needed. No production uninstall. |
| API compatibility environments unavailable | S25 Android 16 is the current real acceptance device. | Keep API 29/31/34/36 rows distinct. Obtain an approved suitable test environment later, never infer old-API PASS. |
| Performance noise masks regressions | No native Android timing distribution was collected during planning. | Phase 07 paired baseline rejects excessive uncertainty before setting allowances. |
| Sanitization conflicts with a build input | Package exporter currently archives the private Git tree. | Phase 08 checks every omission against build inputs and updates distribution spec before activation. Never fake exact source. |
| True screen-lock/PIN acceptance | Existing approval boundary remains. | Ben owns permission. Do not change keyguard or guess credentials. |
| New roadmap execution and publication | User delegated planning, not future implementation or release. | Ben owns later execution/push/tag/signing/publication approvals. Quiet planning needs no interruption. |
| Two future core features lack briefs | Existing ask ledger explicitly defers their specification. | Product owners supply the briefs tracked in [ASKS](../../ASKS.md). This roadmap does not invent or implement them. |

## Deferred extraction is conditional, not forgotten

`LocalPlayback`, `RemoteSheet`, a shared recorder implementation and broader native ownership tagging are not automatic deliverables.
Add one only when a named failing test or repeated ownership conflict demonstrates the benefit after inherited repairs.
Its compiled task must name exact owners, unchanged behavior, activation seam, regression and reverse-order rollback.
Do not turn this condition into an open-ended cleanup bucket.

## Safety token

**Blocked:** name the exact requirement that cannot be met.
**Evidence:** record source identity, command and observation.
**Best current result:** retain the verified useful state without claiming full acceptance.
**Next step:** name the smallest source check, test environment or approval that resolves the bottleneck.

Do not interrupt Ben's media because a phone or signing gate is blocked. Continue independent quiet work where available.
