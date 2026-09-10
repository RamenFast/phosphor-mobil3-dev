# R05 code review, attempt 1 of 4

- Tree: `phosphor-mobil3` HEAD `00f6154f91907a0de2accb34db9e781926332054` plus dirty HDR slice.
- Sibling: `phosphor` HEAD `0ffd658d7f19e68180c2720e0500b23644619e90` plus dirty `crates/phosphor-render-gpu` `lib.rs` and `shaders.wgsl`.
- Freeze file: `dev/scratch/r05-implementation-20260910/source-hdr.sha256`.
- All 11 freeze hashes matched the working tree. This reviewer holds no freeze.
- Sibling shader/lib and `HdrPresentationPolicyTest.kt` are outside that freeze. They were read as named slice and wiring evidence.
- APK SHA-256 `7663517fe35c5ede1ce6ed171855ee630edca1da06f3453bf2f06c183e5db25d` is recorded, not device-accepted.
- Reviewer: Grok code quality only. No visual-taste or physical-nit judgment.
- Score: **7/10**.
- Gate: Ben accepts >= 8/10. Attempt 1 does not pass. One output-owner correction is required, then independent round 2.

This is source code-quality review of the named HDR slice. It is not device, luminance, or beauty acceptance.

## Requirements that hold

These are required behavior. They are not defects.

- `hdr_requested` is schema/2, `v2Only`, missing-key default off. Export writes only present prefs. Active facts are not stored. Instrument and appearance sources do not contain the key.
- Request-on is not treated as success. The chip is named REQUEST HDR. `choose_output` reason is `attempt_linear`. `HdrPresentationPolicy.reason` else-branch is "attempt linear HDR". `activeLabel` "linear HDR configured" stays unwired. Production `reason(on, false, SDK_INT, false)` never claims active HDR.
- Transfer 0/1 packing is unchanged: offscreen `output_transfer = 0.0`, sRGB surface `1.0`. Shader dither `select(..., hw_encode >= 1.5)` is identity below 1.5. Manual gamma remains `hw_encode < 0.5`. Sibling golden files are not dirty.
- Brightness pin is independent. Separate key, setter, restore, and chip. `applyBrightnessPin` and `screenBrightness` are unchanged.

Inherited, not rerun: parent 949 JVM tests 0 fail, lint, dual APK, checkEngine.

## Material findings

### F1. `configure` may pick `Rgba16Float` without owning the compiled destination

**Priority: high. Contract:** typed per-destination output. Same-device format change needs a compatible composite pipeline and a format-keyed retained presenter. Evidence: source, high confidence.

`choose_output` may set `use_fp16` for requested + Vulkan + API>=34 + `Rgba16Float` in caps. `configure` then selects `Rgba16Float`. That pick is in this slice. The live owner is not.

- `Gpu.format` is stored only on first GPU init.
- Later `bring_up` calls `configure`, then only `GpuRenderer::resize`.
- `resize` reallocates energy. It does not rebuild `composite_pipeline`.
- `output_transfer` is assigned only in `new_for_surface`.
- `set_hdr_requested` stores atomics and sends `Cmd::DisplayDirty`. It does not reconfigure.
- `retained_presenter` is `get_or_insert_with` once and is never cleared.
- `present_retained` overwrites `data[24]` as `presenter.format.is_srgb() ? 1.0 : 0.0`. An FP16 destination therefore packs transfer 0, so HOLD applies manual gamma and 8-bit dither onto a linear surface.

Default-off then REQUEST HDR then a later `SurfaceCreated` (rotation, HUD, recreate) can present FP16 with an SDR pipeline. First-surface FP16 from a saved true request is live-coherent via `new_for_surface`, then HOLD still packs 0/1.

This is a reachable format/encoding split introduced by the new pick, not a dataspace-claim issue.

**Smallest correction:** one output owner. Do not select a format other than the compiled composite target until same-device pipeline replacement exists, or implement that replacement now. Key or rebuild the retained presenter by destination format. Pack `output_transfer` from the live destination, including `2.0` for linear FP16. Drive configure/rebuild from the HDR request, not only `DisplayDirty`. Keep request/attempt/SDR status until dataspace and matching present exist.

## Limits

No product source, build, device, Git, or network work. This critique file is the only write. No freeze is held.

## Disposition

Reject attempt 1 at **7/10**. Release: fail. Round 2 reviews the F1 output-owner correction only. The four named must-nots still have to hold.
