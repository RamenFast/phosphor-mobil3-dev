# Section 6 correction 02: inactive storage is not a track event

## Context and outcome

The immutable round-2 critique scored exact `1e68b6327252b22424839b5f6c8464cd5731f3da` at 7/10.
Its SHA-256 is `3dbc5fb961fb3b904b12fecd9934dcb3a75ee48c6baa0e0f2069289b90dd5b70`.
The original remains unchanged in `critiques/section-06-round-02.md`.
This correction is specified before native edits. It addresses the two reproduced inactive-storage TRACK steps, not Android acceptance.

## Required behavior

1. Editing an unselected saved RGB slot preserves the active beam, timer leg, shuffle bag, and random generator.
2. With generated automatic color active, editing or deleting saved storage does not generate another color.
3. An exact deletion still validates the old bank and shifted mask before any state mutation.
4. Deleting an unselected slot remaps surviving slot identities and queued bag indices without changing their order or active color.
5. Deleting a selected slot is a real membership change. Preserve the prior identity remapping and no-immediate-repeat behavior, including duplicate RGB slots.
6. A real owner, selected RGB, selected membership, or active shuffle change retains the existing activation behavior.
7. An explicit apply may still retire a temporary roll. Timing changes apply at the next leg boundary.

The native comparison uses active selected slot identities and colors, not the entire saved bank. For an exact unselected deletion, compare against the old bank after its validated index remapping. Generated ownership ignores saved storage. No audio, display ownership, JNI schema, archive format, or persistence path changes are needed.

## Worked examples and validation

With red and green selected in TRACK, editing an unselected blue slot leaves red displayed until an actual track event.
With generated TRACK active, deleting saved red leaves the generated RGB and RNG state unchanged.
With selected slots 1 and 3, deleting unselected slot 0 maps them to 0 and 2. The current identity and remaining shuffle order map once, without a draw.

Production native tests will assert these outcomes for TRACK and TIMER. Retain all 13 existing policy tests, including selected deletion with duplicate RGB. Run the full frozen native, Android unit/lint, dual-APK, GPU and source-boundary gates. Keep later independent correction assessment separate from the original report.

**Blocked outcome:** any failed state or integration check leaves this correction unaccepted. Device timing, clicks, accessibility, and pixels remain unproven until tested on the authorized non-root device.
