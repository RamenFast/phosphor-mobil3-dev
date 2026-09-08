# Section 13 foreground brightness contract

Canonical requirement: MOBILE-EXPANSION-PLAN.md section13/R06. This records the settled behavior and current integration seam before implementation. It does not claim a working brightness control.

## Outcome

PIN SCREEN BRIGHTNESS is an explicit, persisted setting, off when absent. While the full Activity is foreground, enabling it requests `WindowManager.LayoutParams.screenBrightness = 1.0f` and keeps that window awake even with no source or paused playback. Disabling it or leaving full-app foreground restores `BRIGHTNESS_OVERRIDE_NONE`. PiP, a background Activity and a floating HUD must not brighten another app. No global brightness/auto-brightness write, special permission, root request or polling loop is allowed.

The UI explains sustained-brightness/static-image exposure and panel, thermal, battery and accessibility limits without repeated blocking confirmation. Requested window brightness is not measured panel luminance. This feature is separate from beam energy, auto-gain and HDR.

## Existing source seam at485bc9f

`MainActivity.reassertSourceWake` currently owns both `scopeSurface.keepScreenOn` and `FLAG_KEEP_SCREEN_ON`, based on `SourceWakePolicy.visible` and live mic/playback/capture ownership. Integrate the pin into that existing owner rather than creating competing awake-flag writers. Preserve the original source-driven behavior when the pin is off. Existing lifecycle methods already expose Activity start/stop, pause/resume, destruction, PiP and surface replacement.

Add only the necessary persisted setting, pure decision policy, Activity event adapter, Display & HUD control/status, exact archive handling and manual description. Keep sources, audio routing, instrument/appearance ownership and root helpers unchanged. Reassert through real lifecycle/import/window events, not a periodic callback. Define the exact foreground condition and event ordering in the writer handoff before runtime edits.

## Checks

Pure tests must cover default-off, active foreground, paused/no-source, disabled, paused/background, PiP, destruction, recreation, source-driven wake preservation and imported preference. Source-adapter checks remain supplementary. The full Android gate must compile/lint/build both APKs and retain unchanged source inventories.

Authorized device acceptance must observe the actual window attributes and awake behavior, ordinary auto-brightness and user slider interaction, exit/recreation/import/disable restoration, no HUD takeover and unchanged global settings. Do not increase an unrelated device volume or operate S25 for this nonroot feature. If ASUS is unavailable, retain source/build evidence and name device acceptance as blocked rather than inventing a brightness result.
