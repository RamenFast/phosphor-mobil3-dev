# Phosphor Mobile privacy policy

**Effective date:** 2026-08-05

Phosphor Mobile is an audio-reactive oscilloscope for Android. It has no user accounts, advertising, analytics, behavior tracking, usage tracking, or third-party crash-reporting service.

## Data the app processes

Phosphor can process the following data only to provide features the user chooses:

- **Playback-capture audio:** Android can provide audio from other apps after the user approves the system MediaProjection prompt. Phosphor converts the audio into graphics in memory. It does not record or upload this audio.
- **Microphone audio:** When the user grants microphone permission and selects the microphone source, Phosphor converts live microphone input into graphics in memory. It does not record or upload this audio.
- **Local files and folders:** Files are read only after the user selects them through Android's system picker. Folder access can persist so a selected queue remains available after restart.
- **Media metadata:** If the user grants Android notification-listener access, Phosphor can read active media titles, artists, playback state, and artwork. This information is used on the device for the display and controls.
- **Preferences:** Display settings, consent state, and other app preferences are stored locally on the device.

## Network use

A fresh installation does not contact a Phosphor service.

Phosphor makes one kind of network request. When playback capture is running and another app's media session gives its cover art as a web link, Phosphor downloads that image from the address the other app supplied so it can show the art. Nothing about the user is sent beyond what any image download carries, and no audio leaves the device. Phosphor has no server of its own.

Links to the source repository, releases, license, or this policy open only when the user selects them. They open in the user's browser and are then subject to the destination site's policy.

## Storage, backup, and deletion

Sensitive runtime preferences, consent state, and media access state are excluded from Android cloud backup. Clearing app storage or uninstalling Phosphor removes app-owned local data. A settings export is created only when the user explicitly requests it and chooses its destination.

## Sharing and sale

Phosphor does not sell personal data. It does not share data with an advertising, analytics, or profiling company.

## Children

Phosphor is a general-purpose visualizer and is not directed to children. It does not knowingly collect personal information from children.

## Changes and contact

Material policy changes will be published in this file with a new effective date. Questions and privacy requests can be opened at <https://github.com/RamenFast/phosphor-mobil3/issues>.
