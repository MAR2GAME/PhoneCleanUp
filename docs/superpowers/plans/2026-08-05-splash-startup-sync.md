# Splash Startup Logic Synchronization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Synchronize CoolCleaner's splash advertisement, consent, notification permission, progress, and launch-routing behavior into PhoneCleanUp while routing to PhoneCleanUp's existing screens.

**Architecture:** Move startup orchestration into `SplashActivity`, matching the source activity's bounded consent/ad workflow and lifecycle guards. Keep navigation decisions in a small pure resolver so normal, shortcut, notification, permission, and unknown-route behavior can be tested without Android runtime dependencies.

**Tech Stack:** Kotlin 2.2, AndroidX Activity/Lifecycle, Kotlin coroutines, ViewBinding, JUnit 4, UMP, `com.pdffox:advertise:1.0.10`.

---

## File Structure

- Create `app/src/main/java/com/cleanphone/freespace/utility/ui/splash/SplashNavigationResolver.kt`: pure startup destination selection.
- Create `app/src/test/java/com/cleanphone/freespace/utility/ui/splash/SplashNavigationResolverTest.kt`: route behavior coverage.
- Modify `app/src/main/java/com/cleanphone/freespace/utility/ui/SplashActivity.kt`: consent, permission, progress, launch-ad, lifecycle, and navigation orchestration.
- Modify `app/src/main/java/com/cleanphone/freespace/utility/base/config/AppConfig.kt`: splash timing and first-open preference keys.
- Modify `gradle/libs.versions.toml`: expose AndroidX lifecycle-process using the existing lifecycle version.
- Modify `app/build.gradle.kts`: add lifecycle-process required by `ProcessLifecycleOwner`.

The workspace is not recognized as a Git repository, so the normal per-task commit steps are intentionally omitted. No source files under `D:\work\qingli\CoolCleaner` will be modified.

### Task 1: Add Tested Splash Route Resolution

**Files:**
- Create: `app/src/test/java/com/cleanphone/freespace/utility/ui/splash/SplashNavigationResolverTest.kt`
- Create: `app/src/main/java/com/cleanphone/freespace/utility/ui/splash/SplashNavigationResolver.kt`

- [ ] **Step 1: Write the failing route resolver tests**

Create the test file with fixed labels so tests are independent from Android resources:

```kotlin
package com.cleanphone.freespace.utility.ui.splash

import org.junit.Assert.assertEquals
import org.junit.Test

class SplashNavigationResolverTest {
    private val labels = SplashRouteLabels(
        home = setOf("Other"),
        junk = setOf("Junk files", "Remove Junk"),
        storageLoading = setOf("Large File Cleaner", "Screenshot Manager"),
        loading = setOf("App Manager", "Process Manager", "Battery Info", "Speaker Cleaner"),
    )

    @Test
    fun `normal first launch opens phone scan`() {
        assertEquals(
            SplashDestination.SCAN_PHONE,
            resolve(appOpenFrom = null, nextAction = null, scanCompleted = false, storageGranted = false),
        )
    }

    @Test
    fun `normal returning launch opens main`() {
        assertEquals(
            SplashDestination.MAIN,
            resolve(appOpenFrom = null, nextAction = null, scanCompleted = true, storageGranted = false),
        )
    }

    @Test
    fun `home notification opens main`() {
        assertEquals(
            SplashDestination.MAIN,
            resolve(appOpenFrom = "notification", nextAction = "Other", scanCompleted = true, storageGranted = true),
        )
    }

    @Test
    fun `junk route opens scan files when storage is granted`() {
        assertEquals(
            SplashDestination.SCAN_FILES,
            resolve(appOpenFrom = "persistent", nextAction = "Remove Junk", scanCompleted = true, storageGranted = true),
        )
    }

    @Test
    fun `junk route falls back to main without storage permission`() {
        assertEquals(
            SplashDestination.MAIN,
            resolve(appOpenFrom = "notification", nextAction = "Junk files", scanCompleted = true, storageGranted = false),
        )
    }

    @Test
    fun `storage feature route requires storage permission`() {
        assertEquals(
            SplashDestination.MAIN,
            resolve(appOpenFrom = "notification", nextAction = "Large File Cleaner", scanCompleted = true, storageGranted = false),
        )
        assertEquals(
            SplashDestination.LOADING,
            resolve(appOpenFrom = "notification", nextAction = "Large File Cleaner", scanCompleted = true, storageGranted = true),
        )
    }

    @Test
    fun `non-storage feature route opens loading without storage permission`() {
        assertEquals(
            SplashDestination.LOADING,
            resolve(appOpenFrom = "notification", nextAction = "Battery Info", scanCompleted = true, storageGranted = false),
        )
    }

    @Test
    fun `unknown external route falls back to main`() {
        assertEquals(
            SplashDestination.MAIN,
            resolve(appOpenFrom = "notification", nextAction = "unknown", scanCompleted = true, storageGranted = true),
        )
    }

    private fun resolve(
        appOpenFrom: String?,
        nextAction: String?,
        scanCompleted: Boolean,
        storageGranted: Boolean,
    ): SplashDestination {
        return SplashNavigationResolver.resolve(
            appOpenFrom = appOpenFrom,
            nextAction = nextAction,
            hasCompletedInitialScan = scanCompleted,
            hasStoragePermission = storageGranted,
            labels = labels,
        )
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
$env:ANDROID_HOME='D:\sdk'
.\gradlew.bat :app:testDebugUnitTest --tests "com.cleanphone.freespace.utility.ui.splash.SplashNavigationResolverTest" --no-daemon --console=plain
```

Expected: compilation fails because `SplashRouteLabels`, `SplashDestination`, and `SplashNavigationResolver` do not exist.

- [ ] **Step 3: Implement the minimal pure resolver**

Create:

```kotlin
package com.cleanphone.freespace.utility.ui.splash

internal enum class SplashDestination {
    SCAN_PHONE,
    MAIN,
    SCAN_FILES,
    LOADING,
}

internal data class SplashRouteLabels(
    val home: Set<String>,
    val junk: Set<String>,
    val storageLoading: Set<String>,
    val loading: Set<String>,
)

internal object SplashNavigationResolver {
    fun resolve(
        appOpenFrom: String?,
        nextAction: String?,
        hasCompletedInitialScan: Boolean,
        hasStoragePermission: Boolean,
        labels: SplashRouteLabels,
    ): SplashDestination {
        if (appOpenFrom.isNullOrBlank()) {
            return if (hasCompletedInitialScan) {
                SplashDestination.MAIN
            } else {
                SplashDestination.SCAN_PHONE
            }
        }

        val route = nextAction.orEmpty()
        return when {
            route in labels.home -> SplashDestination.MAIN
            route in labels.junk -> if (hasStoragePermission) {
                SplashDestination.SCAN_FILES
            } else {
                SplashDestination.MAIN
            }
            route in labels.storageLoading -> if (hasStoragePermission) {
                SplashDestination.LOADING
            } else {
                SplashDestination.MAIN
            }
            route in labels.loading -> SplashDestination.LOADING
            else -> SplashDestination.MAIN
        }
    }
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the command from Step 2 again.

Expected: all eight resolver tests pass.

### Task 2: Add Required Startup Configuration

**Files:**
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/base/config/AppConfig.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

These are build/configuration declarations approved for compile verification rather than standalone unit tests.

- [ ] **Step 1: Add splash preference keys**

Add next to `IS_SCAN_PHONE` in `AppConfig`:

```kotlin
const val START_TIME = "start_time"
const val IS_SP_FIRST_SET = "sp_first_set"
```

- [ ] **Step 2: Add lifecycle-process to the version catalog**

Add under `[libraries]` in `gradle/libs.versions.toml`:

```toml
androidx-lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "lifecycleRuntimeKtx" }
```

- [ ] **Step 3: Add the application dependency**

Add beside the existing lifecycle dependencies in `app/build.gradle.kts`:

```kotlin
implementation(libs.androidx.lifecycle.process)
```

- [ ] **Step 4: Verify configuration compiles**

Run:

```powershell
$env:ANDROID_HOME='D:\sdk'
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
```

Expected: Gradle resolves `libs.androidx.lifecycle.process`; any remaining failure must be limited to the not-yet-migrated `SplashActivity` only if Task 3 edits have started.

### Task 3: Synchronize Splash Startup Orchestration

**Files:**
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/ui/SplashActivity.kt`
- Reference only: `D:\work\qingli\CoolCleaner\app\src\main\java\com\cleantool\coolcleaner\ui\SplashActivity.kt`

- [ ] **Step 1: Replace the MVVM splash shell**

Change the class to:

```kotlin
class SplashActivity : BaseViewActivity<ActivitySplashBinding>()
```

Remove `SplashViewModel`, StateFlow collection, `onStart()` progress resume, and the old `onStop()` progress pause. Keep the existing combined terms/privacy `SpannableString` implementation.

- [ ] **Step 2: Add source-equivalent constants and startup state**

Use the source timing values exactly:

```kotlin
private const val PROGRESS_UPDATE_INTERVAL = 80L
private const val TOTAL_PROGRESS = 100
private const val PREPARATION_PROGRESS_MAX = 99
private const val FINAL_PROGRESS_DURATION_MS = 500L
private const val PROGRESS_ANIMATION_FRAME_MS = 16L
private const val CONSENT_PREPARATION_TIMEOUT_MS = 30_000L
private const val STARTUP_PROGRESS_DURATION_MS = CONSENT_PREPARATION_TIMEOUT_MS
private const val OPEN_AD_LOAD_WAIT_TIMEOUT_MS = 12_000L
private const val OPEN_AD_OFFLINE_LOAD_WAIT_TIMEOUT_MS = 2_500L
private const val OPEN_AD_FOREGROUND_WAIT_TIMEOUT_MS = 6_000L
private const val OPEN_AD_POLL_INTERVAL_MS = 100L
private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
private const val OPEN_REFERRER = "open"
```

Add the three jobs, progress counters, lifecycle/ad/permission booleans, start time, and these completion gates:

```kotlin
private val notificationPermissionFinished = CompletableDeferred<Unit>()
private val consentInitFinished = CompletableDeferred<Boolean>()
```

- [ ] **Step 3: Wire activity initialization and cleanup**

Implement initialization in this order:

```kotlin
override fun initializeActivity() {
    super.initializeActivity()
    startTime = System.currentTimeMillis()
    AppPrefsUtils.commitLong(AppConfig.START_TIME, startTime)
    initFirstLaunchTracking()
    setupBackPressHandler()
    suppressForegroundOpenAdDuringSplash()
    setupClickableText()
    startStartupFlow()
    requestPostNotificationsPermissionIfNeeded()
}
```

Port the source `onStop()`, `onDestroy()`, `setupBackPressHandler()`, `initFirstLaunchTracking()`, `logSplashDuration()`, `completeNotificationPermission()`, and `completeConsentInitialization()` behavior with target package imports.

- [ ] **Step 4: Port permission, consent, and preload gates**

Port these source methods without timing changes:

```text
startStartupFlow
beginEarlyConsentInitialization
beginEarlyLaunchOpenAdPreload
requestPostNotificationsPermissionIfNeeded
waitForConsentInitialization
showSplashConsentForStartup
awaitConsentCallbackBeforeFormIsShown
awaitConsentCompletionAfterFormIsShown
remainingConsentPreparationTime
```

Use target imports:

```kotlin
import com.cleanphone.freespace.utility.advertise.AdvertisePageMediator
import com.cleanphone.freespace.utility.advertise.AdvertiseUserAttributes
import com.cleanphone.freespace.utility.utils.SdkInitUtils
```

The permission callback must always complete the gate and persist the result:

```kotlin
if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS) {
    isWaitingForNotificationPermission = false
    val isGranted = grantResults.isNotEmpty() &&
        grantResults[0] == PackageManager.PERMISSION_GRANTED
    LogUtil.log(
        if (isGranted) "agree_push_permission" else "disagree_push_permission",
        emptyMap(),
    )
    AppPrefsUtils.commitBoolean("has_notification_permission", isGranted)
    completeNotificationPermission()
}
```

- [ ] **Step 5: Port launch-ad and progress behavior**

Port these source methods without changing timeout or one-shot behavior:

```text
handleLaunchOpenAdStage
prepareLaunchOpenAd
waitForLaunchOpenAd
finishLaunchOpenAdStage
waitUntilForegroundForOpenAd
isForegroundForOpenAd
suppressForegroundOpenAdDuringSplash
suppressForegroundOpenAdAfterSplashBackground
restoreForegroundOpenAdIfNeeded
startProgressLoop
completeProgressQuickly
```

Adapt `updateProgress` to the target layout, which has no percentage TextView:

```kotlin
private fun updateProgress(progress: Int) {
    currentProgress = progress.coerceIn(0, TOTAL_PROGRESS)
    binding.progressBar.progress = currentProgress
}
```

- [ ] **Step 6: Make terms/privacy navigation cancel startup**

Before opening `WebViewActivity`, guard navigation and cancel work:

```kotlin
private fun navigateToWebView(url: String) {
    if (hasContinuedToNextStep) return
    hasContinuedToNextStep = true
    startupJob?.cancel()
    progressJob?.cancel()
    val bundle = Bundle().apply {
        putString(WebViewActivity.EXTRA_URL, url)
        putString("tag", "splash")
    }
    readyGoThenKill(WebViewActivity::class.java, bundle)
}
```

Have each existing clickable span call `navigateToWebView(url)` instead of constructing its own bundle.

- [ ] **Step 7: Connect the tested route resolver**

Build localized route groups once per navigation:

```kotlin
private fun routeLabels() = SplashRouteLabels(
    home = setOf(getString(R.string.other)),
    junk = setOf(getString(R.string.junk_files), getString(R.string.remove_junk)),
    storageLoading = setOf(
        getString(R.string.large_file_cleaner),
        getString(R.string.screenshot_manager),
    ),
    loading = setOf(
        getString(R.string.app_manager),
        getString(R.string.process_manager),
        getString(R.string.battery_info),
        getString(R.string.speaker_cleaner),
    ),
)
```

Resolve and navigate once:

```kotlin
private fun determineNavigationPath() {
    val appOpenFrom = intent.getStringExtra("AppOpenFrom")
    val nextAction = intent.extras?.getString(AppConfig.NEXT_TAG)
    val destination = SplashNavigationResolver.resolve(
        appOpenFrom = appOpenFrom,
        nextAction = nextAction,
        hasCompletedInitialScan = AppPrefsUtils.getBoolean(AppConfig.IS_SCAN_PHONE),
        hasStoragePermission = hasStoragePermission(),
        labels = routeLabels(),
    )

    if (!appOpenFrom.isNullOrBlank()) {
        val referrer = if (appOpenFrom == "persistent") "app_shortcuts" else "notification"
        intent.putExtra("referrer_name", referrer)
        if (appOpenFrom != "persistent") {
            intent.putExtra("skip", true)
            LogUtil.log(
                "notification_app_clicked",
                mapOf("scene" to intent.extras?.getString("id").orEmpty()),
            )
        }
    }

    when (destination) {
        SplashDestination.SCAN_PHONE -> {
            LogUtil.log("enter_guidepage", mapOf("referrer_name" to OPEN_REFERRER))
            readyGoThenKill(ScanPhoneActivity::class.java)
        }
        SplashDestination.MAIN -> {
            if (appOpenFrom.isNullOrBlank()) {
                LogUtil.log("enter_homepage", mapOf("referrer_name" to OPEN_REFERRER))
            }
            readyGoThenKill(MainActivity::class.java, intent.extras?.let(::Bundle))
        }
        SplashDestination.SCAN_FILES ->
            readyGoThenKill(ScanFilesActivity::class.java, intent.extras?.let(::Bundle))
        SplashDestination.LOADING ->
            readyGoThenKill(LoadingActivity::class.java, intent.extras?.let(::Bundle))
    }
}
```

`continueToNextStep()` must guard once, restore the foreground App Open handler, and call `determineNavigationPath()`.

- [ ] **Step 8: Compile the migrated activity**

Run:

```powershell
$env:ANDROID_HOME='D:\sdk'
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL` with no unresolved splash, lifecycle-process, resource, or advertising symbols.

### Task 4: Full Verification

**Files:**
- Verify all modified and created files from Tasks 1-3.

- [ ] **Step 1: Run all unit tests**

```powershell
$env:ANDROID_HOME='D:\sdk'
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```

Expected: all existing advertising/config tests and the new splash resolver tests pass with zero failures.

- [ ] **Step 2: Build the debug APK**

```powershell
$env:ANDROID_HOME='D:\sdk'
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL` and a debug APK under `app/build/outputs/apk/debug/`.

- [ ] **Step 3: Inspect the final diff without modifying the source project**

Because PhoneCleanUp is not a Git repository, list the expected changed files directly and confirm CoolCleaner's working tree is unchanged:

```powershell
Get-Item `
  'app\src\main\java\com\cleanphone\freespace\utility\ui\SplashActivity.kt', `
  'app\src\main\java\com\cleanphone\freespace\utility\ui\splash\SplashNavigationResolver.kt', `
  'app\src\test\java\com\cleanphone\freespace\utility\ui\splash\SplashNavigationResolverTest.kt', `
  'app\src\main\java\com\cleanphone\freespace\utility\base\config\AppConfig.kt', `
  'gradle\libs.versions.toml', `
  'app\build.gradle.kts'
git -C 'D:\work\qingli\CoolCleaner' status --short
```

Expected: all six target files exist; CoolCleaner's status remains limited to its pre-existing modifications in `app/build.gradle.kts` and `app/src/main/java/com/cleantool/coolcleaner/base/MyApp.kt`.

