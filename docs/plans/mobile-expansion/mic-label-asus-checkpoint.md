# Mic-label ASUS SRC honesty

2026-09-10. HEAD `14dac99`. Dedicated `NAAIB70036673ZC`. MUSIC 1/30. Prefs restored. RECORD_AUDIO revoked.

## Source

- Grok attempt 1 **7/10 FAIL** (F1 name-suffix poisoned `key`): `critiques/mic-label-grok-code-round-01.md`
- Muse 1.3 Contributor attempt 2 **9/10 PASS** (`displayTag` only): `critiques/mic-label-muse-code-round-02.md`
- 956 JVM tests, 82 suites, 0 fail. lintDebug, assembleDebug, assembleDebugAndroidTest.
- Installed APK SHA256 `08923e33cdfa25a15c1329f597632f1dd9ce68ae6f918f7bc6b0fdc73ffbd76a` matched readback. Signer `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.

## Device

Audio policy inputs include Built-In Mic `@bottom` (port 19) and Built-In Back Mic `@back` (port 21). No USB or connected Bluetooth accessory.

SRC sheet showed two distinct rows:

- `ASUS_AI2202 · built-in (19)`
- `ASUS_AI2202 · built-in (21)`

Start on 19 reached `MicCaptureService`. Status: `Microphone active · ASUS_AI2202 · built-in · Device 48000 Hz · 1 channels · encoding 2`. Stop returned `Microphone stopped`; service gone.

Start on 21 after tapping that row also reached `MicCaptureService` with the same format line. Checkmark moved to `(21)`. Not "Selected microphone unavailable".

While 21 was selected, runtime stored unsuffixed identity `15\nback\nASUS_AI2202`. Display suffix is not in the key.

Active-status prose still omits `(id)` (ML-N1). Identity is unaffected.

## Restoration

`phosphor.prefs.xml` and `phosphor.runtime.xml` pulled hashes match the root-stub baseline. RECORD_AUDIO granted=false. No Phosphor services listed. MUSIC 1/30 speaker. App force-stopped; launcher.

Shots: `dev/scratch/mic-label-20260910/`. External copy: `/media/ben/Mass storage/agenticTinkering/claude/phosphor-mobile/mic-label-20260910/`.

## Still open

Accessory USB/BT (none connected; bonded devices exist but disconnected — not taken over). Playback+mic mix, HUD overlay, linger. Remaining visual and regression work unchanged.
