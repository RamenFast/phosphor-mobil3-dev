# R09 ASUS PiP / route matrix (partial)

2026-09-10. NAAIB70036673ZC. HEAD 203fd77. MUSIC 1/30. Prefs restored. RECORD_AUDIO revoked.

## Proven

- Confirmed default mic starts MicCaptureService `98f5d96` in pid 26351.
- HOME with `pip_auto_enter` enters pinned PiP (`mode=pinned`, `mLastReportedPictureInPictureMode=true`).
- Same process and same MicCaptureService record remain live in PiP. Identity held.
- Foreground notification channel `phosphor_microphone` stays up in PiP.
- Available input devices: Built-In Mic (bottom), Built-In Back Mic (back). No USB/wired/BT input port currently connected.

## Not proven / blocked

- Playback + mic mix needs live standard capture (`CaptureService.mixSession()`). That is MediaProjection consent. Not prompt-free. Not exercised.
- Accessory USB/wired/BT mic: none connected. Cannot fake a route.
- Duplicate built-in UI rows: SRC sheet did not open from the SRC key this run. Policy only suffixes `(id)` when type+address+name collide. Bottom vs back have different names.
- HUD overlay: `SYSTEM_ALERT_WINDOW` is denied. Not granted. Not exercised.
- Linger-background: not exercised (HOME took PiP because `pip_auto_enter` is true).
- After returning from PiP to fullscreen, UI showed `0 segs` while MicCaptureService was still foreground. Visualization stall after PiP is not closed.

Root capture still deferred.
