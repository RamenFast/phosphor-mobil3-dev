# Signal check observation contract

## Goal and boundary

R17 explains the current signal without creating another signal path. The canonical requirements remain section8 of `MOBILE-EXPANSION-PLAN.md` and Signal check in `spec/EXPANSION.md`.

Opening SIGNAL CHECK is inert. It does not start a reader, request consent, probe root, connect a relay, change routing, change volume or consume another audio tap. Recovery buttons invoke the existing explicit source actions only after a user gesture. No diagnostic field enters portable settings or a new network endpoint.

This contract precedes implementation. The bounded source audit against mobile `019c0da` will provide exact observation adapters and identify missing fields. Until an adapter has evidence, its fields remain unavailable. The current root stereo and R09 mixing acceptance gaps are not hidden by this view.

## One observation, explicit provenance

The view renders an immutable, small snapshot for the current source-selection revision. Each input row carries the actual owner identity and that owner's current lifecycle revision. Owner replacement invalidates old measurements even if the source label, package, host or selected device is unchanged.

The snapshot separates these facts:

- Selected source versus active owner and backend.
- Requested microphone route versus actual recorder route.
- Negotiated input rate, channels and sample format versus normalized scope transport.
- Actual input contribution versus configured or pending contribution.
- Reader/link state, completed-read progress and arriving sample progress.
- Input measurement versus the display's consumed tap measurement.
- External transport pause versus intentional display HOLD or black-on-pause.

Unknown fields are nullable observations, not zero, guessed defaults or values copied from a setting. Capture format becomes negotiated only after the actual recorder/helper reports it. A root input negotiated as16kHz mono must not become a claim of original stereo because its normalized transport is48kHz with duplicated channels. The existing48/96/192kHz beam reconstruction control is not a recorder negotiation result.

For a future mixed source, playback and mic retain independent owner identities, measurements and contribution flags. One failed input does not erase the other row's health. Until R09 actually installs two inputs, diagnostics must not claim both contribute.

## Measurement and freshness

Use bounded observations published by existing owners. A UI read copies an observation without resetting it. No audio buffer is copied into a diagnostic history. Small aggregate metadata may be retained only for the active owner and freshness calculation.

The existing native `StereoWindow.take` consumes a display-tap measurement. It cannot be called again from SIGNAL CHECK, and its absence during HOLD is not input silence. An adapter may reuse the already delivered UI measurement with its real age and label it as display-tap data. Continued input flow during HOLD requires an actual producer counter or timestamp, not extrapolation from that meter.

A new producer observation, if needed, is bounded and source-owned. Its publication uses existing read/admission work and does not wait for UI, disk, network or another recorder. It distinguishes completed reads from positive sample counts. A zero-length read is not a measured zero-amplitude sample window.

Input peak or level is unavailable until at least one finite sample was measured for that current owner. Exact zero amplitude with positive sample count is measured digital silence. Negative infinity dBFS may be rendered as silence rather than a fabricated finite number. Full-scale indication means that the measured representation reached its known full-scale boundary. It is a clipping warning, not proof of a source application's internal limiter or hardware clipping. Unknown format or unknown samples cannot produce a clean/no-clipping claim.

All ages use one monotonic clock domain, or an explicitly converted timestamp with checked provenance. Wall-clock changes do not make old samples fresh. Counter regressions or identity changes clear the previous comparison. A failed snapshot read is unavailable, not the last successful snapshot relabeled as current.

## Status precedence

Input health and display state are separate labels. Neither HOLD nor black-on-pause masks a reader error.

| Observation | Input label and behavior |
| --- | --- |
| Observation belongs to a different selection or owner | Discard it. Show waiting for current owner or unavailable data. |
| Current owner reports a terminal failure, lost route or disconnected link | Show that precise state and existing recovery action. Old peaks cannot imply current flow. |
| Current selection needs consent | Waiting for consent. Opening diagnostics does not open the permission flow. |
| Current owner is starting and no completed sample window exists | Starting or waiting for samples, not silence. |
| Current owner reports finite samples arriving and nonzero measured amplitude | Samples arriving, with the measured level and age. |
| Current owner reports positive sample count with zero finite amplitude | Measured silence, with format and freshness. Do not infer opt-out or DRM. |
| Read loop progresses but no samples arrive | No samples observed. Keep read-loop health distinct from sample flow. |
| Expected-running reader has owner-reported stalled state | Stalled reader. Report actual age or timeout reason. |
| Only an old observation exists | Stale measurement. Do not infer reader failure from an unobserved interval alone. |
| External transport is paused | Transport paused as a separate fact. Do not invent an active sample stream or failure. |
| Display is held or black-on-pause | Add display held or display black. Input facts continue to come from actual owners. |

The UI samples at most four times per second while the expanded view is visible, the Activity is foreground and its controls are not obscured. Collapse, navigation, stop, destruction and PiP suspend its refresh callback. It does not add a hidden continuous poller. Observation freshness thresholds must be named with the owner adapters, not inferred from UI poll cadence.

## Surface and recovery

Provide an expandable SIGNAL CHECK entry under Signal & Startup and a compact entry from no-signal/error context. The usual instrument remains quiet when it is closed. Reading diagnostics preserves source, tuning, gestures, display inspection and settings expansion state.

Rows show a useful unavailable reason rather than an empty numerical table. Examples:

- `Playback capture · consent needed` with the existing explicit grant action.
- `Everything playing › root ·16kHz mono input ·48kHz duplicated-mono transport ·samples arriving ·display held`, only when each fact has current evidence.
- `Mic ·requested USB input ·routed input unavailable`, without declaring playback disconnected.

Keep private route/host identities local. Use readable labels and accessible targets of at least48dp. Provide no executable diagnostic shell or raw arbitrary command field.

## Verification and valid blocked outcome

1. Test status precedence with actual production policy fixtures for current, stale and replaced owners.
2. Test missing measurement versus zero amplitude, zero read versus positive silent samples, and counter reset.
3. Test input and normalized format labels independently, including root duplicated mono and unavailable negotiation.
4. Test separate playback/mic rows and partial failure without claiming current runtime mixing support.
5. Prove refresh is visible-only and observation entry invokes no start, grant, route or connection action.
6. Gate the actual Kotlin/native/Android adapters, not only copied policy helpers.
7. On an identified authorized phone, exercise silence, signal, full-scale warning, denial, route loss, root failure and HOLD/resume against the same owners shown by the normal source face.

**Blocked:** a source owner lacks the required current observation. **Evidence:** its exact published fields and lifecycle show that gap. **Best current result:** render supported fields with the missing item explicitly unavailable. **Next step:** add the smallest bounded owner-tagged observation seam, then test it. Unsupported observation is not full R17 acceptance.
