# U1: opt-in no-track console key bed

## Context and outcome

The mobile console gets tactile keys without replacing existing saved looks.
This unit uses Android Compose and the current authored appearance workflow.
Only the console key bed with no track and no remote transport uses the new rendering.
Title, seek, sheets, stage, root, HDR, engine and playback dispatch remain unchanged.

## Authored appearance contract

- AppearanceValue.lookVersion is1 by default and allows only1 or2.
- Version1 omits look_version in every encoded value. Existing bytes and hashes remain exact.
- The optional wire key look_version accepts integer2 only. Explicit1, other numbers,
  wrong types and unknown keys fail strict decoding before persistence.
- The editor toggles only its draft. PREVIEW, APPLY, SAVE and CANCEL retain their existing owners.
- Named records, style edits and archives retain the authored choice. Reset selects the
  original AMOLED value and retains named looks. Curated and legacy records stay unchanged.
- RoomStyle carries the choice. Its coupled FEEL changes must not discard it.
- Fresh tactile default is deferred: preinit meaningful-settings detection is not proven.
  Missing appearance_state does not establish a fresh user. Explicit opt-in completes this unit.

## Presentation contract

Static tokens come from authored colors, never the polled beam palette. An opaque well and
key faces make the contrast background exact, including Glass. Preserve authored corner radius.
AMOLED field and well remain true black. Its face is141414, never a whole-field gray wash.
Labels and primary boundaries target4.5:1. Essential edges, focus and disabled marks target3:1.
Compute nearest contrast-safe ink blends per surface, with a deterministic black/white fallback
for extreme authored palettes. No saved color is changed. Bevel catch and shade encode depth.

Primary play key minimum56dp. Other keys minimum48dp. Wrap rows at narrow width or large fonts,
let labels grow without ellipsis, and retain fixed-size vector symbols. Press sinks content1dp
and reverses bevel polarity. Selected keys have a bar and tick. Disabled keys have dashed borders
and semantic reason. Focus has a2dp ring separated by a1dp plane gap. No new animation or timer.

Real transport state selects play/pause. Display-only mode keeps HOLD/LIVE labels and dispatch.
Capture gates, MODE random-arm, SRC action, S9 tap/pull owner and console swipe remain intact.
S9 gains keyboard/semantic activation without a second pointer owner.

## Verification and boundaries

Native focused tests cover byte-exact legacy appearance encoding, strict optional-field rejection,
workflow preview/cancel/apply/save/reset and import, style-copy and editor preservation.
Token tests measure contrast against actual opaque faces and well, including black and pathological
custom palettes. Native focused compilation and policy tests supplement, not replace, Android proof.
Parent owns full Gradle builds, Git, device checks and independent visual/code reviews.

Device checklist: four families and old/new comparisons; no-track playing/HOLD/LIVE/capture gates;
pressed, selected, focus and disabled rendering;320dp and font1/1.3/2 portrait/landscape;
S9 tap/pull/keyboard, MODE random-arm, SRC and upward settings reveal; hidden/PiP/reduced motion;
unchanged track/title/seek and multi-open settings. Save/import and restart must preserve opt-in.
Screenshots must inspect vector shapes at actual displayed size and compare transparent geometry
against the opaque face. No icon packaging assets change in this Canvas-only unit.

Blocked is valid if compilation, migration preservation, contrast or actual target layout fails.
Record the exact bottleneck and useful partial result. Do not claim visual acceptance from tests.


## Visual round1 correction

Muse scored5/10. Keep the review and its font2 addendum unchanged.
The prior340dp threshold was incorrectly applied to the inner well width.
The ASUS normal inner width is about306dp, derived from the actual348dp card.
Use a compact56/64/56/48dp row when its keys and12/8/8dp gaps fit.
A320dp window has about234dp usable inner width and uses two rows at normal fonts.
Font2 uses full-width rows. Fixed geometry vectors must cover both windows at1/1.3/2.

The old4dp internal focus gutter reduced visible48dp targets to40dp silhouettes.
There is no app density override in current source. Scope the tactile key bed to the
Android view resource density, retain font scale, and draw focus outside each key.
The key silhouette itself now fills its48dp or56dp minimum measured hit layout.
Bed padding and gaps reserve the exterior focus ring without expanding touch ownership.

The outer silhouette remains continuous with contrast at least3 against the well.
Do not paint a bright inner border uniformly on all sides. Raised top/left receive the
light step; bottom/right receive the recessed step. Pressed/selected reverse intentionally.
Shared status overlap at font2 is a pre-existing baseline TODO, outside U1.


## Visual round2 correction batch

Muse7 and DeepSeek8 bounded scores remain immutable, separate from source scores.
SIGNAL CHECK on the native black plot uses a local readable ink, never a saved palette edit.
The status band measures its two headings with the actual font resolver. If they do not fit,
stack the unchanged text groups and wrap their data. Position SIGNAL CHECK below the measured
band height with8dp clearance. No additional status data or actions are introduced.

Focus uses the full authored accent if it clears4.6:1 against the well, otherwise a local
computed color. The2dp ring stays separated from key and well step. Increase well gutter3to5dp
and reduce new-only card padding2dp to keep approved key positions. Never change hit bounds.
Move designators to the3dp top-left corner. Main legends remain centered. If actual text boxes
intersect at larger fonts, grow only that key enough to separate them rather than shift its legend.
The inherited loud outer frame remains a baseline hierarchy TODO. Dark/Glass brightness is a
metric-definition issue, not authority to repaint authored nonblack backgrounds.
