# Interstitial Ad Reliability Design

## Goal

Make feature-entry interstitial requests use the intended policy area, wait for the asynchronous Advertise SDK initialization, and produce enough diagnostics to distinguish policy rejection from loading or coordinator failures.

## Confirmed Failure Modes

Device logs prove that AdMob interstitial preload and direct load can complete successfully. The primary application bug is an ambiguous feature-entry API: some callers pass a localized page label while others pass an `AdAreaKeys.Interstitial` value. The mediator always treats the value as a page label, so direct area keys miss every branch and fall back to `enterFileManageAdv`.

The existing mapping also maps `images` to the speaker-clean area and repeats the speaker-clean branch. Separately, application initialization is asynchronous and only the splash flow explicitly waits for it. A feature entry opened through another route can request an interstitial before initialization finishes.

## API Boundary

`AdvertisePageMediator` will expose two explicit entry points:

- `showFeatureEntryInterstitialByTag(activity, nextTag, onClosed)` resolves a localized page label to an area key.
- `showFeatureEntryInterstitialByAreaKey(activity, areaKey, onClosed)` forwards an already resolved area key unchanged.

The existing `showFeatureEntryInterstitial` method will remain as a compatibility wrapper for label-based callers. Existing callers that pass `AdAreaKeys` constants will move to the explicit area-key method.

Page-label resolution will live in a small pure Kotlin resolver. It receives a set of known labels so its behavior is unit-testable without an Android `Context`. File categories such as images, videos, audio, APKs, and the generic large-file/file-manager labels resolve to `enterFileManageAdv`. Unknown labels retain the current file-manager fallback.

## Initialization Sequencing

The mediator will wait for `SdkInitUtils.awaitInitialization()` in an Activity lifecycle coroutine before calling `AdvertiseSdk.showInterstitialAd`. The wait is asynchronous and does not block the main thread. If initialization completed unsuccessfully, the mediator will log the state and continue the application flow through `onClosed` rather than attempting an invalid request or leaving navigation stuck.

The callback remains exactly-once. Activity destruction suppresses SDK display work, but completion is still invoked when the Activity is usable so current navigation behavior remains intact.

## Diagnostics

Every request will log the requested area key, initialization state, and the transition into the SDK. SDK-side diagnostics will distinguish:

- full-screen coordinator acquisition failure;
- policy rejection for the requested area key;
- unsupported or inactive ad platform;
- AdMob load failure, including error code, message, domain, and response information where available.

Existing analytics events remain unchanged. Logs are diagnostic only and must not turn policy rejection into an ad request.

## Policy Constraints

This work does not change the remote policy, traffic routing, frequency caps, global ad switch, or any `rate=0` result. A policy-rejected request continues immediately through the business callback. The fix only ensures that the correct area key reaches the policy engine and that the reason for rejection is observable.

## Testing

Focused unit tests cover the pure resolver, including direct area-key forwarding by the public source contract, every supported page-label mapping, file-category mapping, unknown-label fallback, and removal of duplicate speaker-clean branches. A source-contract test protects the distinction between the tag and area-key APIs and verifies that callers passing constants use the explicit area-key method.

Verification runs the complete Debug unit suite, Kotlin compilation, and Debug assembly. Device logs can then be filtered by `AdvertisePageMediator`, `ShowInterAd`, and `AdLoader` to confirm the real policy decision for each request.

## Success Criteria

- An `AdAreaKeys.Interstitial` value reaches the Advertise SDK unchanged.
- Localized labels resolve to their intended feature area.
- Feature-entry requests do not race SDK initialization.
- Every non-display path has a specific diagnostic reason.
- Business callbacks are not blocked by initialization or advertisement failures.
- Remote policy and frequency behavior remain authoritative.
