# Decisions, alternatives and traps

## How we chose

Three isolated writers generated candidates without reading one another. A separate worker then criticized all three.
Root checked key source findings and synthesized the selected program rather than concatenating the candidates.

| Candidate frame | Useful contribution | Why it is not the whole plan |
|---|---|---|
| A: fewer owners, provable source ownership | Explicit completion, current-owner identity, native event consumption and typed settings | Bundled reader/ring/service/extraction changes cross too many boundaries before Android tests exist |
| B: the beam is the product | Fresh smoke receipts, fixed scenes, separate pixels/semantics, settings and archive checks | Needs tighter source-face/reader priorities and fewer standalone verifier tools |
| C: remove the proposed architecture layer | Test current adapters first, retain valid platform owners, extract only demonstrated conflicts | Needs B's full signal/UI/delivery coverage to become a complete program |

**Pick:** C's restraint, B's evidence-first sequencing, and A's explicit ownership contracts at narrow seams.
The cost is accepting some large source files for longer and doing test work before visible refactoring.
The benefit is fewer simultaneous moving parts while preserving the instrument Ben already likes.

## Settled technical choices, do not re-ask

1. Keep Kotlin/Compose, Media3, Rust JNI and the shared Phosphor engine. No rewrite justified by line count.
2. Keep microphone lifetime in Activity and projection lifetime in CaptureService. No new background-mic guarantee.
3. Use one pure SourceFace projection. Do not require PlaybackService merely to publish mic state or add mic to Media3.
4. Keep existing STOP correlation distinct from resource completion. Every old request still receives its cleanup reply.
5. Classify terminal reader health and test production adapters before considering owner-tagged JNI migration.
6. Keep the accepted B6 native event receiver unique. Metadata projection does not consume it again.
7. Use typed portable settings with current storage/schema. Do not add an event log or persistence framework.
8. Separate semantics, beam pixels, decoded samples, Android integration and physical latency claims.
9. Extend existing pm3 and fixture suites. Do not create a new fleet of registered tools for this documentation request.
10. Freeze measured performance uncertainty before candidate comparisons. No invented FPS promise or permission to stress Ben's GPU now.
11. Keep private provenance and sanitized public distribution inventories distinct while preserving exact build-source bytes.
12. Preserve current repair ordering. New roadmap entry needs Phase 18 behavior acceptance, source recompile and implementation authorization.

## Attractive ideas we rejected

| Trap | Why it fails | Selected alternative |
|---|---|---|
| One universal source service | Could create a new mic lifecycle and background policy merely to unify labels | Pure projection over existing owners |
| Extract every large file first | File size does not identify a broken contract, and mechanical movement can hide changed ordering | Production-adapter tests, then one proven boundary |
| Replace all readers and rings together | Couples Android consent, stop replies, JNI and remote cleanup into one risky migration | Terminal classifier plus generation/cleanup tests |
| Declare renderer unavailable after timeout | The old Android window can still be owned after the callback returns | Retain explicit unresolved risk and compile a separate lifetime design if needed |
| Trust nonempty selftest.json | An old or failed report can satisfy current shell success | Correlated run/build/image identity and rejection fixtures |
| Count a skipped GPU test as passed | Cargo can exit zero without a GPU comparison | Adapter identity and nonzero completed scenes |
| Use one screenshot to prove everything | Compose may omit SurfaceView, and pixels do not prove audio ownership | Separate semantic, surface-inclusive, sample and lifecycle evidence |
| Fix contrast by recoloring the beam | Changes the instrument instead of repairing chrome access | Stable chrome contrast/focus cues |
| Call a private Git archive public-safe | Exact source can still include private plans and receipts | Reviewed public content manifest and explicit omissions |

## Architecture after the selected work

```text
Activity -> consent, view lifetime, mic owner
CaptureService -> projection + capture recorder owner
PlaybackService -> existing local/capture-mirror/remote playback authority
        observations + accepted identity
                    |
             pure SourceFace
                    |
          Compose source/transport face

Existing native event consumer -> accepted local playback state
Rust audio/render paths -> actual samples and beam
Typed portable settings -> existing persistence -> UI/native configuration
Debug/test boundary -> bounded receipts, never product authority
```

There is no new universal runtime controller in this diagram.
The separate critic's final findings and source identities are recorded in [PLAN-REVIEW](verification/PLAN-REVIEW.md).
