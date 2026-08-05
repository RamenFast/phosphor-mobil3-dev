# Serious todos

## Primetime cleanup

- [ ] Remove the Nexus AIDL, Binder, provider, authority, tailnet runtime, and tests.
- [ ] Remove product-control verbs from `dev/pm3` while keeping the developer CLI conformant.
- [ ] Replace causal authority and audit persistence with direct user-facing settings.
- [ ] Migrate the legacy HUD value once, then delete obsolete Nexus and audit state.
- [ ] Remove dormant trial and entitlement scaffolding.
- [ ] Collapse Play and Fortress into one debug/release product.
- [ ] Add `.debug` application ID suffix.
- [ ] Lower `minSdk` to 29 and add API 29, 31, 34, and 36 compatibility paths.
- [ ] Request microphone permission before MediaProjection consent.
- [ ] Remove or contextually request `POST_NOTIFICATIONS`.
- [ ] Mark microphone hardware optional.
- [ ] Add an in-app privacy summary and stable HTTPS policy link.
- [ ] Prove fresh-install network silence until explicit remote use.
- [ ] Run Android unit, lint, build, bundle, manifest, dex, dependency, and 16 KiB checks.
- [ ] Run locked Rust core and relay tests, formatting, and clippy.
- [ ] Run live local playback, capture, PiP, rotation, and Tailscale relay tests.
- [ ] Build one canonical signed release and verify the exact APK on the S25.

## Deferred networking work

- [ ] Add relay authentication, host identity, and application-layer encryption in the later relay-polish stage.
- [ ] Improve first-run remote setup without changing protocol v2 during cleanup.
- [ ] Revisit relay geometry decimation, s16 transport precision, and JNI capture allocation after the clean release.

## Deferred product work

- [ ] Define the next two core features with Ben after repository cleanup.
- [ ] Revisit commerce only through a separate product and Play Billing decision.
- [ ] Revisit broader ABI support after the arm64 release is stable.

Archived ProjectM, root, Shizuku, ADB, and Nexus plans are not active todos.
