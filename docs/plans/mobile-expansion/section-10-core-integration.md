# Section 10 core and migration integration receipt

Coordinator gate `5457814owf` completed on 2026-09-08 at14:27:07UTC with exit0. It tested released working source at mobile9b34243 plus the enumerated new appearance and settings-focus paths. Shared source stayed0ffd658d7f19e68180c2720e0500b23644619e90. Both source inventories matched before and after the gate.

## Observed checks

- Android gate passed803 JVM tests across68 suites, with zero failures/errors, lint, both debug APK builds and checkEngine.
- Native host suite passed126 tests. Three offscreen retained-frame GPU tests passed on the host RADV adapter.
- Production source boundary and release helper packaging passed.
- New appearance checks include46 pure document/codec/collection cases,12 actual legacy migration bridge cases and8 public archive cases. The settings focus wiring adds one source assertion.
- Palmtree's original84-test cached host receipt remains separate. Its source archive, compiled inputs, dependencies, JDK and complete manifest verified before the coordinator build.

## Exact retained evidence

Prefix: `dev/scratch/mobile-expansion-20260908T001819Z/appearance-core-migration-r01`.

| Artifact | SHA256 |
| --- | --- |
| Gate runner `run-appearance-core-migration-r01.sh` | fa5fb217b02038d9d7af77a72399810a488e28beb8683bcf22b3fbc92eaacc65 |
| Pre/post verified mobile source inventory | c6868a0ff4658f66ab516233a0ebffde5c7e6aa2170d6f8795d9d79a7b7e0061 |
| Debug APK | 27fedae3277a1370df3fde0fdd3dbbafa51c677ad3d1e1dab8e088696cc48f6c |
| Android test APK | 82c7b7eac2d06772002ba72dcb161c56a960e1c8597ef6b49306dba2456bf466 |
| Retained JVM results archive | a14f941c251e6c72265169465f3d3a90acd8c990843406b83c8ccb1826731a26 |
| Original worker REPORT.md | b9cee31d298f3d41890392de360d8901e438718ff04edda078271c5273c6f1b1 |
| Original worker MANIFEST.sha256 | 6bd2be4f300e0d9c5575f44c3d5b041d84ea07e6d7bd1394b45871a5ad2f1c4c |
| Original worker source.tar | b2c277ab064cf22b2fd0c8af95d3360b05da51abd6bf0f208c0e39363b6cfd48 |

Worker receipts remain under `/home/ben/.jcode/scratch/section10-core-20260908T1404-lcOPkW/` and were not rewritten. Original implementation handoff is section-10-core-handoff.md.

## Boundary and next work

The migration adapter uses actual Rooms/AppearancePalette constants and preserves raw provenance. SettingsArchive now admits the validated nested appearance string only in schema /2. No Activity migration, preference writes, editor, preview, runtime transitions or appearance recovery wiring were added by this gate. Those follow the already committed section-10-runtime-contract.md.

Neither APK was installed. The gate does not prove Android preferences durability, Compose callback order, focus pixels, large-font layout, audio continuity or device appearance. The combined working-source gate is not the final reviewed clean dual-APK freeze.
