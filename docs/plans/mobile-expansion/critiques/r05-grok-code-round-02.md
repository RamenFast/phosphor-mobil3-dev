# R05 code review, attempt 2 of 4

- Tree: `phosphor-mobil3` HEAD `00f6154f91907a0de2accb34db9e781926332054` plus dirty HDR slice after the F1 correction.
- Sibling: `phosphor` HEAD `0ffd658d7f19e68180c2720e0500b23644619e90` plus dirty `crates/phosphor-render-gpu` `lib.rs`, `retained_frame.rs`, and `shaders.wgsl`.
- Freeze file: `dev/scratch/r05-implementation-20260910/source-hdr.sha256`.
- All 11 freeze hashes matched the working tree. This reviewer holds no freeze.
- Sibling shader/lib/retained_frame and `HdrPresentationPolicyTest.kt` sit outside that freeze. This review read them as named slice and wiring evidence.
- APK SHA-256 `5bb32536416a29e91b848b694dec9c5ddca0a489e1e6517d4fd0123387c2e568` is a recorded hash, not device acceptance. It matches `dev/scratch/r05-implementation-20260910/app-hdr.apk`.
- Reviewer: Grok code quality only. No visual-taste or physical-nit judgment.
- Score: **8/10**.
- Gate: Ben accepts >= 8/10. Attempt 2 passes. F1 is closed.
- Historical: attempt 1 = **7/10**, unchanged.

This is source acceptance of the output-owner correction. It is not device, luminance, or beauty acceptance.

## Diff versus attempt 1

Portable `hdr_requested` still uses schema/2, `v2Only`, and a missing-key default of off. Export still writes present prefs only. The chip is still named REQUEST HDR. Production `reason(on, false, SDK_INT, false)` still never claims active HDR. Transfer 0/1 packing and dither identity below 1.5 still hold. The brightness pin stays independent. Those must-nots still hold. They are not fails.

The F1 owner did change. `GpuRenderer` stores `output_format` and `output_transfer`. `new_for_surface` packs transfer 2.0 for `Rgba16Float`, 1.0 for sRGB, 0.0 otherwise. `SurfaceCreated` resizes only when `r.output_format == a.config.format`. A mismatch drops `retained_presenter` and the renderer, then rebuilds with `new_for_surface`.

`set_hdr_requested` sends `Cmd::HdrChanged`, not `DisplayDirty`. `HdrChanged` reconfigures the live surface. Format change clears `retained_presenter` and rebuilds `GpuRenderer`. HOLD `present_retained` packs `data[24]` from the presenter destination, including 2.0 for `Rgba16Float`. `retain_frame` records `RetainedTransfer::LinearScRgb` when `output_transfer >= 1.5`. Shader dither stays `select(..., hw_encode >= 1.5)`, identity below 1.5. Sibling goldens are not dirty. Parent inherited checkEngine, lint, and assemble. The APK hash is the post-correction build, not the hash recorded in attempt 1.

## F1 closed

Attempt 1 failed because `configure` could pick `Rgba16Float` while the live owner still resized an SDR `composite_pipeline`. That path never cleared `retained_presenter`. HOLD packed transfer 0/1 from `is_srgb()`. HDR arrived only through `DisplayDirty`.

Current `SurfaceCreated` owner:

```
let same = renderer.as_ref().is_some_and(|r| r.output_format == a.config.format)
if same { resize } else { retained_presenter = None; renderer = None }
if renderer.is_none() { new_for_surface(..., a.config.format) }
```

`resize` still reallocates energy only. Format mismatch no longer calls it. `output_format` comes from `GpuRenderer::build` as `composite_format`. `new_for_surface` passes the surface format there.

Current live request owner: `set_hdr_requested` stores the atomics and sends `HdrChanged`. That command calls `configure` on the live surface, writes `g.format` from the new config, and on format change clears `retained_presenter` then `new_for_surface` for `a.config.format`. Field copy covers focus, persistence, grid, scale, theme, and scope alpha. The next live frame writes grid spacing and angle from the computer.

Current HOLD packing: `present_retained` writes `data[24] = 2.0` when `presenter.format == Rgba16Float`, else 1.0 for sRGB, else 0.0. After those clears, the presenter rebuilds via `get_or_insert_with(|| r.retained_presenter(a.config.format))`. LIVE `composite_data` packs `self.output_transfer`, so FP16 also skips manual gamma (`hw_encode < 0.5`) and 8-bit dither (`hw_encode >= 1.5`).

Default-off then REQUEST HDR then a later `SurfaceCreated` now rebuilds the compiled destination before present. First-surface FP16 from a saved true request remains live-coherent via `new_for_surface`. HOLD no longer packs 0 onto that destination.

## Evidence classes

This review verified freeze files after hash match. Dirty sibling GPU and the policy test are wiring evidence only.

- `hdr_requested` is schema/2, `v2Only`, missing-key default off. Export writes only present prefs. Active facts are not stored. Instrument and appearance sources do not contain the key.
- Request-on is not treated as success. The chip is named REQUEST HDR. `choose_output` reason is `attempt_linear`. `HdrPresentationPolicy.reason` else-branch is "attempt linear HDR". `activeLabel` "linear HDR configured" stays unwired. Production `reason(on, false, SDK_INT, false)` never claims active HDR.
- Transfer 0/1 packing is unchanged for SDR: offscreen `output_transfer = 0.0`, sRGB surface `1.0`. Shader dither `select(..., hw_encode >= 1.5)` is identity below 1.5. Manual gamma remains `hw_encode < 0.5`. Sibling golden files are not dirty.
- Brightness pin is independent. Separate key, setter, restore, and chip. `applyBrightnessPin` and `screenBrightness` are unchanged.
- Format mismatch rebuilds `GpuRenderer` via `new_for_surface` and clears `retained_presenter`. `HdrChanged` reconfigures the live surface.

Inherited, not rerun: parent checkEngine, lint, assemble.

Unobserved: dataspace readback, compositor pair, panel nits, S25 present, transparent HDR, PiP HDR.

## Material findings

None on the inspected correction source.

## Residual notes

These are not attempt-2 fails.

R1. `HdrChanged` keeps the old `GpuRenderer` alive while `new_for_surface` allocates. `SurfaceCreated` drops first. A rebuild error after `surface.configure` would leave the new format with the old pipeline. Same-size rebuild is the common path. The attempt-1 recreate path drops first and acks failure if rebuild fails.

R2. Live HDR format change replaces energy textures. The decay trail is lost. HOLD still presents frozen energy with destination-owned transfer.

R3. `retained_presenter` is cleared at the two format-change owners. It is not keyed by format. Size-only reuse is pre-existing.

R4. No executed test constructs the `output_format` mismatch or HOLD packing of 2.0. `choose_output` and policy tests cover request gates only. This review verified the production seams by reading freeze and sibling source.

R5. `choose_output.transfer` is unused in packing. The live destination format owns `output_transfer`. `Transfer::ManualSdr` is never returned.

## Requirement map

| Review seam | Result |
| --- | --- |
| Typed per-destination output | **Pass.** F1 closed. Resize only when `output_format` matches. Mismatch rebuilds via `new_for_surface`. |
| Retained presenter vs destination | **Pass.** Format change clears it. HOLD packs 0/1/2 from presenter format. |
| HDR request drives configure | **Pass.** `HdrChanged` reconfigures the live surface. Recreate still reads the atomics in `configure`. |
| Request/attempt/SDR status | Pass. Production status never claims active HDR. |
| Transfer 0/1 and goldens | Pass. FP16 adds 2.0. SDR packing and dither below 1.5 are unchanged. |
| Portable `hdr_requested` | Pass. schema/2, `v2Only`, missing default off, present-prefs export. |
| Brightness pin independent | Pass. Separate key and window override. |

## Limits

No product source, build, device, Git, or network work. This critique file is the only write. This reviewer holds no freeze. Hardware-unavailable dataspace and panel branches were not scored as fails.

## Disposition

**8/10. Pass.** Preserve attempt 1 = 7. Source-accept the F1 output-owner correction. Do not treat this as device or APK acceptance.
