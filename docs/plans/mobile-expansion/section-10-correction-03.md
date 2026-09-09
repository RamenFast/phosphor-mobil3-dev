# Appearance correction 03: readable pause status

The immutable full appearance ROUND2 report at4f285a6 remains7/10. Its R2-F2 identifies the pause-state label as a separate consumer drawing Light muted ink directly over BLACK or a held native image.

Before implementation: give this label an opaque authored-plane backplate and resolve its normal text using the existing readableOn(plane) presentation path. Keep the label independent of BAND visibility. Do not modify authored appearance bytes, native BLACK/HOLD behavior, source ownership or status wording.

Check the actual palette composite over black and white held content at4.5:1, with byte-identical authored document. Add a source adapter assertion for this exact label/backplate pair. Run the complete Android gate only after the manual writer releases. Physical pixels, accessibility and Android callback acceptance remain unproven by host checks.
