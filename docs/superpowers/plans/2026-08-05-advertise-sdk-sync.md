# Advertise SDK Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Advertise SDK dependency, externalized PhoneCleanUp configuration, and non-blocking application initialization without copying CoolCleaner production credentials or adding ad display flows.

**Architecture:** Gradle resolves app-specific values from project properties, `PHONE_CLEANUP_`-prefixed environment variables, or ignored `local.properties`, then emits consistent `BuildConfig` fields and manifest placeholders. A focused `AdvertiseConfigFactory` disables infrastructure PhoneCleanUp does not own, while `SdkInitUtils` isolates SDK initialization from normal startup.

**Tech Stack:** Android Gradle Plugin 8.12, Kotlin 2.2, JUnit 4, Kotlin coroutines, `com.pdffox:advertise:1.0.10`.

---

The directory is not a Git repository, so the normal per-task commit steps cannot be executed. Do not initialize a repository as part of this task.

### Task 1: Add a failing synchronization contract test

**Files:**
- Create: `app/src/test/java/com/cleanphone/freespace/utility/AdvertiseSdkSyncTest.kt`

- [ ] **Step 1: Write the failing test**

Create a JUnit test that reads the project files from the app module working directory:

```kotlin
package com.cleanphone.freespace.utility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdvertiseSdkSyncTest {
    @Test
    fun advertiseDependencyConfigurationAndInitializationAreWired() {
        val settings = File("../settings.gradle.kts").readText()
        val appGradle = File("build.gradle.kts").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val myApp = File(
            "src/main/java/com/cleanphone/freespace/utility/base/MyApp.kt",
        ).readText()

        assertTrue(settings.contains("http://192.168.10.128:8081/repository/maven-public/"))
        assertTrue(settings.contains("https://maven.singular.net/"))
        assertTrue(settings.contains("https://cboost.jfrog.io/artifactory/chartboost-ads/"))
        assertTrue(settings.contains("https://android-sdk.is.com/"))
        assertTrue(settings.contains("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea"))
        assertTrue(settings.contains("https://artifact.bytedance.com/repository/pangle"))

        assertTrue(appGradle.contains("implementation(\"com.pdffox:advertise:1.0.10\")"))
        assertTrue(appGradle.contains("PHONE_CLEANUP_ADV_ADMOB_APP_ID"))
        assertTrue(appGradle.contains("ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID"))
        assertTrue(appGradle.contains("validateReleaseAdvertiseConfig"))
        assertTrue(appGradle.contains("buildConfig = true"))

        assertTrue(manifest.contains("com.google.android.gms.ads.APPLICATION_ID"))
        assertTrue(manifest.contains("${'$'}{advAdmobAppId}"))
        assertTrue(manifest.contains("com.pdffox.adv.notification.CommonService"))
        assertTrue(manifest.contains("tools:node=\"remove\""))

        assertTrue(
            File(
                "src/main/java/com/cleanphone/freespace/utility/advertise/AdvertiseConfigFactory.kt",
            ).isFile,
        )
        assertTrue(
            File(
                "src/main/java/com/cleanphone/freespace/utility/advertise/AdvertiseRuntimeCapabilities.kt",
            ).isFile,
        )
        assertTrue(
            File(
                "src/main/java/com/cleanphone/freespace/utility/utils/SdkInitUtils.kt",
            ).isFile,
        )
        assertTrue(File("src/main/res/raw/ad_policy.json").isFile)
        assertTrue(File("src/main/res/raw/adload_config.json").isFile)
        assertTrue(myApp.contains("SdkInitUtils.initSDKAsync(this)"))
        assertTrue(myApp.contains("SdkInitUtils.cleanup()"))

        val synchronizedFiles = listOf(
            appGradle,
            File("src/main/java/com/cleanphone/freespace/utility/advertise/AdvertiseConfigFactory.kt")
                .takeIf(File::isFile)?.readText().orEmpty(),
        ).joinToString("\n")
        listOf(
            "ca-app-pub-3615322193850391",
            "1143549684477594",
            "e43af0818296ada1f3fd979b4284b3ff",
            "YwnJx9ucAmqM/yfK6TsknhYSSHvlPVuXymrLCMwcwOM=",
        ).forEach { coolCleanerValue ->
            assertFalse(synchronizedFiles.contains(coolCleanerValue))
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
$env:ANDROID_HOME='D:\sdk'; .\gradlew.bat :app:testDebugUnitTest --tests com.cleanphone.freespace.utility.AdvertiseSdkSyncTest --no-daemon --console=plain
```

Expected: FAIL because the dependency, repositories, configuration files, raw resources, and initialization calls do not exist yet.

### Task 2: Add repositories and externalized Gradle configuration

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add required dependency repositories**

Keep the existing repositories and add these entries once to `dependencyResolutionManagement.repositories`:

```kotlin
maven {
    url = uri("http://192.168.10.128:8081/repository/maven-public/")
    isAllowInsecureProtocol = true
}
maven { url = uri("https://repository.liferay.com/nexus/content/repositories/public/") }
maven { url = uri("https://maven.singular.net/") }
maven { url = uri("https://cboost.jfrog.io/artifactory/chartboost-ads/") }
maven { url = uri("https://artifact.bytedance.com/repository/pangle") }
maven { url = uri("https://android-sdk.is.com/") }
maven { url = uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea") }
```

Add the same public third-party repositories, excluding the internal Advertise artifact repository, to `pluginManagement.repositories` only if Gradle dependency resolution reports a plugin lookup from one of them. The expected implementation does not add Firebase plugins.

- [ ] **Step 2: Add property resolution helpers and constants**

At the top of `app/build.gradle.kts`, add:

```kotlin
import java.util.Properties

val localAdvProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun Project.advStringProperty(
    propertyName: String,
    environmentName: String = "PHONE_CLEANUP_${'$'}propertyName",
    fallback: String = "",
): String {
    return providers.gradleProperty(propertyName).orNull?.takeIf(String::isNotBlank)
        ?: providers.environmentVariable(environmentName).orNull?.takeIf(String::isNotBlank)
        ?: localAdvProperties.getProperty(propertyName)?.takeIf(String::isNotBlank)
        ?: fallback
}

fun String.asBuildConfigString(): String =
    "\"${'$'}{replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n")}\""

val googleTestAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val advAdmobAppId = advStringProperty("ADV_ADMOB_APP_ID")
val advAdmobBannerId = advStringProperty("ADV_ADMOB_BANNER_ID")
val advAdmobInterstitialId = advStringProperty("ADV_ADMOB_INTERSTITIAL_ID")
val advAdmobOpenId = advStringProperty("ADV_ADMOB_OPEN_ID")
val advAdmobNativeId = advStringProperty("ADV_ADMOB_NATIVE_ID")
val advAdmobRewardedId = advStringProperty("ADV_ADMOB_REWARDED_ID")
val advFacebookAppId = advStringProperty("ADV_FACEBOOK_APP_ID")
val advFacebookClientToken = advStringProperty("ADV_FACEBOOK_CLIENT_TOKEN")
val advDefaultTopic = advStringProperty("ADV_DEFAULT_TOPIC", fallback = "phone-cleanup")
val advDebugOverrideMode = advStringProperty("ADV_AD_DEBUG_OVERRIDE_MODE", fallback = "POLICY")
val advRemoteConfigEncryptionKey = advStringProperty("ADV_REMOTE_CONFIG_ENCRYPTION_KEY")
val advRemoteConfigEncryptionKeyId = advStringProperty("ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID")
```

- [ ] **Step 3: Emit Debug and Release configuration**

Enable `buildConfig` next to `viewBinding`:

```kotlin
buildFeatures {
    buildConfig = true
    viewBinding = true
}
```

Add a `debug` build type whose AdMob manifest value uses the explicit property or the Google test App ID. Emit these fields in both build types, using the real values for Release and the test App ID only for Debug metadata/config consistency:

```kotlin
buildConfigField("boolean", "ADV_ADMOB_ENABLED", advAdmobAppId.isNotBlank().toString())
buildConfigField("String", "ADV_ADMOB_APP_ID", resolvedAdmobAppId.asBuildConfigString())
buildConfigField("String", "ADV_ADMOB_BANNER_ID", advAdmobBannerId.asBuildConfigString())
buildConfigField("String", "ADV_ADMOB_INTERSTITIAL_ID", advAdmobInterstitialId.asBuildConfigString())
buildConfigField("String", "ADV_ADMOB_OPEN_ID", advAdmobOpenId.asBuildConfigString())
buildConfigField("String", "ADV_ADMOB_NATIVE_ID", advAdmobNativeId.asBuildConfigString())
buildConfigField("String", "ADV_ADMOB_REWARDED_ID", advAdmobRewardedId.asBuildConfigString())
buildConfigField("boolean", "ADV_FACEBOOK_ENABLED", (advFacebookAppId.isNotBlank() && advFacebookClientToken.isNotBlank()).toString())
buildConfigField("String", "ADV_FACEBOOK_APP_ID", advFacebookAppId.asBuildConfigString())
buildConfigField("String", "ADV_FACEBOOK_CLIENT_TOKEN", advFacebookClientToken.asBuildConfigString())
buildConfigField("String", "ADV_DEFAULT_TOPIC", advDefaultTopic.asBuildConfigString())
buildConfigField("String", "ADV_AD_DEBUG_OVERRIDE_MODE", advDebugOverrideMode.asBuildConfigString())
buildConfigField("boolean", "ADV_REMOTE_CONFIG_ENABLED", (advRemoteConfigEncryptionKey.isNotBlank() && advRemoteConfigEncryptionKeyId.isNotBlank()).toString())
buildConfigField("String", "ADV_REMOTE_CONFIG_ENCRYPTION_KEY", advRemoteConfigEncryptionKey.asBuildConfigString())
buildConfigField("String", "ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID", advRemoteConfigEncryptionKeyId.asBuildConfigString())
manifestPlaceholders["advAdmobAppId"] = resolvedAdmobAppId
```

Use `val resolvedAdmobAppId = advAdmobAppId.ifBlank { googleTestAdmobAppId }` inside `debug`, and `val resolvedAdmobAppId = advAdmobAppId` inside `release`.

- [ ] **Step 4: Validate Release and add the dependency**

Add after the `android` block:

```kotlin
val validateReleaseAdvertiseConfig = tasks.register("validateReleaseAdvertiseConfig") {
    doLast {
        check(advAdmobAppId.isNotBlank()) {
            "Release requires ADV_ADMOB_APP_ID via a Gradle property, " +
                "PHONE_CLEANUP_ADV_ADMOB_APP_ID, or local.properties."
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseAdvertiseConfig)
}
```

Add to `dependencies`:

```kotlin
implementation("com.pdffox:advertise:1.0.10")
```

- [ ] **Step 5: Re-run the contract test**

Run the focused test command from Task 1.

Expected: still FAIL because source configuration, manifest overrides, resources, and initialization wiring are not implemented.

### Task 3: Add the PhoneCleanUp SDK configuration and policy resources

**Files:**
- Create: `app/src/main/java/com/cleanphone/freespace/utility/advertise/AdvertiseRuntimeCapabilities.kt`
- Create: `app/src/main/java/com/cleanphone/freespace/utility/advertise/AdvertiseConfigFactory.kt`
- Create: `app/src/main/res/raw/ad_policy.json`
- Create: `app/src/main/res/raw/adload_config.json`
- Create: `app/src/test/java/com/cleanphone/freespace/utility/advertise/AdvertiseConfigFactoryTest.kt`

- [ ] **Step 1: Write the failing factory behavior test**

```kotlin
package com.cleanphone.freespace.utility.advertise

import com.cleanphone.freespace.utility.base.config.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AdvertiseConfigFactoryTest {
    @Test
    fun createUsesPhoneCleanUpIdentityAndDisablesUnavailableInfrastructure() {
        val config = AdvertiseConfigFactory.create(
            packageName = "com.cleanphone.freespace.utility",
            isDebug = true,
        )

        assertEquals("com.cleanphone.freespace.utility", config.packageName)
        assertEquals(AppConfig.PRIVACY_URL, config.privacyUrl)
        assertEquals(AppConfig.TERMS_URL, config.termsUrl)
        assertFalse(config.server.enabled)
        assertFalse(config.firebase.analyticsEnabled)
        assertFalse(config.firebase.messagingEnabled)
        assertFalse(config.thinking.enabled)
        assertFalse(config.singular.enabled)
        assertFalse(config.tiktok.enabled)
        assertFalse(config.safe.enabled)
        assertFalse(config.push.enabled)
        assertFalse(config.notifications.enabled)
        assertFalse(config.playIntegrity.enabled)
    }
}
```

- [ ] **Step 2: Run the factory test and verify RED**

Run:

```powershell
$env:ANDROID_HOME='D:\sdk'; .\gradlew.bat :app:testDebugUnitTest --tests com.cleanphone.freespace.utility.advertise.AdvertiseConfigFactoryTest --no-daemon --console=plain
```

Expected: FAIL during Kotlin test compilation because `AdvertiseConfigFactory` does not exist.

- [ ] **Step 3: Add runtime capability declarations**

```kotlin
package com.cleanphone.freespace.utility.advertise

import com.cleanphone.freespace.utility.BuildConfig

object AdvertiseRuntimeCapabilities {
    val ADMOB_ENABLED: Boolean
        get() = BuildConfig.ADV_ADMOB_ENABLED
    val FACEBOOK_ENABLED: Boolean
        get() = BuildConfig.ADV_FACEBOOK_ENABLED
    val REMOTE_CONFIG_ENABLED: Boolean
        get() = BuildConfig.ADV_REMOTE_CONFIG_ENABLED

    const val SERVER_ENABLED = false
    const val FIREBASE_ANALYTICS_ENABLED = false
    const val FIREBASE_MESSAGING_ENABLED = false
    const val FIREBASE_SUBSCRIBE_DEFAULT_TOPIC = false
    const val THINKING_ENABLED = false
    const val SINGULAR_ENABLED = false
    const val TIKTOK_ENABLED = false
    const val SAFE_ENABLED = false
    const val PUSH_ENABLED = false
    const val NOTIFICATIONS_ENABLED = false
    const val PLAY_INTEGRITY_ENABLED = false
}
```

- [ ] **Step 4: Add the configuration factory**

Implement both `create(Context)` and JVM-testable `create(packageName, isDebug)` overloads. The shared builder configuration must call every capability group explicitly so the library's production defaults cannot activate accidentally:

```kotlin
package com.cleanphone.freespace.utility.advertise

import android.content.Context
import com.cleanphone.freespace.utility.BuildConfig
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.base.config.AppConfig
import com.pdffox.adv.AdvertiseSdkConfig
import com.pdffox.adv.AdvertiseSdkConfigBuilder
import com.pdffox.adv.AdvertiseSdkConfigs
import com.pdffox.adv.NotificationFeatureConfig

object AdvertiseConfigFactory {
    fun create(context: Context): AdvertiseSdkConfig =
        AdvertiseSdkConfigs.create(context, BuildConfig.DEBUG) {
            configurePhoneCleanUp(context.packageName)
        }

    fun create(packageName: String, isDebug: Boolean): AdvertiseSdkConfig =
        AdvertiseSdkConfigs.create(packageName, isDebug) {
            configurePhoneCleanUp(packageName)
        }

    private fun AdvertiseSdkConfigBuilder.configurePhoneCleanUp(packageName: String) {
        legal(AppConfig.PRIVACY_URL, AppConfig.TERMS_URL)
        defaultTopic(BuildConfig.ADV_DEFAULT_TOPIC)
        adDebugOverride(BuildConfig.ADV_AD_DEBUG_OVERRIDE_MODE)
        resources(
            adPolicyRawResId = R.raw.ad_policy,
            adLoadConfigRawResId = R.raw.adload_config,
            cloudCidrsRawResId = com.pdffox.adv.R.raw.cloud,
            googleCidrsRawResId = com.pdffox.adv.R.raw.google,
        )
        server(false, "", "", "")
        firebase(false, false, false)
        remoteConfig(
            enabled = AdvertiseRuntimeCapabilities.REMOTE_CONFIG_ENABLED,
            encryptionKeyBase64 = BuildConfig.ADV_REMOTE_CONFIG_ENCRYPTION_KEY,
            encryptionKeyId = BuildConfig.ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID,
        )
        thinking(false, "", "")
        singular(false, "", "")
        adMob(
            enabled = AdvertiseRuntimeCapabilities.ADMOB_ENABLED,
            appId = BuildConfig.ADV_ADMOB_APP_ID,
            bannerId = BuildConfig.ADV_ADMOB_BANNER_ID,
            interstitialId = BuildConfig.ADV_ADMOB_INTERSTITIAL_ID,
            nativeId = BuildConfig.ADV_ADMOB_NATIVE_ID,
            openId = BuildConfig.ADV_ADMOB_OPEN_ID,
            rewardedId = BuildConfig.ADV_ADMOB_REWARDED_ID,
        )
        facebook(
            enabled = AdvertiseRuntimeCapabilities.FACEBOOK_ENABLED,
            appId = BuildConfig.ADV_FACEBOOK_APP_ID,
            clientToken = BuildConfig.ADV_FACEBOOK_CLIENT_TOKEN,
            advertiserIdCollectionEnabled = AdvertiseRuntimeCapabilities.FACEBOOK_ENABLED,
        )
        tiktok(false, "", "", packageName, false)
        safe(
            enabled = false,
            expectedSignatures = "",
            rejectDebuggableBuilds = false,
            rejectDebuggerAttached = false,
            killProcessOnFailure = false,
            expectedPackageName = packageName,
        )
        push(false, false, false, false, false, false, false, false)
        notifications(NotificationFeatureConfig(enabled = false))
        playIntegrity(false, 0L, false)
    }
}
```

- [ ] **Step 5: Add local policy resources**

Copy CoolCleaner's `ad_policy.json`, change `package_name` to `com.cleanphone.freespace.utility`, and set both `global_ad_switch` and `first_open_enabled` to `false`. Keep its area definitions unchanged for later screen integration.

Copy CoolCleaner's `adload_config.json`, then set every value under `adload_trigger_timing_open`, `adload_trigger_timing_inter`, and `adload_trigger_timing_native` to `false`. This prevents initialization-only synchronization from preloading advertisements.

- [ ] **Step 6: Run the factory test and verify GREEN**

Run the focused factory test command.

Expected: PASS.

### Task 4: Wire non-blocking initialization and disable unused manifest components

**Files:**
- Create: `app/src/main/java/com/cleanphone/freespace/utility/utils/SdkInitUtils.kt`
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/base/MyApp.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add the asynchronous initializer**

```kotlin
package com.cleanphone.freespace.utility.utils

import android.app.Application
import android.util.Log
import com.cleanphone.freespace.utility.BuildConfig
import com.cleanphone.freespace.utility.advertise.AdvertiseConfigFactory
import com.pdffox.adv.AdvertiseSdk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

object SdkInitUtils {
    private const val TAG = "SdkInit"
    private val isInitializing = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    private var initializationJob: Job? = null
    private val initializationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initSDKAsync(context: Application): Job {
        if (isInitializing.getAndSet(true)) {
            return initializationJob ?: Job().apply { complete() }
        }
        initializationJob = initializationScope.launch {
            try {
                AdvertiseSdk.init(
                    context = context,
                    isTest = BuildConfig.DEBUG,
                    sdkConfig = AdvertiseConfigFactory.create(context),
                )
                isInitialized.set(true)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Advertise SDK initialization failed; continue app startup", exception)
                isInitialized.set(false)
            } finally {
                isInitializing.set(false)
            }
        }
        return initializationJob!!
    }

    suspend fun awaitInitialization(timeoutMs: Long? = null) {
        val job = initializationJob ?: return
        if (timeoutMs == null) {
            job.join()
        } else {
            withTimeoutOrNull(timeoutMs) { job.join() }
        }
    }

    fun isInitialized(): Boolean = isInitialized.get()

    fun cleanup() {
        initializationJob?.cancel()
        initializationScope.cancel()
        initializationJob = null
        isInitialized.set(false)
        isInitializing.set(false)
    }
}
```

- [ ] **Step 2: Call initialization from `MyApp`**

Import `SdkInitUtils`, call `SdkInitUtils.initSDKAsync(this)` after `instance = this` in `onCreate()`, and call `SdkInitUtils.cleanup()` before clearing `instance` in `cleanup()`.

- [ ] **Step 3: Add manifest metadata and remove disabled library components**

Inside `<application>`, add:

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="${advAdmobAppId}" />

<service
    android:name="com.pdffox.adv.notification.CommonService"
    tools:node="remove" />
<service
    android:name="com.pdffox.adv.push.MyFirebaseMessagingService"
    tools:node="remove" />
<service
    android:name="com.pdffox.adv.push.ServiceStarterJobService"
    tools:node="remove" />
<receiver
    android:name="com.pdffox.adv.notification.NotificationDeletedReceiver"
    tools:node="remove" />
<receiver
    android:name="com.pdffox.adv.notification.BootReceiver"
    tools:node="remove" />
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.pdffox.adv.fileprovider"
    tools:node="remove" />
```

Do not remove permissions that PhoneCleanUp already declares for its own features. Use manifest merger output during verification to confirm the removed SDK components are absent.

- [ ] **Step 4: Run the contract test and the full Debug unit suite**

Run:

```powershell
$env:ANDROID_HOME='D:\sdk'; .\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```

Expected: all tests PASS, including `AdvertiseSdkSyncTest` and `AdvertiseConfigFactoryTest`.

### Task 5: Verify dependency resolution, manifests, Debug, and Release

**Files:**
- Modify only if verification proves necessary: `app/proguard-rules.pro`

- [ ] **Step 1: Compile and assemble Debug**

Run:

```powershell
$env:ANDROID_HOME='D:\sdk'; .\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Expected: BUILD SUCCESSFUL with `com.pdffox:advertise:1.0.10` resolved.

- [ ] **Step 2: Inspect the merged Debug manifest**

Check `app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml` and verify:

- `com.google.android.gms.ads.APPLICATION_ID` equals the configured value or Google's test App ID.
- `com.pdffox.adv.notification.CommonService` is absent.
- `com.pdffox.adv.push.MyFirebaseMessagingService` is absent.
- `com.pdffox.adv.push.ServiceStarterJobService` is absent.
- `com.pdffox.adv.notification.NotificationDeletedReceiver` is absent.
- `com.pdffox.adv.notification.BootReceiver` is absent.
- `${applicationId}.pdffox.adv.fileprovider` is absent.

- [ ] **Step 3: Verify Release validation fails without configuration**

Run:

```powershell
$env:ANDROID_HOME='D:\sdk'; .\gradlew.bat :app:assembleRelease --no-daemon --console=plain
```

Expected: FAIL at `validateReleaseAdvertiseConfig` with the explicit `ADV_ADMOB_APP_ID` message.

- [ ] **Step 4: Assemble Release with a temporary verification value**

Run:

```powershell
$env:ANDROID_HOME='D:\sdk'; .\gradlew.bat :app:assembleRelease -PADV_ADMOB_APP_ID=ca-app-pub-3940256099942544~3347511713 --no-daemon --console=plain
```

Expected: BUILD SUCCESSFUL. The test identifier is supplied only on the command line and is not persisted in the project.

- [ ] **Step 5: Handle shrinker evidence only if needed**

If Release fails in R8 due to missing optional classes, first confirm the classes belong to an intentionally disabled Advertise SDK integration. Add the narrow `-dontwarn` rule named in the R8 diagnostic; do not copy CoolCleaner's broad ProGuard file. Re-run the complete Release command until it exits successfully.

- [ ] **Step 6: Final requirement audit**

Re-read `docs/superpowers/specs/2026-08-05-advertise-sdk-sync-design.md`, inspect the final file diff using file timestamps/content because Git is unavailable, and confirm each success criterion against fresh test/build output.
