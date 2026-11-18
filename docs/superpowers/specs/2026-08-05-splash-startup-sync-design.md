# Splash Startup Logic Synchronization Design

## Goal

Synchronize the advertisement, consent, notification permission, startup progress, and post-splash routing behavior from `D:\work\qingli\CoolCleaner` into this project's `SplashActivity` while keeping this project's screens, route keys, layout, and existing SDK configuration.

## Scope

- Replace the current fixed progress-only splash sequence with the source project's coordinated startup flow.
- Initialize UMP consent and preload launch ads as early as possible.
- Request Android 13+ notification permission and wait for the permission result before advancing the visible startup sequence.
- Show a cached launch App Open ad when available, with bounded loading and foreground waits.
- Prevent the global foreground App Open handler from displaying a second ad during splash.
- Preserve this project's splash layout and clickable terms/privacy text.
- Route normal launches, persistent shortcuts, and notification/deep-link launches to this project's existing activities.
- Preserve relevant `LogUtil` events and first-launch advertising user attributes.

## Non-Goals

- Do not copy `GuideActivity` or `ScanJunkActivity` from CoolCleaner.
- Do not replace the splash layout with CoolCleaner's layout.
- Do not add the ThinkingData SDK solely for splash traffic-source super properties.
- Do not refactor unrelated advertising, notification, or navigation code.

## Startup Flow

`SplashActivity` will use `BaseViewActivity<ActivitySplashBinding>` because the startup state and timing are owned by the activity and no longer use `SplashViewModel`.

On initialization, the activity will:

1. Record the splash start time and first-launch advertising attribute.
2. Disable back navigation.
3. Disable the global foreground App Open ad handler for the splash lifetime.
4. Configure the existing clickable terms and privacy text.
5. Start UMP consent initialization.
6. Start launch-ad preloading after `SdkInitUtils` initialization completes.
7. Request notification permission on Android 13+ when needed.
8. Wait for notification permission completion, then start visible progress and the consent/ad stage.

Consent preparation has a 30-second total deadline. Launch-ad loading waits up to 12 seconds online and 2.5 seconds offline. A cached ad is only shown while both the activity and application are foregrounded. Foreground waiting is bounded to 6 seconds.

Progress advances toward 99 percent while startup preparation runs. When preparation finishes or fails, it animates to 100 percent over 500 milliseconds before the ad is shown or navigation continues.

## Lifecycle Behavior

- Permission dialogs, UMP forms, and the launch ad do not count as the user backgrounding the splash.
- If the user backgrounds the app during other splash work, the launch ad is skipped and the next foreground App Open is suppressed once.
- Startup, progress, and preload jobs are cancelled when the activity is destroyed.
- The global foreground App Open handler is restored before leaving splash and during destruction.
- Navigation and callback continuations are guarded so they run at most once.

## Permission Behavior

For Android versions below 13, or when notification permission is already granted, the permission gate completes immediately.

For Android 13 and later, `POST_NOTIFICATIONS` is requested when missing. Grant and denial both continue startup. The result is stored under `has_notification_permission` and logs either `agree_push_permission` or `disagree_push_permission`.

## Navigation Design

Routing uses this project's `AppConfig.NEXT_TAG` value and existing activity set.

| Launch type | Route | Destination |
| --- | --- | --- |
| Normal launch, scan not completed | none | `ScanPhoneActivity` |
| Normal launch, scan completed | none | `MainActivity` |
| Shortcut/notification home route | other/home | `MainActivity` |
| Junk or remove-junk route with storage permission | junk/remove junk | `ScanFilesActivity` |
| Junk or remove-junk route without storage permission | junk/remove junk | `MainActivity` |
| Large-file or screenshot route with storage permission | matching `NEXT_TAG` | `LoadingActivity` |
| Large-file or screenshot route without storage permission | matching `NEXT_TAG` | `MainActivity` |
| App, process, battery, or speaker route | matching `NEXT_TAG` | `LoadingActivity` |
| Unknown route | unknown | `MainActivity` |

`AppOpenFrom == "persistent"` is treated as a persistent shortcut. An empty `AppOpenFrom` is a normal launch. Other non-empty values are treated as notification/deep-link launches. Existing extras are copied to the destination, including `NEXT_TAG`, `id`, `skip`, and `referrer_name` where applicable. Storage permission is only required for routes that read shared-storage content: junk, large-file, and screenshot flows.

## Terms And Privacy

The current combined `tvHint` clickable-span implementation remains. Opening a terms or privacy URL cancels splash work and navigates to `WebViewActivity` with `tag = "splash"`. Returning from the web page starts a new splash instance through the existing `WebViewActivity` behavior.

## Error Handling

- Consent initialization failure continues to ad loading/navigation.
- Consent form errors are logged and do not block startup.
- SDK preload errors are contained by `runCatching`.
- Ad load timeout or cache miss skips the launch ad.
- An exception in the coordinated startup sequence logs `splash_sequence_error`, completes progress, and continues navigation.
- A failed cached-ad display immediately continues navigation.

## Testing And Verification

- Add unit tests for a pure splash route resolver before implementing its production code.
- Cover normal launch, shortcut/deep-link home, junk, supported feature, permission-denied, and unknown-route decisions.
- Compile the debug Kotlin sources after wiring the activity.
- Run all existing and new unit tests.
- Build the debug APK to verify resources, manifest, ViewBinding, and SDK calls integrate correctly.
