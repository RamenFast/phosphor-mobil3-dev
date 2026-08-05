# Relay routing boundary

**Date:** 2026-08-05
**Status:** accepted
**Scope:** current Phosphor Mobile PC relay deployment and Android route ownership

## Context

Protocol v2 is raw TCP without application-layer authentication or encryption. The retained Android implementation previously exposed automatic, Wi-Fi, and mobile choices by calling `bindProcessToNetwork`. That API changes the default network for the whole process and can move the Rust-owned relay socket away from the Tailscale VPN.

The current host store already accepts only Tailscale MagicDNS names, `.ts.net` names, legacy `.tailnet` names, and IPv4 addresses in `100.64.0.0/10`.

## Decision

1. The current mobile product supports PC relay endpoints through Tailscale only.
2. Android and Tailscale own route selection. Phosphor does not request a physical Wi-Fi or cellular network and does not bind the process to one.
3. Direct LAN endpoints, public DNS endpoints, public-internet exposure, and other private overlays are outside the current product boundary.
4. Peer loss or route loss remains visible as reconnecting or unavailable. It is not reported as measured silence.
5. Protocol authentication, encryption, discovery, and connection polish remain deferred to the later networking stage.

This decision supersedes the broader trusted-network and private-overlay deployment wording in `decisions/2026-08-05-product-scope-reset.md`. It does not change the decision to retain the PC relay.

## Consequences

- Remove physical-network selectors, process-wide binding, related permissions, persistence, and settings-archive fields.
- Keep Tailscale host validation and user-initiated dialing.
- Test MagicDNS, `.ts.net`, legacy `.tailnet`, and `100.64.0.0/10` endpoints without forced route ownership.
- Do not claim direct LAN support until a later accepted decision defines its transport security and routing behavior.
