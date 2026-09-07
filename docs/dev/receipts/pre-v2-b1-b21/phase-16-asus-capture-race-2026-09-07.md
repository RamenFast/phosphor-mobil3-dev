# ASUS capture prediction race: reproduced and corrected

2026-09-07, root-only acceptance continuation. S25 excluded. No expansion features started.
Ben's current ASUS MUSIC ceiling is **1/30**, read back after his 01:18 UTC instruction.
Do not restore the older muted baseline. Keep the requested plugged-in wake settings.

## Reproduction

The previous implementation `6183aa8` was backed up and verified against its retained APK hash.
A behavior-preserving debug observation candidate exposed the failure during actual Spotify capture.
Media3 optimistically changed its controller's `playWhenReady` and `isPlaying` before Spotify's callback.
The console drew the predicted glyph, reverted it, then drew the confirmed glyph.

Five routed commands produced 15 glyph changes, including five premature changes opposite the latest observed source state.
Example, device uptime in milliseconds:

| Time | Observation |
|---|---|
| 16960324 | Activity predicts paused before the routed Pause command |
| 16960325 | Pause is routed to the external controller |
| 16960335 | Console draws Play while the observed source is still PLAYING |
| 16960346 | Console draws Pause again |
| 16960425 | Spotify callback reports PAUSED |
| 16960459 | Console draws the confirmed Play glyph |

This establishes a transient inversion. It does not prove every sustained inversion Ben previously reported shares this cause.
The first recording finished before the rapid actions because an intervening tool gate delayed them.
It is not counted as visual evidence for those transitions. The draw timeline and settled UI evidence are retained.

## Correction

Publish numeric observed capture state through the existing MediaSession extras.
The capture glyph and toggle read that state, not optimistic controller fields.
Retirement publishes NONE. Activity reconnect reads the retained extras, and callbacks check current Activity/controller ownership.
Local and remote playback keep their existing display and toggle rules.
Session extras avoid relying on metadata-extra equality to deliver state changes.

Debug observations are explicitly enabled through the `PhosphorAcceptance` log tag.
The release source set has an inline no-op implementation. No receiver, command, app administration API or audio logging was added.
Draw callbacks establish submitted glyph choice, not panel scanout.

## Actual after-check

Installed candidate SHA256: `03d9f7c5c8d2354fec0a2f61e6f6568e37fa6cbacee254e7675b976119a72961`.
`dev/pm3 install` read back matching APK bytes and signer `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`.

Eight taps produced seven routed commands. Do not claim eight completed transitions.
Those seven commands produced seven glyph changes and **zero premature changes**.
A concurrent 20-second recording, UI dumps and source/mirror state dumps were retained.
The final settled source and mirror were PLAYING and the console showed Pause.

The first scripted Home/return raced automatic PiP and remained on the launcher, so it does not prove reconnect.
A later test waited for Home to settle, reopened Phosphor, verified its actual focused Activity and confirmed Pause while playing.
Playback was then explicitly paused.
Permission revocation published NONE and the visible console returned to `everything playing` without transport buttons.
Restoring the tested listener reattached the actual paused Spotify session and controls.

## Validation and limits

- Android unit tests, lint, debug assembly and `checkEngine` passed after the fix.
- Regression cases cover optimistic prediction/rollback sequences, missing/error state, unchanged local/remote truth, and callback ownership wiring.
- Initial lint rejected duplicated identical log-tag literals. A shared constant resolved it without suppression.
- Release Kotlin compilation reached the protected release-provenance gate and stopped on the dirty tree. No gate was bypassed.
- Actual BUFFERING/CONNECTING and every original B8 variant are not proved by the seven-transition run.
- Native warnings can evict the device log buffer quickly. Use a live finite reader rather than relying on a later dump.

Private raw evidence and rollback files are under `dev/scratch/asus-timeline-20260907T0113Z/`.
`capture-before-analysis.json` and `capture-fixed-analysis.json` contain the exact counted transitions.
`check-capture.rb` defines a premature change against the latest published platform callback.
The analyzer does not claim source-internal timestamps or sound-to-photon latency.

Capture access was revoked before no-audio gesture testing. Final byte-exact restoration and committed-build installation remain part of this continuation's closeout.
