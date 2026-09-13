# Unit 1 focused correction review

## Outcome

**One R2 source blocker remains.** Failed framing saves lose their failure owner across Activity recreation. R1's elapsed-time accounting is corrected in source. R3's microphone descriptor/window pairing is corrected for observed format changes.

Reviewed the current source and changes against `bf85108`, plus the prior review, Unit 1 spec, stereo decision, and `docs/AGENTS.md`. No test, build, device, network, worker, or Git mutation ran in this review. Root owns executable results and installation. This is not phone acceptance.

## R2 blocker: recreation can promote failed cached preferences to saved UI state

Locations: `MainActivity.kt:146,1077-1081,2071-2080,2164-2170,2426-2435`; `ui/AutoFramePreference.kt:43-53`.

The completed-gesture and RESET paths now perform a checked synchronous `commit()`. Live edits remain pending, failures show a toast and retry action, and there is no commit on each pointer sample. Those corrections close the original trailing-debounce loss window after a successful completion.

The remaining sequence is:

1. A completed edit calls `commit()`. Android updates the process-memory SharedPreferences map before the disk result returns false.
2. `AutoFrameSave` correctly retains the pending value in the current Activity.
3. `onStop()` retries, but storage still fails. `saveTuning()` then writes the same framing key through unchecked `apply()`.
4. Activity recreation constructs a new, empty `AutoFrameSave`.
5. `restoreTuning()` reads the failed value from `p.all`, calls `restored()`, and clears `autoFrameSaveStatus`.
6. The new Activity presents no failure and has no pending retry. Disk can still contain the previous value. A later process restart restores that older value.

The pure map writer in `AutoFrameSaveTest` does not model SharedPreferences' memory-before-disk behavior. It also does not reconstruct the Activity owner after a failed lifecycle flush.

**Smallest fix:** Preserve the pending/failure owner across Activity recreation, or require a checked framing write when restoring cached state before treating it as saved. Do not clear failure merely because `p.all` contains the value. Keep successful archive import as an explicit retirement of older pending edits. Remove the framing write from unchecked `saveTuning()` so the checked owner controls its completion. Preserve the last confirmed durable state and report uncertainty if rollback cannot establish it.

**Required focused regression:** Use separate preference-cache and disk maps. A failed writer must update cache but leave disk unchanged. Fail completion and lifecycle retry, recreate the Activity adapter, and assert that pending/failure and RETRY remain. A successful retry must update disk and clear failure. A successful import must supersede the old pending edit. Keep normal completed-gesture and RESET restart checks.

## R1 source result and fixture limits

`engine.rs:774-816` now accumulates bounded `pending_seconds` over empty presentation drains. Nonempty input consumes that budget. Empty calls do not raise gain. A gap reaching 100 ms discards the next rise budget. Silence and unstructured nonempty windows consume the budget without raising gain. Current peaks still clamp effective gain before DSP.

`render.rs:344-346,704-706,764-766,807-818` rebases at suspended presentation, HOLD, source/visual epochs, inactive input, manual mode, and remote geometry. `set_auto` and `set_manual` also clear pending time. The single raw stereo drain remains intact. No PCM or audio gain path was added.

The new `auto_framing_empty_render_drains_keep_time_between_100hz_input_batches` fixture covers scheduled 100 Hz arrivals at 60/90/120/240/1000 Hz presentation. It checks equal-time final gain. It is a schedule model, not a real SampleRing integration: lower presentation rates pass one tone window rather than concatenated producer batches. It has no batch jitter or explicit empty-tick monotonic assertion. Silence, loud protection, and starvation have separate fixtures. HOLD/manual/epoch rebases remain source-reviewed wiring rather than one combined behavioral test. Do not report broader fixture coverage than this.

## R3 microphone provenance result

`SignalRecorderWindow.kt:7-20` compares client format, device format, actual route, and requested route. An observed identity change replaces the meter. Timestamp-only refresh retains it. The reader captures a token before blocking read and publishes through the token check afterward (`MicController.kt:187-202`). Synchronization prevents an old token from populating the replacement meter. Snapshotting one immutable State pairs the matching descriptor and meter.

`StereoFirstInputTest.kt:7-23` now covers the same owner, changed device format, rejected queued read, fresh new measurement, and timestamp-only refresh. A replacement meter has no paired result until fresh input arrives.

`SignalPresentation.kt:82-86` limits the paired L/R row to microphone input. This receipt makes no new claim for capture paired provenance. Client/device formats and physical independence remain separate. Unobserved OEM format transitions and physical L/R independence still require phone evidence.

## New focused fixtures

- `engine.rs:1379-1427` sends the controller's gain into real XY and waveform DSP at minimum/default/maximum framing. It asserts an extent increase over 90 pixels and first-loud-frame viewport containment. This is actual geometry evidence if root's execution passes. It does not assert exact fill or independently bound both channel extents.
- `engine.rs:1430-1460` rejects windows shorter than 32 stereo frames, checks rotated versions of one noise window, exercises the 0.0005 and 0.02 boundaries, and checks starvation. Rotation changes window boundaries, but it is not independently generated noise. Ambient/colored noise and coherent hum remain phone limits, not newly solved classification.
- The all-mode ceiling check populates history but still uses the default reconstruction factor. It does not exercise all supported factors.
- `SettingsArchiveTest.kt:525-541` now decodes valid-checksum fixtures for finite out-of-range and wrong-type values before the destination merge. The destination mutation is inside the failing operation. Nonfinite values still exercise export rejection, since JSON cannot encode them as standard numeric values. This closes the misleading atomic-import assertion for representable invalid values.

## Remaining bounded evidence

Root must complete its build/test gates after the R2 correction. Phone evidence still owns exact APK/hash/signer installation, selected built-in mic and client/device formats, spatial channel response, quiet/loud geometry and recovery, real pinch/drag and ownership exclusions, completed-action restart, unchanged audio levels, and same-package retention. Ben's physical usefulness feedback is separate from synthetic and screenshot evidence.

No unrelated backlog is reopened. Source reading ownership is released to root. The only review write is this report.

## Reviewed source hashes

- `rust/src/engine.rs`: `1564fb70753ee848c636507faba5fb15a36c03c6ab364f9b89efb591d47768f4`
- `rust/src/render.rs`: `293524b89397c2bc2a19b81293ffb03bc908f8cb53cf0cab3841255236f16500`
- `app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt`: `07865044c2ac68869b57c7aa6a900ee279f31be600444340368e3c7810e4bcb8`
- `app/src/main/kotlin/dev/phosphor/mobil3/ui/AutoFramePreference.kt`: `b016fb5d6fd4dbe9688927cbb5d7c511b50202915d2554c2be3faeb4fa0f72d7`
- `app/src/main/kotlin/dev/phosphor/mobil3/SignalRecorderWindow.kt`: `6156db6958653fd08befc425818812905aafe7284a51e6c2775120f3c0056059`
- `app/src/main/kotlin/dev/phosphor/mobil3/MicController.kt`: `a50f495d574667199c51662ff961c80f45427f882040471197ef2486b50a2f34`
