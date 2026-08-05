# Audio and connectivity specification

## 1. Source hierarchy

Phosphor supports four source classes:

1. Local file or folder playback.
2. Microphone input.
3. Android playback capture.
4. PC relay playback.

One source owns the audible and visual path at a time. Source changes must release the previous owner before the next owner flows.

## 2. Local playback

Local playback uses the shared Phosphor audio engine and Android media-session integration.

The scope input must be tapped from the same sample stream that reaches the output callback. Gapless playback, seek, pause, next, previous, metadata, artwork, audio focus, and noisy-route behavior must remain coherent.

## 3. Microphone

Microphone input requires `RECORD_AUDIO`. The app must request permission after an explanatory user action.

Microphone hardware is optional. Devices without a microphone may still install and use local or remote sources.

## 4. Android playback capture

Playback audio capture requires Android 10, API 29.

The capture path uses:

- `MediaProjectionManager`
- user consent for each required session
- `AudioPlaybackCaptureConfiguration`
- `AudioRecord`
- a `mediaProjection` foreground service

The capture configuration includes media, game, and unknown audio usages.

Capture works only when the source application and Android policy allow it. Protected or opted-out content may produce silence. Phosphor must report that limitation without claiming a broken connection.

### 4.1 Version behavior

- API 29 through 33 use `createScreenCaptureIntent()`. These versions capture the complete display.
- API 34 and newer use `MediaProjectionConfig.createConfigForDefaultDisplay()`.
- API 34 and newer require single-use projection tokens and the declared foreground-service permission and type.
- Projection revocation or screen lock must stop capture and release audio resources.

## 5. PC relay

The relay streams audio, scope geometry, metadata, artwork, file browsing, and transport state over protocol v2.

The phone must retain independent audio and geometry stream controls. File playback may stream audio regardless of the live-capture audio toggle.

The app must preserve the established reconnect, jitter-buffer, source-switching, and link-truth behavior.

## 6. Network boundary

Protocol v2 uses raw TCP and has no application-layer authentication or encryption.

Supported deployment:

- a trusted local network
- Tailscale
- another private encrypted overlay controlled by the user

Unsupported deployment:

- a port exposed to the public internet
- an untrusted shared network without a secure overlay

The app must not seed private estate hosts into production builds. Users add or select their own hosts at runtime.

The app must not dial a host until the user invokes a remote operation.

## 7. Network selection

The user may select automatic, Wi-Fi, or mobile routing where Android exposes the requested transport.

A selected transport must bind only the remote session. It must not silently reroute unrelated application traffic.

Transport loss must produce reconnecting or unavailable state. It must not appear as measured silence.

## 8. Deferred relay polish

Application-layer authentication, host identity, protocol encryption, discovery, and simplified setup remain deferred.

This cleanup may strengthen documentation and boundary tests. It must not replace the working relay architecture.

## 9. Excluded capture paths

The product does not use Visualizer API, root, Shizuku, ADB sidecars, privileged permissions, AudioPolicy injection, or DRM bypass.

Archived experiments do not authorize these paths.
