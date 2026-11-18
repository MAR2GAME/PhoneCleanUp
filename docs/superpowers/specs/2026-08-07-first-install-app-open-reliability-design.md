# First-Install App Open Reliability Design

## Goal

Increase first-install App Open ad display rate without extending the existing online ad wait beyond 12 seconds, blocking normal navigation, or requesting ads before Google UMP permits it.

## Root Cause

The bundled preload policy disables `open_app`. Firebase Remote Config enables it asynchronously, but the current splash loop retries preloading only once. If that retry occurs before Remote Config applies, the remaining wait only polls an empty cache and never starts an ad request. SDK initialization also completes before all asynchronous advertising configuration is ready.

## Chosen Approach

Keep remote policy authoritative and fix the host-side orchestration. Do not force the bundled `open_app` policy to `true` and do not require a new advertising SDK release.

The splash flow will use a small, pure Kotlin preload retry scheduler. While the existing bounded ad wait is active, it permits a new preload attempt at most once every 500 milliseconds when no valid cached ad exists and no App Open request is already loading. This changes the current one-shot retry into bounded, throttled readiness polling. As soon as Remote Config enables startup preloading, the next permitted attempt starts the request.

## Request Gates

An App Open preload attempt is dispatched only when all locally observable gates pass:

1. The advertising SDK initialization job has completed successfully.
2. Google UMP reports `ConsentInformation.canRequestAds()`.
3. The SDK startup preload policy permits `LOAD_TIME_OPEN_APP`.
4. No App Open ad is already loading and no valid cached App Open ad exists.

The mediator will pass `LOAD_TIME_OPEN_APP` to the SDK preload API so the request and its diagnostics retain the real trigger source.

## Timing And Navigation

- Online App Open waiting remains capped at 12 seconds.
- Offline waiting remains capped at 2.5 seconds.
- Retry eligibility is checked every 100 milliseconds, but a preload dispatch is throttled to once per 500 milliseconds.
- A cache hit immediately ends preparation and proceeds to display.
- A timeout, SDK initialization failure, UMP denial/unavailability, remote policy remaining disabled, load failure, or no fill immediately follows the existing progress-completion and navigation path.
- No new unbounded wait or delay is introduced.

UMP initialization and the consent form retain their existing behavior. Early preload orchestration may start concurrently, but the request gate prevents an actual ad request until UMP allows it.

## Components

### LaunchOpenAdPreloadScheduler

A pure Kotlin state holder under the splash package decides whether another preload attempt is permitted. It depends only on elapsed time, cache state, and loading state, making the regression independently testable without Android or advertising SDK mocks.

### AdvertisePageMediator

The mediator will expose whether UMP currently permits ad requests. `preloadLaunchAds` will return a result describing whether an App Open request was dispatched, skipped because consent was unavailable, skipped by policy, or failed. This result is used for diagnostic logging; it never controls navigation.

### SplashActivity

The splash wait loop will use the scheduler instead of the one-shot `requestedRetryPreload` flag. Preload jobs remain lifecycle-bound and are cancelled on destruction. Exceptions from initialization or preload are logged and contained.

## Observability

Add one concise splash diagnostic event for preload outcomes, including the reason and attempt count. Avoid logging on every 100-millisecond poll; log only actual attempts and the final timeout state. Existing SDK load, fill, display, and close events remain unchanged.

## Testing

Unit tests will prove the scheduler:

- permits an initial attempt when there is no cache and no request loading;
- permits another attempt after the 500-millisecond interval if the first attempt did not start a load;
- suppresses attempts during the interval;
- suppresses attempts while a request is loading;
- suppresses attempts after a valid cache appears.

Focused unit tests must fail before production implementation is added. Final verification includes all debug unit tests, debug Kotlin compilation, and a debug APK build.

## Non-Goals

- Do not change the bundled or remotely delivered advertising policy.
- Do not extend splash timeout values.
- Do not modify navigation, notification permission, progress animation, or foreground App Open behavior.
- Do not change the advertising SDK artifact version.
