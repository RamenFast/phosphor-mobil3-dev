# R13 correction 01: source-retirement energy

This is a correction note, not a replacement or alteration of the independent baseline review. The critic reads immutable mobile44172dc/shared084f5d9. Capture read-fence work has separate ownership and evidence.

## Confirmed source gap

`pause::invalidate` retires retained History and CPU visual ingress. The render loop's VISUAL_FRESH branch resets DSP and timing but leaves the live GPU ping-pong energy textures unchanged. A later draw can therefore retain old-source emission under a new History generation. The shared decay pass can preserve that emission. Clearing only the retained Arc is insufficient.

## Required correction

Add a narrow public shared `GpuRenderer::clear_energy` operation. Encode clear passes for both existing energy textures and submit in queue order. Use no new energy allocation, CPU image readback, device wait, shader change or audible operation. Reset the current energy index. Preserve all appearance settings and independently retained snapshots.

Call it exactly when the live render loop consumes a visual-fresh boundary, before new-source DSP/deposit. Resume and source invalidation both use this boundary. HOLD drawing remains based on its immutable retained snapshot, so clearing the live textures cannot mutate the pinned image. Hidden rendering remains inactive until its existing surface lifecycle permits work.

## Verification

Extend the actual synthetic offscreen GPU test: retain bright frame A, clear live energy, verify black live pixels with grid disabled, advance empty passes through both ping-pong sides, and verify no emission returns. The retained A pixels must remain byte-identical throughout. Add a focused source-integration assertion that the VISUAL_FRESH branch invokes the tested public clear operation before sample consumption. Re-run the frozen Android/native/GPU integration gate after the independent capture writer releases.

This establishes GPU pixel behavior and source wiring, not Android source-switch timing, driver-stall recovery or physical scanout. Those acceptance checks remain open. Preserve the original review report and assess corrections separately.

## Transport initialization and session publication

Two further baseline source traces require correction. An initial PAUSED observation currently changes `transport_paused=None` into intentional HOLD. Initial state must instead establish the observation baseline without pinning or resuming. Explicit display/user pause still acts immediately. Only a later observed edge changes pause state.

A remote M packet currently checks its session before parsing, then changes global History without an owner or generation check. Retarget can occur between those operations. Capture History generation before parsing and validate it plus actual session liveness inside the pause transition. Apply observation, pause state and visual-resume ring reset as one ordered transaction using ring, meter, then History locks. No GPU work or transport action runs inside these locks. Drop retired snapshot ownership afterward.

Remote connect/disconnect must invalidate the old link generation before invalidating History, and notify the control thread afterward. Otherwise an old still-valid session can adopt the newly reset History generation. A stale observation rejected by generation or liveness must change neither transport baseline nor display state. Test initial paused/playing snapshots, later edges, repeated observations, rejected old generation and rejected old session. Preserve audible queues and source retirement semantics. Source tests do not substitute for Android callback and real reconnect acceptance.

RemotePlayer's later tokenless metadata callback no longer repeats display observation. It still updates the existing UI transport state. Native M ingestion owns the source-fenced observation, while explicit user transport commands retain their pre-command pause hook. This does not claim to redesign or solve all preexisting metadata-session provenance.

## Independent HUD return to live

The baseline HUD exposes display resume only when transport control is unavailable. A held local track transferred from the app therefore has no HUD-only way to resume visualization without changing audio. Add a dedicated LIVE action beside FIT in a separate 48dp inspection row. LIVE invokes only `setDisplayPaused(false)` and refreshes display status. It is enabled while HOLD or BLACK is active regardless of transport capability. Keep the source play/pause action separate and truthful. Do not add a sixth minimum-width button to the existing transport row. Source-wiring tests must verify the separate action and absence of controller or seek calls. Actual HUD touch, accessibility, audio continuity and position checks remain Android acceptance work.

## Observed combined gate, 08:01 UTC

Coordinator task `4104942tt5` ran the released source unchanged from08:00:10 to08:01:36 UTC and exited0. XML independently totals521 Android unit tests, zero failures/errors/skips. Locked native tests passed91. Three actual offscreen GPU tests passed on AMD RADV GFX1201, including the retired-energy regression. Lint, both debug APKs, engine integration, release helper and12-check production source boundary passed. Complete source manifests and file sets were identical before/after in both repositories. No installation or Android device acceptance occurred.

Evidence prefix: `dev/scratch/mobile-expansion-20260908T001819Z/hold-correction-01-0756`. Runner: `run-hold-correction-01-0756.sh`, SHA256 `3b01c38c2e289144aa090c2a782e38eba83fa8802471e208efd8a79c8dd5c546`.

| Evidence suffix | SHA256 |
|---|---|
| `results.json` | `4a4e27d389ae7374f0276fb56525d17f71b94fbeb5dd28ac7826e8bb83e102b1` |
| `mobile-source.sha256` | `af759af1cb9b4df217cac46b864ae8a37855709003edd42636e5a0e35e4f3984` |
| `engine-source.sha256` | `581cd08f0a1fedcb8470e6848957edcec1017012bb772462e01280bc1d30acb9` |
| `gpu.log` | `760efb2653436e6edc6d4ffce03309c38bd350daf6a8c56bba95fc0574f44f60` |
| `native.log` | `89878b80d94b30995fa6692c6647c7ff74ad11a92c55b77428c6386ccb4e9e01` |
| `gradle.log` | `fbb95ef36c67fd0bf92da48029eb4319026e62307a2ef3d9fcb1c9a4b5469e3e` |

The original [round1 review](critiques/section-05-round-01.md) remains6/10 at mobile44172/shared084f. This correction has not received its independent second review. The [capture writer handoff](section-05-capture-fence-handoff.md) provides exact production-primitive read-order tests and explicitly leaves root protocol and already-buffered source-time freshness unresolved. Working-tree APKs are not the final reviewed clean installation freeze. Physical Android HUD controls, handoffs, callback ordering, source continuity, GPU failure behavior and actual capture freshness remain acceptance work.
