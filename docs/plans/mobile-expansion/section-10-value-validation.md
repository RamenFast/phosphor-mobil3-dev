# Appearance value checkpoint

Section10's [contract](section-10-appearance-contract.md) preceded these pure primitives. `AppearanceValue.kt` implements immutable authored color/style fields, the four curated values, nonmutating contrast checks and visibility/reduced-motion predicates. It does not replace the existing palette, migrate settings, add a picker or start animation clocks.

Actual-source Kotlin2.4.10 compilation and JUnit4.13.2 execution passed eight tests at12:48:28 UTC. Evidence directory: `/home/ben/.jcode/scratch/appearance-value-1248.0RnkM5`. `source.sha256` matched before and after. `dependencies.paths`, `dependencies.sha256`, compiler log, test log and compiled test artifact remain retained. No Gradle, Android, JNI, GPU, device or source action ran through this test.

Tests cover all32 curated text/backplate and essential-boundary contrast checks, known21:1 and1:1 endpoints, ARGB compositing, unchanged low-contrast custom values, all legacy radius values0–64, finite style bounds, immutable copy behavior and all visibility/reduced-motion/work-state combinations. New curated AMOLED keeps black surfaces and zero corners. New Glass uses eased motion, sharp corners and0.62 material alpha. Old palettes and explicit legacy styles remain unchanged.

These are opaque text-backplate checks. They do not prove dynamic Glass readability, transient beam-accent contrast, actual control contrast or Android color composition. Migration, exact codec/schema, collection/editor, persistence/rollback, current-user preservation, complete visibility integration and device acceptance remain open. Do not claim section10 is delivered from these eight tests.
