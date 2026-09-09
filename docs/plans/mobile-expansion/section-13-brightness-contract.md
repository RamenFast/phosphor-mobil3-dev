# Section 13 foreground brightness contract

Canonical requirement: MOBILE-EXPANSION-PLAN.md section13/R06. This records the settled behavior and current integration seam before implementation. It does not claim a working brightness control.

## Outcome

PIN SCREEN BRIGHTNESS is an explicit, persisted setting, off when absent. While the full Activity is foreground, enabling it requests `WindowManager.LayoutParams.screenBrightness = 1.0f` and keeps that window awake even with no source or paused playback. Disabling it or leaving full-app foreground restores `BRIGHTNESS_OVERRIDE_NONE`. PiP, a background Activity and a floating HUD must not brighten another app. No global brightness/auto-brightness write, special permission, root request or polling loop is allowed.

The UI explains sustained-brightness/static-image exposure and panel, thermal, battery and accessibility limits without repeated blocking confirmation. Requested window brightness is not measured panel luminance. This feature is separate from beam energy, auto-gain and HDR.

## Exact R06 implementation contract, 2026-09-09

`pin_screen_brightness` is a portable Boolean in schema `phosphor.settings/2` only.
Absent or malformed local values mean off. Schema /1 skips this new key.
Omitted import keys preserve the existing setting. Explicit false disables it.
The key does not belong to instrument or appearance presets. Import validates the
whole archive before persistence and applies this window preference only after success.

The pin is active only when requested and the Activity is started, resumed, focused,
current in its task, not destroyed, not in PiP, and not displaced by the floating HUD.
Focus loss includes notification/permission overlays and split-screen focus changes.
Playback pause and source absence do not disable an otherwise active pin.
Active requests window brightness 1.0f. Every inactive condition requests -1.0f,
Android BRIGHTNESS_OVERRIDE_NONE, without restoring a copied global slider value.

Lifecycle, focus, PiP, HUD handoff, surface replacement, import, and explicit-setting
events apply brightness. Writes occur only when the actual window attribute differs.
The existing periodic source-wake refresh never writes brightness attributes.
The one source-wake owner combines its original source decision with the active pin.
An inactive pin leaves all original source-driven wake behavior unchanged.
Pause/stop/destruction update their state before restoring the window override.
The UI reports a requested override, not measured panel luminance. Persistence errors
remain visible and do not prevent immediate local disable/restoration.

## Existing source seam at485bc9f

`MainActivity.reassertSourceWake` currently owns both `scopeSurface.keepScreenOn` and `FLAG_KEEP_SCREEN_ON`, based on `SourceWakePolicy.visible` and live mic/playback/capture ownership. Integrate the pin into that existing owner rather than creating competing awake-flag writers. Preserve the original source-driven behavior when the pin is off. Existing lifecycle methods already expose Activity start/stop, pause/resume, destruction, PiP and surface replacement.

Add only the necessary persisted setting, pure decision policy, Activity event adapter, Display & HUD control/status, exact archive handling and manual description. Keep sources, audio routing, instrument/appearance ownership and root helpers unchanged. Reassert through real lifecycle/import/window events, not a periodic callback. Define the exact foreground condition and event ordering in the writer handoff before runtime edits.

## Checks

Pure tests must cover default-off, active foreground, paused/no-source, disabled, paused/background, PiP, destruction, recreation, source-driven wake preservation and imported preference. Source-adapter checks remain supplementary. The full Android gate must compile/lint/build both APKs and retain unchanged source inventories.

Authorized device acceptance must observe the actual window attributes and awake behavior, ordinary auto-brightness and user slider interaction, exit/recreation/import/disable restoration, no HUD takeover and unchanged global settings. Do not increase an unrelated device volume or operate S25 for this nonroot feature. If ASUS is unavailable, retain source/build evidence and name device acceptance as blocked rather than inventing a brightness result.

## Archive canonicalization correction

The real ASUS SAF check exposed a pre-existing platform mismatch: Android escapes
forward slashes in JSONObject.quote; the host test JSON library usually does not.
Schema strings contain a slash, so host and Android produced different checksum bytes.
The archive checksum must use explicit Android-compatible string quoting on every runtime.
Escape quote, backslash, slash, and controls U+0000..U+001F; preserve other UTF-16 text.
Use short escapes for tab, backspace, newline, carriage return and form feed; other
controls use lowercase four-digit Unicode escapes. Ordinary JSON parsing/serialization
stays unchanged. Existing Android archive hashes remain valid, with no checksum fallback.
Prior host-only fixtures are not supported Android exports and must be regenerated.
