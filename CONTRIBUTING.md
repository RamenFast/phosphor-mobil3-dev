# Contributing to Phosphor Mobile

This is the development line. Everything lands here first. The stable repo, [phosphor-mobil3](https://github.com/RamenFast/phosphor-mobil3), receives tagged releases from this one and is what the Play Store listing points to.

## Where things go

| You want to | Go to |
|---|---|
| Report a bug in the store build | [stable issues](https://github.com/RamenFast/phosphor-mobil3/issues) |
| Report a bug in a dev build, propose a feature, or send code | here |
| Show off a beam, ask a question | [Discussions](https://github.com/RamenFast/phosphor-mobil3-dev/discussions) |

## Build it

```bash
./scripts/bootstrap-android.sh   # one-time: JDK, SDK, NDK, cargo-ndk into .toolchain/
source scripts/env.sh
./dev/pm3 doctor                 # tells you what is missing
./dev/pm3 build                  # debug APK
./dev/pm3 install && ./dev/pm3 run
```

The Rust beam engine lives in the desktop [phosphor](https://github.com/RamenFast/phosphor) repo and is expected as a sibling checkout at `../phosphor`. `pm3 doctor` checks for it.

## Send a change

1. Fork, branch from `master`.
2. Keep one change per PR. Small is fast.
3. Run `./scripts/ship-check.sh --quick` before pushing. It is the same gate a release uses.
4. Say what you tested on: phone model, Android version, source (file, capture, mic, relay).
5. Rust warnings and Kotlin lint should be clean. `pm3 smoke` runs the on-device smoke.

No Python in the tree, no analytics SDKs, no stubs. The `nopython`, `privacy.tracking`, and `antistub` gates enforce this.

## Good first contributions

- A new scope view or geometry effect in `rust/` (see how `kaleido` is wired end to end).
- A theme. Palettes are data in `app/src/main/kotlin/dev/phosphor/mobil3/ui/`.
- Device receipts: run a dev build on a phone we have not seen, file what worked and what did not.
- Translations of the in-app manual.

## License

GPLv3. By contributing you agree your work is licensed the same way.
