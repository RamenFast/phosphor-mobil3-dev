# Section 1: exact production boundary

Date: 2026-09-08. Coordinator-authored implementation, not an independent critique.

## Change

The existing `check-play-boundary` command now parses source and merged production manifests with the existing build JDK. A private Java source helper uses the standard namespace-aware XML parser with DTD/entity access disabled. There is no new product dependency, public helper command, service, or Android build authority.

Blanket source bans on overlay/capture/notification permission names are replaced by exact declaration and component rules. Current production permissions remain supported. Optional root/HUD/mic entries reserve private service identities, exact FGS roles, paired permissions, and a required specialUse purpose. This does not install or activate those features or establish their platform/store acceptance.

The parser rejects debug identities, instrumentation, unknown permissions/components, exported private helpers, unprotected AndroidX entry points, unsupported SDK/hardware requirements, expired required permissions, split launcher routing, malformed XML, external DTDs/entities, and oversized input. Reviewed AndroidX merged entries remain allowed. Existing archive/dependency/reporting/endpoint and source-routing restrictions remain active. Concrete LSPosed/Xposed identities are excluded without treating explanatory prose as an implementation.

Tool version is 3.2.0. Source success reports 12 checks. Artifact success reports 6 checks. `schema` retains existing descriptor fields and adds a strict success/error result schema. Manifest failures use the normal exit-4 error/fix envelope, including XML-decoded tab characters.

## Requirement-to-check evidence

| Requirement / changed output | Check | Observed result |
|---|---|---|
| Current production source remains valid | `scripts/check-play-boundary.sh source --json` | Exit 0, 12 source checks |
| Exact permissions, roles, exposure, lifecycle capability declarations, XML parsing | Actual private parser compiled with JDK `--release 17 -Xlint:all -Werror`, then `ManifestBoundaryTest` | 91 checks passed, including the real main manifest and synthetic positive/negative variants |
| Approved HUD declaration no longer trips the old name ban | Existing artifact command against a bounded synthetic archive and permitted HUD manifest | Exit 0, 6 artifact checks |
| Exported HUD is rejected through the public interface | Same command with only `exported` changed | Exit 4, `manifest_boundary_violation`, nonempty fix |
| Decoded control characters and malformed XML cannot corrupt CLI output | Tab-permission and truncated XML fixtures through the public command | Valid single error objects with exact declared keys |
| Strict result schema remains discoverable | `schema --json` and structural JSON checks | Success/error alternatives and success data reject additional properties |
| Retained artifact boundaries | Existing symlink, path escape, malformed/missing archive, retired identity, and split-string fixtures | Passed in `scripts/test-play-boundary.sh` |
| Release safeguards remain active | `scripts/test-release-gates.sh` | Passed isolated dirty/untagged/mismatched/incomplete-source and absent-signing rejection checks |
| Owned changes preserve source hygiene | `bash -n`, `git diff --check`, protected archive SHA-256 manifest | Passed |

Raw evidence is in ignored `dev/scratch/mobile-expansion-20260908T001819Z/section-01-*` logs. These are actual tool checks with synthetic boundary inputs where stated. No production release artifact or store approval is claimed. The previously recorded Android/native/relay baseline remains separate.

## Review and recovery

Independent Astra critique is blocked by the unanswered required routing confirmation. No reviewer has run and no score or review round is assigned. Continue independent work while carrying this gate under R15.

Revert only the owned boundary implementation commit to restore the preceding 7153570 checkpoint. No app install, permission grant, kernel setting, system file, phone source, or audio route changed in this section. The original installed APK/settings backups remain valid.
