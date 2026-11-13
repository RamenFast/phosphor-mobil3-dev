# Google Play: setup and release todo

Started 2026-11-01 in Hermes session `20261101_181137_520e6c` (CLI, titled "Google Play developer account setup"), on branch `play-store`. The code line itself is worked in session `20261101_174338_669df7`, and its starting point is recorded in [the S25 base reproduction receipt](../dev/receipts/play-store/2026-11-01-s25-base-reproduction.md).

What Ben asked (2026-11-01 PST, times from the session record):

> 18:12 · Could you walk me through how to get all that I need setup on the google side to become a developer?

> 19:07 · Gotcha, could you save this in the repo as a durible todo list? With exact links/references where applicable/available for a later session to pick back up on? With a reference to this session/date here

The note from the code session that he brought in:

> For Play later: there's no Play-upload signing key yet, and origin is public, so nothing gets pushed without your yes.

His decision during the walkthrough (18:14):

> my s25 is not rooted, i'm not worried about having a root app on the play store. it's disabled rn

So the S25, with root off, is the device for Play Console device verification, and the root capture path is not treated as a Play blocker. Its foreground service still needs a Console declaration (section 4).

## How to use this file

- Google's rules below were read from the linked official pages on 2026-11-01. Open the link again before acting on a step, because Google changes these.
- Steps marked **Ben** need his identity, his card, his acceptance of legal terms, or his final submission. An agent prepares them and never performs them. Repo law: no push, publish, or store submission without his explicit approval (`docs/AGENTS.md:22`).
- Tick a box only with evidence: a Console screenshot, a command's output, or a commit. Update this file in the same commit as the evidence.

## 1. Developer account · Ben

- [ ] Pick the Google account that will own the developer account. Device verification must later be done signed in as this same owner.
- [ ] Sign up at https://play.google.com/console/signup. You must be 18 or older and accept the Developer Distribution Agreement. Ref: https://support.google.com/googleplay/android-developer/answer/6112435
- [ ] Pay the US$25 one-time registration fee with a credit or debit card in the legal name. Prepaid cards are refused. The fee is not refunded if the identity information is found invalid. Ref: same page.
- [ ] Choose a **Personal** account. An Organization account requires a D-U-N-S number (free from Dun & Bradstreet), an official organization document, and a verified website. Ref: https://support.google.com/googleplay/android-developer/answer/13634885
- [ ] Verify identity with an official government ID that matches the linked Google payments profile. The legal name is shown on Google Play. What else is shown depends on the account type and on whether the app is monetized. The first release is free (`docs/AGENTS.md:33`). Ref: https://support.google.com/googleplay/android-developer/answer/10841920
- [ ] Verify the developer email, which is shown on Google Play, and the contact email and phone, which only Google sees. Phone verification unlocks only after identity verification and device verification. Ref: same page.
- [ ] Device verification: Play Console web, Home, "Verify that you have access to an Android mobile device", View details. Scan the QR code, open the Play Console mobile app, sign in as the owner, select the developer account, tap Verify. It needs a non-rooted physical Android 10+ device. Use the S25 with root off. Ref: https://support.google.com/googleplay/android-developer/answer/14316361
- [ ] Recommended, not a Google gate: 2-step verification on the owner account.

## 2. Create the app in Play Console · Ben, agent briefs

- [ ] Create the app: name, default language, App (not game), Free. Ref: https://support.google.com/googleplay/android-developer/answer/9859152
- [ ] Package name `dev.phosphor.mobil3` (`app/build.gradle.kts:167`). Google: "Package names for app files are unique and permanent" and "can't be deleted or re-used" (same page). Confirm it before the first upload. Debug builds use `dev.phosphor.mobil3.debug` (`app/build.gradle.kts:186`) and never go to Play.
- [ ] Play App Signing: Google holds the app signing key, and we hold only the upload key. Ref: https://support.google.com/googleplay/android-developer/answer/9842756
- [ ] After the app exists, open Play Console's Android developer verification page and confirm `dev.phosphor.mobil3` shows as registered. Refs: https://developer.android.com/developer-verification and https://android-developers.googleblog.com/2026/06/android-developer-verification.html

## 3. Upload key · agent with Ben

The build already has the slot for this key. It is empty.

- [ ] Generate a dedicated Play upload keystore outside the repository, in the same private secrets location as the existing release keystore. Never commit it (`docs/AGENTS.md:19`).
- [ ] Supply its five inputs through the environment or `local.properties`, both read by `propertyOrEnvironment` (`app/build.gradle.kts:19-21`):
  - `PLAY_UPLOAD_STORE_FILE`
  - `PLAY_UPLOAD_STORE_PASSWORD`
  - `PLAY_UPLOAD_KEY_ALIAS`
  - `PLAY_UPLOAD_KEY_PASSWORD`
  - `PLAY_UPLOAD_CERT_SHA256`

  The definitions are at `app/build.gradle.kts:33-58` and `:65`. Select the key with `PHOSPHOR_SIGNING_PROFILE=play-upload` (`:60-66`). Example block: `README.md:92-99`. In the play-store worktree, `local.properties` is a symlink to the root checkout's file (receipt, "Worktree setup"), so an edit there changes the root checkout too.
- [ ] Back up the keystore and its passwords to two controlled places. A lost upload key can be reset through Google ("you are not locked out of your app", 9842756, "Upload key reset"), but the reset takes time.
- [ ] Prove the key with `./gradlew :app:verifyPlayUploadSigning` (task at `app/build.gradle.kts:351`).
- [ ] The full release gate in `scripts/ship-check.sh` wants both the direct-APK signer (`RELEASE_*`) and the Play signer (`PLAY_UPLOAD_*`), and the two must differ (`scripts/ship-check.sh:265-288`, `README.md:81`). Decide with Ben whether the direct-APK signer is provisioned now or whether the Play AAB goes first. The old ask ledgers (removed in the step 1 cleanup, kept at tag `archive/pre-cleanup-2026-11-01`) carried the same open item: provision both signers outside the repository, build the final APK/AAB, verify the signer, manifest, archive boundary and 16 KiB native alignment, then install the exact packaged APK with `dev/pm3 --profile release --serial <serial> install <APK>`.

## 4. App content declarations · agent drafts, Ben submits

Console path: Monitor and improve, App content. Overview: https://support.google.com/googleplay/android-developer/answer/9859455

- [ ] **Privacy policy URL. Broken today.** `PRIVACY.md` exists in this repo, and the in-app link (`app/src/main/kotlin/dev/phosphor/mobil3/ui/ManualSheet.kt:269-270`) opens `https://github.com/RamenFast/phosphor-mobil3/blob/master/PRIVACY.md`. On 2026-11-01 that URL returned HTTP 404. GitHub redirects `blob/master` to `blob/main` (302), the public `RamenFast/phosphor-mobil3` repo has only `main`, and there is no `PRIVACY.md` on it. The same policy is live on the dev repo: `https://github.com/RamenFast/phosphor-mobil3-dev/blob/master/PRIVACY.md` returned 200 at 19:14 PST, and its blob `81556229a1629a9bd368b3d389c5f5d7afebb9fa` is byte-identical to this branch's `PRIVACY.md`. There are two ways to fix it, and both are Ben's call:
  - push `PRIVACY.md` to stable `main` (a push, so it needs his yes)
  - point the in-app link at the dev URL

  Then give Console the same URL. The `ManualSheet.kt` change belongs to the code session (`20261101_174338_669df7`). Requirement: `docs/plans/DISTRIBUTION-PERMISSIONS-AND-SIGNING.md` section 6. Google's User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- [ ] Before publishing, check `PRIVACY.md` against this branch's manifest. It must cover the microphone, playback capture, the root capture service, notification-listener metadata and local files, and it must say the app makes no network requests (remote streaming and the internet permission were removed on 2026-11-13). Spec section 6 lists the required topics.
- [ ] Later, before publishing: give the privacy policy a public address and point the in-app link at it. Today the "privacy policy" link in `ManualSheet.kt` opens `PRIVACY.md` on the stable repo's master, which does not exist there yet (404). Ben, 2026-11-13: the policy will be roughly "no data collection, GPLv3, do as you will"; candidates are the dev repo's file, a file in the main repo, or a small github.io page. Play Console also needs this address.
- [ ] Data safety form, filled in from the exact release binary. Google requires it for every app on Google Play, including apps on closed, open, and production testing tracks. Ref: https://support.google.com/googleplay/android-developer/answer/10787469
- [ ] Ads: No. App access: no login. Explain how a reviewer reaches each audio source.
- [ ] Target audience and content: https://support.google.com/googleplay/android-developer/answer/9285070
- [ ] Content rating questionnaire: https://support.google.com/googleplay/android-developer/answer/9859655
- [ ] Foreground-service declarations, one per type. Each needs the feature that uses the service, the user impact if the task is deferred or interrupted, a link to a video of the user starting it, and a use case. Ref: https://support.google.com/googleplay/android-developer/answer/13392821
  - [ ] `mediaProjection`: `CaptureService` (`app/src/main/AndroidManifest.xml:40-43`)
  - [ ] `specialUse`: `RootCaptureService` (`app/src/main/AndroidManifest.xml:45-51`, subtype text at `:50`). Expect the closest review here.
  - [ ] `mediaPlayback`: `PlaybackService` (`app/src/main/AndroidManifest.xml:68-76`)
- [ ] Check the in-app disclosure shown before microphone access (`AndroidManifest.xml:10`) and before notification-listener access (`AndroidManifest.xml:52-60`) against the User Data policy's prominent-disclosure rules (link above).

## 5. Store listing · agent drafts, Ben approves

Ref: https://support.google.com/googleplay/android-developer/answer/9866151

- [ ] App icon: 512 by 512 px, 32-bit PNG with alpha, at most 1024 KB, no badges or text that suggests ranking or price.
- [ ] Feature graphic: 1024 by 500 px, JPEG or 24-bit PNG, no alpha.
- [ ] At least two screenshots, JPEG or 24-bit PNG, no alpha, taken from the release build. `docs/screenshots/` holds older candidates.
- [ ] Short description, full description, and support email.

## 6. Build and internal test · agent builds, Ben uploads

- [ ] Upload an Android App Bundle (`.aab`). APK uploads are only for apps created before August 2021. Ref: https://support.google.com/googleplay/android-developer/answer/9859348
- [x] Target API: `targetSdk = 36` (`app/build.gradle.kts:169`). Since 2026-08-31, new apps and app updates must target Android 16 (API 36) or higher. Ref: https://support.google.com/googleplay/android-developer/answer/11926878
- [ ] `versionCode` must increase with every upload (9859152). Today it is derived from `appVersion = "2.0.0"`, giving `2000000` (`app/build.gradle.kts:10-12`).
- [ ] Before any upload, run the release checks in `docs/AGENTS.md`, Validation, and `docs/plans/DISTRIBUTION-PERMISSIONS-AND-SIGNING.md` section 8: bundletool validation, signer fingerprints, 16 KiB alignment, Data safety and foreground-service answers matching runtime behavior, and a reachable privacy policy. The bundle step is `:app:bundleRelease :app:validateReleaseBundle :app:checkPlayBoundary` (`scripts/ship-check.sh:315`).
- [ ] Internal testing release: up to 100 testers, and builds normally reach them within seconds. Ref: https://support.google.com/googleplay/android-developer/answer/9845334

## 7. Closed test and production access · Ben

This applies to personal accounts created after 2023-11-13. Ref: https://support.google.com/googleplay/android-developer/answer/14151465

- [ ] Closed testing unlocks once app setup (sections 4 and 5) is complete.
- [ ] At least 12 testers must be opted in continuously for the 14 days before applying. A tester who opts out early does not count, and opting back in restarts their 14 days. Recruit more than 12.
- [ ] Give testers a feedback channel and tell them to stay opted in for the full 14 days. Feedback appears under Monitor and improve, Ratings and reviews, Testing feedback. Keep a record, because the application asks for a summary.
- [ ] Apply for production access from the Dashboard and answer the questions about the test, the app, and its readiness. Google may require more testing if fewer than 12 testers stayed opted in or engagement was low.
- [ ] Production release and rollout need Ben's explicit yes. Ref: https://support.google.com/googleplay/android-developer/answer/9859348

## Outside Play

- A limited distribution account on the Android Developer Console needs no fee and no government ID, and installs on up to 20 devices. It is enough for sharing builds with friends, but it is not a Play listing. Ref: https://developer.android.com/developer-verification

## Background in this repo

- `docs/plans/GOOGLE-PLAY-PUBLISHING-PLAN.md`: the 2026-07-22 plan, archived at the 2026-08-05 scope reset and moved here in the step 1 cleanup. Its links to other docs point at files that now live only at tag `archive/pre-cleanup-2026-11-01`. Its trial and $3.99 Billing model is superseded by `docs/AGENTS.md`, Product boundaries (the first public release is free, with no purchase flow). Its `minSdk = 35` is stale: the minimum is now 29 (`app/build.gradle.kts:168`). Its section 17 is still a useful index of official pages.
- `docs/plans/DISTRIBUTION-PERMISSIONS-AND-SIGNING.md`: the signing, permission and declaration requirements (moved from `spec/` in the step 1 cleanup).
- `scripts/check-play-boundary.sh` and `scripts/test-play-boundary.sh`: the source and artifact boundary checks for the Play build.

## Picking this up

Read session `20261101_181137_520e6c` with Hermes `chat_history_lookup` for the full walkthrough. Then work the first unticked box above, and re-open its link before acting.
