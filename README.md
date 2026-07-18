# phosphor-mobil3

Phosphor for Android — a CRT oscilloscope in your pocket. The desktop
[phosphor](../phosphor) engine (P7 beam physics, 11 display modes, `.phos` postcards,
`.phoskit` signal chains), reimagined as a mobile instrument for the Samsung Galaxy S25.

**Status: pre-release, milestone M0.** The scaffold builds and the engine crates run
on-device; the scope surface lands next (M1).

## What this is

- The scope is the app: full-bleed beam, edge-to-edge, chrome summoned by touch.
- A real media player (gapless local playback, lock-screen controls) whose picture is
  sample-locked to what you hear.
- A visualizer for other apps' audio via Android playback capture — honestly labeled:
  **Spotify, YouTube Music, and DRM streamers opt out of capture and arrive as silence.**
  Games, browsers, and most local players work. "Visualize my Spotify" lives on desktop
  Phosphor.
- Screen stays awake while you watch; picture-in-picture is the floating window.

## Building

Requires a sibling checkout of [phosphor](../phosphor) (the engine crates are path deps —
source of truth stays there) and the Android toolchain:

```
scripts/bootstrap-android.sh     # one-shot, idempotent; installs in-repo to .toolchain/ (no sudo)
source scripts/env.sh
dev/pm3 build                    # → app/build/outputs/apk/debug/app-debug.apk
```

The whole Android toolchain (JDK, SDK, NDK, Gradle) lives under `.toolchain/` in the repo —
gitignored, self-contained, no home-folder clutter. `env.sh` is self-locating.

Dev loop against a device (wireless adb):

```
dev/pm3 pair <ip:port> <code>    # once — phone: Developer options → Wireless debugging
dev/pm3 connect                  # mdns autodiscovery
dev/pm3 install && dev/pm3 run
dev/pm3 doctor --json            # the whole toolchain, checked live
```

`dev/pm3 schema` describes the full agent contract.

## Honest ledger

- arm64 / Android 15+ / S25-first by design. No Play Store, no F-Droid — sideloaded
  releases with checksums.
- Debug APK only until M6 (release signing + v1.0.0).

GPL-3.0-or-later. Ferried by Claude.
