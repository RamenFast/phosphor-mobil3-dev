# R13 round3 disposition and carried acceptance gaps

The immutable [third review](critiques/section-05-round-03.md) scores exact mobile0591d7345cdbc701dd2549ef62037959c1fbdaca/shared0ffd658d7f19e68180c2720e0500b23644619e90 at7/10. SHA256 is `3b520a8e1cb964474891cbe5ca25e272e3f69dcf2eee5dc994c7a533e63685b5`. The private readset SHA256 is `cfd10527fd07f6ab414c4871ac9a9ea63dfe77692628864624ad70bf1402bf63`. Both original artifacts were verified mode444. The report was retained byte-for-byte and Mizaru stopped after releasing its read-only scope.

The review supports correction02's exact retained-commit fencing, reset publication, final local producer retirement and per-controller initial pause seed. The successful clean526 JVM/94 native/3 host-GPU gate remains valid in its stated scope. Neither that gate nor the7/10 review accepts the whole R13 outcome.

Two material boundaries remain:

1. Root PCM still lacks a producer-coordinated read epoch/acknowledgement. A delayed old packet can arrive after visual ingress was cleared. App pre-read and remote identity fences also do not establish actual AudioFlinger/network buffered-source age. Received timestamps must not be presented as source timestamps.
2. A render can drain old work, stall, then first submit/present it after resume or retirement completed on another thread. Token-checked commit rejects it and the next new-token clear removes its energy, so this no longer corrupts accepted HOLD history. It is nevertheless post-boundary application output, not merely already-submitted panel scanout. The earlier physical-scanout disclosure does not prove this boundary closed.

The next correction must distinguish requested CPU transition from render-owner fresh-presentation completion, with a finite integrated drain/admission/submission check. Do not hold ring, meter or History across GPU/driver operations or delay urgent audible pause. A token check immediately before submission alone still leaves a check-to-submit race. Root epoch/PCM coordination and actual source-age evidence remain separate work. No unreviewed implementation of that protocol is implied here.

Three of the maximum four section-level critique rounds have been used:6,7,7. One remains for a material corrected candidate, not a repeat of the same source. Independent color/preset work proceeds while these requirements remain explicitly open. No device installation, Android callback acceptance, true stereo, SoundCloud, audibility or latency acceptance follows from this disposition.
