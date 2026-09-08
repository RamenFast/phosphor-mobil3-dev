# R15 section 6 round 1: process disclosure addendum

This addendum is part of the immutable review report bundle. Preserve `report.md` unchanged.

At 2026-09-08T08:53:23Z, this reviewer executed the new private `seal-readset.py` once. The coordinator's task-wide no-Python reminder reached this worker at 08:56:20Z. The already executed step is disclosed rather than omitted.

The script read 27 frozen snapshot files and their specified Git blob IDs. It verified exact blob identity, calculated SHA-256 values, and wrote only these new private artifacts:

- `readset.json`
- `readset.sha256`
- `host-artifacts.sha256`

It then marked those three artifacts read-only. It did not edit live product source, mutate Git, access the phone, run tests, change services, or overwrite existing reports. The script itself remains preserved for audit. This was a scratch receipt-generation step, not a product implementation or Python test substitute.

No further Python execution will occur. Final integrity verification and sealing use shell `sha256sum` and `chmod` only. The procedural deviation does not change the source findings or their independent Rust/JVM reproductions.

At 08:56:20Z, the coordinator separately reported reproducing F2 and F3 with exact-7ac1 native source in private Rust tests. Its two desired-behavior assertions both failed. That is additional inherited confirmation, not a check performed by this reviewer. The original report and original host logs remain unchanged.

All review holds remain released. The report's score and the carried R13 gaps remain unchanged.
