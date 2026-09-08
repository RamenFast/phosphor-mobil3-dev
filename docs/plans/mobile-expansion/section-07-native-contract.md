# Instrument setup native admission

## Context and wire shape

The committed section-07 instrument contract defines R16. The Kotlin core owns portable records and setup encoding. Native application consumes only `InstrumentPresetCodec.encodeSetup`, never record metadata or arbitrary preferences.

The setup uses the exact snake-case field set in the core handoff. Nested light contains `preset`, RGB `slots`, `selected_mask`, `seconds`, `clock` (`TIMER` or `TRACK`), `generated_auto`, `shuffle`, `random_interval`, `interval_min`, and `interval_max`. Every field is required. Unknown or duplicate fields, wrong types, nonfinite values, and invalid ranges are rejected. Native input is at most 16 KiB of UTF-8. This bound exceeds the complete six-slot canonical setup and limits JNI parsing allocation. The portable collection retains its separate 1 MiB bound.

## Admission primitive

Each immutable candidate belongs to one request with Pending, Committed, Cancelled, or Rejected state and a monotonic deadline. The render owner admits only Pending requests before that deadline. Cancellation before admission makes later execution inert. Capability rejection changes only the request result. A timeout after an already committed admission returns that exact committed outcome, not a guessed rollback or retry.

Admission and the finite CPU assignment run under the request's short mutex. No GPU, driver, surface, waiting, persistence, or source command is permitted inside that assignment. Observers cannot see a partially committed request. The render loop handles one complete setup before starting another live frame. Existing HOLD images remain unchanged.

The first bounded implementation adds the typed decoder and host-tested request primitive. It is not an exposed app feature until the renderer, JNI, Activity owner, persistence, and UI use those exact primitives. No stub may report successful application.

## Verification

### App-owned JNI adapter

The JNI adapter reserves and queues one request on the calling thread, returning a positive process-unique ID. Kotlin will keep that ID before scheduling its off-main wait. Reservation errors use negative codes and never queue a command. The adapter checks UTF-16 length before copying the Java string, then the UTF-8 parser bound.

At most four request receipts can be retained. The caller explicitly releases its own receipt after consuming the result or retiring its Activity. Release cancels Pending work before discarding the lookup. A queued render command holds its own Arc, so discarded Pending work remains cancelled. Releasing one ID never affects another. Exhausted storage rejects the new request without evicting an unresolved result.

The fixed admission deadline is 750 ms. Off-main await returns Committed=1, Cancelled=2, Rejected=3, or unavailable ID=4. Cancel reports the exact terminal outcome if already committed. No missing receipt is interpreted as success. Renderer channel failure cancels and removes only the new reservation.

One renderer command installs mode, cancels an obsolete flip, sets local manual/auto gain, geometry, beam, glow, grid, focus, DSP reconstruction, and the existing light cycle. Remote geometry capability is checked on the actual render owner at admission. UI-only random arms, ranges and grid-data preference travel in the same validated setup but remain authored UI state to publish after the matching result. They do not create another native clock or synthetic track event.

The native seam still has no user-facing apply path until the Activity serialized owner, persistence, rapid guard, source-change handling and preset UI are integrated and tested.

Decode actual Kotlin setup vectors with the production Rust parser. Check all required fields, unknown/duplicate keys, exact integer and Boolean types, six-slot membership, sorted unique mode bans, armed eligibility, and numeric boundaries. Retain the existing native light validator rather than a divergent color policy.

Use bounded barrier tests against the production request primitive. Cancel or expire a queued request before admission and prove its assignment never runs. Reject changed geometry capability without mutation. Commit one complete candidate and verify a later cancellation or delayed reply still returns Committed. No host test proves Android scheduling, JNI execution, rendered pixels, or durable persistence.

**Blocked outcome:** a schema or lifecycle mismatch leaves the native seam unconnected until corrected. Root owns integration and the full released-source Android gate.
