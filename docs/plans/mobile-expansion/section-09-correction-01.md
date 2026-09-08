# Section 9 correction 01: final gesture direction and presentation custody

Written before runtime corrections on 2026-09-08. Baseline mobile855ae09, shared0ffd658. Maple's pinned374326f review remains unchanged.

## Required behavior

A short downward pull followed by an upward release must return SETTINGS, not dismiss it. Velocity may use only the current uninterrupted direction segment. Equal-position release samples must not erase the last movement direction. The separate192dp deliberate-distance rule remains unchanged.

Worked adapter trace: down0dp/0ms, header80dp/40ms, reverse to70dp/50ms, release70dp/50ms. Expected RETURN, despite the earlier positive100ms average. A subsequent slow1dp downward movement cannot borrow the first segment's speed. A genuinely renewed fast downward segment can qualify independently.

The screen-owned settings presentation state must be remembered before the hidden and PiP early returns. Expansion and scroll survive those branches while the containing Activity composition remains alive. This does not promise process-death restoration or preserve an open sheet over PiP.

## Checks and boundaries

- Exercise the actual production SettingsGestureAdapter with short reversal, slow renewed movement, renewed downward flick and unchanged long-distance behavior.
- Retain all existing owner/adapter fixtures. Add a source-order assertion for the actual Compose lifetime placement. Android callback and recomposition behavior still need device acceptance.
- Run the released frozen Android dual-APK/unit/lint/native/GPU/boundary gate. Hash both sources before and after.
- No device actions, source/audio changes, preference writes or changes to other sheets' dismissal rules.
- Inherited child-control accessibility is a separate correction within section9. These two repairs do not close R11 by themselves.

Blocked outcome: report any failing production test or Android gate without claiming gesture or hardware acceptance. Preserve the review and failing evidence before correcting.

## Observed integration, 13:33 UTC

Frozen gate3294511d5q passed in78.01s. It ran726 JVM tests across62 suites,126 native tests and three offscreen GPU tests. Android dual-APK compilation, lint, engine/helper and source boundary gates passed. Both repository manifests and path sets remained unchanged. The four new adapter/lifetime cases passed. No APK was installed and no Android gesture or recomposition acceptance ran.

Evidence prefix: `dev/scratch/mobile-expansion-20260908T001819Z/settings-direction-integration-1332`.

| Artifact | SHA256 |
| --- | --- |
| Runner | `a13323cf8becc96d9cf9c25b604bd30167f1d4be275627259903a2f329b6afec` |
| App APK | `f3cf692f7e0e07554a8d3eaa0d3fbff0891f084af52fb0df4676263731a8bc62` |
| androidTest APK | `d5c0f7a2c99f302b32f4ea4c16a73c0edc7744dacf505d14d21ac64a8e66a901` |
| JVM results archive | `413483ddcd47fb7fed5daa5091bc1763d75b938d8e5faba3917387c2f6778010` |
| Mobile source manifest | `a4db73822981c7142994e5ef833cb66532a525f54c702399122735625475bdca` |

This is working-source integration evidence on855ae09 plus the four correction paths, not a final reviewed clean install freeze. The independent374326f review is not rescored here.
