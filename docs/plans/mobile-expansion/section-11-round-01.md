# SECTION 11 / R03 / ROUND 1: independent full-section review

## Decision

**Overall score: 7/10.** The section has a substantial offline manual, a sound bounded pure navigation model, preserved characters, and careful audio-acceptance caveats. It does not yet meet the complete practical-help outcome. Several existing controls have no searchable explanation, opening a chapter or typing can move the relevant content out of view, and one manual control still uses the compact non-wrapping row. Appearance help also describes an earlier integration stage.

The largest intent mismatch is help written as implementation invariants instead of instructions that let Ben operate and recover the actual instrument. Twenty-six chapter IDs satisfy the inventory target. They do not establish coverage of every feature or control.

Disposition: return the finite findings below to the coordinator for correction. This is round 1, not final acceptance. The coordinator owns corrections, integration checks, any subsequent R15 critique, and device work. Original reports and receipts remain intact.

## Assignment, scope, and provenance

- Assigned reviewer: GPT Astra / high. The coordinator supplied this model/effort assignment. This worker did not query a provider, change routes, or spawn workers.
- Mobile target: `485bc9fa946cf3e33cc32c062868f4192a0327b6`.
- Shared target: `0ffd658d7f19e68180c2720e0500b23644619e90`.
- Request received: 2026-09-08 15:44:36 UTC.
- Source reading stopped: **2026-09-08 15:55:39 UTC**. No further source reads are needed.
- Bound: 20 minutes, with an explicit 16:06 UTC stop/release deadline. The source read ended within both limits.
- Governance read first: the system filing cabinet and context standards, followed by coding and scratch-placement standards. Pinned mobile `docs/AGENTS.md` and shared governance were also read.
- Every production source read came from an immutable Git object or its new private copy. No changing working source was read. No Git mutation, product edit, original-report overwrite, build, test execution, Gradle, Android/JNI/GPU execution, ADB, GUI, audio, network, download, service operation, Python, or subagent action occurred.
- Production ceiling: **24 distinct current mobile production paths**, including the manifest, plus **one immutable pre-expansion ManualSheet snapshot**, conservatively counted as **25 production snapshots**. No shared production implementation was re-reviewed. The shared commit object was verified and pinned. Shared renderer/native gate results remain inherited evidence, not a new engine audit.
- The complete `ManualContent.kt` and `ManualSheet.kt` were inspected. Supporting source and test files were inspected at relevant seams, not represented as complete independent reviews of their owning sections.
- Baseline used only to check preservation: mobile `ccee7c827da44d4f7dc2268eecbe6619685594a6:app/src/main/kotlin/dev/phosphor/mobil3/ui/ManualSheet.kt`. This does not change the review target.

Private artifact directory:

`/home/ben/.jcode/scratch/section11-r03-round1-astra-high-Brjvr3b9`

`readset.tsv` records repository, commit, original path, Git blob ID, and SHA-256 for captured inputs. Local governance is explicitly marked non-Git. Its SHA-256 is:

`e118d9a687ea869194073839ac9f9008bb627803f37b8ac7c7014af14a596179`

Key input SHA-256 values:

| Input | SHA-256 |
| --- | --- |
| Canonical MOBILE-EXPANSION-PLAN.md | `3f280299b39d4765296de9c39e0005c773cddd08419f0ecee81736cbba8d56f4` |
| section-11-manual-contract.md | `69237e2a8b7e76ae6dc955de93370bdd4743ba59e3ebbbe3feeb0a6ba9315d1d` |
| section-10-11-integration.md | `0c63b6702e21e3b73fdb542abe53e47b6266ada43ddac7ad2414bb9593c52a90` |
| ManualContent.kt | `14df6d7daf55fdd105014d784d48f286599cdc4ba864418ffd68ebc72abd87f6` |
| ManualSheet.kt | `8e6ee3ac33c6c3cdc947f0c1e16f296a0e906b8e0838aba8fca19aef3f88a923` |
| ManualContentTest.kt | `d382de39fad44afa53286062de25b90445bf7673ad0b9a2900a97f34202d2de5` |

The final artifact digest is emitted after writing and sealing this report. `SHA256SUMS` includes the report and private readset. Files are sealed read-only, not claimed to have filesystem immutable flags.

## Requested outcome and requirement coverage

Authority: canonical plan lines 229-235, R15 lines 294-310, and R03 acceptance line 320. The active expansion manual contract is `spec/EXPANSION.md:161-171`. The local manual contract supplies the concrete navigation, availability, callback, accessibility, and evidence conditions at lines 7-21.

The requested outcome is an indexed, searchable, practical, meme-rich manual for the entire approved instrument. It must preserve the hidden workshop and other art, give the turtle a visible smile and tail, and tell the truth about what works now. An offline model, a build, or a review score does not substitute for rendered/device acceptance.

### All approved outcomes

All ManualContent line references below are at the exact mobile target. A chapter mapping means source coverage was inspected, not that its owning feature has been accepted.

| Requirement | Chapter and source evidence | Assessment |
| --- | --- | --- |
| R01 root | `root`, 30-32. Disclosure/actions in ManualSheet 193-209 | Experimental 16 kHz mono and missing original stereo/SoundCloud/latency acceptance are explicit. Existing opt-in/provider callbacks remain. Root row readability is F4. Backend acceptance remains open. |
| R02 floating HUD | `hud`, 78-80 | Overlay, transparency, one owner, PiP handoff, and refused mic transfer are explained. Current menu paths and distinction from STATS HUD need F1. Device acceptance is explicitly pending. |
| R03 manual | Complete ManualContent 6-124 and ManualSheet 37-315 | 26 chapters and distinct contextual responses, index/search/history, four beasts, smile/tail source, links. F1, F2, F4, and F5 prevent complete practical/recoverable delivery. |
| R04 meaningful motion/layout | `sections`, 42-44, mentions a Motion group. `appearance`, 81-83, describes looks | No substantive searchable explanation of meaningful glyphs, reduced motion, visible-only animation, frame rate, or beam reconstruction. F1. No animation/performance claim made from this review. |
| R05 HDR | `hdr`, 84-86 | Correctly planned. Distinguishes genuine surface/compositor headroom from bright SDR, notes separate transparent-HUD proof, names SDR alternative. No accepted HDR claim. |
| R06 brightness pin | `brightness`, 87-89 | Correctly planned, window-only and foreground-only, separate from beam/gain/HDR, system limits retained. Current non-pin alternative should be explicit under F1. |
| R07 random color/interval | `random`, 57-59 | Independent modes and track identity described. Actual precedence, legal ranges, and user-facing rapid-cycle recovery are incomplete. F1. |
| R08 six saved colors | `light`, 54-56 | Six slots, membership, edit/delete, appearance separation described. Unnamed fallback and unqualified one-slot statement omit generated-color ownership. F1. |
| R09 mic route/mix | `mic`, 33-35, and `mix`, 36-38 | Existing input path is distinguished from requested/routed accessory facts. Mixing/accessory work remains planned. Bluetooth bandwidth/output limitations and visualization-only intent are retained. No two-input acceptance claim. |
| R10 settings dismissal | `gesture`, 39-41 | Deliberate dismissal and delayed-input recovery are described. Actual opening instruction was removed rather than preserved in help. F1. This review does not re-accept the Settings adapter. |
| R11 expandable settings | `sections`, 42-44 | Six independent groups, no setting mutation on expansion, focus/scroll expectations, and explicit exits are described. Android ordering/readability still needs device evidence. |
| R12 themes/appearance | `appearance`, 81-83 | Four names, true-black AMOLED, translucent-not-blur Glass, and preset ownership are correct. Runtime-integration wording and practical editor help are stale at this pin. F3. |
| R13 HOLD/BLACK/inspect | `hold`, 72-74, and `inspect`, 75-77 | Display versus transport, retained-image ownership, reset, current timeline, and present/scanout/source-age distinctions are sound. Exact current action path should be supplied in F1. No physical latency or buffered-age acceptance claim. |
| R14 startup | `startup`, 90-92, plus `start`, 18-20 | Correctly planned. Fresh launch differs from resume/rotation, no-default remains inert, real permissions and inert imports are retained. No configured-startup acceptance claim. |
| R15 critique | This report, pinned canonical protocol 294-310 | One independent assigned-model section score, finite source findings, limitations and disposition. No new worker or route setup. Root owns retained compact receipt and subsequent review. |
| R16 instrument presets | `presets`, 60-62, `preset-recovery`, 63-65 | Named operations, authored settings, exclusions, inert import, undo, matching receipts, and explicit durability recovery are covered in text. Current Settings/LIGHT entry wiring is present. Existing R16 acceptance is not re-scored. |
| R17 Signal Check | `signal`, 66-68, and `rails`, 69-71 | Provenance, unavailable versus zero, measured silence, stale samples, digital rails, duplicated mono, and progress-not-read-count caveats are covered. Current OPEN SOURCES recovery route should be named under F1. Existing R17 acceptance is not re-scored. |

### All 26 chapters and current-source coverage

| Chapter IDs | Complete text lines | What was checked |
| --- | --- | --- |
| start, local, relay | 18-26 | SRC/MODE/LIGHT orientation, local picker/cancel/queue/reselection, saved Tailscale-only remote entry, audio versus geometry ownership and reconnect distinction. |
| capture, root, mic, mix | 27-38 | Standard Android consent versus root manager/profile authorization, source silence ambiguity, mic permissions/privacy route facts, pending accessory/mix limits. |
| gesture, sections, mode | 39-47 | Gesture/dismissal content, six groups and accessibility intent, representation versus source and remote geometry refusal. Practical gaps are F1. |
| gain, beam, light, random | 48-59 | Authored versus measured gain, view ownership, focus/emission/persistence, six slots, independent random modes. Practical light and control coverage is F1. |
| presets, preset-recovery | 60-65 | Separate named instrument setup ownership and failure recovery. No new device/CRUD correctness claim. |
| signal, rails | 66-71 | Current observations and provenance, not generic claims from silence. |
| hold, inspect, hud | 72-80 | Display/transport/held-image/source distinctions, surface ownership and explicit acceptance limits. |
| appearance, hdr, brightness, startup | 81-92 | Implemented-versus-planned wording against current source and retained integration receipt. Appearance is stale, F3. Other planned labels stay. |
| privacy | 93-95 | Offline manual, no account/ads/analytics/feed, explicit relay and links, separate access types, no prompt loops or silent source switch. |

These rows account for every chapter, including every current source. Content can share a chapter, but the missing user actions below still need explicit searchable explanations.

### Manual mechanics, preserved surfaces, state, and boundaries

| Contract | Source observation | Evidence boundary |
| --- | --- | --- |
| Stable IDs and 24+ distinct contextual responses | ManualContent 6-96 has 26 chapters. Responses follow practical paragraphs in ManualSheet 274-276 | Static inventory and inherited tests, not a new executed test. Humor does not replace the selected paragraph. |
| Bounded, case-insensitive all-word search | ManualContent 16, 101-106 bounds to 256 chars and uses Locale.ROOT. ID/title/availability/body/response participate | Empty and whitespace queries return the index by all(empty). No match gives explicit recovery text, ManualSheet 260-266. Content gaps remain searchable gaps. |
| Chapter admission and bounded history | ManualContent 98-99 and 110-124 admit known IDs, keep 32 entries, return through chapters and filtered index | No source defect found in intended UI navigation model. Search intentionally clears history. INDEX preserves the filter, as the actual test expects. |
| UI BACK versus sheet Back/Close | ManualSheet 269-270 uses navigation.back/index. Sheets 301-310, 365-368, 509-515 owns sheet dismissal | Hardware/system Back is deliberately the independent sheet exit. It is not incorrectly treated as chapter BACK. |
| Entry and return | Sheets 777 and 1472-1473 open manual from Source and Settings. PhosphorScreen 802-807, 833-859 remembers origin and supplies callbacks | Both source branches and callback adapters are present. Manual Close returns to its origin through the sheet completion path. No phone callback exercise occurred. |
| Search/chapter viewport | ManualSheet 162-172 and 244-279 uses one scrolling column and unconditional scroll-to-zero on query/chapter change | F2. Pure navigation tests do not exercise this Compose effect. |
| Navigation and disclosure lifetime | ManualSheet 163-166 uses remember for taps, disclosure, expansion and navigation. PhosphorScreen 274-281 owns current sheet/origin | They are transient and reset after leaving composition. Durable chapter restoration is not promised. Manifest 31 handles listed orientation/size changes, not every possible recreation. No process-recreation acceptance claim. |
| Welcome and other beasts | Immutable baseline comparison preserves WelcomeArt, skeleton, rhy/fox, umbrella glyphs and their identities/prose. Turtle changes at ManualSheet 55-60 | Four beast identities remain. Root controls are now behind an explicit bestiary expansion after discovery. This is not lost discovery persistence. |
| Turtle smile/tail and art scrolling | ManualSheet 55-60 contains left mouth `\_/` and separate right tail `_\_>`. Lines 176-177 and 223-227 give horizontal scrolling and labels | Source art and accessibility descriptions exist. Their actual pixels across themes/large fonts are unaccepted. |
| Five-tap and persistence | ManualSheet 179-184 retains threshold and callback. MainActivity 2040-2042 publishes/saves bestiary_found. Restore is 1926 | Source linkage and retained baseline are inspected. No device tapping, persistence flush, or update-survival test ran. |
| Root opt-in/disclosure/grant/manager/cancel | ManualSheet 193-209, MainActivity 1691-1714, RootCaptureSettings 17-64 | Existing callbacks and limitations stay. NOT NOW only closes disclosure. Busy cancel/disable use false callback. Enable checks authorization without starting audio. F4 concerns the root row presentation, not backend authorization correctness. |
| Root status visibility | Current root state is refreshed into the sheet. SourceSheet 743-773 exposes root input and explicit standard alternative | Opening a manual chapter does not start capture or make active capture secret. Notifications/backend visibility are inherited outside this local manual review. |
| Public links | Five unchanged exact URLs in ManualSheet 294-307, routed through PhosphorScreen 858 to MainActivity 2045-2049 | Explicit callback only. No WebView or automatic fetch. F5 records the existing unhandled-to-user launch failure. No network destination was opened. |
| Wrapping, labels, keyboard and touch | ManualHeading, ManualKey and LinkCard wrap. Search and manual keys have 48dp minimums and focus decoration. Prose wraps through Type 56-73 | Root SheetRow is the exception, F4. Clickable keyboard mechanics are source-visible, not keyboard/device acceptance. |
| Theme/large-font legibility | Theme palette is supplied to the sheet. Art scrolls horizontally. Fox keeps its existing fixed RhyGreen at 102/221 | No rendered contrast, clipping, or shape judgment is certified. Specifically include the fixed green fox on Light and Glass-over-moving-content in later pixel checks. |
| Privacy/performance | Search operates only on bounded static text. ManualNavigation keeps bounded transient history. No shell evaluator, WebView, remote feed, recorder, analytics or persistent query log exists in the complete manual files | Manual itself adds no polling or idle animation loop. It inherits SheetHost reduced-motion handling. Broader app privacy/performance was not re-audited. |

## Finite source findings

The following **five** findings are the complete correction set from this pass. Schedules are source-derived reproductions for the coordinator, not actions this reviewer performed. Line references use the exact target, except explicitly labeled preservation baseline references.

### F1. P1: nominal topic inventory is not comprehensive practical help

**Requirement:** plan 232-234 and 320, local manual contract 7 and 13-15.

**Exact affected source:** `app/src/main/kotlin/dev/phosphor/mobil3/ui/ManualContent.kt:18-19,39-59,66-91`. The complete 26-chapter inventory is lines 18-95.

Concrete missing or incomplete help:

1. Opening Settings is described only as being from the play bar or keeping its existing gesture. It does not say to swipe up. The previous manual explicitly said so at baseline ManualSheet 248-254. Current Console 188-189 and 231 retain the play-bar upward-swipe seam. Double-tap playback exists at PhosphorScreen 657-665 but is absent from the manual.
2. The entire index lacks operational help for reduced motion/meaningful motion, frame rate versus beam reconstruction, GRID/GRID DATA, STATS HUD/BAND versus floating HUD, FULLSCREEN, scope rotation/UI placement/system rotation lock, AUTO PiP, and BACKGROUND LINGER. Actual controls are in Sheets 1249-1385 and 1420 onward. ScopeModel 45-72 explicitly distinguishes panel presentation from reconstruction, while manual search has no corresponding explanation. These are current controls, not future expansion requests.
3. Light text says only "documented fallback" at line 55. It never names that fallback. The generated-color owner can override even a single selected slot, contrary to the unqualified one-slot sentence. LightSheet 83-88 and 130-154 exposes the actual precedence: generated color, selected slots, otherwise the selected preset. TRACK retains interval values. The manual lacks the actual add action, legal 0.1-60-second range, sub-one-second photosensitivity explanation, and KEEP SAFE recovery. LightSheet 73-80, 104-154 and LightSettings 23-27,94-100 contain those rules/actions.
4. Pause and Signal Check paragraphs mostly explain internal guarantees. They do not name the current `Settings > DISPLAY & HUD > PAUSE DISPLAY ONLY` route or Signal Check's `OPEN SOURCES` recovery. Those controls exist at Sheets 1237-1246 and SignalCheckSheet 50-57. Pending features also need an explicit current alternative where absent, rather than only their intended later policy.

**Schedule:** a user opens the manual and searches `rotation`, `double tap`, `frame rate`, `beam rate`, `linger`, or `reduced motion`. The complete text has no matching operational chapter. A user trying to configure safe light timing reads about a guard but cannot learn its threshold or action from this help.

**Why inherited tests miss it:** ManualContentTest 24-28 asserts a fixed set of IDs. Lines 9-20 assert string length and uniqueness. Neither maps actual current controls to instructions and recovery paths.

**Correction:** add a finite control-to-help map for the items above, with real labels, gestures, ranges, precedence and recovery. Restore useful prior instructions while replacing their obsolete three-color/theme claims. Add requirement-linked search examples and review their actual answer content, not just chapter existence. Keep unavailable work explicitly planned.

### F2. P2: typing or changing chapters jumps to the welcome, not the task

**Exact affected source:** `ManualSheet.kt:162-172,193-249,259-279`.

Line 167 runs `scroll.scrollTo(0)` whenever query or chapter ID changes. The same scrolling column places welcome art, expanded root controls and all four beasts before search and selected chapter content.

**Schedule:** reveal/open the bestiary, use a short landscape or large-font viewport, scroll to SEARCH CHAPTERS, then type a character. `navigation.search(it)` changes query and the effect moves the column to offset zero. The search field and results can leave the viewport. Likewise, selecting NEXT CHAPTER while the workshop is expanded returns to the welcome/root prefix instead of the new chapter heading.

This is a deterministic source scroll target. Whether the IME also requests bring-into-view and the final focus-visible ordering require Android observation. Neither outcome makes unconditional top-of-document scrolling the correct chapter/search target.

**Correction:** separate the workshop from the main reading viewport or scroll to the relevant search/chapter anchor. Do not reset the entire column on each keystroke. Preserve visibility during text entry and make chapter navigation land on its heading. Add an actual Compose/Android schedule with workshop expansion, IME, short viewport and large font. ManualContentTest's string-presence tests at 107-119 cannot establish this behavior.

### F3. P2: appearance help is frozen at the pre-integration stage

**Exact affected source:** `ManualContent.kt:81-83`.

The chapter says "Runtime editor integration is in development" and offers no actual editor procedure. At this exact pin, Settings composes `AppearanceEditor(state, actions)` at Sheets 1414-1418. MainActivity 324-365 initializes, publishes and wires its runtime workflow. AppearanceEditor 104-110 and 145-176 contains LOAD DRAFT, PREVIEW, APPLY, CANCEL, named save, rename/delete, legacy snapshots and reset actions.

The retained `section-10-11-integration.md:3-9,23,27-29` records writer release and the combined Android host gate, while explicitly retaining device/review gaps. The local manual contract at 15 requires availability wording to track later implementation receipts.

**Schedule:** after entering the actual integrated appearance editor, the user searches `appearance` in the manual. Help describes runtime integration as forthcoming instead of distinguishing the existing draft/preview/apply/save actions and their still-unaccepted phone behavior.

**Correction:** describe this as host-integrated with device acceptance pending, not fully accepted. Add current editor/recovery instructions and exact labels. Do not change mix/HDR/brightness/startup planned labels or infer migration/Glass phone acceptance from the combined host gate.

### F4. P2: the root toggle bypasses the manual's wrapping/accessibility adaptation

**Exact affected source:** `ManualSheet.kt:195-197`, `Controls.kt:359-388`, `Type.kt:34-49`, `SettingsControlAccess.kt:30`, `Dimens.kt:22`.

The root entry still uses generic SheetRow. Its Mono label inherits one line outside the Settings accessibility provider. It has no manual-local wrapping override. The row has 14dp padding but no explicit 48dp minimum, unlike ManualKey. Checked state is only a checkmark prefix and border color, without a switch/checkbox role or Boolean state semantics. It also lacks the manual key's explicit focus-border treatment. Foundation clickable may expand hit bounds and supports keyboard activation, so this is not a claim that the row cannot be clicked or that a measured hit target failed.

**Schedule:** open the workshop with a narrow multi-window width and large font, then inspect the ROOT CAPTURE label and focus/state presentation. Whenever its measured line exceeds the available width, Type's single-line ellipsis truncates it instead of wrapping. This is precisely the compact-text behavior that ManualHeading already avoided for other manual headings.

**Correction:** use a manual-local wrapping toggle with an explicit minimum row size, readable focus treatment and checked-state semantics. Preserve the existing disclosure/enable/disable/cancel callback conditions. Add control-specific assertions and actual narrow/large-font accessibility checks. A generic `maxLines = Int.MAX_VALUE` substring elsewhere in ManualSheet does not cover this root label.

### F5. P3, inherited seam: a failed external-link launch has no user-visible recovery

**Exact affected source:** `MainActivity.kt:2045-2049`, reached from `ManualSheet.kt:294-307` through `PhosphorScreen.kt:858`.

`openLink` wraps ACTION_VIEW launch in `runCatching` and discards failure. This is an existing callback seam, not a newly introduced URL or network behavior.

**Schedule:** a user taps any manual card on a device with no enabled handler for that HTTPS view intent, or an Activity launch is rejected. `startActivity` throws, the catch absorbs it, and the user gets no reason or next action. No web request was made during review.

**Correction:** keep the explicit external-browser callback and existing URLs, but present a small failure message with a concrete browser/URL recovery. Do not add automatic retries, a WebView, or a network fallback. The existing test at ManualContentTest 122-127 proves destination strings only, not failure handling. Root may retain this lower-priority inherited item separately, but should not call the recovery path complete.

## Actual tests and inherited checks

**New test/build/device executions in this review: none.** Source inspection, Git-object export, private-copy diffing and SHA-256 provenance hashing were the only checks performed. A command that was rejected before running produced no source or artifact mutation. All successful writes were new private artifact files or the owned readset manifest.

The pinned ManualContentTest has **12 JUnit methods**. More precisely, nine exercise the pure model and three inspect ManualSheet source strings. Calling all twelve rendered UI tests would be incorrect.

| Actual test source | Inspected evidence and limitation |
| --- | --- |
| ManualContentTest 9-28 | Distinct substantial entries and exact expected ID inventory. Does not verify practical completeness. |
| ManualContentTest 31-51 | Empty/whitespace, case-insensitive all-word search, Turkish locale, input bounds and command-shaped query treated as data. No execution in this review. |
| ManualContentTest 53-80 | Chapter admission, filtered-index Back, search history reset, 32-entry bound and immutable lists. Intended model behavior is coherent. |
| ManualContentTest 83-93 | Planned labels and root caveats, unavailable versus zero. No current appearance integration assertion. |
| ManualContentTest 95-119 | Five-tap/callback/turtle/wrapping/search token assertions. They do not execute discovery persistence, Compose scroll/focus order, actual root row layout or art pixels. |
| ManualContentTest 122-133 | Exact five URL callback strings and source-file lookup. No browser launch/error test. |
| RootCaptureProductTest 32-43,114-142,191-201 | Root-off and explicit authorization/backend boundaries, private settings, manifest role/source assertions. Protocol/ownership tests were read as relevant context, not rerun or used to certify root audio. |
| AppearanceRuntimeWiringTest 14-100 | Current runtime migration/action/lifecycle/editor source assertions. The test itself disclaims Android lifecycle proof. Supports the distinction behind F3. |
| PauseDisplayPolicyTest 8-70,73-105 | HOLD default, transport versus display-only labels, missing frame/current source/present-pending behavior and source adapter assertions. No scanout/age claim. |
| SheetEntryPolicyTest 20-165 and relevant test inventory | Entry/exit policies and explicit source-only header/Back/close checks. Not an executed ManualSheet navigation or IME schedule. |

Inherited only:

- The manual contract at lines 25-27 reports 12 passing tests at 15:01 UTC and again at 15:06 UTC, using the actual production model plus supplementary sheet-source assertions. It explicitly says the initial host runner did not compile ManualSheet and that text tests are not pixel acceptance.
- Retained coordinator **gate405519lhyk** reports **871 JVM tests / 73 suites**, **126 native host tests**, **3 offscreen public GPU retention tests**, Android lint, actual Android/Compose compilation, engine check, release-helper build, production source boundary, and **two debug APKs**. These are the application and androidTest APKs, not a new verified release installation.
- The pinned integration receipt is the source of those counts. Raw private result archives and APK bytes were not independently opened or rehashed here. The receipt describes a combined released working-source gate, not final clean reviewed freeze/device acceptance. This review does not upgrade that into an independently reproduced exact-commit build.
- That receipt states no installation or device actions occurred. This review also performed none.

## Truthful current availability and acceptance blockers

Current at the pinned source: the offline manual model/UI and explicit source/settings entry callbacks are implemented. Discovery persistence, root authorization callback wiring, external-browser launch wiring, and the appearance runtime editor are present in source. Normal feature labels may describe existing controls without proving their device behavior.

Still not accepted: original root stereo, SoundCloud compatibility, acoustic latency, actual buffered-source age, mic mixing/accessory routing, genuine HDR, brightness pin and configured startup. None is accepted by this report. Root authorization is an existing opt-in entry, not a certificate of full audio support.

Remaining acceptance work, owned by root:

1. Correct F1-F4 and decide the retained F5 callback recovery disposition, then run the appropriate actual checks outside this worker's prohibition boundary.
2. Exercise real manual search, result admission, previous/next, in-manual BACK/INDEX, CLEAR SEARCH, no-result recovery, independent hardware Back/Close, Source/Settings return paths, keyboard/IME ordering and the F2 expanded-workshop schedule.
3. Check the turtle's smile and separate tail, welcome and other beasts, text wrapping, horizontal art scrolling, root disclosure/cancel/grant/manager, focus and touch targets across Light, Dark, Glass and AMOLED at large fonts and narrow widths. Include the fixed green fox and Glass content. Host text/substring checks cannot close this.
4. Exercise five-tap persistence/reopen and an explicit external-link success/failure path on an authorized phone. No phone installation has been done by this review, and no phone acceptance is implied.
5. Preserve the separate open root/audio/HDR/brightness/startup/mixer gates and prior accepted R16/R17 review records. Do not resolve these limitations with future-tense success claims in the manual.

There is no blocker to delivering this source review. The blocked part is runtime/device acceptance: the user explicitly prohibited the actions required to observe it in this worker. Best current result is this exact-hash review, with finite source schedules and inherited evidence separated. The smallest next action is coordinator-owned correction and authorized verification, not another source reread by this completed worker.

## Release and closeout

Only this new private report/readset directory changed. No product or Git state changed. No device, build, service, source-write, or worker resource was acquired. Source reading stopped at 15:55:39 UTC. On submission of the completion report, **all reader/review holds are released**. The worker has no follow-up reads, reruns, or background work queued and will not reread or rerun after completion.
