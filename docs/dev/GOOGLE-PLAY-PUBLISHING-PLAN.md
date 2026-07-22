# Google Play publishing plan

> **Product-alignment addendum, 2026-07-22:** This document still controls Google Play publication, commerce, privacy, and Console work. The broader product is now specified in `vision/` and `spec/`. Play and Fortress are separate compile-time distributions. Fortress-only agent, overlay, ADB/Shizuku, and privileged-audio work must be absent from the Play AAB. Any earlier first-release non-goal that conflicts with the new cabinet means “does not block initial Play submission,” not “must not be designed or built in Fortress.” The Play promises remain unchanged: no ads, seven full days, $3.99 one-time Pro, no auto-charge, no subscription, and minimal user-controlled diagnostics.

**App:** Phosphor Mobile (`dev.phosphor.mobil3`)
**Plan date:** 2026-07-22
**Repository baseline:** `master` at `9e1ab3b`, app version `1.0.7` / version code `10007`
**Commercial model:** Free download, seven full days of complete use, then a **$3.99 USD one-time Pro unlock**
**Product promises:** No advertisements, no subscription, no automatic charge after the trial, no account requirement, and no change to Phosphor's purpose as a focused CRT oscilloscope and audio visualizer.

This is a publication plan, not a redesign brief. Its purpose is to put the existing, solid MVP through the engineering, policy, commerce, privacy, and operational work needed for a responsible Play release without turning it into a different product.

---

## 1. How to read this plan

Every action is classified so that Google requirements cannot be confused with advice or future scope.

| Mark | Meaning |
|---|---|
| **[GOOGLE REQUIRED]** | Needed to publish or remain compliant on Google Play. Some items are conditional on account type, permissions, or final implementation. |
| **[PRODUCT REQUIRED]** | Needed to honor the product decisions in this brief, even if Google does not mandate it. |
| **[GOOGLE RECOMMENDED]** | Google quality, security, testing, or operational guidance that is not normally a publication prerequisite by itself. |
| **[PHOSPHOR RECOMMENDED]** | Recommended specifically for this app's reliability, privacy, maintainability, or user trust. |
| **[OPTIONAL / LATER]** | Legitimate future improvement that must not hold the first release or move the product's goalposts. |
| **Human gate** | Requires Ben's identity, legal agreement, financial information, judgment, or explicit authorization. AI can prepare but must not decide or submit it autonomously. |
| **AI-owned** | Can be designed, implemented, tested, documented, or prepared by an AI coding agent, with receipts. |

Google changes Play rules over time. Recheck every linked official source immediately before the first production submission and before later major releases.

---

## 2. Fixed product decisions and non-goals

### 2.1 Decisions this release must preserve

1. **[PRODUCT REQUIRED] Free Play listing.** The app is installed free of charge so the trial can occur before purchase.
2. **[PRODUCT REQUIRED] Seven-day full trial.** Every functional feature is available during the trial. This is not a limited demo.
3. **[PRODUCT REQUIRED] One-time Pro purchase.** After the trial, the user explicitly chooses a single $3.99 USD purchase. It is not a subscription and does not renew.
4. **[PRODUCT REQUIRED] No automatic trial conversion.** No payment method is required to start the trial. Nothing is charged when the seven days end.
5. **[PRODUCT REQUIRED] No advertisements.** Do not include an ad SDK, cross-promotion network, sponsored placements, or an “ad-free” upsell.
6. **[PRODUCT REQUIRED] No required Phosphor account.** Google Play purchase ownership is sufficient. Phosphor does not need profiles, passwords, or cloud accounts.
7. **[PRODUCT REQUIRED] Minimal, transparent diagnostics.** The first release uses Android vitals plus local, user-reviewed diagnostics. It does not need behavioral analytics or permanent automatic telemetry.
8. **[PRODUCT REQUIRED] User control.** The first-run setup explains diagnostics and provides an off switch. It remains available later in Settings.
9. **[PRODUCT REQUIRED] Preserve the instrument.** The scope opens into the existing visual experience. Commerce, privacy, and setup use Phosphor's existing sheets and visual language rather than turning the app into a conventional dashboard.

### 2.2 Explicit non-goals for the first Play release

These are not Play requirements and must not block release:

- Supporting Android versions below the current `minSdk = 35`.
- Adding x86_64, ChromeOS, Wear OS, TV, tablet-specific, or foldable-specific experiences.
- Adding social features, accounts, cloud sync, advertising, subscriptions, or usage analytics.
- Replacing the private-network remote relay with a public hosted streaming service.
- Reworking the renderer, visual modes, theme system, or core scope controls.
- Adding localization beyond a well-written English listing and UI.
- Building aggressive trial enforcement based on hardware fingerprinting.
- Adding a third-party crash SDK before Android vitals and user-submitted reports prove insufficient.

Keeping Android 15+ and arm64-only for the initial release is a defensible product scope. It narrows the Play device catalog, but it is not inherently noncompliant. The listing must state compatibility honestly, and the release must be tested on a representative set of supported arm64 devices.

---

## 3. Executive publication strategy

The recommended commercial and technical structure is:

1. Create a **free** Play application using the permanent package name `dev.phosphor.mobil3`.
2. Create one Play Billing **non-consumable one-time product**, tentatively `phosphor_pro`.
3. Start the local seven-day trial only when the user taps **Begin 7-day trial** in first-run setup. Do not start it on installation or before the user understands the terms.
4. During the trial, run the existing app without feature restrictions.
5. At expiry, preserve all settings and local selections but stop or prevent paid scope sessions until the user buys or restores Pro.
6. Query Play purchase ownership on startup, resume, account reconnection, and after purchase. A verified purchase unlocks Pro permanently for that Google account.
7. Use Android vitals as the always-available Play stability signal. Add a default-off local diagnostics option that never uploads automatically and always lets the user inspect a report before sharing it.
8. Submit through internal testing, then the required closed test if the developer account is subject to it, then production with conservative rollout and measurable crash/ANR gates.

### Important Play Billing fact

Google Play does not attach a configurable seven-day free trial to a one-time in-app product. Play free-trial offers otherwise belong to subscription products. Google added a separate 60-minute, Play-managed trial feature for **paid games**, but Phosphor is an app and the duration does not meet this brief. Therefore the seven-day trial must be implemented by Phosphor itself, while the eventual Pro unlock must use Google Play Billing. This arrangement preserves the requested one-time price and avoids a subscription.

The store and paywall language must be unambiguous:

> Try every Phosphor feature free for 7 days. No card is required and nothing renews. Continue afterward with one $3.99 purchase. No subscription. No ads.

---

## 4. Repository audit and current release position

### 4.1 What is already healthy

| Area | Evidence | Result |
|---|---|---|
| Release bundle | `./gradlew :app:bundleRelease` | Passed. Current AAB is about 15.4 MB. |
| Signing | `app/build.gradle.kts` plus local release keystore | The current machine can produce a genuinely release-signed build. |
| Target SDK | `app/build.gradle.kts` | `targetSdk = 36`, already meeting the Android 16 target requirement taking effect for new submissions and updates on 2026-08-31. |
| Minimum SDK | `app/build.gradle.kts` | `minSdk = 35`, an intentional Android 15+ scope. |
| Native ABI | `app/build.gradle.kts` | arm64-v8a only, consistent with the stated first-release scope. |
| 16 KB pages | Release native objects and APK alignment | App native objects use 16 KB load alignment and `zipalign -P 16` passed. This is required for native apps targeting Android 15+. |
| Core tests | `rust/` | 32 Rust tests passed. |
| Relay tests | `relay/` | 15 Rust tests passed. |
| Dependency resolution | Gradle release runtime classpath | Resolved successfully. |
| Advertising | dependency and source audit | No ad SDK was found. |
| Broad telemetry | dependency and source audit | No analytics or crash-reporting SDK was found. |
| Permission timing | `MainActivity.kt`, `CaptureService.kt`, `ManualSheet.kt` | Mic and playback capture are user-selected flows rather than blanket first-launch prompts. Playback capture already has clear explanatory copy. |
| Local-first behavior | `CaptureService.kt`, `PlaybackService.kt`, `RemotePlayer.kt` and documentation | Scope audio is rendered in memory, local playback metadata is used locally, and remote mode connects to a host selected by the user to receive desktop audio/geometry/metadata and send user-invoked controls. |
| App identity | `docs/UX-SPEC.md`, existing Compose surfaces | The UI already has a strong system suitable for integrated setup and purchase sheets. |

### 4.2 Current publication blockers

| Priority | Finding | Classification | Required result |
|---|---|---|---|
| Blocker | `:app:lintRelease` reports **126 errors**, including 125 Media3 opt-in errors and one missing audio permission check. | **[PHOSPHOR RECOMMENDED]** as an engineering gate; unresolved permission behavior can also become **[GOOGLE REQUIRED]** policy/quality risk. | Fix the causes. Do not hide them with a blanket lint baseline. Make release CI require lint success. |
| Blocker | No Play Billing or entitlement layer exists. | **[GOOGLE REQUIRED]** for selling digital functionality; **[PRODUCT REQUIRED]** for Pro. | Implement the one-time product lifecycle, restore, pending state, acknowledgement, and durable entitlement. |
| Blocker | No seven-day trial state exists. | **[PRODUCT REQUIRED]** | Implement explicit trial start, active/expired states, offline behavior, and paywall transitions. |
| Blocker | No first-run commercial/privacy setup exists. | **[PRODUCT REQUIRED]**; disclosure can be **[GOOGLE REQUIRED]** where sensitive data or permissions require it. | Add first-run setup with trial, privacy, diagnostics, and permission explanations. |
| Blocker | Release signing silently falls back to the debug key when release credentials are absent. | **[PHOSPHOR RECOMMENDED]** and essential release hygiene. | Release builds must fail closed if production signing is unavailable. Never ship a debug-signed “release.” |
| Blocker | No Android unit or instrumentation test source sets are present, and `testReleaseUnitTest` does not exist. | **[GOOGLE RECOMMENDED]**, **[PHOSPHOR RECOMMENDED]** | Add deterministic tests for billing, trial, consent, backup, and service behavior plus device instrumentation coverage. |
| Blocker | `android:allowBackup="true"` exists without `dataExtractionRules` or an explicit backup policy. | **[PHOSPHOR RECOMMENDED]**; the missing rule is a current lint warning. | Exclude trial state, entitlement cache, diagnostics, remote host information, and sensitive transient state from backup. Decide which harmless settings may restore. |
| Blocker | No public privacy policy URL or in-app policy surface exists. | **[GOOGLE REQUIRED]** | Publish an HTTPS privacy policy, link it in Play Console and inside the app, and keep it accurate. |
| Blocker | Store declarations and foreground-service evidence have not been prepared. | **[GOOGLE REQUIRED]** | Complete Data safety, ads, app access, target audience, content rating, and foreground-service declarations. |
| High | `PlaybackService` is exported, and lint flags it. | **[PHOSPHOR RECOMMENDED]** | Prove export is necessary for `MediaSessionService`, restrict interactions to the minimum supported contract, and document the decision. |
| High | The app has mostly been tuned on one Galaxy S25. | **[GOOGLE RECOMMENDED]**, **[PHOSPHOR RECOMMENDED]** | Test representative API 35/36 arm64 devices and GPU families before production. |
| High | The native renderer requests a high-performance adapter and lacks a polished unsupported-GPU path. | **[PHOSPHOR RECOMMENDED]** | Fail gracefully with a useful compatibility report rather than crashing or showing a blank scope. |
| High | The build consumes the sibling `../phosphor` checkout through path dependencies. | **[PHOSPHOR RECOMMENDED]** | Pin and record the exact desktop engine commit for every release. A clean checkout must reproduce the app without silently consuming a different sibling state. |
| High | Font and third-party license notices are not assembled into an app/release notice. | **[GOOGLE REQUIRED]** where licenses require attribution; **[PHOSPHOR RECOMMENDED]** generally. | Inventory Gradle, Rust, IBM Plex, JetBrains Mono, and bundled assets. Ship required notices and corresponding GPL source. |
| Medium | Current README says the app is not on Play and only documents sideloading. | **[PRODUCT REQUIRED]** at publication | Update install, privacy, purchase, support, and source instructions immediately before release. |
| Medium | Current notification/media, photosensitivity, PiP, icon, and backup lint warnings need individual disposition. | **[PHOSPHOR RECOMMENDED]** | Fix material warnings and document accepted warnings. Do not pursue unrelated dependency churn only to reach zero warnings. |

### 4.3 Permissions and sensitive capability inventory

Current manifest capabilities are narrow but important:

- `RECORD_AUDIO`: mic source, requested only when the user chooses mic.
- MediaProjection foreground service: captures device playback only after Android's system consent dialog.
- Notification listener access: user opens system settings and grants access so Phosphor can obtain active media-session metadata and art.
- `INTERNET`, `ACCESS_NETWORK_STATE`, and `CHANGE_NETWORK_STATE`: the user-configured desktop relay, network selection/binding, optional metadata artwork retrieval, and future Play Billing.
- Media playback foreground service: local and remote audio playback and media controls.
- `POST_NOTIFICATIONS`: persistent foreground-service/media notification behavior on supported Android versions.

Do not request these together during setup. Explain them during setup, then ask in context when the user chooses the related source. This is both better UX and closer to Google's user-data and permissions expectations.

---

## 5. Trial and Pro entitlement design

### 5.1 State model

**[PRODUCT REQUIRED]** Implement one authoritative entitlement state machine in a testable non-UI class. Suggested states:

```text
SETUP_INCOMPLETE
TRIAL_NOT_STARTED
TRIAL_ACTIVE(expiresAt)
TRIAL_EXPIRED
PURCHASE_PENDING
PRO_VERIFIED
BILLING_TEMPORARILY_UNAVAILABLE(lastKnownState)
```

Rules:

1. Installation alone does not start the trial.
2. Completing setup alone does not need to start it unless the user explicitly taps **Begin 7-day trial**.
3. `TRIAL_ACTIVE` grants every normal app feature.
4. Seven days means 168 elapsed hours from the explicit start instant.
5. A pending purchase does not grant Pro until Play reports `PURCHASED`.
6. A purchased, verified, and acknowledged non-consumable grants permanent Pro for the purchasing Google account.
7. The app must restore a previous purchase after reinstall or on another compatible device signed into the owning Google account.
8. Temporary network or Billing service failure must not relock a user whose Pro ownership was previously verified. Cache the last good entitlement and only revoke after an authoritative Play response indicating ownership is absent or revoked.
9. Refunds and revoked purchases must eventually remove Pro after Play reports the change. Never remove user settings or files.
10. Trial expiry never deletes preferences, selected folders, themes, or tuning. It only gates use of paid instrument functions.

### 5.2 Fair local trial storage

**[PHOSPHOR RECOMMENDED]** Keep trial enforcement proportionate to a $3.99 privacy-focused app:

- Store trial state in a dedicated no-backup preference/DataStore file.
- Record wall-clock start, elapsed-realtime anchor, last-seen wall time, and enough boot context to detect obvious clock rollback without punishing normal reboot or travel.
- If the clock moves backward, use the last credible time rather than extending or instantly expiring the trial.
- If time cannot be established safely, show the user what happened and retry instead of failing into an unexplained lock.
- Exclude this state from Android backup and device transfer.
- Accept that uninstalling and reinstalling may reset a local trial in v1. Do not fingerprint the device, read hardware identifiers, require an account, or build invasive anti-abuse infrastructure for this price point.

**[OPTIONAL / LATER]** If trial resetting becomes materially harmful, use a minimal server-issued installation token or an appropriately scoped Play Integrity feature. This must undergo a new privacy review. It is not a first-release requirement.

### 5.3 Expiry behavior

**[PRODUCT REQUIRED]** At expiry:

- Finish any active audio/capture session cleanly or give a brief, non-disruptive notice before stopping it.
- Present an in-style Pro sheet with the localized Play price, **Unlock Pro**, **Restore purchase**, **Privacy**, and **Support**.
- Keep setup, privacy, support, purchase restore, and basic settings reachable.
- Do not repeatedly interrupt the user with modal prompts. A paywall appears when starting the instrument after expiry or choosing a paid source.
- Never use a countdown panic animation, fake discount, misleading close control, preselected recurring purchase, or other dark pattern.

A gentle status line in Settings and a single final-day notice are sufficient. The scope itself should remain visually quiet.

### 5.4 Play Billing implementation

1. **[GOOGLE REQUIRED]** Use Google Play Billing for the Pro unlock because it sells digital functionality in a Play-distributed app.
2. **[GOOGLE REQUIRED]** Use a supported Billing Library version at implementation time. Recheck the Billing deprecation schedule before every release.
3. **[PRODUCT REQUIRED]** Create a non-consumable one-time product, not a consumable and not a subscription. Proposed permanent product ID: `phosphor_pro`.
4. **[PRODUCT REQUIRED]** Configure a base US price of **$3.99 USD**. Review Play's generated local prices, taxes, and country availability. The UI must display `ProductDetails` formatted pricing rather than hardcoding `$3.99` for every country.
5. **[GOOGLE REQUIRED]** Handle `PENDING` purchases without granting entitlement.
6. **[GOOGLE REQUIRED]** Acknowledge a completed purchase within Google's required window, currently three days, only after entitlement is granted and the purchase is genuinely `PURCHASED`.
7. **[PRODUCT REQUIRED]** Query existing one-time purchases on startup/resume and expose **Restore purchase**. Restore should normally be automatic, with the button available for reassurance and error recovery.
8. **[PHOSPHOR RECOMMENDED]** Handle already-owned, canceled, declined, disconnected, account-changed, refunded, and service-unavailable outcomes with plain language.
9. **[PHOSPHOR RECOMMENDED]** Keep the billing coordinator independent from Compose and Android services so it can be state-tested.
10. **[GOOGLE RECOMMENDED]** Validate purchase tokens securely. Google's strongest pattern is backend verification, Real-time Developer Notifications, and reconciliation through the Voided Purchases API so refunds, chargebacks, and other voided purchases can revoke entitlement correctly.

For this privacy-first, accountless MVP, use one of these explicit tiers:

- **Recommended launch tier:** a tiny stateless/serverless verification endpoint that accepts package name, product ID, and purchase token, verifies them with Google Play Developer API, processes Real-time Developer Notifications, reconciles the Voided Purchases API, returns entitlement, retains no profile or audio data, hashes any operational token, and has a short documented retention period. No user account is added.
- **Acceptable privacy-first MVP tier:** client-side Billing purchase queries, acknowledgement, and durable local caching, with the known limitations that a GPL client can be modified more easily and that refunds/chargebacks may not be detected promptly without server-side RTDN or Voided Purchases API access. Ordinary client purchase queries are not a complete voided-purchase ledger. If prompt, reliable refund revocation is a release requirement, this tier is not sufficient.

Choose the tier before implementation and reflect it in Data safety and the privacy policy. Do not quietly add a general-purpose backend.

### 5.5 Billing test matrix

**[GOOGLE RECOMMENDED]** Use Play license testers and Play Billing Lab. AI should implement and record all of these cases:

- Fresh install, trial not started.
- Trial starts only from the explicit button.
- Trial remains active across app restart and device reboot.
- Exactly-before and exactly-after expiry boundaries.
- Clock moved forward, backward, and timezone changed.
- Billing disconnected during active trial.
- Successful purchase during trial.
- Successful purchase after expiry.
- Purchase canceled from the Play sheet.
- Card declined.
- Slow/pending payment, then completed.
- Pending payment canceled.
- App killed between purchase completion and acknowledgement.
- Already owned response.
- Reinstall and automatic restore.
- A second supported device using the purchasing account.
- Different Google account with no ownership.
- Refund, chargeback, and revocation, including verification through RTDN/Voided Purchases API when the backend tier is selected and the explicitly documented delayed-detection limitation when the client-only tier is selected.
- Offline launch for a previously verified Pro owner.
- Billing service unavailable for a new buyer.
- Price or currency different from USD.

No production rollout occurs until these cases have receipts.

---

## 6. First-run setup and paywall design

### 6.1 UX principle

The setup should feel like opening an instrument case, not creating an account. Use the existing Phosphor sheet system, sharp geometry, Blossom Dark default, restrained motion, and direct language. Avoid generic rounded subscription cards, celebratory confetti, or a new navigation hierarchy.

**[PRODUCT REQUIRED]** Show setup on first meaningful launch and make it reopenable from `Settings > About & privacy > Setup and privacy`. Do not force it on every ordinary boot after completion. “On boot” should mean first boot plus permanent later access, not a repetitive obstacle.

### 6.2 Recommended four-step setup

#### Step 1: What Phosphor is

- One sentence describing the CRT oscilloscope and its audio sources.
- “Everything runs in full for 7 days.”
- “Then continue with one $3.99 purchase. No subscription. No ads.”
- “No card is required and the trial does not auto-renew.”
- Primary action remains **Continue**, not purchase.

#### Step 2: Privacy and diagnostics

- Audio used by the scope is processed in memory and is not recorded by Phosphor.
- Track metadata and art from active media sessions remain on device.
- Remote mode connects to the host the user configures. The phone receives desktop audio, geometry, track metadata, and art, while sending user-invoked transport, source/output, tuning, and stream-selection controls. The current implementation does not upload the phone's local file, microphone, or playback-capture audio to that host. Describe it as a trusted private-network feature.
- Android vitals may provide Play with crash and ANR information according to the user's Google/Android diagnostics settings. Phosphor cannot override that system setting.
- A distinct toggle, default **off**, controls Phosphor's local diagnostic report preparation.
- Link **Read privacy policy**.

Suggested toggle copy:

> **Prepare diagnostic reports**
>
> When enabled, Phosphor keeps a small local, redacted event trail so you can review and share useful details after a problem. Reports are never sent automatically. Turn this off anytime to erase the local trail.

#### Step 3: Sources and permissions

Explain without requesting permissions:

- **Files:** Android's picker grants access only to chosen files/folders.
- **Everything playing:** Android shows a system capture prompt each time it is needed.
- **Microphone:** requested only after choosing MIC.
- **Now-playing details:** optional notification access, used for active media-session title/art rather than reading message content.
- **Remote:** receives desktop audio/geometry/metadata from the chosen host and sends only the user's remote-control and stream-selection commands. It does not send local phone audio in the current design.

Each permission remains contextual at first use. Declining one source must not break the others.

#### Step 4: Begin

- Restate the exact trial end date after the user starts it.
- Button: **Begin 7-day trial**.
- Secondary action: **Not now**, returning to a limited setup/privacy surface without silently starting the clock.
- Record setup and consent version separately from trial state so revised disclosures can be shown when materially changed.

### 6.3 Paywall sheet after expiry

Use the live localized Play price. Recommended hierarchy:

1. “Your full 7-day trial has ended.”
2. “Keep every source, mode, theme, geometry control, remote feature, and PiP with one purchase.”
3. **Unlock Pro · {formatted price} once**
4. **Restore purchase**
5. “No subscription. No ads.”
6. Privacy and support links.

Do not invent a crossed-out price or call the one-time unlock a “lifetime” purchase unless the support policy defines what lifetime means. Safer copy is “one-time Pro unlock for this app through your Google Play account.”

### 6.4 Accessibility and interruption rules

**[GOOGLE RECOMMENDED]** and **[PHOSPHOR RECOMMENDED]**:

- Every setup, permission, trial, and purchase control has a TalkBack label and logical focus order.
- Minimum target sizes and text contrast pass Android guidance.
- Setup remains usable with large font and display scaling.
- No critical message depends only on beam color or animation.
- Back dismisses only when dismissal is safe. A purchase in progress is not lost.
- Rotation, PiP transitions, process death, and configuration changes do not duplicate a purchase or start the trial twice.
- Honor existing photosensitivity protections and include a concise store/help note that high-intensity visual patterns can be reduced in settings.

---

## 7. Minimal diagnostics plan

### 7.1 Launch architecture

Use three deliberately separate layers:

#### Layer A: Android vitals

**[GOOGLE RECOMMENDED]** Android vitals supplies aggregated Play Console crash, ANR, device, OS, and bad-behavior metrics for eligible Play-distributed usage. It requires no app analytics SDK, but it is not a complete per-user crash feed: visibility depends on Play/Android diagnostics availability, aggregation, privacy thresholds, and Console reporting. Some individual failures will not appear. It is controlled through the user's Google/Android diagnostics relationship, not Phosphor's in-app toggle.

The setup must not falsely claim that disabling Phosphor diagnostics disables all reporting performed by Android or Google Play.

#### Layer B: Local redacted diagnostics

**[PRODUCT REQUIRED]** Implement a small structured ring buffer only when the user has enabled it. It should survive a crash but remain local until the user reviews and shares it.

Allowed fields:

- App version name/code and source commit.
- Install channel and build type.
- Android release/API level.
- Manufacturer, model, ABI, memory class, display resolution/refresh rate.
- Renderer backend, adapter name where available, supported feature flags, and initialization result.
- Source category only: file, mic, playback capture, or remote.
- Service and lifecycle transitions expressed as stable event codes.
- Error classes and redacted native/JVM stack traces.
- Last 100 to 200 event timestamps with a strict size limit.
- Trial/billing state category, never purchase token or order number.

Forbidden fields:

- Raw audio or rendered audio samples.
- Track title, artist, album, artwork, media ID, or notification text.
- Selected file/folder names, content URIs, or document-provider paths.
- Remote host name, IP address, Tailscale identity, library path, or network payload.
- Google account, email, phone, contacts, advertising ID, Android ID, serial, IMEI, or hardware fingerprint.
- Purchase token, order ID, banking information, or full Billing response payload.
- Free-form UI text that could accidentally contain private data.

When disabled, stop writing the ring immediately and delete its local file. Previously shared reports cannot be recalled, which must be stated plainly.

#### Layer C: User-reviewed share

After detecting a previous crash through platform APIs such as `ApplicationExitInfo`, or when the user selects **Report a problem**:

1. Generate the report locally.
2. Show a readable preview and exact field list.
3. Let the user remove the event trail if desired.
4. Export through Android's Sharesheet to an app the user chooses.
5. Never upload in the background.

This produces actionable device-compatibility evidence without a behavioral telemetry system.

### 7.2 When to consider Crashlytics or Sentry

**[OPTIONAL / LATER]** Add an opt-in automatic crash SDK only if Android vitals plus reviewed reports repeatedly fail to diagnose real production crashes, especially native GPU failures.

If added:

- Use only the crash product, not Analytics, Performance Monitoring, ad attribution, sessions, or remote-config tracking.
- Disable automatic collection in the manifest/build before first initialization.
- Initialize collection only after affirmative consent.
- Upload native symbols and obfuscation mappings for the exact build.
- Set a short, documented retention policy where configurable.
- Update Data safety and privacy disclosures before releasing the SDK.
- Explain any installation identifier it creates.
- Ensure off disables future transmission and clears unsent local reports.

A third-party crash SDK is not currently required by Google and should not be added merely because it is common.

### 7.3 User support channel

**[GOOGLE REQUIRED]** Supply a functioning support email in the Play listing. **[PHOSPHOR RECOMMENDED]** Also provide an in-app Support sheet containing:

- Copy app/device summary.
- Create reviewed diagnostic report.
- Open public issue tracker with a privacy warning, if appropriate.
- Email support.
- Privacy policy.
- App version and open-source license information.

AI can draft responses and cluster duplicate reports. Sending messages or publishing issue content remains a human/user-confirmed action.

---

## 8. Privacy, Data safety, and permission disclosures

### 8.1 Privacy policy requirements

**[GOOGLE REQUIRED]** Publish a public, stable HTTPS page that is readable without login and is linked both in Play Console and in-app. It should be plain language, not only a license notice.

It must cover:

- Developer/legal contact identity and support address.
- What the scope does with file, microphone, playback-capture, media-session, and remote data.
- Which operations are on-device.
- What leaves the device, under which user action, and to whom.
- Google Play Billing processing.
- Android vitals and the user's platform diagnostics setting.
- Local diagnostic report behavior and voluntary sharing.
- Any purchase-verification endpoint if selected.
- Retention and deletion rules.
- Security practices and limits of private-network remote transport.
- Children's policy and intended audience.
- Policy effective date and change process.
- How to ask privacy questions or request deletion of support/diagnostic submissions.

A lawyer is not necessarily required to draft the first policy, but Ben must review and own the factual/legal claims. AI must not promise behavior the code does not implement.

### 8.2 Proposed data inventory for the privacy review

| Data/capability | Current path | Leaves phone? | Developer collection? | Release treatment |
|---|---|---:|---:|---|
| File audio | SAF selection, staged local playback | No | No | Explain on-device staging and automatic cleanup. The current remote feature does not upload phone file audio. |
| Microphone audio | `MicController.kt` to native renderer | No | No | In-context runtime permission and setup explanation. |
| Playback capture audio | `CaptureService.kt` to native renderer | No | No | System MediaProjection consent and persistent foreground notification. |
| Track title/artist/art | `PlaybackService.kt` active media session | Ordinarily no | No | State that message notification content is not used; avoid logging metadata. |
| Remote commands and stream preferences | relay socket to configured host | Yes, to the user's chosen host | Normally no, because the endpoint is the user's own relay rather than Phosphor's service | Disclose exactly, label trusted private network, and document protocol security. |
| Desktop audio, geometry, metadata, and art | received from the configured relay | It arrives on the phone; Phosphor Mobile does not retransmit it to the developer | No | Explain that remote content is processed/displayed locally and avoid logging it. |
| Purchase status | Google Play Billing | Communicates with Google Play; a verification endpoint also receives package/product/purchase-token and entitlement-request data | Google processes Billing. Under client-only verification the developer does not receive a token service-side. Under backend verification the developer/service collects the minimum verification payload. | Include the chosen tier in the policy and Data safety review. For backend verification, define token hashing/storage, RTDN/voided-purchase handling, retention, security, and deletion terms. |
| Crash/ANR aggregates | Android vitals | Platform-controlled | Available to developer through Play Console | Explain separately from the app toggle. |
| Local diagnostic report | app storage and Sharesheet | Only when user shares | Yes if the user sends it to the developer | Preview, redact, minimize, and define deletion/retention. |
| Remote host preferences | app preferences | No | No | Exclude from backups and reports. |

### 8.3 Data safety form process

**[GOOGLE REQUIRED]** Complete the Data safety form based on the exact shipping binary, not this plan alone.

AI-owned pre-submission procedure:

1. Produce a Software Bill of Materials for Gradle and Rust dependencies.
2. Inspect every SDK's data practices.
3. Trace every network call from the release build.
4. Compare the binary behavior against the table above.
5. Draft each Console answer with source-code evidence.
6. Run the release on a monitored test network and verify no undisclosed endpoint.
7. Have Ben review and submit the declarations.

Do not automatically answer “no data collected” merely because there is no analytics SDK. The answer depends on the final diagnostics and purchase-verification architecture, and on Google's then-current definitions and exemptions for user-directed transfers.

### 8.4 Prominent disclosure and consent

**[GOOGLE REQUIRED, CONDITIONAL]** When access or data use is not reasonably expected from the immediate feature, Google requires an in-app prominent disclosure before the permission/consent request. It cannot exist only in the privacy policy.

For Phosphor:

- Keep the calm playback-capture explanation before Android's MediaProjection dialog.
- Keep microphone disclosure next to choosing MIC.
- Explain notification access before opening system Notification Access settings, including what is and is not read.
- Explain the bidirectional remote connection before the first connection: commands leave the phone for the chosen relay, and desktop audio/geometry/metadata arrive on the phone. State clearly that local phone audio is not uploaded by current remote mode.
- Keep diagnostics consent separate from trial acceptance and purchase.
- Record disclosure version and consent state without logging private content.

### 8.5 Backup and device transfer

Add both modern `dataExtractionRules` and the appropriate compatibility backup configuration.

Recommended classification:

- May back up: theme, harmless visual tuning, accessibility preferences.
- Must not back up: trial timestamps, entitlement cache, pending purchase state, consent audit details beyond the current device, diagnostic ring, remote hosts/ports, transient media paths, staged audio, tokens, or any future server credential.

Purchase ownership restores from Google Play rather than backup. Trial integrity must not depend on backed-up SharedPreferences.

---

## 9. Foreground services, notifications, and media policy

### 9.1 Foreground service declarations

The manifest currently uses media playback and media projection foreground-service types.

**[GOOGLE REQUIRED]** Complete Play Console's foreground-service declaration for each applicable type and explain:

- Which feature starts it.
- Why work must continue while the app is not foregrounded.
- That the user initiates it.
- What persistent notification appears.
- How the user stops it.
- Why a deferred/background mechanism is not sufficient.

Prepare a short review video from the exact release build showing:

1. User selects Everything Playing.
2. Phosphor explains the operation.
3. Android shows MediaProjection consent.
4. The persistent notification appears.
5. The scope reacts.
6. The user stops capture from the app and notification.
7. Local/remote playback notification and transport controls, if declared separately.

**[PHOSPHOR RECOMMENDED]** Add an explicit stop action to capture notifications if one is not currently present. A user-visible, immediately effective stop control substantially strengthens the declaration.

### 9.2 Notification permission behavior

Test with `POST_NOTIFICATIONS` granted and denied. Foreground services must still obey Android rules, and the app must explain any reduced notification visibility without pressuring the user. Ask only when a feature benefits from it.

### 9.3 Exported media service review

`PlaybackService` is exported because it declares `MediaSessionService`. Before release:

- Confirm Media3 requires this exact exported configuration.
- Ensure no custom Binder, intent extra, or command gives another app unintended access.
- Reject unknown/custom commands by default.
- Add instrumentation tests for malicious or malformed external intents.
- Document why the lint warning is accepted if export remains necessary.

---

## 10. Device compatibility and quality strategy

### 10.1 Supported catalog

**[PRODUCT REQUIRED]** Initial support remains:

- Android 15/API 35 and newer.
- arm64-v8a.
- Phones first.

In Play Console, inspect the Device catalog exclusions created by these settings. Do not manually expand support until it has been tested. Store copy should say “Requires Android 15 or newer” if Play does not already communicate it prominently enough.

### 10.2 Required engineering compatibility work

1. Build a renderer capability probe that records adapter/backend/features without private identifiers.
2. If `wgpu` cannot obtain an adapter/device, show a styled unsupported-renderer sheet with report/export and support actions.
3. Never enter a crash loop on renderer initialization.
4. Confirm Android lifecycle recreation does not leak or duplicate native surfaces.
5. Validate 16 KB page alignment for every native library in every final AAB, including transitive AndroidX native objects.
6. Test low-memory kill and process recreation.
7. Test all source types with screen off, lock screen, PiP, rotation locks, Bluetooth, wired output, speaker, and interrupted audio focus.
8. Ensure remote socket timeouts and malformed frames cannot freeze the UI.

### 10.3 Device test matrix

**[GOOGLE RECOMMENDED]** Minimum before production:

| Dimension | Coverage |
|---|---|
| OS | API 35 and API 36 release builds. |
| Vendors | Samsung Galaxy, Google Pixel, and at least one other mainstream arm64 vendor such as OnePlus or Motorola. |
| GPU | At least one Adreno device, one Mali device if supported by catalog, and the current Radeon/desktop build environment only as a development comparison. |
| Display | 60 Hz, 90/120 Hz, portrait, landscape, cutout/rounded-corner device. |
| Memory | One lower-memory supported phone plus the S25. |
| Audio | Speaker, Bluetooth, local file, microphone, playback capture, remote relay. |
| Lifecycle | Background/foreground, screen lock, phone call/audio-focus interruption, process death, reboot, PiP. |
| Permissions | Each permission allowed, denied, “don't ask again,” revoked later, and system setting removed while active. |
| Commerce | All trial and Billing cases from section 5.5. |
| Accessibility | TalkBack, large font, display scaling, reduced animation, color/contrast review. |

Use Firebase Test Lab or Play pre-launch report for broad device coverage, then verify audio/GPU behavior on physical devices because virtual devices do not reproduce all capture and rendering paths.

### 10.4 Quality gates

Do not submit production until:

- `lintRelease` has zero errors.
- Rust core and relay tests pass.
- New Kotlin unit and instrumentation tests pass.
- A clean release AAB builds without debug signing fallback.
- Bundle inspection confirms only intended ABIs, permissions, endpoints, and SDKs.
- 16 KB page checks pass.
- Play pre-launch report has no unexplained crash, ANR, accessibility blocker, or security issue.
- Trial and purchase tests pass through Play test tracks.
- The exact release has a fresh S25 smoke receipt through `dev/pm3` plus at least the representative physical device matrix.

Recommended initial production health thresholds:

- User-perceived crash rate below Play's current bad-behavior threshold and ideally below 0.5%.
- User-perceived ANR rate below Play's current bad-behavior threshold and ideally below 0.2%.
- No repeatable purchase loss, false trial expiry, foreground-service policy failure, or renderer crash.

Always use the live Android vitals thresholds displayed by Play Console, since Google can change them.

---

## 11. Release engineering and supply-chain work

### 11.1 Signing and Play App Signing

1. **[GOOGLE REQUIRED]** Enroll the app in Play App Signing. New Play apps use an Android App Bundle and Play-managed app signing.
2. Treat the existing release key as a candidate upload key, not automatically as the final choice.
3. Decide whether to use the existing key or generate a dedicated Play upload key before the first upload.
4. Store the upload key and credentials encrypted in at least two controlled backups.
5. Never commit the keystore, passwords, `google-services.json`, service-account key, or Play API credential.
6. Change Gradle so production release signing fails clearly when credentials are missing.
7. Record the upload certificate fingerprint in private release documentation.
8. Test key rotation/recovery instructions before they are needed.

The package name becomes effectively permanent after the listing exists. Confirm `dev.phosphor.mobil3` before creating the Play app.

### 11.2 Versioning

- Every Play upload needs a unique, increasing `versionCode`.
- Uploaded version codes cannot be reused, even if a release is discarded.
- Keep the user-facing semantic version honest.
- The first Billing/setup release should receive a new version rather than republishing `1.0.7` unchanged.
- Generate release notes from committed changes, not memory.

### 11.3 Reproducible build spine

The current sibling path dependency is a release provenance risk. Before publication:

- Pin the exact `../phosphor` engine commit in a machine-readable release manifest.
- Make CI fetch or verify that exact commit.
- Fail if the sibling engine tree is dirty or at a different commit.
- Record Android SDK, NDK, Rust, Kotlin, AGP, Gradle, and cargo-ndk versions.
- Enable Gradle dependency verification/locking where practical.
- Generate checksums for AAB, mapping file, native symbols, source archive, and SBOM.
- Archive the exact source and build receipt for every Play version code.

### 11.4 Lint and test implementation order

1. Add explicit Media3 `@OptIn(UnstableApi::class)` at the narrowest maintainable scope.
2. Fix the actual microphone permission race around `AudioRecord`; do not merely suppress it.
3. Resolve backup/data-extraction rules.
4. Review exported service, PiP source rectangle, locale formatting, icon shape, and native ABI warnings.
5. Treat pinned dependency/version warnings separately from functional defects.
6. Add unit tests before trial and Billing UI so business rules are not trapped in Compose callbacks.
7. Add instrumentation tests for first-run, permissions, process recreation, trial expiry, and paywall.
8. Add a CI release gate that runs lint and tests before bundle creation.

### 11.5 Licenses and public source

Phosphor is GPLv3. Before distribution:

- Confirm corresponding source for the exact release is publicly available from a stable URL/tag.
- Include build instructions sufficient for the published source.
- Inventory all Rust crates, Android libraries, fonts, icons, and copied assets.
- Include required IBM Plex and JetBrains Mono notices/licenses.
- Add an in-app **Open source licenses** surface or bundled notice file.
- Publish source archive and checksums alongside each release where possible.
- Keep the public export sanitizer, but verify it does not omit source required by GPL while still excluding private hosts, credentials, and machine facts.

---

## 12. Play Console setup, exactly what must be completed

### 12.1 Developer account

**Human gate:**

1. Choose personal or organization account honestly.
2. Be at least 18, accept the Developer Distribution Agreement, and pay Google's one-time registration fee, currently US $25.
3. Complete identity, email, and phone verification.
4. Complete any device verification required for a new personal account.
5. Create a payments/merchant profile because the app sells an in-app product.
6. Supply tax, banking, legal name, and public address details as requested. Monetizing developer contact/address information may be displayed by Google.
7. Protect the account with strong MFA and at least one backup owner/contact where the account type permits it.

AI may walk through fields and explain consequences, but must not accept legal terms, submit identity documents, pay fees, or invent tax/banking answers.

### 12.2 Create the app

**[GOOGLE REQUIRED]**:

- Default language: English, unless Ben chooses otherwise.
- App name: confirm “Phosphor” availability and trademark risk before finalizing.
- App or game: App.
- Free or paid: **Free**. A free app cannot later become a paid download, which is intentional because monetization is the in-app Pro product.
- Declare no ads.
- Package: `dev.phosphor.mobil3` after final confirmation.
- Enroll in Play App Signing.

### 12.3 Monetization product

Create one in-app one-time product:

| Field | Proposed value |
|---|---|
| Product ID | `phosphor_pro` |
| Type | Non-consumable one-time product |
| Name | Phosphor Pro |
| Description | Unlock every Phosphor feature after the full 7-day trial. One purchase, no subscription, no ads. |
| Base price | $3.99 USD |
| Availability | Same launch countries as the app unless local legal/tax review says otherwise |

Review automatically converted regional prices. Google no longer uses the old reusable pricing-template workflow for this purpose. Do not promise exactly `$3.99` in non-US currencies.

### 12.4 Store listing assets

**[GOOGLE REQUIRED]** Prepare from the exact release candidate:

- 512 by 512 PNG app icon, maximum 1 MB.
- 1024 by 500 JPEG or 24-bit PNG feature graphic, no alpha.
- At least two phone screenshots meeting current Console dimensions.
- Short description, currently up to 80 characters.
- Full description, currently up to 4,000 characters.
- Support email.
- Privacy policy URL.

**[GOOGLE RECOMMENDED]** Provide 6 to 8 strong phone screenshots, with at least four high-resolution screenshots suitable for Play recommendation surfaces. Capture portrait and landscape if both are genuinely supported.

Suggested screenshot story:

1. Blossom Dark scope in the signature visual mode.
2. Local playback with tasteful, non-private sample metadata.
3. Geometry manipulation.
4. Source picker showing file, playing audio, mic, and remote options.
5. Distinct theme comparison.
6. PiP or lock-screen media controls.
7. First-run privacy/trial clarity.
8. One-time Pro sheet showing no subscription and no ads.

Use only media/art that can legally appear in marketing. Scrub real host names, personal music libraries, account names, IP addresses, notification content, and debug overlays.

### 12.5 Store copy requirements

Copy must state:

- The actual purpose and supported audio sources.
- Seven-day full trial.
- One-time in-app purchase after trial.
- No subscription and no ads.
- Android 15+ compatibility if useful.
- Remote feature requires the companion desktop relay and a trusted network.
- No false promise that every streaming app permits playback capture.
- No misleading performance, privacy, or “zero data” claim.
- A restrained photosensitivity note and where intensity controls are found.

Do not keyword-stuff competitors, use unsupported “best” claims, imply Google endorsement, or describe planned features as present.

### 12.6 App content declarations

**[GOOGLE REQUIRED]** Complete and retain evidence for:

- Privacy policy.
- Ads: **No**, and keep the binary free of ad SDKs.
- App access: no login. Explain the seven-day trial and exact reviewer path. If a feature needs a desktop relay, explain that dependency without representing it as generally accessible.
- Target audience and content: choose truthful age groups. Do not select adults only merely to avoid Families rules. The app is not designed for children, so marketing and listing art should not target children.
- Content rating questionnaire: answer for actual visual/audio/network functionality.
- Data safety.
- Foreground-service permissions and review video.
- Any sensitive permission declarations Play requests based on the final manifest.
- News, health, financial, government, and account-deletion declarations: mark not applicable only after confirming the final app does not enter those categories.

Because there is no Phosphor account, an in-app account deletion feature is not needed. If accounts are added later, Google account-deletion requirements must be revisited before release.

### 12.7 Countries, tax, and pricing

**Human gate:** choose launch countries after reviewing:

- English support capacity.
- Local price conversions.
- Tax and trader/consumer disclosures.
- Refund expectations.
- Whether the private-network desktop relay documentation is usable there.

A practical first release can use a small set of English-speaking countries, but Google does not require this. It is acceptable to launch more broadly if support, policy, and pricing are ready. Do not let geographic expansion become an engineering redesign.

Google's service fee and tax treatment can vary by program and region, and rules changed again in 2026. Confirm the live fee table and merchant terms rather than planning net revenue from an assumed fixed percentage.

---

## 13. Testing tracks and production access

### 13.1 Internal testing

**[GOOGLE RECOMMENDED]** Upload the first signed AAB to internal testing as early as possible. Internal testing validates:

- Play App Signing and delivery.
- Correct package/version.
- Billing product availability.
- License tester behavior.
- Play-generated split APK compatibility.
- Console and pre-review checks that may surface manifest, policy-form, or Data safety inconsistencies. Uploading to an internal track does not validate that Data safety answers are factually accurate; the developer remains responsible for tracing and declaring actual behavior.

Do not wait for perfect marketing assets to start internal engineering tests.

### 13.2 Closed test requirement for newer personal accounts

**[GOOGLE REQUIRED, CONDITIONAL]** Personal developer accounts created after 2023-11-13 generally must run a closed test with at least 12 testers opted in continuously for 14 days, then apply for production access. Google may ask questions about testing, feedback, and readiness.

If this account is subject to the rule:

- Recruit more than 12 people, preferably 15 to 20, so one opt-out does not reset eligibility.
- Give testers a focused script covering sources, permissions, trial, purchase test paths, GPU behavior, rotation, PiP, and remote mode where available.
- Collect structured feedback and device details without collecting music metadata or personal content.
- Respond to real findings and preserve commit/test receipts.
- Keep the test active continuously until Console confirms eligibility.
- Have Ben submit the production-access answers truthfully. AI can draft them from actual evidence.

Do not hire fake testers or manufacture feedback.

### 13.3 Open test

**[OPTIONAL / LATER]** An open test can help compatibility coverage, but it is not necessary if the closed cohort and pre-launch report provide enough signal. It also exposes an unfinished store presence more broadly.

### 13.4 Production rollout

**[PHOSPHOR RECOMMENDED]**:

1. Freeze the release candidate.
2. Tag source and archive checksums/symbols.
3. Upload to production without changing the artifact tested in closed/internal tracks.
4. Use a staged rollout if the Console permits it for the first release. Suggested steps are 5%, 20%, 50%, then 100% with at least 24 to 72 hours of healthy vitals between steps.
5. Halt for elevated crash/ANR, lost entitlement, false expiry, Billing acknowledgement failure, renderer initialization failure, or capture-service policy issues.
6. Never hot-fix by reusing a version code.

A first release can also be limited geographically if percentage staging is not offered for that launch shape.

---

## 14. AI execution model

### 14.1 Work AI should own end to end

- Implement trial, Billing, entitlement caching, restore, pending, refund observation, and paywall state.
- Implement first-run setup and privacy/diagnostics settings in the existing design system.
- Implement local redacted reports and report preview/export.
- Fix lint errors and build warnings that affect release quality.
- Add unit, instrumentation, service, and permission tests.
- Build and inspect AABs, native symbols, manifests, permissions, endpoints, alignment, SBOM, and licenses.
- Drive the connected Galaxy S25 through ADB without destroying Ben's saved preferences.
- Run additional device-lab tests and interpret pre-launch reports.
- Draft privacy policy, Data safety answers, permission disclosures, store copy, release notes, support templates, and reviewer instructions from source evidence.
- Generate and capture store assets from the exact release build, with user approval of final creative choices.
- Prepare Play Console fields in a review document or draft state.
- Create a release checklist with machine-verifiable gates and receipts.
- Monitor Android vitals after release and prepare prioritized fixes.
- Maintain an incident ledger mapping production symptoms to app version, device class, fix, and verification.

### 14.2 Work AI may assist but Ben must approve or execute

- Developer account type and public developer name.
- Identity verification.
- Registration fee payment.
- Merchant, banking, tax, and address information.
- Legal agreements and policy attestations.
- Final target-audience and content-rating answers.
- Final privacy policy publication.
- Launch countries and price review.
- Production-access application.
- Final release submission and rollout expansion.
- Replies sent to users or public issue publication.

### 14.3 Automation after the first manual setup

**[OPTIONAL / LATER]** After the first Play app, signing, and API access are established, configure a narrowly scoped Play Developer API/service account or Gradle Play Publisher workflow for:

- Uploading an already-approved AAB to internal testing.
- Uploading symbols/mappings.
- Drafting release notes.
- Promoting between test tracks under explicit approval.
- Downloading vitals/review reports where APIs permit.

Keep production publishing disabled by default. Require a human confirmation token or manual Console promotion. Credentials stay outside the repo and use least privilege.

---

## 15. Phased implementation plan and completion gates

**Classification rule for this section:** every phase gate is **[PRODUCT REQUIRED]** for this planned Play release. Individual tasks inherit the more specific Google, product, recommendation, or optional classification from the subject sections above. A recommendation becomes a release gate here only because this plan deliberately adopts it as the quality bar for Phosphor, not because Google universally mandates it.

### Phase 0: Confirm irreversible decisions

**Owner:** Ben with AI briefing<br>
**Estimated active work:** 1 focused session

- Confirm package name `dev.phosphor.mobil3`.
- Confirm public app/developer name.
- Confirm free listing plus `phosphor_pro` one-time product.
- Confirm trial begins on **Begin 7-day trial**, not installation.
- Choose purchase verification tier.
- Confirm support email/domain and privacy-policy host.
- Determine developer account type and whether the 12-tester rule applies.
- Choose initial countries.

**Gate:** A signed-off one-page decision record. Do not create the permanent Play listing before this gate.

### Phase 1: Make release engineering trustworthy

**Owner:** AI<br>
**Estimated active work:** 2 to 4 development sessions

- Remove debug-signing fallback.
- Pin/verify engine commit and toolchain.
- Fix all lint errors.
- Add backup/data-extraction rules.
- Review exported service and manifest surface.
- Add CI gates for clean build, lint, unit tests, Rust tests, bundle inspection, and 16 KB alignment.
- Add SBOM/license inventory and exact-source release manifest.

**Gate:** Clean checkout produces a release AAB only with explicit release credentials; lint has zero errors; existing tests pass; artifact provenance is recorded.

### Phase 2: Build trial and Billing as domain logic

**Owner:** AI<br>
**Estimated active work:** 3 to 6 development sessions

- Implement state machine and no-backup storage.
- Add clock/reboot behavior.
- Integrate current supported Billing Library.
- Implement product query, purchase, pending, acknowledge, restore, cache, revocation observation, and errors.
- Implement chosen verification tier.
- Write exhaustive unit tests before UI binding.

**Gate:** Domain test matrix passes with fake Billing/time sources; no Compose or Activity is required to prove entitlement rules.

### Phase 3: Integrate setup, paywall, privacy, and diagnostics

**Owner:** AI, Ben reviews voice/design<br>
**Estimated active work:** 3 to 5 development sessions

- Add the four-step first-run sheet.
- Add contextual permission disclosures.
- Add Pro sheet and trial status.
- Add local diagnostic ring, `ApplicationExitInfo` import where useful, preview, share, clear, and toggle.
- Add privacy/support/open-source surfaces.
- Ensure settings and app state survive expiry.
- Add accessibility semantics and configuration-change tests.

**Gate:** On-device walkthrough proves every disclosure, toggle, trial transition, purchase state, restore path, and opt-out. No report leaves the phone without review and a user share action.

### Phase 4: Compatibility and policy hardening

**Owner:** AI<br>
**Estimated active work:** 3 to 7 sessions plus device availability

- Add unsupported-renderer path and renderer facts.
- Run API 35/36 and vendor/GPU matrix.
- Test every permission denial/revocation.
- Test FGS notifications and stop actions.
- Run Play Billing Lab cases.
- Run pre-launch report and address findings.
- Validate actual release network traffic and Data safety inventory.
- Capture foreground-service review video.

**Gate:** No known critical crash/ANR, purchase-loss, false-expiry, blank-renderer, privacy, or FGS blocker remains.

### Phase 5: Publishing materials and Console draft

**Owner:** AI prepares, Ben approves<br>
**Estimated active work:** 2 to 4 sessions

- Publish privacy policy and support page.
- Prepare exact-build icon, feature graphic, and screenshots.
- Draft short/full descriptions.
- Prepare reviewer instructions.
- Complete draft Data safety, ads, app access, target audience, content rating, FGS, and permission declarations.
- Create and activate `phosphor_pro` in test context.
- Update README/source release materials.

**Gate:** A human-readable cross-check proves every store claim matches the release binary and every Console answer has code/policy evidence.

### Phase 6: Internal and closed testing

**Owner:** AI operates tests, Ben manages real testers and final answers<br>
**Calendar time:** at least 14 continuous days if the personal-account rule applies

- Upload internal AAB.
- Complete Billing license testing.
- Run closed cohort with structured scenarios.
- Fix findings through new version codes.
- Keep required tester count continuously opted in.
- Prepare truthful production-access application.

**Gate:** Play grants production access if required, tester feedback has no unresolved release blocker, and final candidate passes all receipts.

### Phase 7: Production

**Owner:** Ben authorizes; AI prepares and monitors

- Tag and archive final source/artifacts.
- Submit exact tested AAB.
- Start conservative rollout.
- Monitor vitals, reviews, Billing errors, and diagnostic submissions daily during launch.
- Pause rollout on any stop condition.
- Expand only after healthy observation windows.

**Gate:** 100% intended launch availability with healthy vitals and no commerce/privacy incident.

### Phase 8: Ongoing operations

- Recheck target API, Billing Library, Data safety, FGS, and account policies quarterly and before each release.
- Review dependency/security advisories monthly or through automated alerts.
- Answer support promptly and categorize compatibility reports.
- Upload native symbols for every build.
- Preserve purchase compatibility and never convert one-time owners into subscribers.
- Keep privacy policy and Data safety synchronized with code.
- Maintain Android vitals health and investigate device-model clusters.
- Give existing owners reasonable continued access if the app is ever delisted or substantially changed.

---

## 16. Master release checklist

**Classification rule for this section:** unchecked items are **[PRODUCT REQUIRED]** release gates unless a more specific classification is printed beside the item. This does not reclassify them as universal Google rules.

### Product and commerce

- [ ] **[PRODUCT REQUIRED]** Free listing confirmed.
- [ ] **[PRODUCT REQUIRED]** Full seven-day trial confirmed.
- [ ] **[PRODUCT REQUIRED]** Trial does not require payment and does not auto-renew.
- [ ] **[GOOGLE REQUIRED]** Pro is a Play Billing non-consumable.
- [ ] **[PRODUCT REQUIRED]** US base price is $3.99; UI uses localized Play price.
- [ ] **[PRODUCT REQUIRED]** No subscriptions, ads, accounts, or feature-limited trial.
- [ ] Restore, pending, refund, offline, and account-switch behavior passes.

### Engineering

- [ ] Release signing fails closed.
- [ ] Engine/toolchain provenance pinned.
- [ ] `lintRelease` has zero errors.
- [ ] Kotlin unit and instrumentation tests pass.
- [ ] 32 core and 15 relay tests still pass.
- [ ] AAB builds from a clean checkout.
- [ ] AAB contains only intended and documented permissions, SDKs, ABIs, and endpoints, including an explicit decision for `CHANGE_NETWORK_STATE`.
- [ ] Every native library passes 16 KB alignment checks.
- [ ] Backup exclusions are verified with actual backup/restore tests.
- [ ] Native symbols and any mapping files are archived/uploaded.
- [ ] OSS/GPL/source obligations are fulfilled.

### UX and privacy

- [ ] First boot explains trial, price model, privacy, and permissions.
- [ ] Trial starts only through explicit action.
- [ ] Diagnostics toggle defaults off and remains accessible.
- [ ] Disabling diagnostics clears the local trail.
- [ ] Diagnostic report preview contains no forbidden fields.
- [ ] Mic, capture, notification access, and the exact bidirectional remote connection have contextual explanations.
- [ ] Privacy policy and support are reachable in-app.
- [ ] TalkBack, text scaling, contrast, rotation, PiP, and process recreation pass.

### Play Console

- [ ] Developer identity/contact/payment verification complete.
- [ ] Play App Signing configured and upload key backed up.
- [ ] Privacy policy URL live.
- [ ] Data safety accurate.
- [ ] Ads declaration says no.
- [ ] App access/reviewer instructions accurate.
- [ ] Target audience and content rating complete.
- [ ] FGS declarations and video complete.
- [ ] Store copy and graphics match exact build.
- [ ] Support email works.
- [ ] `phosphor_pro` active in required countries/tracks.
- [ ] Required internal/closed testing complete.
- [ ] Production access granted if applicable.

### Launch

- [ ] Exact tested artifact promoted.
- [ ] Source tag, checksums, SBOM, notices, symbols, and release notes archived.
- [ ] Rollout stop conditions documented.
- [ ] Android vitals alerting/inspection assigned.
- [ ] Support and privacy-response process ready.
- [ ] Ben explicitly authorizes production submission.

---

## 17. Official sources to recheck

### Account, app creation, testing, and release

- Developer account registration: <https://support.google.com/googleplay/android-developer/answer/6112435>
- Developer account contact requirements: <https://support.google.com/googleplay/android-developer/answer/10840893>
- Create and set up an app: <https://support.google.com/googleplay/android-developer/answer/9859152>
- Testing requirements for newer personal accounts: <https://support.google.com/googleplay/android-developer/answer/14151465>
- Prepare and roll out a release: <https://support.google.com/googleplay/android-developer/answer/9859348>
- App content and review preparation: <https://support.google.com/googleplay/android-developer/answer/9859455>
- Pre-launch reports: <https://support.google.com/googleplay/android-developer/answer/9844487>
- Android vitals: <https://support.google.com/googleplay/android-developer/answer/9844486>

### Technical requirements

- Android App Bundles: <https://developer.android.com/guide/app-bundle>
- Play App Signing: <https://support.google.com/googleplay/android-developer/answer/9842756>
- Target API level requirements: <https://support.google.com/googleplay/android-developer/answer/11926878>
- 16 KB page-size support: <https://developer.android.com/guide/practices/page-sizes>
- Core app quality: <https://developer.android.com/docs/quality-guidelines/core-app-quality>
- Play Integrity overview, optional for this launch: <https://developer.android.com/google/play/integrity/overview>

### Billing and pricing

- Integrate Play Billing: <https://developer.android.com/google/play/billing/integrate>
- One-time product lifecycle: <https://developer.android.com/google/play/billing/lifecycle/one-time>
- Billing security and verification: <https://developer.android.com/google/play/billing/security>
- Test Billing integration: <https://developer.android.com/google/play/billing/test>
- Billing Library deprecation schedule: <https://developer.android.com/google/play/billing/deprecation-faq>
- Paid-game trial exception, not applicable to Phosphor: <https://support.google.com/googleplay/android-developer/answer/16923846>
- Payments policy: <https://support.google.com/googleplay/android-developer/answer/9858738>
- App and in-app product pricing: <https://support.google.com/googleplay/android-developer/answer/6334373>
- Google Play service fees: <https://support.google.com/googleplay/android-developer/answer/112622>

### Privacy, permissions, and declarations

- User Data policy and prominent disclosure: <https://support.google.com/googleplay/android-developer/answer/10144311>
- Data safety form: <https://support.google.com/googleplay/android-developer/answer/10787469>
- Foreground-service declaration: <https://support.google.com/googleplay/android-developer/answer/13392821>
- Ads policy and ads declaration context: <https://support.google.com/googleplay/android-developer/answer/9857753>
- Target audience and content: <https://support.google.com/googleplay/android-developer/answer/9285070>
- Content rating: <https://support.google.com/googleplay/android-developer/answer/9859655>
- Store listing asset requirements: <https://support.google.com/googleplay/android-developer/answer/1078870>

---

## 18. Final recommendation

Phosphor does not need a business-model redesign to reach Google Play. The correct path is a privacy-respecting commercial shell around the existing instrument:

- free install;
- explicit, complete seven-day trial;
- one $3.99 Play Billing unlock;
- no subscription, ads, or account;
- Android vitals plus a local, opt-in, user-reviewed report path;
- contextual permission explanations;
- strong release, compatibility, and policy evidence.

The MVP is already technically substantial and the release AAB, native alignment, core tests, and relay tests are healthy. The main work is not inventing more scope. It is making entitlement, disclosure, signing, testing, compatibility failure, and Play Console evidence as solid as the scope itself.
, signing, testing, compatibility failure, and Play Console evidence as solid as the scope itself.
