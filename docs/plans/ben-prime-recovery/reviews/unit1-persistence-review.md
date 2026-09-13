# Final R2 persistence recheck

## Verdict

**Source-clear.** The correction closes the reviewed false-durability and lost-retry path across Activity recreation. No remaining material R2 defect was found.

## Source evidence

- `MainActivity.kt:155-158` keeps `AutoFrameSave` in the companion object. Activity recreation does not replace the pending owner.
- `ui/AutoFramePreference.kt:43-55` keeps pending state after a false return or exception. Only a successful checked flush or explicit restore clears it.
- `MainActivity.kt:1081-1082,2163-2168` retries on stop and restores the pending value before consulting the preference cache. Recreation restores the failure message rather than treating cached data as durable.
- `MainActivity.kt:2427-2434` clears failure only after a successful flush. `Sheets.kt:1261-1264` exposes RETRY for the restored failure.
- `MainActivity.kt:697-711` retires the older pending edit only after the checked import transaction succeeds and includes the framing key. Failed imports and imports without that key leave the owner intact.
- `MainActivity.kt:2081` excludes pending framing from direct unchecked autosave writes. Normal completion and RESET still use the checked framing commit.

## Android cache behavior

Excluding the key from an editor does not exclude it from Android's whole-cache disk write. An unrelated `apply()` can still persist a framing value previously published to process memory by a failed `commit()`.

This does not falsely clear the explicit failure owner. Those writes do not call `flush()` or `restored()`. Pending state and RETRY remain conservative until a checked framing retry succeeds or a checked import supersedes it. An incidental successful write can make the warning stale, but cannot create the reviewed false-success state. A retry of the same cached value can still write a generation whose earlier disk write failed.

## Test review and limits

`AutoFrameSaveTest` now separates process memory from disk for the failure sequence. It checks retained pending state, another failed flush, and successful retry. Other cases cover completed edits, RESET, latest-edit retry, exceptions, and explicit import retirement.

The recreation fixture aliases the same process owner. It does not construct Android Activities or test real SharedPreferences. `AutoFrameWiringTest` supplies source-string checks for the companion owner, import retirement, and autosave condition. The restore and failure-message paths were independently read here. These are source and fixture reviews, not executed test results.

Root owns the full Android test results and phone acceptance. No build, test, device, network, worker, or Git write ran here. R1 and R3 remain outside this recheck. Source review ownership is released.

## Reviewed source hashes

- `app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt`: `07865044c2ac68869b57c7aa6a900ee279f31be600444340368e3c7810e4bcb8`
- `app/src/main/kotlin/dev/phosphor/mobil3/ui/AutoFramePreference.kt`: `b016fb5d6fd4dbe9688927cbb5d7c511b50202915d2554c2be3faeb4fa0f72d7`
- `app/src/test/kotlin/dev/phosphor/mobil3/ui/AutoFrameSaveTest.kt`: `8fcc6f0c0c22276bc26201c41eaa04c3cc448e4d10c3a87671ddfe7a14f26d04`
- `app/src/test/kotlin/dev/phosphor/mobil3/ui/AutoFrameWiringTest.kt`: `32d62147d9f99f69a3f223333027ba51cc95f132422bb282e2e2df75b2ce4c78`
