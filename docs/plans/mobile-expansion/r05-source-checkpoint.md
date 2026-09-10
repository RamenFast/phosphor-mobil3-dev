# R05 HDR request path: source checkpoint

2026-09-10. Grok OAuth implementation and independent Grok OAuth code review.

## Outcome

Portable `hdr_requested` default off. REQUEST HDR chip. Vulkan+FP16+API34 selector. Live format change rebuilds the composite pipeline and retained presenter. HOLD packs transfer 2.0 on FP16. Linear shader path skips gamma and 8-bit dither. ndk `nativewindow` feature enabled.

- 949 JVM tests, lint, dual APK, checkEngine at the reviewed APK `5bb32536416a29e91b848b694dec9c5ddca0a489e1e6517d4fd0123387c2e568`.
- Code: attempt 1 **7/10 FAIL** (F1 format/pipeline split). Attempt 2 **8/10 PASS**.
- Production status never claims active HDR. Dataspace, matching present, and panel nits remain unproven.

Sibling `phosphor` render-gpu transfer packing is part of this slice.

## Not done

Device proof, HUD/PiP HDR, HOLD SDR↔HDR appearance matrix, physical luminance. Screenshots are not HDR.
