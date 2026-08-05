# Primetime cleanup phase 00 baseline

**Recorded:** 2026-08-05 UTC

## Source checkpoint

- Branch: `release/phosphor-2.0.0`
- Commit: `1b2a69eefb7c6c6dba9d248e87e2456a9aaf2904`
- Local rollback tag: `checkpoint/phosphor-2.0.0-pre-primetime-cleanup`
- Local `master` was 3 commits ahead of `origin/master`.
- The release branch was 31 commits ahead of local `master`.
- The release branch was 24 commits ahead of `origin/release/phosphor-2.0.0`.
- Both comparisons were linear. No independent branch commits were found.

## Protected material

The working tree started with one modified file and two untracked files. The approved cleanup preserved each file byte-for-byte under `docs/dev/archive/2026-08-05-scope-reset/protected/`.

| Original path | SHA-256 |
|---|---|
| `Phosphor build.md` | `beb8ae3a05b903580f70fb41bf5de3db99cfadae103ae0e929c19d8b406a3583` |
| `handoffclaude.md` | `57d8e336941b76666ae5f074fd9d591ec0c3f20d1edbc4d71d03d5791e8e465a` |
| dirty `docs/ARCHITECTURE.md` | `0b4a7b816114433cc110764f55f05ceb28481ae914cf59f113ac6fdbc0cd5133` |

`sha256sum -c` passed for all three archive copies before the original working-tree files were reconciled.

## Toolchain and product baseline

- Android application version: `2.0.0`
- Android version code: `20000`
- Compile SDK: 36
- Minimum SDK: 35
- Target SDK: 36
- Gradle wrapper: 9.3.1
- Rust toolchain: 1.96.0
- Android ABI: `arm64-v8a`

The cleanup decision changes the minimum SDK to 29 while retaining compile and target SDK 36.

## Artifact finding

Six local Android artifacts and many historical validation artifacts existed before cleanup. Several differently hashed APKs shared the same package version. No existing artifact is a release candidate.

The canonical release must be rebuilt from the final tagged source. Its build manifest must bind the commit, package, version, signer, and hashes together.

## Rollback

Restore source from `checkpoint/phosphor-2.0.0-pre-primetime-cleanup`. Restore the three protected files from the private archive and verify them with `SHA256SUMS`.
