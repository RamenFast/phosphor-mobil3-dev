<p align="center">
  <img src="docs/screenshots/kaleido-live.png" width="30%" alt="Kaleidoscope effect on live audio">
  <img src="docs/screenshots/mode-sheet.png" width="30%" alt="Eleven scope views">
  <img src="docs/screenshots/kaleido-dense.png" width="30%" alt="Dense kaleidoscope from live music">
</p>

<h1 align="center">Phosphor Mobile</h1>
<p align="center">A CRT oscilloscope in your pocket. Point it at any sound and watch the beam.</p>

<p align="center">
  <a href="https://github.com/RamenFast/phosphor-mobil3/releases/latest"><img src="https://img.shields.io/github/v/release/RamenFast/phosphor-mobil3?label=latest&color=ff9cc4&labelColor=000" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-10%2B-9ad0ff?labelColor=000" alt="Android 10+">
  <img src="https://img.shields.io/badge/license-GPLv3-ffd166?labelColor=000" alt="GPLv3">
  <img src="https://img.shields.io/badge/tracking-none-9ad0ff?labelColor=000" alt="No tracking">
</p>

---

## What it does

Phosphor turns audio into light the way a real cathode-ray tube would: a beam with mass, phosphor that glows and fades, eleven ways to draw the signal. It runs the same Rust engine as the desktop [phosphor](https://github.com/RamenFast/phosphor), edge to edge on your phone.

- **Any source.** Your music files, whatever another app is playing, the microphone, or your PC over Tailscale.
- **Eleven scope views** and a stage of geometry effects: kaleidoscope, spin, tunnel, pulse.
- **Media controls** in the notification and lock screen. Gapless queues from a folder.
- **Portrait, landscape, split-screen, picture-in-picture.** The tube follows you.
- **No account, no ads, no tracking.** Settings stay on your phone. [Privacy policy](PRIVACY.md).

Some DRM-heavy apps forbid capture and arrive as silence. Phosphor tells you instead of pretending.

## Install

**Google Play:** coming. This repo is the source the listing will link to.

**Direct APK:** grab the latest from [Releases](https://github.com/RamenFast/phosphor-mobil3/releases/latest). Each release ships a `.sha256` next to the APK.

```bash
sha256sum -c phosphor-mobil3-vX.Y.Z.apk.sha256
adb install phosphor-mobil3-vX.Y.Z.apk
```

**Obtainium:** add `https://github.com/RamenFast/phosphor-mobil3` and it will track releases for you.

Needs Android 10 or newer, arm64.

## Want more?

This repo is the **stable line**: what ships, tagged and checksummed. Every commit here came through the dev line first.

The **[dev line](https://github.com/RamenFast/phosphor-mobil3-dev)** is where the fun is: new scope views, effects, themes, experiments that may or may not survive. Build it yourself, file ideas, show your beams in [Discussions](https://github.com/RamenFast/phosphor-mobil3-dev/discussions), or send a PR. [CONTRIBUTING.md](https://github.com/RamenFast/phosphor-mobil3-dev/blob/master/CONTRIBUTING.md) has the map.

Found a bug in a release build? [Open an issue here](https://github.com/RamenFast/phosphor-mobil3/issues/new). Say which phone, which Android, and which source.

## Build from source

```bash
git clone https://github.com/RamenFast/phosphor.git
git clone https://github.com/RamenFast/phosphor-mobil3.git
cd phosphor-mobil3
scripts/bootstrap-android.sh && source scripts/env.sh
./gradlew :app:assembleDebug
```

The desktop `phosphor` checkout must sit next to this one: the beam engine is a path dependency.

## PC relay

Install `scripts/relay-install.sh` on a Linux PC and add it in the app by its Tailscale name. Audio, track metadata, artwork, and transport control flow over the tailnet. Do not expose port 45777 to the internet. Details in [docs/REMOTE.md](docs/REMOTE.md).

## License

GPLv3. The tube is yours.
