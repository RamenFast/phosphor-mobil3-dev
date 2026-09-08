# Section 1 independent critique, round 1

- Reviewer: `session_hippo_1788829951212_0461d6f207979b37`, GPT Astra through the enforced `openai-oauth:gpt-6-astra` route, explicit high reasoning.
- Reviewed implementation: `283a0177b77e9c6ab5b6d97faab0edea7aa8eec8`, with baseline/contracts `7153570`.
- Completed: 2026-09-08 at 01:23 UTC. The coordinator received the detailed direct report at 01:27:34 UTC.
- Score: **7/10**. This is an independent reviewer score, not a coordinator score or expanded-feature acceptance.
- Round budget: 1 of at most 4 used. Correct these findings, then independently review round 2.

## Findings

### P1: detached manifest can misrepresent the packaged artifact

`scripts/check-play-boundary.sh:147–151,238–245` checks the separately supplied merged XML but never decodes the archive's own manifest. The reviewer paired a real debug APK with an unrelated minimal production XML and obtained exit 0, six artifact checks and one trusted-runtime exemption. The actual archive still declared `dev.phosphor.mobil3.debug`, `debuggable=true` and SelfTestReceiver. aapt2 independently confirmed those entries.

Tested APK SHA-256: `e5b5f087574de77a609c729745b15e012d7cbbdf21b33982673f2dae87ee12f4`. This was a generated artifact read-only check, not a phone install. Private evidence: `/home/ben/.jcode/scratch/section1-critic.Wofyi9/real-apk-output.json`. The weakness predates this implementation but prevents the promised exact artifact boundary.

Required correction: always decode and validate the named APK/AAB's actual packaged manifest. Detached XML can be supplementary evidence, never a replacement. Include real-format APK and AAB positive/negative fixtures and the mismatched debug-APK reproduction.

### P2: XML 1.1 control characters break JSON errors

At `scripts/check-play-boundary.sh:15–21`, escaping handles only tab, CR and LF among C0. Permission text `UNKNOWN&#x1;NAME` produces raw U+0001. The command exits 4 but jq rejects its output with exit 5. Private evidence: `control.xml` and `control-output.json` in the review directory.

Required correction: escape every representable C0 character in every string and test the public envelope.

### P3: missing compiler receives the wrong exit class

An executable Java runtime without `jdk.compiler` returns exit 4 `manifest_parser_failed`. The spec requires unavailable dependency exit 2. The reviewer used the pinned Java with `--limit-modules java.base,java.xml` through a private launcher. Private evidence: `jre/bin/java` and `missing-compiler.json`.

Required correction: detect unavailable compiler capability before source launching, with the normal error/fix envelope.

## Independent checks and limits

The reviewer ran the exact pinned 91 parser cases and complete public boundary fixtures from a private git-archive snapshot. Baseline backup hashes matched. Protected historical blobs and retained release-gate scripts were unchanged. No implementation edits, device actions, Gradle build or generated-APK mutation occurred. Later expansion features were not scored as section 1 failures.

The coordinator retains these findings and owns corrections, integration, final verification and the next independent review. The reviewer was stopped after its report was retained.
