# R09 ASUS PiP segs after rebind

2026-09-10. HEAD 9ff96bd. APK SHA256 `1e704d99026393a267b9d1e19f12daff9bb4ed56db41dcfe1885a7dd762e5f39`.
NAAIB70036673ZC. MUSIC 1/30. Prefs restored. RECORD_AUDIO revoked. Grok OAuth 8/10 on source.

## Proven

- Before HOME PiP: UI `116.6 fps · 480 segs`, `src · mic`, MicCaptureService live.
- HOME: `mode=pinned`, same MicCaptureService.
- After return to fullscreen: native render log recovered to `480`/`960 segs last frame` at ~119.5 fps (pid 28558).
- First UI dump ~1.5s after return showed `119.4 fps · 0 segs`. That HUD sample is transient. Native segs were not stuck at 0.

## Not claimed

- HUD line is not a nits/seg proof by itself.
- Mix/accessories/HUD overlay/linger still open.
