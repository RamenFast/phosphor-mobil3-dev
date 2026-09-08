# Section 7 correction01: pure owner checkpoint

## Outcome and ownership

Cactus reviewed exact mobile `5b1be193e6fbcb0d3f779a8c0da5774a9a05efc6` and shared `0ffd658d7f19e68180c2720e0500b23644619e90`. Its full-section round1 scored 7/10. The [original report](critiques/section-07-round-01.md) remains byte-identical, SHA256 `9d439ab826632d0fa32c6c9183c34b2cb8f3a34508ac6182ea7b1541cd69e415`. Cactus stopped after receipt verification.

The [correction design](section-07-correction-01-design.md) preceded runtime changes. Clover explicitly released only `settings/instrument/**` and their tests at 11:30 UTC. The coordinator changed only `InstrumentWorkflow.kt` and its production-owner tests. Clover still owns R17 and Activity/runtime source until its full release. No Gradle, Android, JNI, device or audio action occurred in this correction window.

The pure owner now provides:

- An opaque authored-intent identity and one settings-import ticket, captured after prior native work settles. Later edit/apply/undo/publication, recovery or retirement invalidates old replies even when values return to their original tuple.
- A finish method that consumes only its exact ticket and rejects stale input before entering any write callback.
- A single automatic-save policy that preserves active-unsaved or uncertain tuning and does not prevent source actions.
- Typed external persistence-failure admission. Failed rollback latches shared tuning uncertainty. Verified rollback does not invent uncertainty. Only explicit complete save clears storage uncertainty, and it cannot repair an unknown native outcome.

**The Activity adapters are not corrected at this checkpoint.** F1/F2/F3 remain open integration findings. The compact LIGHT recall entry is designed but not implemented. This is not a full-section correction acceptance or a new review score.

## Checks

Task `488497nmjq` ran for 12.2 seconds and exited 0. Eight pure actual-source JUnit suites passed 85 tests, including nine new workflow tests. Before/after snapshot and live pure-source hashes matched. Compilation and scoped `git diff --check` passed.

| Requirement | Observed production-owner check | Boundary |
| --- | --- | --- |
| Preserve failed preset across source/gain autosave | Exact prior map, including missing focus, survives suppressed partial writes. Source action remains available. Explicit full retry succeeds | Pure policy and map adapter, not Activity or disk |
| Reject stale whole-settings import | Apply, manual edit, equal-value round trip, undo intent and queued later native request all reject the old ticket before mutation | Actual owner, controlled native receipt adapter |
| Provider delay with actual document decoding | A latch holds production `SettingsArchive.decode`. A later preset commits. Delivered old archive never enters the settings write callback | JVM thread barrier, not Android SAF |
| Reconcile pending work before choosing a document | Already committed apply settles before ticket capture. Unchanged baseline imports once. Duplicate replies remain inert | Actual owner and write owner |
| Retire/cancel operation identity | Cancelled and old picker replies cannot claim a replacement ticket. Closed owner cannot accept or save | Actual owner, not Activity recreation |
| Shared rollback failure | Light and archive transactions use actual `SettingsWriteOwner.commit`, fail save and rollback, and enter the same recovery latch | In-memory storage adapter, not filesystem durability |
| Recovery boundaries | Automatic writes, tuning, APPLY, UNDO and import stay blocked after uncertainty. Complete retry uses no native request. Unknown native result remains blocked | Actual workflow and receipt adapter |

An initial broader attempt `209691f0jr` compiled and ran 89 tests, with one failure because an existing source-only Activity assertion had no Activity file in the intentionally pure snapshot. Its output is retained. No production behavior failed. A pure-only rerun passed 84 tests, then the actual archive barrier test increased the corrected run to 85. The final runner explicitly excludes both classes containing Activity source assertions. The eventual full Android gate must run those unchanged or correctly adapted checks against live integrated source.

## Exact evidence

Private directory: `/home/ben/.jcode/scratch/r16-correction01-owner-1138/`.

| Evidence | SHA256 |
| --- | --- |
| Coordinator runner `run-instrument-owner-correction-1138.sh` | `90e6f23793d8a3d896e37cf59642351f1887676b4f0377104f0ebe1e885278c9` |
| Pure source manifest | `c72ab5be89939d573610b84acd10bfc7c8d01de817319a8ebeb4acb3077dd7c2` |
| Generated host runner | `ae42e22b1e9839ec91a9a79878ebe565caed39f66b2c24fd0cae0c3a47eddfc0` |
| Test result | `6eed09f6cb354fcf770705984b73dab5c45dfc8bb5069df361e3f6f5be44f1aa` |

The directory also retains dependency paths and actual JAR hashes, compiled tests and full compiler output. No source substitute, Android stub or altered production implementation was used in the snapshot.

## Next gate

After Clover releases Activity/runtime source, wire all three adapters and the compact recall entry. Run actual adapter source checks plus the full frozen Kotlin/native/GPU/lint/dual-APK/engine/boundary gate. Retain a separate correction record and obtain independent round2. Real SharedPreferences rollback, SAF delivery, JNI ordering, source continuity and accessibility still need the identified ASUS. S25 remains undisturbed.
