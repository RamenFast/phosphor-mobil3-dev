# R09 microphone mixer: source checkpoint

2026-09-10. Implementation from Astra interrupted tree, completed in-session on grok-oauth Grok 4.6. Independent Grok OAuth code review.

## Outcome

Service-owned standard-only microphone, explicit route proof before PCM, and visualization-only composite mixer. Mixed PCM never reaches speakers. Root stays deferred and inert.

- Full JVM gate: 946 tests, 80 suites, 0 fail/error/skip. lintDebug, assembleDebug, assembleDebugAndroidTest, checkEngine passed.
- Code reviews: attempt 1 **7/10 FAIL** (F1 epoch rewind). Attempt 2 **8/10 PASS**. Both reports retained.
- Final APK SHA256 `4e436e263443c1d9b4f4ef572b7f08251b7f93b6e1971ad60708e6008debcfd8`.
- F1 correction: `CaptureMixSession.offer` does not call `core.epoch`. Publisher/init own mixer epoch. Stale pre-read offers are rejected.

## Not done

This is source acceptance, not device or release acceptance. ASUS built-in and accessory matrix, PiP/HUD/linger mic identity, two-input visualization proof, and 30s route flow remain. No raw mic recording/export. S25 excluded.

See critiques/r09-grok-code-round-01.md and r09-grok-code-round-02.md. Contract: section-03-r09-contract.md.
