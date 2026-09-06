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

A selected local folder means its complete supported audio tree. Traversal must use stable name order, reject repeated document identities, and persist the user-granted tree access. Decoder validation must occur before an invalid file can replace the audible source or start the output stream. An invalid entry must report and skip while later valid entries remain available.

Seek, next, previous, and folder-open work must run through one service-owned serial path. A new request replaces pending work. Android media controls and the main looper must return immediately, and only the latest requested item may publish audio or metadata.

After a current native seek succeeds, publish its actual position to the media session even while paused and when metadata is unchanged. A command acknowledgement is not proof of completed native seek. Failed or superseded seeks cannot publish completion, and later state reads cannot replay an old completion.

Tagged local metadata must follow the item that the decoder actually starts. Blank title falls back to the filename. Blank artist remains absent. Metadata delivery must not block open or compete with folder validation for event ownership.

Auto-gain's new-item boundary uses the existing serial request and event owners. Every native decoder open has a distinct transient identity, including same-path seek reopens. PlaybackTruth checks that identity on TrackStarted and terminal events as well as the exact current Open object and latest request. Only a successfully published new local item with matching TrackStarted proof may confirm one peak reset. Same-item seek, metadata refresh, retained-source preflight failure, unpublished or failed open, capture and microphone input cannot confirm a reset. Explicitly opening the same path as a new item can confirm one after its own proof. The render command rechecks the current published native identity under the deck lock, so delayed old proof cannot reset a replacement. This identity fence does not change metadata-driven beam-cycle behavior or the terminal, paused-tail, queue and wake policies.

## 3. Microphone

Microphone input requires `RECORD_AUDIO`. The app must request permission after an explanatory user action.

Microphone hardware is optional. Devices without a microphone may still install and use local or remote sources.

A capture-to-microphone switch must stop capture first and wait for an idle observation that belongs to that stop request. A stale earlier idle state must not start the microphone. The microphone publishes live state only after `AudioRecord` initializes, recording starts, and the scope input is armed successfully. The handoff must not use a timer.

When stops overlap, an older completion must not replace the latest mic request's idle snapshot. This also applies while the activity's status receiver is stopped. Every request keeps its source-stop acknowledgement so late reader loss remains visible. A newer non-mic selection cancels pending microphone startup.

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

Captured transport and metadata must mirror the active Android MediaSession without scraping notification text. Playing, buffering, paused, and resumed states must drive an honest in-app glyph. Seek is available only when the session advertises `ACTION_SEEK_TO` and supplies a real duration. A failed, absent, or superseded session must not leave a stale title, artist, seek rule, or transport face.

### 4.1 Version behavior

- API 29 through 33 use `createScreenCaptureIntent()`. These versions capture the complete display.
- API 34 and newer use `MediaProjectionConfig.createConfigForDefaultDisplay()`.
- API 34 and newer require single-use projection tokens and the declared foreground-service permission and type.
- Projection revocation or screen lock must stop capture and release audio resources.

## 5. PC relay

The relay streams audio, scope geometry, metadata, artwork, file browsing, and transport state over protocol v2.

The phone must retain independent audio and geometry stream controls. File playback may stream audio regardless of the live-capture audio toggle.

The app must preserve the established reconnect, jitter-buffer, source-switching, and link-truth behavior.

A direct relay file remains a direct play request. Playing the current relay folder must recursively collect supported audio descendants in stable name order, start the first item, and advance through nested descendants at end of file. Directory-row selection remains browse. Folder playback reuses the existing protocol-v2 play and session path; it must not add a queue protocol, JNI mode, or privileged file path.

Folder entries use lexicographic paths relative to the selected directory. For example, `10-album/01.wav` precedes `15-middle.wav`, which precedes `20-disc/01.wav`. Sorted traversal chooses the first alias for each repeated local directory identity. Hidden files and directories are omitted. Existing deliberate local links outside the configured root remain supported, with cycle prevention rather than a new containment restriction.

A direct file still starts the selected item in its existing sorted sibling sequence. Folder selection must not reduce its recursive queue to the first file. Cancellation or a superseded remote fetch must not replace a newer selection. The folder action uses one accepted listing root/path pair, never an old path with a newly selected root. A browse request also belongs to its selected relay peer and requires a later matching listing revision. Host selection and browser closure invalidate retained callbacks immediately. Every callback, including root selection, rechecks the current peer and request identity. Play, dismissal and disposal retire that request's authority. Reading a listing must not pair old JSON with a newer revision. Literal backslashes remain filename characters in local and rclone listings, not path separators.

Regular-app relay acceptance includes live PC audio, a direct root file, a whole-folder first item, nested end-of-file advance, disconnect, and reconnect. A fresh Linux user installation must prove the relay binary, user service, schema, Tailscale listener boundary, capture dependencies, and readable library root.

The canonical installer preserves its default local and explicit remote install paths while following the workspace CLI contract. Validate arguments before build or activation. Inspect the installed schema, configuration and doctor report before enabling the service. Doctor reports missing optional features as well as required dependencies, so `all_ok=false` remains an explicit warning, not a new blanket activation veto. A failed or malformed diagnostic command remains an installer error. Installation success must not imply dependency, readable-library or Tailscale acceptance. A busy port is an expected active-service note only when separate evidence identifies that service as its owner.

## 6. Network boundary

Protocol v2 uses raw TCP and has no application-layer authentication or encryption.

Supported deployment:

- Tailscale

Unsupported deployment:

- a port exposed to the public internet
- a local or shared network without Tailscale protection

The app must not seed private estate hosts into production builds. Users add or select their own hosts at runtime.

The app must not dial a host until the user invokes a remote operation.

## 7. Network routing

Relay traffic follows Android's normal routing through Tailscale. Phosphor must not bind the process to a physical Wi-Fi or mobile network because that can bypass the Tailscale route and reroute unrelated application traffic.

Route or peer loss must produce reconnecting or unavailable state. It must not appear as measured silence.

## 8. Source shutdown

With `linger_background=false`, task removal must stop local and relay playback, release capture, clear the capture session and consent state, and stop the real microphone owner. The next capture start must request consent again.

With `linger_background=true`, only an already service-owned source may continue. The product must not promise background microphone survival while the microphone remains activity-owned.

Task removal retires queued source starts, consent results and runtime-state saves from that task. A late callback must not stop or republish a newer task's source. A retained service must also handle a later removal after the app reopens and linger is disabled.

Activity destruction stops that activity's microphone owner in either linger mode. Ordinary backgrounding and document-picker navigation must not be treated as destruction. Portable tuning settings remain unchanged by source teardown.

The service owns native source teardown. Releasing a Media3 player face must complete immediately without a second native disconnect or external playback command. Capture-stop notification must reach only an existing matching playback owner and must not recreate the service.

Ordinary playback-service destruction must not stop an activity-owned microphone or a separate capture owner. When a new playback service binds to surviving capture, it must restore the matching metadata and transport mirror without restarting projection or requesting consent.

A replacement source must wait for earlier native cleanup. Reader-stop failure remains visible at the real reader owner and must support a later stop retry. A transient reader timeout must not permanently poison unrelated future playback after that reader actually stops.

Once capture cleanup returns for a removed task, its started service must end even if the reader reported a timeout. Retain that failure and stop-only retry access after destruction. Ending the service must not be reported as proof that an unjoined reader stopped.

## 9. Deferred relay polish

Application-layer authentication, host identity, protocol encryption, discovery, and simplified setup remain deferred.

This cleanup may strengthen documentation and boundary tests. It must not replace the working relay architecture.

## 10. Excluded capture paths

The product does not use Visualizer API, root, Shizuku, ADB sidecars, privileged permissions, AudioPolicy injection, or DRM bypass.

Archived experiments do not authorize these paths.
