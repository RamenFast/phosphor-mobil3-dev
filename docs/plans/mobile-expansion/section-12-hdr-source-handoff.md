# SECTION12 / R05 true HDR implementation handoff

## Outcome and boundary

**Result: the installed, version-matched wgpu Vulkan backend can express the required Android FP16 extended-linear-scRGB surface. There is no source-proven wgpu color-space-expression blocker.** Its surface capability filter and swapchain creation agree on the exact format/color-space pair. This finding is stronger than assuming FP16 implies HDR, but it does not establish support on the S25.

**Acceptance remains open.** No target surface capability, compositor dataspace, layer range metadata, current headroom, transparent HDR, PiP HDR, or physical panel output was measured in this task. Public SDK definitions expose the required requests and observations, but not Samsung's implementation or the Vulkan-loader-to-SurfaceFlinger path. This is a source handoff, not an HDR implementation, runtime receipt, or review score.

Research started 2026-09-09 at 01:55:57 UTC. Source discovery stopped at 02:05:41 UTC. The final receipt records report completion and release. Budget: 45 research source files, 25 minutes, release before 02:22 UTC. Only immutable Git objects, installed cached source, and Android SDK public definitions were inspected. No mutable Activity/UI source was read. No product edit, Git mutation, build, test, Android/JNI execution, GPU/device/ADB/GUI/audio/network/service operation, Python, or worker occurred. `javap -public -constants` only inspected the installed SDK stub JAR.

The readset includes 40 research artifacts and six mandatory governance/skill files. The 46 total entries include those instructions, SDK definitions, and documentation, not 46 product source files. One earlier brightness-contract locator is included for completeness. All source identities use shell SHA-256. No registry archive integrity or device state verification is implied.

## Pinned evidence conventions

- `M:` means mobile `/home/ben/Dev/ClaudeWorkspace/phosphor-mobil3` at `4f285a6fc705c3ba3e81a91678c18a5f16158d4f`.
- `S:` means shared `/home/ben/Dev/ClaudeWorkspace/phosphor` at `0ffd658d7f19e68180c2720e0500b23644619e90`.
- `C:` means `/home/ben/.cargo/registry/src/index.crates.io-1949cf8c6b5b557f/`.
- `SDK:` means `M` working-directory location `.toolchain/Sdk/platforms/android-36/`, read as installed public definitions, not Git source.
- `NDK:` means `.toolchain/Sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/android/`.
- All line references are one-based file lines. JAR signatures have class/method identities rather than invented source line numbers. `SDK:data/api-versions.xml` supplies line-addressable introduction levels.
- `readset.tsv` records exact paths, revision origins, and hashes. It does not claim every line in each file was read.

`M:rust/Cargo.lock:1695-1698,1724-1727,1783-1786,1832-1835` pins wgpu 27.0.1, core 27.0.3, hal 27.0.4, and types 27.0.1. The inspected cache directories match these versions. Ash is 0.38.0+1.3.281 at lines 53-56. NDK Rust wrapper 0.9.0 appears at lines 734-737, but its implementation was not inspected.

## 1. Canonical contract, not a reduced substitute

`M:MOBILE-EXPANSION-PLAN.md:237-244` requires actual FP16 linear-scRGB capability probing, explicit shared output mode, linear HDR, controlled emission above SDR reference white, unchanged SDR, safe owner/display/held transitions, truthful requested/active state, independent transparent-HUD checks, and a bounded native alternative if FP16 fails.

`M:MOBILE-EXPANSION-PLAN.md:322,330` requires real compositor and panel evidence plus SDR, HOLD/BLACK, HUD and HDR recreation regression. Screenshots alone cannot prove luminance. `M:spec/EXPANSION.md:118-134` preserves the same boundary.

`M:docs/plans/mobile-expansion/section-05-hold-contract.md:11-25,41-45` establishes:

1. Capture exact GPU energy and frozen composite uniforms for the application present submission.
2. Acquire before changing history. Commit only after application `present()` returns.
3. Keep retained resources independent of surface lifetime and destructive resize.
4. Preserve committed, candidate, and concurrently pinned ownership with checked allocation bounds.
5. Recompose destination-owned background opacity from frozen emission. Never fade an opaque retained rectangle.
6. Preserve source extent, energy format, and transfer provenance. HDR conversion belongs to R05.
7. Do not perform GPU work without a visible presentation owner. Paused redraw is damage-driven.

`M:docs/plans/mobile-expansion/section-13-brightness-contract.md:9` separates brightness override from HDR. Do not change `screenBrightness`, beam-energy tuning, auto-gain, or global brightness to manufacture HDR evidence.

## 2. Exact wgpu format/color-space proof

| Evidence | Exact behavior | Meaning |
|---|---|---|
| `C:wgpu-hal-27.0.4/src/vulkan/instance.rs:293-296,325-345` | Requests Android surface extension and `VK_EXT_swapchain_colorspace`, retaining only available instance extensions. | The non-sRGB color-space extension is not desktop-only. Availability is still runtime-dependent. |
| `C:wgpu-hal-27.0.4/src/vulkan/adapter.rs:2638-2657` | Calls `get_physical_device_surface_formats` and filters each raw pair through `map_vk_surface_formats`. | The public format list is derived from actual Vulkan format/color-space pairs for that surface. |
| `C:wgpu-hal-27.0.4/src/vulkan/conv.rs:161-183` | Maps `R16G16B16A16_SFLOAT` with `EXTENDED_SRGB_LINEAR_EXT` to `Rgba16Float`. Other FP16/color-space combinations are not exposed. | On this Vulkan backend, `caps.formats.contains(Rgba16Float)` proves that exact pair was enumerated. It does not prove display luminance. |
| `C:wgpu-hal-27.0.4/src/vulkan/device.rs:506-532` | Chooses `EXTENDED_SRGB_LINEAR_EXT` when `config.format == Rgba16Float`, otherwise `SRGB_NONLINEAR`. Passes it to `.image_color_space(color_space)`. | Selecting FP16 through the supported public configuration requests linear scRGB at swapchain creation. |
| `C:wgpu-hal-27.0.4/src/vulkan/device.rs:527-554` | Builds `SwapchainCreateInfoKHR` and calls `create_swapchain`. | This is a real WSI request, not an unused constant or shader-only claim. |
| `C:wgpu-types-27.0.1/src/lib.rs:5705-5721,5741-5793` | Public capability/configuration exposes format, alpha, usage, modes and dimensions, but no independent swapchain color-space field. | The proof depends on this pinned backend mapping. It is not a general wgpu guarantee for all backends or future versions. |

**Implementation selector:** inspect the chosen adapter backend. Accept the source-derived pair inference only for `Backend::Vulkan` with this dependency identity. Require that the actual current surface's capability list contains `Rgba16Float`. Configure that format. Keep format selection and output-transfer selection as separate typed decisions.

`M:rust/src/render.rs:1022-1033` currently uses `Instance::default()` and logs adapter information. It does not force Vulkan. Do not label an arbitrary backend's float target as this proven Vulkan path. Preserve normal SDR fallback instead of forcing the entire application to fail on a non-Vulkan adapter.

**Do not substitute:** `Rgb10a2Unorm` is admitted in `conv.rs:175` only with `SRGB_NONLINEAR`. It is not an HDR10/PQ substitute. An offscreen `Rgba16Float` texture also does not establish a presentation color space.

## 3. Android window, surface, dataspace, and headroom

### 3.1 Public API map

| API | Minimum API | Evidence and correct role |
|---|---:|---|
| `Window.setColorMode(ActivityInfo.COLOR_MODE_HDR)` | 26 | `SDK:data/api-versions.xml:73273,73344`, `ActivityInfo` JAR constants: DEFAULT=0, WIDE_COLOR_GAMUT=1, HDR=2. Request full-app window HDR while requested/eligible. Restore DEFAULT for the SDR path. These are enum alternatives, not OR-able flags. |
| `WindowManager.LayoutParams.setColorMode(int)` | 26 | `api-versions.xml:73727,73739`. The overlay owns LayoutParams, not an Activity Window. Apply only to the HUD's own window if its path is being negotiated. |
| `SurfaceView.getSurfaceControl()` | 29 | `api-versions.xml:71607`. Candidate public handle for per-layer range transactions. Retain no control across surface destruction or a new lease generation. |
| `SurfaceControl.Transaction.setExtendedRangeBrightness(control, currentBufferRatio, desiredRatio)` | 34 | `api-versions.xml:71503` and public JAR signature. Native equivalent and parameter semantics below. |
| `SurfaceView.applyTransactionToFrame(transaction)` | 34 | `api-versions.xml:71600`. Candidate seam to synchronize metadata with a newly rendered buffer. SDK signatures alone do not establish ordering against this app's external Vulkan producer or HOLD with no next frame. Verify before relying on it. |
| `Window.setDesiredHdrHeadroom(float)` | 35 | `api-versions.xml:73352`. Window request, not measured headroom. |
| `SurfaceView.setDesiredHdrHeadroom(float)` | 35 | `api-versions.xml:71610`. Surface request. Do not confuse it with `currentBufferRatio`. |
| `SurfaceControl.Transaction.setDesiredHdrHeadroom(control, float)` | 35 | `api-versions.xml:71501`. Native docs explain interaction with extended-range brightness. |
| `Display.getHdrCapabilities()` and `HdrCapabilities` types/luminance fields | 24 | `api-versions.xml:69872,69923-69928`, public JAR. Advertised metadata, not active compositor/panel evidence. `INVALID_LUMINANCE=-1.0f`. |
| `Display.isHdr()` / `isWideColorGamut()` | 26 | `api-versions.xml:69898,69902`. Capability observations, not the app's negotiated output. |
| `Display.isHdrSdrRatioAvailable()`, `getHdrSdrRatio()` | 34 | `api-versions.xml:69873,69899`. Availability-gated headroom observation. Preserve unavailable as unavailable. Validate finite ratios before renderer use. |
| `Display.registerHdrSdrRatioChangedListener(executor, Consumer<Display>)`, unregister counterpart | 34 | `api-versions.xml:69903-69904`. Observe only the current owner's display. Unregister on retirement/display replacement. |
| `Display.getHighestHdrSdrRatio()` | 36 | `api-versions.xml:69875`. Separate upper-range observation, not current available range or panel luminance. SDK signatures do not document all refresh semantics. |
| `ANativeWindow_setBuffersDataSpace`, `getBuffersDataSpace` | 28 | `NDK:native_window.h:201-228`. Setter applies to subsequently queued buffers and can fail with `-EINVAL`. Getter can report UNKNOWN or `-EINVAL`. |
| `ANativeWindow_getBuffersDefaultDataSpace` | 34 | `NDK:native_window.h:230-238`. Reports consumer default, not necessarily producer/current buffer dataspace. |
| `SurfaceControl.Transaction.setDataSpace(control, int)` | 33 | `api-versions.xml:71500`. Native `ASurfaceTransaction_setBufferDataSpace` exists at API29, `surface_control.h:593-602`, with sRGB default when unset. This is not permission to retag incorrectly encoded pixels. |

`SDK:data/res/values/attrs_manifest.xml:3404-3413` explicitly says the desired activity color mode may be ignored depending on display capabilities. `:1067-1068` defines color-mode configuration changes. This proves a public window request exists. It does not prove that the request is necessary or sufficient for a separately composed Vulkan SurfaceView. Scope the candidate request to the actual owner and verify its effect. A permanent `android:colorMode="hdr"` declaration is not the minimal toggle implementation because the canonical default is off.

### 3.2 scRGB numeric contract

`NDK:data_space.h:392-415` defines extended range and `ADATASPACE_SCRGB_LINEAR = 406913024`. It uses BT.709 primaries, linear transfer, and extended range. Encoded `(1,1,1)` denotes D65 sRGB white at 80 nits in this dataspace definition. Values above one can represent HDR. `ADATASPACE_SCRGB` at lines 434-447 is gamma-encoded and is the wrong label for linear shader output.

This numeric definition does not measure the phone at 80 nits or guarantee a particular actual SDR white after composition. Keep encoding reference white, requested output peak, display-reported ratio, and measured physical luminance as separate values.

### 3.3 The easy-to-miss layer range contract

`NDK:surface_control.h:630-680` documents `ASurfaceTransaction_setExtendedRangeBrightness`:

- It applies to layers with `RANGE_EXTENDED` dataspace.
- `currentBufferRatio` describes the buffer's encoded peak HDR brightness divided by its target SDR white. It is not merely a copy of the display's current available ratio.
- `desiredRatio` requests the intended range. Android may choose not to provide it.
- Both values must be finite and at least one.
- Defaults are one. The docs say default desiredRatio indicates extended range is not being used, with the resulting behavior otherwise determined by dataspace.
- PQ/HLG encode their own ranges and use different metadata semantics.

`NDK:surface_control.h:682-719` prefers this extended-range API for formats carrying an HDR/SDR ratio, while desired-headroom API fits formats such as HLG/HDR10. The two desired-range requests override each other according to call order. Do not issue conflicting requests from Activity, SurfaceHost, and a frame callback.

**Recommended next candidate:** one owner-scoped Android presentation policy requests the window mode and applies scRGB layer range metadata consistent with the renderer's encoded peak/reference white. The renderer selects FP16/scRGB through wgpu and outputs linear values. Read back native-window dataspace after configure and again after a submitted frame. A hardcoded expected value is not readback.

Do not call `ANativeWindow_setBuffersGeometry`, `holder.setFormat(RGBA_F16)`, or dataspace setters behind a live swapchain as a speculative fix. wgpu's Vulkan WSI owns swapchain buffers. If explicit producer dataspace setting is needed, place it in the serialized negotiation sequence and verify that WSI does not overwrite it. For the preferred path, first observe what the correctly configured WSI actually publishes.

**Exact remaining source gap:** the installed SDK has public definitions and native docs, but no inspected platform implementation proving how `SurfaceView.getSurfaceControl()` reaches the relevant BLAST/buffer layer for these metadata operations, how WSI maps its color space into that layer, or whether transaction-to-frame synchronization covers this external producer. No SurfaceFlinger/Samsung implementation was inspected. Treat these as required feasibility observations, not as an invented universal Android limitation.

### 3.4 Headroom negotiation without a deadlock

A display can report ratio one before the app asks for HDR. Do not use that pre-request value as the sole reason never to request an HDR surface. First use capability/surface evidence to negotiate, then observe current headroom. Keep highest/requested/current ratios separate. Avoid a loop that rebuilds the swapchain for every minor ratio change.

A ratio update normally changes bounded output mapping and damages visible held presentation. It need not replace the surface if its format and ownership remain valid. Use a stable request ceiling rather than chasing transient headroom or global brightness. A value of one means no extra range is currently reported. Unavailable, invalid, and stale-generation readings must remain distinguishable.

On API29-33, scRGB WSI and native dataspace APIs exist, but the inspected public layer range API and ratio observation are unavailable. Do not invent a ratio from advertised maximum nits. Keep a concrete unverified-headroom/metadata fallback until that older path is independently established. API34 adds the key extended-range and ratio APIs. API35/36 additions need explicit guards.

## 4. Current implementation hazards and exact seams

### 4.1 Mobile surface format is locked

`M:rust/src/render.rs:178-193` keeps device/adapter state separate from `Active` and releases Surface before the ANativeWindow reference. Preserve that order.

`configure():976-1010` chooses the first sRGB format only when `Gpu.format` is absent. Once set, lines 985-989 require every later surface to support that retained renderer format. `bring_up():1043` locks it. The current design prevents real HDR toggles and HDR-to-SDR owner fallback.

**Change:** make destination presentation configuration explicit per active surface. Remove the permanent first-format constraint. Do not interpret retained *energy* format as the swapchain format. Decide backend, destination format, output transfer, alpha mode, reference white/range, and fallback reason together.

### 4.2 Format changes require new pipelines, not only resize

`M:rust/src/render.rs:334-367` resizes an existing renderer and only constructs a new renderer on initial startup. Shared `new_for_surface` accepts format at `S:crates/phosphor-render-gpu/src/lib.rs:213-230`. The composite pipeline hardcodes that target format at `:435-442`. Shared `resize():840-871` reallocates energy but does not rebuild the composite pipeline.

Add a narrow same-device surface-output reconfiguration API that rebuilds format-dependent composite resources without destroying live energy or retained frames. Keep offscreen output-resource contracts consistent. Do not rebuild DSP/source state, rewarm the beam, or clear retained history on a color-mode toggle. Keep existing SDR constructor behavior for desktop/offscreen callers.

`M:rust/src/render.rs:617` caches a `RetainedPresenter` once. Its shared definition stores destination format at `retained_frame.rs:63-67`. Rebuild or key that cache by destination pipeline format and compatible device/output layout. A global one-time presenter becomes invalid after the first HDR/SDR switch.

### 4.3 Current FP16 output would be wrongly gamma-encoded

`S:crates/phosphor-render-gpu/src/lib.rs:63-66,224,695-715` has only the SDR-oriented `hardware_encodes` boolean. `shaders.wgsl:203-205` manually applies `pow(color, 1/2.2)` whenever that flag is false. FP16 is not an sRGB format, so the current path would gamma-encode the linear-scRGB target.

**Add an explicit output-transfer mode**, distinguishing at least legacy manual SDR, hardware-sRGB SDR, and linear scRGB. Do not lie by setting `hardware_encodes=true` for FP16. Preserve public SDR behavior and exact old shader arithmetic in the SDR branch. Do not fold in an unrelated change from the accepted approximate 2.2 theme decode to another transfer curve.

`shaders.wgsl:179-202` already splits energy-derived glow/flash from theme/grid terms. Put HDR emission mapping there, before any SDR transfer. Keep theme background/grid/Compose chrome at the chosen SDR reference range. Raise only intentional beam emission. Bound finite output and define a controlled peak/shoulder rather than multiplying the whole framebuffer.

`shaders.wgsl:207-221` implements 8-bit dither and premultiplied alpha. Keep SDR dither unchanged. FP16 should not inherit 1/255 SDR dither without a deliberate justification. Keep exact black zero. Keep alpha finite in [0,1] and multiply output RGB by alpha once. HDR premultiplied RGB may exceed alpha because its unassociated radiance may exceed one. Clamping all RGB to alpha would destroy HDR.

Worked candidate, not a measured result: with encoded SDR reference white `W=1`, a controlled beam peak `P=2` uses encoded ratio `P/W=2`. Linear output contains core values above one, black stays zero, and SDR chrome is not multiplied by two. The metadata must describe the actual bounded content range. The display may provide less than the requested range.

### 4.4 Retained transfer needs capture provenance and destination conversion

`S:crates/phosphor-render-gpu/src/retained_frame.rs:46-58,71-147` stores same-format GPU energy and `[f32;28]` frozen composite data. This is not an SDR screenshot. `RetainedTransfer` at `:6-9` contains only HardwareSrgb/ManualSdr and is assigned from `hardware_encodes` at `:136-141`.

`present_retained():215-228` copies frozen data, replaces destination scope alpha, then overwrites the hardware-encoding slot solely from destination format. The current transfer tag does not drive a real HDR conversion.

Add captured source presentation provenance: original presentation format, linear-scRGB versus SDR transfer, source reference white, captured emission range/mapping inputs, and any mapping version needed to reproduce the held image. Keep it immutable with the frame. Destination policy chooses conversion, not new live tuning.

| Source held frame | Destination | Required behavior |
|---|---|---|
| SDR | SDR | Preserve current byte-equivalent behavior, including manual/hardware encoding distinctions. |
| SDR | HDR | Reproduce the captured SDR radiance at reference white. Do not boost an old SDR frame with the current live HDR gain. |
| HDR | HDR | Reproduce frozen source mapping, adapting only to explicit destination range/reference policy. Preserve image identity, extent, inspection, and captured appearance. |
| HDR | SDR | Reconstruct captured linear HDR radiance from frozen energy/uniforms, then apply a defined bounded HDR-to-SDR tone map before the existing destination SDR encoding. Never gamma-decode the energy texture as if it were a screenshot. |

Select and document a deterministic, hue-preserving shoulder that keeps black zero and avoids washed-out clipping. Apply any nonlinear conversion to unassociated linear color before final alpha multiplication. Keep background/grid policy explicit. Merely disabling the source's HDR multiplier loses the captured HDR mapping and is not a general conversion contract.

The retained shader is assembled from the live composite function at `retained_frame.rs:330-335`. Its textual replacement depends on the exact fragment-function prologue. Update that seam and its validation if the prologue changes. `retained_frame.wgsl:5-13` owns fit/pan/zoom and outside-image alpha. It needs no HDR-specific fork if conversion stays in the common composite.

## 5. Safe reconfiguration sequence

All GPU operations remain on the single render thread. `M:rust/src/render.rs:315-398` already serializes attach/destroy. `M:rust/src/jni_glue.rs:96-151` and `surface_lifecycle.rs` already enforce cancellation and actual retirement acknowledgement. Preserve these barriers. A timeout never proves the driver released the window.

1. Receive an owner/generation-tagged presentation request. Keep requested preference separate from acknowledged active state.
2. Stop acquisition at a frame boundary. Drop every old SurfaceTexture, derived view, and encoder reference before configuring.
3. Prepare same-device format-dependent pipelines/resources under error scopes. Preserve committed/pinned energy and inspection.
4. Query current surface capabilities again. Choose HDR or an explicit SDR fallback with compatible alpha and present mode.
5. Apply the owner-scoped window/layer negotiation in a coordinated order. Reject stale Android callbacks and range metadata.
6. Configure the surface. Collect validation/internal/allocation failures before treating configuration as usable.
7. On failure, explicitly configure a supported SDR destination or retire presentation. Do not assume the old swapchain survived.
8. Update the live renderer's destination mode and invalidate the retained presenter cache where needed.
9. Render live or the existing held frame once. Keep BLACK RGBA=(0,0,0,1), including transparent HUD.
10. Publish source-accurate configuration/submission/dataspace/headroom observations for that generation. Keep acceptance of physical HDR separate.

Supporting evidence:

- `C:wgpu-27.0.1/src/api/surface.rs:79-108`: configure waits for idle, concurrent submission can cause validation errors, and an old live SurfaceTexture or unsupported/zero-area configuration can panic.
- Public configure returns `()`, not Result. Its wrapper stores a config clone at `:93-97`.
- `C:wgpu-27.0.1/src/backend/wgpu_core.rs:3787-3797`: core configure errors route through the device error sink. `:271-326` maps validation, internal, and allocation classes.
- `C:wgpu-core-27.0.3/src/present.rs:70-143`: unsupported format/alpha/mode, old output, zero/oversized extent, and GPU wait timeout are named errors. Use error scopes around the actual call, not a fabricated successful Result wrapper. Restore all scopes on every path.
- `C:wgpu-hal-27.0.4/src/vulkan/instance.rs:1037-1049` takes and releases the old swapchain before building the new one. `device.rs:552-562` destroys the old swapchain before propagating create failure. Keeping the old Rust config is not rollback.
- `M:rust/src/render.rs:595-610` already bounds held recovery to one retry before another real event. Preserve bounded retry and damage-driven paused redraw.
- `M:rust/src/render.rs:901-922` captures and commits retained history around the same application submission. Do not change that ordering for HDR.

Prefer the existing device across owner/display transitions so retained GPU snapshots remain valid. If a new display is incompatible with the current adapter or the device is lost, do not use its textures on another device. Report the actual presentation/history limitation. Cross-device retained transfer is not provided by this source and must not become an implicit CPU-readback feature.

## 6. Transparent HUD, PiP, and truthful state

`M:app/src/main/kotlin/dev/phosphor/mobil3/SurfaceHost.kt:29-32,46-68` requests transparent/opaque holder format, sets transparent Z order, attaches native output, and falls back to SOLID when native alpha cannot support transparency. Native return values currently mean only -1 unavailable, 0 solid, 1 premultiplied transparency. `PhosphorNative.kt:17-22` documents that contract.

Do not overload return value 1 to also mean HDR. Add an owner-tagged structured presentation observation or a separate bounded HDR status seam. Distinguish requested HDR, backend, destination format, source-derived Vulkan color space, observed dataspace, negotiated alpha, app-submission success, range metadata, headroom availability/value, and fallback reason. Do not store active capability facts in portable archives.

`SurfaceHost.kt:113-135` gives HUD preference over activity presentation and retires hidden owners. Scope all HDR transactions and listeners to that same lease. `SurfaceOwner.kt:3-27` establishes main-thread generation fencing and detach-before-successor. Add no second GPU loop.

`FloatingHudService.kt:104-111,143-149` owns an overlay display/window context, its own LayoutParams, and a SurfaceHost. `:272` handles configuration changes. MainActivity's Window settings cannot stand in for this overlay's state.

`MainActivity.kt:898-915` receives PiP/configuration changes. `:1442-1453` constructs the activity SurfaceHost. `:1045-1062` retires activity presentation on stop. PiP uses the activity host but changes compositor ownership/geometry context. Re-evaluate HDR and range metadata on PiP entry/exit even without a new Java Surface object.

Recommended fallback precedence:

- Full app: attempt proven Vulkan FP16 pair and verify the Android path.
- Transparent HUD: require independent transparent-HDR evidence. Until then retain requested HDR, use supported SDR premultiplied output, and say why. Do not silently sacrifice genuine transparency to keep an HDR label.
- If no premultiplied output exists, preserve the established SOLID fallback and report alpha and HDR reasons separately.
- PiP: independently negotiate/verify. If unsupported, convert held HDR into SDR and restore requested HDR on return to a supported full-app owner.
- No visible owner: stop GPU output and detach headroom listeners. Do not change audio, brightness, or source state.

Suggested labels distinguish facts: `HDR requested · FP16/scRGB configured · headroom unavailable`, `HDR requested · SDR active: current surface lacks FP16/scRGB`, or `HDR requested · SDR transparent HUD: transparent HDR not verified`. A configured HDR surface with reported ratio 1 should not claim extra panel range. Screenshots and an advertised HDR10/HLG type never produce an acceptance flag.

## 7. Minimal implementation ownership map

This is a proposed next implementation allowlist, not permission to edit during this research. Coordinator must re-pin after the manual writer releases mutable Activity/UI. No new renderer fork or vendored wgpu patch is needed for the preferred path.

### Shared renderer writer

| Exact path under shared repo | Minimal responsibility |
|---|---|
| `crates/phosphor-render-gpu/src/lib.rs` | Explicit output-transfer/range configuration, compatible SDR defaults, format-dependent surface pipeline replacement, frozen uniform data. Keep energy resources and existing desktop/offscreen behavior. |
| `crates/phosphor-render-gpu/src/shaders.wgsl` | Separate linear-scRGB output, controlled beam emission, black/alpha/SDR preservation, common retained conversion. |
| `crates/phosphor-render-gpu/src/retained_frame.rs` | HDR capture provenance, destination conversion data, format-keyed presenter, shared shader assembly contract, unit validation. |
| `crates/phosphor-render-gpu/tests/retained_frame.rs` | SDR baseline and source/destination transfer matrix, immutable held pixels, alpha/extent/resource tests. |

`src/retained_frame.wgsl` is a conditional fifth shared path only if the common conversion cannot preserve its existing inspection entry. No changes are expected in beam/DSP/audio or the desktop app. Preserve existing public SDR constructor usage instead of forcing a desktop migration.

### Mobile presentation writer

| Exact path under mobile repo | Minimal responsibility |
|---|---|
| `rust/src/render.rs` | Per-destination HDR selection, actual Vulkan backend guard, safe configure/fallback, source-accurate status, retained presenter invalidation, output-range commands. |
| `rust/src/surface_policy.rs` | Pure deterministic HDR/SDR plus alpha policy and reason precedence, with host fixtures. |
| `rust/src/jni_glue.rs` | Bounded owner-tagged presentation request/status bridge and native-window dataspace observations. Preserve retirement barriers. |
| `app/src/main/kotlin/dev/phosphor/mobil3/PhosphorNative.kt` | Matching typed/bounded bridge declarations without changing the existing alpha result meaning. |
| `app/src/main/kotlin/dev/phosphor/mobil3/SurfaceHost.kt` | Owner-scoped Android mode/layer metadata, display/headroom listeners, attach/fallback lifecycle and exact state reporting. |
| `app/src/main/kotlin/dev/phosphor/mobil3/HdrPresentationPolicy.kt` (new) | Small pure request/capability/range/state policy, API-level guards and metadata validation. Keep Android resource ownership in SurfaceHost. |
| `app/src/test/kotlin/dev/phosphor/mobil3/HdrPresentationPolicyTest.kt` (new) | Request/active separation, API29/34/35/36 guards, stale-generation events, metadata/range bounds and fallback precedence. |

`rust/src/surface_lifecycle.rs`, `rust/src/pause.rs`, and `SurfaceOwner.kt` are regression dependencies, not default edit targets. They need no HDR-specific ownership rewrite. `rust/Cargo.toml`/lock should not need wgpu changes. Any new direct NDK binding dependency must be justified separately rather than added speculatively.

### Coordinator-only integration after manual-writer release

- `MainActivity.kt`: window request/restore, import/restore, PiP/display/configuration hooks, UI action implementation. Exact pinned seams: `:669-699,872-915,1045-1062,1442-1453,1880-1900`.
- `FloatingHudService.kt`: its own overlay LayoutParams/color mode, status and display transitions. Do not write Activity brightness through this path.
- `ui/ScopeUiState.kt`: requested preference plus transient presentation observations. Existing display/owner state is at `:45-53,118-120`.
- `settings/SettingsArchive.kt`: boolean requested HDR key, default off, strict import/export validation. Its typed allowlist begins at `:65-98`. Exclude active format, metadata, headroom, and measured facts. Do not add HDR to instrument presets.
- `ui/PhosphorScreen.kt` and `ui/Sheets.kt`: Display control/action interface, truthful active/fallback text and accessibility. These paths were confirmed in the pinned tree, but their bodies were not inspected in this research. The coordinator owns precise UI insertion after the concurrent writer releases. `MainActivity.kt:51,72` identifies `ScopeActions` as its action interface. Do not invent a separate `ScopeActions.kt` file.
- Existing/new corresponding archive and UI tests, canonical contract/ledger updates, and manual HDR chapter belong to the coordinator. Do not reopen manual-writer-owned files in parallel.

An implementation writer can complete the shared/native/policy core without owning Activity/UI. Feature delivery still requires the coordinator integration above, not a disconnected toggle or native stub.

### Suggested narrow API shape, not existing symbols

- Add shared `OutputMode::{Sdr, LinearScRgb}` and a validated `SurfaceOutput` value containing destination format, mode, encoded SDR reference white, and bounded peak ratio. SDR still derives manual versus hardware encoding from the target and existing compatibility behavior.
- Add `GpuRenderer::set_surface_output(output) -> Result<(), String>` for same-device format/pipeline changes without reallocating energy or mutating snapshots. Validate that LinearScRgb requires the caller's proven FP16 presentation contract. The shared renderer cannot independently prove an Android swapchain color space.
- Extend `RetainedTransfer` with linear-scRGB provenance. Capture the complete source output mapping. Pass a destination output value to retained presentation instead of inferring transfer from `format.is_srgb()` alone.
- Carry a presentation revision with the owner generation through the mobile request/status seam. A headroom callback changes only the accepted generation's mapping. Source retirement remains the separate R13 authority.
- Define the proposed portable boolean key `hdr_requested`, default false, in the canonical contract before implementation. Keep all negotiated observations runtime-only. Key naming is a proposal, not an assertion that this pin already stores HDR.

These names avoid replacing the existing SDR `hardware_encodes` contract with an HDR lie. They do not require a new engine or generic display framework.

## 8. Requirement-linked checks for the next implementation

None of these checks ran here.

| Requirement | Discriminating check and expected evidence |
|---|---|
| Exact format/color space | Record dependency/source identity, actual adapter backend, actual current-surface formats, selected FP16 format, successful configure, and native-window/compositor dataspace. If raw Vk pair is queried, label it queried. Otherwise label the color-space conclusion source-derived from pinned Vulkan mapping. Never print a constant as queried evidence. |
| Correct linear transfer | A controlled FP16 target contains finite core values above 1 and exact zero black. Demonstrate the HDR branch skips manual SDR gamma. Use a real 8-byte/pixel FP16 readback fixture in the future, not the existing 4-byte SDR helper. |
| Controlled emission and SDR chrome | Compare known black, grid, background, beam-core and glow regions. HDR extends beam range without globally scaling Compose chrome or background. Check opaque and premultiplied cases. |
| Unchanged SDR | Existing manual-SDR/offscreen outputs remain byte-equivalent where promised. Compare hardware-sRGB output with its own baseline. SDR-off output stays unchanged after HDR-on/off and fallback cycles. |
| Layer range metadata | Record actual buffer reference/peak ratio and desired range request, owner/display generation, transaction application, and compositor layer observations. Verify metadata targets the actual visible buffer layer and does not leak into SDR successors. |
| Headroom | Exercise unavailable, invalid, ratio1, ratio>1, changing ratio, API36 highest/current distinction, display replacement, and listener removal. Avoid negotiation deadlock at pre-request ratio1. Advertised nits do not become measurements. |
| Physical HDR | Controlled real-panel SDR/HDR comparison at recorded conditions. Instrumented luminance is needed for numeric nits claims. Screenshots and successful configure are insufficient. Preserve global/window brightness state except the separate user-selected R06 policy. |
| Safe reconfiguration | Toggle and owner/display transitions during live, HOLD, BLACK, and acquisition failure. Assert no stale SurfaceTexture/view at configure, no two swapchains on one window, error scopes drain, and fallback really recreates SDR after HDR creation failure. |
| HOLD transfer matrix | SDR→SDR, SDR→HDR, HDR→HDR, HDR→SDR, then back. Preserve frame identity, frozen mapping, original extent, inspect transform and source transport. Check deterministic tone map rather than washed/clipped reinterpretation. |
| Retained failure discipline | Failed acquire/allocation/configure preserves committed/pinned history. Source stop still invalidates history. Device loss never submits old-device textures into a new device. |
| Genuine transparent HDR | Place contrasting content underneath, verify true holes, premultiplied beam edges, FP16/scRGB layer state and range. Check independent SOLID/TRANSPARENT transitions and explicit SDR fallback. No opaque rectangle fade. |
| BLACK | Opaque `(0,0,0,1)` clear in full app, SDR/HDR, transparent HUD and PiP. HOLD→BLACK→HOLD preserves the frame. |
| PiP and HUD owners | Full app↔PiP↔HUD, rotation, display/context replacement, lock, permission loss, old callbacks and return-to-app. Check generation-tagged metadata/listeners/status and unchanged audio/transport. |
| Idle and resource bounds | No visible owner produces no GPU/animation/listener work. Unchanged HOLD produces no continuous recomposition. Retained committed/candidate/pinned resource count and existing checked 128 MiB per-frame bounds remain enforced, with aggregate footprint tracked. |
| Persistence/import | Clean install off, requested preference survives restart/update/import, active facts do not. Invalid archives fail without partial application. Instrument presets remain HDR-free. |
| Integration | Shared shader validation and retained suites, native policy tests, real Android unit/lint/JNI build/checkEngine and exact packaged source/APK verification. Host fixtures cannot prove Android-only compilation or runtime. Coordinator owns all builds/device gates after this source window. |

Existing test anchors: `S:crates/phosphor-render-gpu/tests/retained_frame.rs:131-204` covers frozen pixels, destructive resize and destination alpha using an SDR destination. `:207-228` covers live reset without retained mutation. `S:crates/phosphor-render-gpu/src/retained_frame.rs:299-345` contains bounds and shader-validation seams. Extend these rather than claiming they already cover HDR.

## 9. Bounded native alternative, only if the target path fails

No alternative implementation is justified by a claimed wgpu-expression failure today, because the required pair is expressible. If the real target fails, retain the exact failure stage first: missing instance extension, raw pair absent, filtered pair absent, swapchain failure, wrong dataspace, metadata mismatch, headroom unavailable, or compositor/panel failure.

A bounded follow-up should inspect at most eight additional source files for at most 30 minutes, with no driver/system changes:

1. Compare raw Vulkan surface pairs against the pinned filter for the same Android window.
2. If raw FP16/scRGB is present but wgpu fails, isolate extension/configuration/lifetime errors before replacing the presenter.
3. If only a valid native HDR pair such as PQ is available, inspect a minimal native Vulkan presenter on the same device, with explicit color-space selection and the required transfer/metadata. The current `conv.rs:166-183` filter does not expose arbitrary PQ pairs, and `device.rs:506-512` cannot select PQ through public format alone.
4. Investigate shared-renderer texture interop and fence/ownership contracts before proposing that native presenter. Do not copy the beam engine, read back live pixels through the CPU, or assume a HAL texture-import API is safe without its source contract.
5. If the problem is Android layer metadata rather than swapchain selection, inspect the public ASurfaceControl buffer/dataspace/extended-range route. Its basic setters are documented at `NDK:surface_control.h:593-719`, but wgpu/AHardwareBuffer interop and correct producer ownership are not established here.
6. Stop with a concrete unsupported/blocked result if neither same-engine path preserves R13, alpha and lifecycle contracts within the bound.

This is a future investigation scope, not an executed native alternative or a promise that PQ/ASurfaceControl will work. A brighter SDR beam never closes R05.

## 10. Honest stop condition and immediate executor entry

**Blocked acceptance:** actual Android compositor and panel support cannot be established from these cached definitions under the source-only fence.

**Evidence:** the wgpu pair selection is proven, but no current target capabilities or presentation were observed. The public SDK does not expose the inspected implementation chain needed to prove SurfaceView layer targeting and WSI metadata propagation.

**Best current result:** an exact wgpu API/source proof, Android public API/version map, explicit shader/retained/reconfiguration changes, minimal ownership split, and requirement-linked acceptance matrix.

**Next implementation:** begin with the shared output-transfer and per-destination native selector plus owner-scoped Android range negotiation. Keep requested/active state honest from the first patch. Coordinator integrates released Activity/UI, then performs the real format/dataspace/range feasibility check before a feature acceptance claim. No user brightness change or fake HDR stub is needed to start.

The research task ends with this report, readset, shell hash receipt, and swarm completion report. No product file ownership was taken. All research ownership is released on report submission.

## Final private integrity receipt

- Completed source/report integrity verification at 2026-09-09T02:14:09Z.
- Recomputed every readset entry from its immutable Git object or installed public/cache file: 46 of 46 SHA-256 values matched.
- Unique research artifacts: 40, below the 45-source-file bound. Mandatory governance/skill files: 6 additional instruction files.
- Private directory mode: 0700. Report and readset mode: 0600.
- Form inspection found no prose em dash or semicolon. The Rust type `[f32;28]` retains its required code syntax.
- No product tests, builds, runtime checks, network, workers, device operations, or Git mutation ran.
- Research is stopped and report ownership is released. No product ownership or external resource was acquired.
- `SHA256SUMS` records the final report/readset shell hashes. Swarm completion follows this receipt before 02:22 UTC.
