# Distribution, permissions, and signing specification

**Status:** Binding

## 1. Compile-time distribution seam

Add a Gradle flavor dimension named `distribution`:

```text
play
fortress
```

Recommended identities:

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

### 2.0 Existing package lineage gate

The working MVP already uses `dev.phosphor.mobil3` with a local release signature. Before Play enrollment or installing flavored builds over real user data, Ben must choose and receipt one lineage strategy:

1. **Preserve update lineage:** use the current acceptable release app-signing key as the Play app-signing key where Play Console enrollment supports that choice, then move Fortress to the suffix package.
2. **Start a new Play lineage:** use a new/Google-generated Play app-signing key, accept that it cannot update the currently installed differently signed package, and provide explicit settings/theme export-import or a documented uninstall/reinstall migration.

Android will not update an installed package across unrelated signing certificates. A package-name match does not solve that. Do not let a local MVP and Play build silently compete for `dev.phosphor.mobil3` with incompatible certificates.

The intended steady state remains Play on `dev.phosphor.mobil3` and Fortress on a co-installable suffix, but implementation must inspect the current installed/release certificate and make the lineage decision before data-bearing device migration.

### 2.1 Play

- Upload key signs submitted AABs.
- Google Play App Signing holds the app-signing key used on installed Play builds.
- Enroll and record the Play signing certificate SHA-256 from Play Console.
- Pin the installed-app certificate, not only the upload certificate, in Nexidex trust configuration.
- Preserve signing lineage and recovery material according to Play policy.

### 2.2 Fortress

- Use a dedicated Ben-controlled release key, not the Android debug keystore.
- Store private key material outside the repository.
- Record certificate SHA-256 and rotation procedure.
- Prefer the same estate trust family as Nexidex only if the certificate governance is intentionally shared.
- Do not require the Play key to build or use Fortress.

### 2.3 Local development

Local dev signatures may be enrolled explicitly in a development trust file. They never automatically inherit production trust.

## 3. Nexidex compatibility

Nexidex must accept identities through a certificate allowlist and signing lineage, not through `BuildConfig.DEBUG` or package name alone.

The UI shows the connected identity distinctly:

- `PHOSPHOR PLAY`
- `PHOSPHOR FORTRESS`
- `PHOSPHOR LOCAL DEV`

Capabilities differ by identity. A Play identity cannot claim Fortress capabilities even if a state flag is tampered with.

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

For every Fortress candidate:

1. Confirm dedicated release signing, not debug signing.
2. Confirm co-installability with Play.
3. Confirm Nexidex certificate identity and capability grants.
4. Confirm elevated features fail closed without Shizuku/ADB.
5. Confirm audit and one-tap stop paths.

## 11. Ask-for-forgiveness interpretation

Engineering continues on the full product vision even when a capability cannot enter Play. It does not mean misleading reviewers, hiding behavior, or violating user consent.

The correct response to a Play conflict is:

1. preserve the capability in Fortress;
2. remove it by compilation from Play;
3. provide the best honest Play-safe alternative;
4. document the distinction clearly;
5. revisit policy with evidence later.

This keeps the originating application intent without endangering the public listing or user trust.

## 12. Human gates before implementation/release

Ben must eventually provide or approve:

- final Fortress package name;
- Fortress signing-key governance;
- Nexidex and Fortress certificate SHA-256 values;
- Play Console signing certificate after enrollment;
- whether any overlay attempt enters the first Play review;
- exact six curated rooms if existing IDs differ;
- final theme and preset license policy;
- store listing/legal/price submission;
- diagnostics backend, if any.

These decisions do not block writing or implementing the neutral architecture first.
