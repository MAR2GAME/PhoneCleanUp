# Advertise SDK Dependency, Configuration, and Initialization Sync

## Goal

Synchronize the `com.pdffox:advertise` dependency, its application-owned configuration, and its initialization flow from CoolCleaner into PhoneCleanUp without copying CoolCleaner's production credentials or adding advertisement display behavior.

## Scope

The change will:

- Add `com.pdffox:advertise:1.0.10` and the Maven repositories required to resolve it and its transitive dependencies.
- Add build-time configuration fields and manifest placeholders for the Advertise SDK.
- Read app-specific values from Gradle properties, environment variables, or `local.properties`.
- Add PhoneCleanUp-owned Advertise SDK configuration and runtime capability declarations.
- Add the local raw resources required by the synchronized SDK configuration.
- Initialize the SDK asynchronously from `MyApp.onCreate()`.
- Add focused tests for the dependency/configuration contract and initialization wiring.

The change will not add splash, app-open, interstitial, banner, native, or rewarded advertisement display calls to PhoneCleanUp screens.

## Build Configuration

PhoneCleanUp will use the same configuration shape as CoolCleaner, adapted to this application. Supported properties will include:

- `ADV_ADMOB_APP_ID`
- `ADV_ADMOB_BANNER_ID`
- `ADV_ADMOB_INTERSTITIAL_ID`
- `ADV_ADMOB_OPEN_ID`
- `ADV_ADMOB_NATIVE_ID`
- `ADV_ADMOB_REWARDED_ID`
- `ADV_FACEBOOK_APP_ID`
- `ADV_FACEBOOK_CLIENT_TOKEN`
- `ADV_DEFAULT_TOPIC`
- `ADV_AD_DEBUG_OVERRIDE_MODE`
- `ADV_REMOTE_CONFIG_ENCRYPTION_KEY`
- `ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID`

Resolution order will be Gradle property, a `PHONE_CLEANUP_`-prefixed environment variable, and `local.properties`. For example, `ADV_ADMOB_APP_ID` maps to `PHONE_CLEANUP_ADV_ADMOB_APP_ID` in the environment. CoolCleaner production values will not be copied.

Debug builds may use Google's documented test AdMob identifiers where a valid application identifier is required for startup. Optional integrations remain disabled when their project-specific values are absent. Release builds will require a nonblank `ADV_ADMOB_APP_ID` and fail during configuration with a clear error instead of producing an incorrectly configured APK. Ad-unit identifiers remain optional until PhoneCleanUp adds the corresponding display flow.

`buildConfig` generation will be enabled. Manifest placeholders will be derived from the same resolved values so Kotlin configuration and merged manifest metadata cannot silently diverge.

## Runtime Capabilities

PhoneCleanUp currently has no `google-services.json`, Firebase Gradle plugins, Thinking Analytics identifiers, Singular identifiers, Advertise server endpoint, or project-specific push configuration. The initial integration will therefore disable these optional capabilities:

- Firebase Analytics and Messaging
- Firebase default-topic subscription
- Remote Config until a PhoneCleanUp encryption key is supplied
- Thinking Analytics
- Singular
- TikTok
- Advertise server integration
- SDK-managed push and persistent notification features
- Play Integrity until a PhoneCleanUp cloud project number is supplied

AdMob capability will be enabled only when the resolved configuration is valid for the active build. Facebook capability will be enabled only when both its app ID and client token are present. SDK safety checks will avoid introducing process termination behavior during this initialization-only synchronization.

This capability boundary keeps the application buildable and preserves current PhoneCleanUp behavior while leaving explicit configuration points for later enablement.

## Application Configuration

An `AdvertiseConfigFactory` in the PhoneCleanUp package will create `AdvertiseSdkConfig` using:

- PhoneCleanUp privacy and terms URLs from `AppConfig`.
- The resolved build configuration fields.
- PhoneCleanUp package name and debug status.
- Local `ad_policy` and `adload_config` raw resources.
- SDK-provided cloud and Google CIDR resources where required by the API.
- Capability flags that match the available PhoneCleanUp infrastructure.

The copied policy resources will be adapted to `com.cleanphone.freespace.utility`. They may retain the source area's configuration data for future use, but no ad is requested or displayed until a PhoneCleanUp screen explicitly integrates a display flow.

## Initialization Flow

`SdkInitUtils` will own initialization state and a supervised IO coroutine scope. `MyApp.onCreate()` will call its asynchronous initialization entry point after setting the application singleton.

The initializer will:

1. Prevent duplicate concurrent initialization.
2. Call `AdvertiseSdk.init()` with the PhoneCleanUp configuration.
3. Record whether initialization succeeded.
4. Preserve coroutine cancellation.
5. Catch other SDK failures, log them, and allow normal application startup to continue.
6. Cancel its job and reset state from application cleanup.

No Activity will wait indefinitely for initialization, and this task will not add consent or ad-display sequencing to `SplashActivity`.

## Manifest and Shrinker Integration

The application manifest will provide Advertise SDK metadata from the same resolved values used by `BuildConfig`. Debug metadata may use documented test values; Release metadata is protected by the required App ID validation. Existing PhoneCleanUp components and permissions will be preserved. New permissions will be limited to those required by the initialized SDK configuration rather than copying every permission from CoolCleaner.

Release verification will include an R8-enabled build. Additional keep or warning rules will be added only if the dependency's consumer rules do not cover an observed build failure.

## Testing and Verification

Implementation will follow a focused red-green cycle:

- Add a source-contract test that initially fails because the Advertise dependency, externalized properties, configuration factory, raw resources, and `MyApp` initialization call are absent.
- Add the minimum build and source changes needed to pass that test.
- Add unit tests for configuration values that can be created without Android runtime resources, using the factory's package-name/debug overload where supported.
- Run `:app:testDebugUnitTest`.
- Run `:app:assembleDebug` to verify dependency resolution, manifest merging, and Kotlin compilation.
- Run `:app:assembleRelease` to verify release parameter validation and R8 behavior. When project-specific production values are intentionally absent, supply temporary non-production verification properties on the command line rather than persisting secrets.

## Success Criteria

- The Advertise SDK dependency resolves at version `1.0.10`.
- No CoolCleaner production identifier, token, or encryption key is added to PhoneCleanUp.
- PhoneCleanUp starts even when SDK initialization throws.
- Initialization is requested once from `MyApp` and does not block the main thread.
- Optional capabilities without PhoneCleanUp infrastructure remain disabled.
- No advertisement display behavior is introduced.
- Focused tests, Debug assembly, and Release assembly complete successfully with explicit verification parameters.
