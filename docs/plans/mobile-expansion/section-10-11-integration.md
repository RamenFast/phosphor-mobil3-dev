# Appearance, Settings and manual integration receipt

2026-09-08,15:15UTC. Coordinator gate405519lhyk passed the released combined working source. This is not a final clean reviewed freeze or device acceptance.

-871 JVM tests across73 suites passed, including actual Android/Compose compilation.
-126 native host tests and3 offscreen public GPU retention tests passed.
-Android lint, both debug APKs, engine check, release-helper build and production source boundary passed.
-Mobile and shared inventories matched before/after. Shared remains0ffd658d7f19e68180c2720e0500b23644619e90.
-No installation, device actions or Android callback/pixel acceptance occurred. Host inventory15:14 found only the S25 wireless transport, no ASUS. S25 was not operated.

## Exact retained artifacts

Private prefix: `dev/scratch/mobile-expansion-20260908T001819Z/appearance-runtime-manual-r02`.

| Artifact | SHA256 |
| --- | --- |
| runner `run-appearance-runtime-manual-r02.sh` |0cf6646452e63e2771c8acaf7c5c847006ffd4ebd6e896931fff67fc6d62f42e|
| app APK |be540e93204cb8cac705465687199f9468492462f8fa60d266f9887933ffa2bc|
| androidTest APK |0c706903bf4d4098b0cab6034de1812fe94e5f6bd514786e5c9c7cd4e3cb98e9|
| JVM result archive |4e0d2c6479dd183a63f67bb9b610ef95bd0d988ae10a6143af5a6f63668fd42c|
| mobile source inventory |e2ab65282095ea3eee038203dec823fbb286bd7925bc22993dc4ae5adff2e854|

Ant explicitly released all source/build ownership at15:05UTC and was stopped after receipt verification. All36 released source hashes and the complete immutable evidence manifest matched. Its private report SHA256 isf860c92f45847edcf40e57d8e325851fba3a779d98c3e568568780d575fbccaa, manifestfcc61a79fefafea340aff4f0c532466b2c5413ae4bfea58408f6f01afdbb74f3. Original host116/syntax evidence remains separate from this coordinator Android gate.

The first combined gate156041kxzn failed three obsolete source-form assertions and one prose scanner match. Its JVM archive is retained with SHA2564f4be40841289de18ed578018a2e600dd3591618c75201bf5ffc3ff529847d4f. Correction01 documents each diagnosed update. No production scanner exemption or test deletion was used.

## Remaining checks

Independent full appearance review, next full Settings review, and manual review remain required. Real migration/update preservation, preview/close/recovery, Glass readability, focus/touch ordering, theme changes, manual navigation and turtle pixels need the authorized phone. Source review and compilation do not close those outcomes. Preserve original R16/R17 accepted source-review scores and all device limitations.
