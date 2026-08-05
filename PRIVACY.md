# Phosphor Mobile privacy policy

**Effective date:** 2026-08-05

Phosphor Mobile is an audio-reactive oscilloscope for Android. It has no user accounts, advertising, analytics, behavior tracking, usage tracking, or third-party crash-reporting service.

## Data the app processes

Phosphor can process the following data only to provide features the user chooses:

- **Playback-capture audio:** Android can provide audio from other apps after the user approves the system MediaProjection prompt. Phosphor converts the audio into graphics in memory. It does not record or upload this audio.
- **Microphone audio:** When the user grants microphone permission and selects the microphone source, Phosphor converts live microphone input into graphics in memory. It does not record or upload this audio.
- **Local files and folders:** Files are read only after the user selects them through Android's system picker. Folder access can persist so a selected queue remains available after restart.
- **Media metadata:** If the user grants Android notification-listener access, Phosphor can read active media titles, artists, playback state, and artwork. This information is used on the device for the display and controls.
- **Preferences:** Display settings, consent state, saved relay endpoints, and other app preferences are stored locally on the device.

## Network use

A fresh installation does not contact a Phosphor service.

Phosphor opens a relay connection only after the user saves and selects a remote host. The app accepts Tailscale MagicDNS names, `.ts.net` names, and Tailscale IPv4 addresses in `100.64.0.0/10`. The connection receives audio or geometry from the user's PC relay and can send playback-control messages. Tailscale supplies network identity and transport encryption. Phosphor does not operate a relay service or receive a copy of this traffic.

Links to the source repository, releases, license, or this policy open only when the user selects them. They open in the user's browser and are then subject to the destination site's policy.

## Storage, backup, and deletion

Sensitive runtime preferences, saved relay hosts, consent state, and media access state are excluded from Android cloud backup. A user can remove saved relay hosts in the app. Clearing app storage or uninstalling Phosphor removes app-owned local data. A settings export is created only when the user explicitly requests it and chooses its destination.

## Sharing and sale

Phosphor does not sell personal data. It does not share data with an advertising, analytics, or profiling company. User-directed relay traffic goes only to the endpoint the user selects.

## Children

Phosphor is a general-purpose visualizer and is not directed to children. It does not knowingly collect personal information from children.

## Changes and contact

Material policy changes will be published in this file with a new effective date. Questions and privacy requests can be opened at <https://github.com/RamenFast/phosphor-mobil3/issues>.
