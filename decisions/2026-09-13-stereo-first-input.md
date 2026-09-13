# Stereo-first input · 2026-09-13

## Ben's direction

Ben explicitly requires stereo as the default for Phosphor development on every phone,
tablet and input device. Independent left/right content gives the scope useful spatial shapes.
Source: Prime session `01a09987-2c87-776d-aac8-f9a9d8765b01`.

## Implementation and evidence

Prefer genuine two-channel capture before mono fallback across supported formats.
Preserve explicitly chosen input devices. Do not silently pick an unrelated accessory.
Keep client channel count, platform device format and demonstrated L/R independence distinct.
A stereo buffer containing duplicated mono does not prove stereo capture.
If Android or the hardware supplies only mono, say so clearly and retain a usable,
truthfully labeled fallback. Do not block all ordinary microphone recovery or invent a root repair.
This narrow negotiation/truth correction belongs in Unit 1. It does not authorize a mixer rewrite,
recording, new relay protocol, S25 operation, system changes or a purchase.

## Checks

- All viable stereo candidates precede mono candidates, including rate/encoding fallback.
- Candidate bounds do not accidentally discard every mono recovery candidate.
- Explicit device and route-loss ownership remain unchanged.
- Actual route, client format and device format are reported separately when they differ.
- Distinguishable L/R fixtures retain separation through the visual path.
- ASUS built-in capability is measured. The earlier device48k mono receipt is not stereo acceptance.

## Source diagnosis at pre-change baseline

`MicrophoneRoutePolicy.candidates` iterates `monoFirst=false` first, then tests
`(count == 1) == !monoFirst`. That selects mono before stereo at each rate.
The comment and variable name obscure the actual order. This is a source-proven default
selection defect, not yet proof that the ASUS provides independent physical stereo.
ASUS audio policy advertises input masks 0x000c, 0x0010 and 0x0030 on built-in ports19/21.
The policy advertisement is capability evidence, not observed two-channel capture.
