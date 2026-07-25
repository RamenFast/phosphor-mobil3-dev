# Phase 00 baseline receipt

**Captured:** 2026-07-25T23:12:04Z through 2026-07-25T23:16:12Z
**Release branch created after capture:** `release/phosphor-2.0.0`
**Behavior changed:** No

## Repository identities before branch creation

| Repository | Branch | HEAD | Upstream state | Dirty state |
|---|---|---|---|---|
| Private `phosphor-mobil3` | `master` | `95a97a0aa7912412cd60a61e2a052b8fb90cecaa` | `origin/master`, ahead 3 | Untracked user file `Phosphor build.md`, SHA-256 `beb8ae3a05b903580f70fb41bf5de3db99cfadae103ae0e929c19d8b406a3583` |
| Public `phosphor-mobil3-public` | `main` | `1f35f3c3e2a7709046252ce18a6fdf1c8bbea710` | `origin/main`, even | Untracked user file `Phosphor build.md`, SHA-256 `f22de087ad79c38b0178d7a7fa149f5dfa45be18b19b583e2faed88d966cc461` |
| Nexus `nexus-mobile` | `master` | `1996803c89cb00e4f56a78ca2d22a51aa4b7b507` | `origin/master`, ahead 2 | Three pre-existing untracked paths, captured by name, metadata, and SHA-256 in the sealed archive |

The private branch was then created at the unchanged private HEAD. The private protected file hash was rechecked before and after `git switch -c` and was identical.

## Existing private artifacts

| Artifact | Captured SHA-256 |
|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | `a0ddf6766158bc89904da290ad406d22369f2e6919ef2b4ab282594db39f0552` |
| `app/build/outputs/apk/release/app-release.apk` | `1f068db5bdd44541b891a3117f7cf976ce0786efca314ae3386bc62a8ffb2397` |
| `app/build/outputs/bundle/release/app-release.aab` | `6f191af88e1a232e2bf484abe406ae41915f3f8a6add1c2a3111a00227c434f3` |

These are baseline artifacts, not Phosphor 2.0 release candidates.

## S25 baseline

- Explicit serial: `100.102.2.83:5555`
- Device: Samsung SM-S931U
- Android: 16, SDK 36
- Build fingerprint: `samsung/pa1qsqw/pa1q:16/BP4A.251205.006/S931USQSBCZF5_OYNBCZF5:user/release-keys`
- Verified Boot: green
- Bootloader state: locked
- Display: physical 1080x2340, physical density 480, override density 450
- Android auto-rotation: enabled
- User rotation: 0
- Screen timeout and screensaver were observed as 30000 ms and enabled. They were not changed.
- Shizuku package is installed. Server baseline was stopped, with the truthful fix `s25-shizuku start`.

## Installed application identities

| Package | Version | APK SHA-256 | Signing certificate |
|---|---:|---|---|
| `dev.phosphor.mobil3` | 1.0.7 | `a0ddf6766158bc89904da290ad406d22369f2e6919ef2b4ab282594db39f0552` | Android Debug, SHA-256 `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d` |
| `dev.nexus.mobile` | 0.1.0 | `ca22d43f8d3be120eb2e9c0308d71a75e1f906a4a26629a6d8a3ff5108be0a0c` | Android Debug, SHA-256 `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d` |
| `dev.phosphor.mobil3.fortress` | absent | n/a | n/a |
| `moe.shizuku.privileged.api` | installed | `6e273ab0e991c4e79bc8b1bbb9b9dd739ccac1a8712a541a214078886b7b790f` | CN=Rikka, SHA-256 `268b5590e868fb08bae7e0ac413564cd1ff88f5ccff74af9dbd0dc918e30db30` |
| `com.tailscale.ipn` | installed, split APK | base `476f8622c2720175dd66190b3d0790a62feb89af9643088c056eb719dbb0f234` | Google Android, SHA-256 `5cdb295551bfe1a087fed6acda07141c6c929fa7c29bd273a7092813acc434bf` |

Package Manager reported the same current signature token for the installed Phosphor and Nexus packages, consistent with the independently extracted debug certificate.

## Baseline build and tests

Commands were run from the release branch before application implementation changes:

```bash
source scripts/env.sh
./gradlew :app:assembleRelease
(cd rust && cargo test --release)
(cd relay && cargo test)
git ls-files '*.py'
./dev/pm3 schema --json
```

Results:

- Gradle release build: `BUILD SUCCESSFUL in 11s`.
- Rust renderer: 32 passed, 0 failed.
- Relay: 15 passed, 0 failed.
- Tracked Python list: empty.
- `pm3 schema`: envelope and exit table exist, but `ts` is empty. This confirms the timestamp defect and keeps it open for the contract/CLI phase.

## Durable archives

The complete baseline includes repository status, worktrees, recent commits, artifact hashes, installed package dumps, archived installed APKs, certificate reports, device state, Shizuku state, Concourse status/doctor output, test logs, and checksum manifests.

- Working sealed capture: `/home/ben/.jcode/scratch/phosphor-2.0-baseline-20260725T2311Z/`
- Durable archive: `/media/ben/Mass storage/agenticTinkering/Codex/phosphor-2.0/baseline-20260725T2311Z/`
- Portable checksum manifest: `MANIFEST.relative.sha256`
- Manifest SHA-256 after adding baseline-test logs and consultation extracts: `3d62a359606cabb3de69f4b4113a5c5d65c8c1a4458db98124cc97a6254c8b15`

The relative manifest was verified after copying to durable storage. It currently covers 56 files including the manifest itself, baseline test logs, and the recovered dated Nexus consultation extracts.

The operational consultation rulings and current-code caveats are projected without private transcript noise in `nexus-consultation-2026-07-25.md`.

## Rollback

Phase 00 contains documentation and evidence only. Its eventual commit can be reverted with:

```bash
git revert <phase-00-commit>
```

The durable baseline archive is intentionally retained even if the documentation commit is reverted.
