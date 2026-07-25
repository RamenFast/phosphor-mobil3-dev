# Distribution, permissions, and signing specification

**Status:** Binding for Phosphor `2.0.0`

## 1. Compile-time distribution seam

Add a Gradle flavor dimension named `distribution`:

```text
play
fortress
```

Required identities:

```text
play:     dev.phosphor.mobil3
fortress: dev.phosphor.mobil3.fortress
```

The existing generic `debug`/`release` build types remain orthogonal to distribution. Fortress is not equivalent to debug.

Elevated code belongs in `app/src/fortress/` or a Fortress-only module. The Play source/dependency graph MUST NOT contain:

- Shizuku APIs or metadata;
- ADB sidecar installation/control;
- privileged shell-audio implementation;
- developer receipt endpoints that expose sensitive system state;
- Fortress-only overlay if current policy decision excludes it;
- hidden permission automation.

A CI artifact scan enforces absence.

## 2. Signing identities

### 2.0 Ratified package and lineage strategy

The working MVP and currently installed debug build already use `dev.phosphor.mobil3`. The 2026-07-25 release instruction resolves the lineage strategy:

1. **Fortress:** `dev.phosphor.mobil3.fortress`, signed by the Ben-controlled RamenFast estate certificate. It installs beside the current debug package and future Play package.
2. **Play:** `dev.phosphor.mobil3`, signed for installed users by a separate Google Play App Signing identity. The Play certificate is unknown until Console enrollment and is a release gate, not a placeholder to guess.
3. **Current debug:** preserve the installed debug `dev.phosphor.mobil3` and its data during Fortress activation. A later Play installation cannot coexist under the same package name and unrelated signer, so it requires a signed settings/theme export, archived debug APK identity, explicit debug uninstall, Play install, and import.

Android will not update an installed package across unrelated signing certificates. A package-name match does not solve that. Do not let a local MVP and Play build silently compete for `dev.phosphor.mobil3` with incompatible certificates.

The intended steady state is Play on `dev.phosphor.mobil3` and Fortress on the co-installable suffix. No build may overwrite, uninstall, clear, or migrate the current debug package implicitly.

### 2.1 Play

- Upload key signs submitted AABs.
- Google Play App Signing holds the app-signing key used on installed Play builds.
- Enroll and record the Play signing certificate SHA-256 from Play Console.
- Pin the installed-app certificate, not only the upload certificate, in Nexidex trust configuration.
- Preserve signing lineage and recovery material according to Play policy.

### 2.2 Fortress

- Use the healthy Ben-controlled RamenFast keystore outside the repository, not the Android debug keystore.
- Required current certificate SHA-256: `e4d14ce2d62983acd393f012cbce759b6c97bdcca979feeb04a97afb279d9b00`.
- Store private key material outside the repository.
- Record certificate SHA-256 and rotation procedure.
- This certificate is the Fortress estate identity. A later rotation requires an accepted Android signing lineage and a superseding decision/receipt.
- Do not require the Play key to build or use Fortress.

Release signing fails closed. If store path, store password, alias, key password, key entry, expected certificate, or signing tool is unavailable or mismatched, `playRelease`, `fortressRelease`, and release bundles fail. They never fall back to the Android debug keystore or produce an unsigned artifact under a release filename.

### 2.3 Local development

Local dev signatures may be enrolled explicitly in a development trust file. They never automatically inherit production trust.

The current installed Phosphor and Nexus packages use Android Debug certificate SHA-256 `f8dfcf73312022dfe8096c8e4c28b1d81199e0c6ce9c73c4394789fe9614632d`. That identity is development-only. It must never be described as the stable release identity.

## 3. Nexidex compatibility

Nexidex must accept identities through a certificate allowlist and signing lineage, not through `BuildConfig.DEBUG` or package name alone.

The UI shows the connected identity distinctly:

- `PHOSPHOR PLAY`
- `PHOSPHOR FORTRESS`
- `PHOSPHOR LOCAL DEV`

Capabilities differ by identity. A Play identity cannot claim Fortress capabilities even if a state flag is tampered with.

The current debug-signed Nexus installation cannot cross the production Fortress signature-permission wall. Production same-phone activation requires: Nexus export/backup, uninstall, estate-signed reinstall, import, signer verification, and bad-signer/rollback tests. Until that human-approved activation gate, use an explicitly enrolled development pair or authenticated tailnet development session and report production Binder as `signer_migration_required`.

## 4. Permission inventory model

Every capability has:

```text
id
label
distribution
Android permission/app-op/system authority
why
requested = true|false
granted = true|false
active = true|false
managed_by
fix
privacy effect
```

The UI provides `REQUEST`, `OPEN SETTINGS`, `STOP`, or `LEARN` as appropriate. It never displays a switch that lies about a system-owned state.

## 5. Capability matrix

| Capability | Play | Fortress | Classification |
|---|---|---|---|
| Internet, network state, change network state | Yes | Yes | Normal permissions; document purpose |
| Foreground playback service | Yes | Yes | Public Android API |
| MediaProjection playback capture | Yes | Yes | User consent, FGS, source opt-out |
| Microphone | Conditional | Conditional | Runtime permission only when implemented/used |
| Notifications | Conditional | Conditional | Runtime permission where Android requires |
| PiP | Yes | Yes | User setting, system behavior |
| Standard HUD inside app | Yes | Yes | No elevated permission |
| Nexidex Binder/tailnet agent entry | No in first public release | Yes, P0 | Shared schemas may remain in Play; exported transport is Fortress-first |
| Application overlay | Policy gate, default no for first Play plan | Yes | Special access, explicit opt-in |
| Notification listener | Only with concrete core use and policy review | Optional | Special access, avoid speculative scope |
| Shizuku UserService | No | Yes | Fortress-only privileged bridge |
| ADB sidecar | No | Yes | Fortress-only developer capability |
| Shell remote-submix audio | No | SPIKE | Fortress-only if proven |
| Accessibility service | No unless a concrete accessibility feature requires it | Omit unless concrete instrument need | Do not use as generic automation escape hatch |
| Google Play Billing | Yes | No requirement | Play commerce |
| Minimal diagnostics | Yes | Yes | User-controlled; disclosure depends on transport |

`CHANGE_NETWORK_STATE` is explicitly part of the existing network selector inventory and must either be justified by actual route binding/selection behavior or removed.

## 6. YOLO master control

The master control is a convenience action that requests or enables a declared bundle. It is not itself an Android permission.

Rules:

- default off;
- preview exactly which capabilities will be requested or enabled;
- one human confirmation before opening sequential system flows;
- do not retry denials in a loop;
- subordinate toggles remain visible and independently reversible;
- system-managed and unavailable items are skipped with explanation;
- Play never includes Fortress-only items in the bundle;
- Nexus can request opening the bundle flow but cannot click Android consent for the user.

## 7. Overlay policy

An overlay can be a legitimate user-facing function, but it introduces special-access scrutiny, privacy expectations, and abuse risk.

Initial release ruling:

- implement and validate in Fortress first;
- keep it absent from the first Play manifest and AAB unless a current policy review confirms the exact use, listing disclosure, permission flow, and review evidence are acceptable;
- PiP is the Play-safe fallback;
- if later submitted, explain that the user explicitly creates a floating audio visualization and can close it from a persistent notification.

Do not use Accessibility to avoid overlay constraints.

## 8. Trial and Pro

PLAY only:

- free listing;
- local seven-day complete trial beginning on explicit user action;
- no payment method required;
- one non-consumable product at $3.99 USD;
- no automatic conversion;
- purchase acknowledgement;
- restore ownership;
- pending/cancelled/refunded/revoked states;
- Voided Purchases API or equivalent backend revocation check if a backend is used;
- preserve local settings and packs after expiry.

FORTRESS is not required to use Play Billing. It may be permanently enabled for Ben/development through a compile-time distribution entitlement that cannot enter Play.

## 9. Diagnostics and privacy

Default product stance:

- Android vitals as aggregated Play stability signal, not a complete per-user crash-reporting substitute;
- local structured diagnostics;
- user decides whether automatic minimal reporting is enabled;
- first-run setup explains the choice;
- settings can disable it later;
- user-reviewed export remains available;
- no behavioral analytics or ad identifier.

If a backend verifies purchases or receives diagnostics, developer processing includes tokens/requests or reports and must be disclosed with security, retention, and deletion terms. Data Safety is written from the exact Play AAB and real network behavior, not from intentions.

## 10. Manifest and artifact gates

For every Play candidate:

1. Merge and inspect the Play release manifest.
2. Confirm Fortress components, permissions, providers, and metadata are absent.
3. Inspect AAB dependencies and strings for Shizuku/ADB/sidecar code.
4. Confirm only intended ABIs and native libraries.
5. Confirm signing certificate path and Play App Signing enrollment.
6. Review exported components and Binder guards.
7. Confirm Data Safety against packet/network behavior and backend logs.
8. Run Play pre-review checks and treat them as findings, not proof of declaration accuracy.
9. Build from a checkout with release-signing inputs removed and assert a release task fails instead of selecting debug signing.
10. Verify package, version `2.0.0`, debuggability, installed-app certificate expectation, and absence of private trust material.

For every Fortress candidate:

1. Confirm dedicated release signing, not debug signing.
2. Confirm co-installability with Play.
3. Confirm Nexidex certificate identity and capability grants.
4. Confirm elevated features fail closed without Shizuku/ADB.
5. Confirm audit and one-tap stop paths.
6. Confirm package `dev.phosphor.mobil3.fortress`, version `2.0.0`, non-debuggable release, and certificate `e4d14c...d9b00` with `apksigner`.
7. Build with every signing input independently removed or corrupted and assert fail-closed behavior.

## 11. Ask-for-forgiveness interpretation

Engineering continues on the full product vision even when a capability cannot enter Play. It does not mean misleading reviewers, hiding behavior, or violating user consent.

The correct response to a Play conflict is:

1. preserve the capability in Fortress;
2. remove it by compilation from Play;
3. provide the best honest Play-safe alternative;
4. document the distinction clearly;
5. revisit policy with evidence later.

This keeps the originating application intent without endangering the public listing or user trust.

## 12. Settings, artifact, and release rollback

Before either release distribution touches data-bearing installs, export settings and themes through a versioned inert format containing schema version, source package, source version, export timestamp, content hashes, and no executable content or secrets. Import validates schema, bounds, hashes, package-independent IDs, and reports every skipped or migrated field.

The release rollback manifest contains:

- private branch, phase commit, tag, release, and asset IDs;
- package names, versions, APK/AAB hashes, and certificate reports;
- archived installed APK identities and signed settings/theme exports;
- exact `git revert <phase-commit>` sequence;
- suffix-only Fortress stop/uninstall command;
- archived APK reinstall instructions where needed;
- signed export import command and verification;
- proof that preserved debug data still launches after Fortress removal.

Git tags/releases and historical assets are retained. A correction publishes a superseding checkpoint rather than mutating signed evidence.

## 13. Human gates before implementation/release

Already resolved by the 2026-07-25 instruction and Phase 01 custody check:

- Fortress package `dev.phosphor.mobil3.fortress`;
- Fortress RamenFast estate certificate `e4d14c...d9b00` and external key custody;
- separate Google Play App Signing identity;
- preservation of the current debug install during Fortress activation.

Ben must eventually provide or approve:

- Play Console signing certificate after enrollment;
- final Nexus signer migration activation after backup/restore rehearsal;
- whether any overlay attempt enters the first Play review;
- exact six curated rooms if existing IDs differ;
- final theme and preset license policy;
- store listing/legal/price submission;
- diagnostics backend, if any.

These remaining decisions do not block neutral architecture, flavor separation, fail-closed signing, export/import, tests, or Fortress artifacts. They do block the specific production trust or store action they govern.
