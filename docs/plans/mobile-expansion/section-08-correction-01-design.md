# Signal correction01 design

This addendum precedes corrections outside the immutable R17 round1 checkpoint `15e4072e86054af15f483f395d5adc8bcb09803f`. The independent report remains unchanged when delivered.

## Local output timing

The native local getter has cumulative output counts, not a producer timestamp. A positive count difference only proves progress between two observations. Never stamp that increment with the newer observation time as though playback had just produced it.

Track the previous observation time with the existing owner and count. Compare only monotonic, valid same-owner snapshots separated by at most the named freshness window. Owner changes, count regressions, failed reads, backward time and larger gaps clear the progress bound. A valid increment carries the earlier observation time as a conservative upper bound on its age, explicitly labeled as a bound. Reopening after a long hidden interval establishes a new baseline without claiming samples are arriving. No new poller or callback work is required.

Check actual local JSON-to-presentation behavior for long hidden intervals followed by pause, short continuous progress, repeated counts, source replacement, failed snapshots and backward time. Existing terminal-state precedence remains.

## Relay wire full scale

Protocol-v2 A blocks are s16le stereo. Their actual decoder divides signed samples by32768. Therefore positive32767 is a full-scale wire rail below float1.0. Signal aggregation must retain this representation provenance and count both negative32768 and positive32767 rails. Near-rail32766 is not full scale. This says nothing about original recorder format or analog clipping.

Use a named wire-PCM16 observation path at the existing successfully decoded A callsite. Keep the float values and audible queues unchanged. Add a real SessionMedia decode-to-aggregate fixture for both rails, adjacent values, channel separation and silence. Expose wire encoding separately from unavailable original recorder format. No target execution or source takeover belongs in this correction.

## Protocol counts and display-tap provenance

Root PCM and independent progress messages both advance the existing aggregate observation count. Label that count as received PCM/progress observations, not completed AudioRecord reads. Keep direct mic/standard recorder read counts labeled as actual completed reads. Root progress timestamps remain app-receipt times, not helper capture timestamps.

The existing GridData reading lacks an owner and measurement timestamp. Keep that normal scope display behavior unchanged, but show its diagnostic tap peak as unavailable until those fields exist. Do not attach a prior-source peak to a new selected input, and do not consume another stats tap.

Without a matched selected input, do not append foreign transport extras. With an input, playback metadata must match its kind and its local-open or relay-session identity where those are available. A current mic cannot inherit the previous capture controller's pause state.

## File and folder selection

Opening a picker is not selecting a file. Keep the current diagnostic source kind while invoking the existing source-selection cancellation/revision path. A successful file or folder result sets LOCAL explicitly through its existing open adapter. Cancelling the picker then leaves the active source diagnosis intact without a second rollback owner. This changes no reader or playback ownership.
