# Section 1 independent critique, round 2

- Reviewed commit: `3a51b35dc8de14496a0bcb6baf565001461e5d61`.
- Reviewer: `session_hedgehog_1788833218692_8aebcc68229bc347`, separate from implementation.
- Route and effort: `openai-oauth:gpt-6-astra`, `high`.
- Score: **7/10**. Round 2 of at most four. This is not release or feature acceptance.
- Full private report: `/home/ben/.jcode/scratch/section1-round2.rWjpyB/REPORT.md`.

## Intent and evidence

The corrected scanner now decodes the actual named artifact. The independent reviewer reran all 102 parser and 67 public CLI cases. The actual copied debug APK retained SHA-256 `31d90be7dd58522148397302f61686806b19e842c4507cfc0d878e02601249d3` and could no longer use unrelated production XML. C0 error bytes round-tripped through valid JSON. A real Java runtime without `jdk.compiler` returned unavailable exit 2 with a fix. Round 1 P1, P2 and P3 are corrected.

Source gate, shellcheck and diff checks passed. The reviewer inspected all section 1 requirement mappings, defaults, ownership, inert imports, baseline records and roadmap reconciliation. Real APK/AAB fixtures exercised numeric FGS roles, signature permission, forbidden fields, duplicate archive entries, tracking, private endpoints and the pinned libc++ exception. No device, root, app build, signing, source edit or Git mutation occurred.

## Finding R2-F1, P2

At `scripts/lib/ManifestBoundary.java:137–139,170–171`, SDK attributes were validated only when present. Missing or duplicate `uses-sdk` declarations were not rejected.

| Actual compiled fixture | Observed incorrect result |
|---|---|
| AAPT2 APK with no SDK declaration, SHA-256 `94b23feb617ed3fa1dbc39d0b98c9fa7bf04f012b9d636f6effcd0865852c1de` | Artifact exit 0, six checks |
| Bundletool-validated AAB with no SDK declaration, SHA-256 `4f784519a2855ce90bfdd02f803b6bd008c1df1d0d2ffefdf3d9c4547ce8e998` | Artifact exit 0, six checks |
| AAPT2 APK with two identical 29/36 SDK declarations, SHA-256 `6e5f5066a056726691b528a767bab35a09eae084123937f5eb4c448af11ef92c` | Artifact exit 0. Duplicate supplementary XML also passed |

Require exactly one SDK declaration in merged evidence and at most one in source. Source may omit it because Gradle supplies it. Preserve exact min 29, target 36 and absent max SDK. Add parser and real-format regressions without weakening previous fixtures.

## Disposition and uncertainty

The sole helper implementation worker received this narrow correction before resuming its helper files. Coordinator verification and round 3 follow the correction commit. The reviewer was stopped after handoff.

The reviewer did not independently test duplicate SDK in AAB, rerun release-gate behavior, or revalidate device baseline state. Those limits do not weaken the reproduced missing APK/AAB and duplicate APK findings. No speculative unrelated fuzzing remains.
