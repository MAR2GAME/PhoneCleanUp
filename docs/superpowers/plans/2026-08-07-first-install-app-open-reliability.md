# First-Install App Open Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retry first-install App Open preloading when asynchronous policy and consent state become ready, without extending the existing 12-second online wait or blocking navigation.

**Architecture:** Add a pure monotonic-time retry scheduler and use it from `SplashActivity` to replace the one-shot preload retry. Keep Google UMP and remote preload policy authoritative in `AdvertisePageMediator`; all attempts are lifecycle-bound, throttled, and fail open to the existing navigation path.

**Tech Stack:** Kotlin 2.2, AndroidX Lifecycle coroutines, Google UMP 4.0, `com.pdffox:advertise:1.0.12`, JUnit 4, Gradle 8.

---

## File Structure

- Create `app/src/main/java/com/cleanphone/freespace/utility/ui/splash/LaunchOpenAdPreloadScheduler.kt`: pure retry timing and cache/loading gates.
- Create `app/src/test/java/com/cleanphone/freespace/utility/ui/splash/LaunchOpenAdPreloadSchedulerTest.kt`: regression tests for the first-install retry race.
- Modify `app/src/main/java/com/cleanphone/freespace/utility/advertise/AdvertisePageMediator.kt`: UMP request gate, explicit startup trigger, and preload outcome.
- Modify `app/src/main/java/com/cleanphone/freespace/utility/ui/SplashActivity.kt`: bounded throttled retries, outcome logging, and removal of the one-shot flag.

The workspace is not a Git repository, so commit steps are omitted. Existing navigation, timeout values, progress behavior, and ad policy resources remain unchanged.

### Task 1: Add The Failing Retry Scheduler Tests

**Files:**
- Create: `app/src/test/java/com/cleanphone/freespace/utility/ui/splash/LaunchOpenAdPreloadSchedulerTest.kt`
- Test: `app/src/test/java/com/cleanphone/freespace/utility/ui/splash/LaunchOpenAdPreloadSchedulerTest.kt`

- [ ] **Step 1: Write the regression tests before production code**

```kotlin
package com.cleanphone.freespace.utility.ui.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchOpenAdPreloadSchedulerTest {
    private val scheduler = LaunchOpenAdPreloadScheduler(retryIntervalMs = 500L)

    @Test
    fun `initial cache miss permits preload immediately`() {
        assertTrue(scheduler.tryAcquire(nowMs = 100L, hasCachedAd = false, isLoading = false))
        assertEquals(1, scheduler.attemptCount)
    }

    @Test
    fun `failed initial attempt can retry after interval`() {
        assertTrue(scheduler.tryAcquire(nowMs = 100L, hasCachedAd = false, isLoading = false))
        assertFalse(scheduler.tryAcquire(nowMs = 599L, hasCachedAd = false, isLoading = false))
        assertTrue(scheduler.tryAcquire(nowMs = 600L, hasCachedAd = false, isLoading = false))
        assertEquals(2, scheduler.attemptCount)
    }

    @Test
    fun `loading request suppresses another attempt without consuming interval`() {
        assertTrue(scheduler.tryAcquire(nowMs = 100L, hasCachedAd = false, isLoading = false))
        assertFalse(scheduler.tryAcquire(nowMs = 600L, hasCachedAd = false, isLoading = true))
        assertTrue(scheduler.tryAcquire(nowMs = 700L, hasCachedAd = false, isLoading = false))
        assertEquals(2, scheduler.attemptCount)
    }

    @Test
    fun `cached ad suppresses further attempts`() {
        assertTrue(scheduler.tryAcquire(nowMs = 100L, hasCachedAd = false, isLoading = false))
        assertFalse(scheduler.tryAcquire(nowMs = 600L, hasCachedAd = true, isLoading = false))
        assertEquals(1, scheduler.attemptCount)
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
$env:ANDROID_HOME='D:\sdk'
.\gradlew.bat :app:testDebugUnitTest --tests "com.cleanphone.freespace.utility.ui.splash.LaunchOpenAdPreloadSchedulerTest" --no-daemon --console=plain
```

Expected: Kotlin test compilation fails because `LaunchOpenAdPreloadScheduler` does not exist.

### Task 2: Implement The Minimal Retry Scheduler

**Files:**
- Create: `app/src/main/java/com/cleanphone/freespace/utility/ui/splash/LaunchOpenAdPreloadScheduler.kt`
- Test: `app/src/test/java/com/cleanphone/freespace/utility/ui/splash/LaunchOpenAdPreloadSchedulerTest.kt`

- [ ] **Step 1: Add the minimal production implementation**

```kotlin
package com.cleanphone.freespace.utility.ui.splash

internal class LaunchOpenAdPreloadScheduler(
    private val retryIntervalMs: Long,
) {
    private var lastAttemptAtMs: Long? = null

    var attemptCount: Int = 0
        private set

    fun tryAcquire(nowMs: Long, hasCachedAd: Boolean, isLoading: Boolean): Boolean {
        if (hasCachedAd || isLoading) return false
        val lastAttempt = lastAttemptAtMs
        if (lastAttempt != null && nowMs - lastAttempt < retryIntervalMs) return false
        lastAttemptAtMs = nowMs
        attemptCount++
        return true
    }
}
```

- [ ] **Step 2: Run the focused test and verify GREEN**

Run the command from Task 1 Step 2.

Expected: all four scheduler tests pass.

### Task 3: Add Consent-Aware Launch Preloading

**Files:**
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/advertise/AdvertisePageMediator.kt:134`
- Reference: `app/src/main/java/com/cleanphone/freespace/utility/ui/SplashActivity.kt:523`

- [ ] **Step 1: Define the preload outcome beside the mediator**

```kotlin
enum class LaunchAdsPreloadResult {
    REQUESTED,
    CONSENT_UNAVAILABLE,
    POLICY_DISABLED,
    FAILED,
}
```

- [ ] **Step 2: Add the UMP request gate**

```kotlin
fun canRequestAds(context: Context): Boolean {
    return runCatching {
        UserMessagingPlatform.getConsentInformation(context).canRequestAds()
    }.onFailure { throwable ->
        Log.w(TAG, "read UMP request state failed", throwable)
    }.getOrDefault(false)
}
```

- [ ] **Step 3: Return an outcome and preserve both startup preloads**

Replace `preloadLaunchAds` with:

```kotlin
@SuppressLint("MissingPermission")
fun preloadLaunchAds(context: Context): LaunchAdsPreloadResult {
    if (!canRequestAds(context)) return LaunchAdsPreloadResult.CONSENT_UNAVAILABLE

    return runCatching {
        val canPreloadOpen =
            AdvertiseSdk.canPreloadOpen(AdvertiseSdk.LOAD_TIME_OPEN_APP)
        if (canPreloadOpen) {
            AdvertiseSdk.preloadOpen(context, AdvertiseSdk.LOAD_TIME_OPEN_APP)
        }
        if (AdvertiseSdk.canPreloadInterstitial(AdvertiseSdk.LOAD_TIME_OPEN_APP)) {
            AdvertiseSdk.preloadInterstitial(context, AdvertiseSdk.LOAD_TIME_OPEN_APP)
        }
        if (canPreloadOpen) {
            LaunchAdsPreloadResult.REQUESTED
        } else {
            LaunchAdsPreloadResult.POLICY_DISABLED
        }
    }.onFailure { throwable ->
        Log.w(TAG, "preloadLaunchAds failed", throwable)
    }.getOrDefault(LaunchAdsPreloadResult.FAILED)
}
```

- [ ] **Step 4: Compile the mediator contract**

Run:

```powershell
$env:ANDROID_HOME='D:\sdk'
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
```

Expected: compilation succeeds with UMP `canRequestAds()` and both explicit preload overloads resolved.

### Task 4: Replace Splash One-Shot Retry With Bounded Throttling

**Files:**
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/ui/SplashActivity.kt:65`
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/ui/SplashActivity.kt:118`
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/ui/SplashActivity.kt:349`
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/ui/SplashActivity.kt:362`
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/ui/SplashActivity.kt:523`

- [ ] **Step 1: Add retry timing and scheduler state**

Add the import and constant:

```kotlin
import com.cleanphone.freespace.utility.ui.splash.LaunchOpenAdPreloadScheduler

private const val OPEN_AD_PRELOAD_RETRY_INTERVAL_MS = 500L
```

Add the scheduler beside the existing jobs:

```kotlin
private val launchOpenAdPreloadScheduler =
    LaunchOpenAdPreloadScheduler(OPEN_AD_PRELOAD_RETRY_INTERVAL_MS)
```

- [ ] **Step 2: Route every early attempt through scheduler gates**

Replace calls to `beginEarlyLaunchOpenAdPreload()` from startup and preparation with `tryBeginLaunchOpenAdPreload()`. Add:

```kotlin
private fun tryBeginLaunchOpenAdPreload() {
    val hasCachedAd = AdvertisePageMediator.hasCachedLaunchOpenAd()
    val isLoading = preloadOpenAdJob?.isActive == true ||
        AdvertisePageMediator.isLaunchOpenAdLoading()
    if (!launchOpenAdPreloadScheduler.tryAcquire(
            nowMs = SystemClock.elapsedRealtime(),
            hasCachedAd = hasCachedAd,
            isLoading = isLoading,
        )
    ) {
        return
    }
    beginEarlyLaunchOpenAdPreload()
}
```

- [ ] **Step 3: Make initialization and preload outcomes observable**

Replace the preload job body with:

```kotlin
private fun beginEarlyLaunchOpenAdPreload() {
    if (preloadOpenAdJob?.isActive == true) return
    preloadOpenAdJob = lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
        runCatching {
            SdkInitUtils.awaitInitialization()
            if (!SdkInitUtils.isInitialized()) {
                LogUtil.log(
                    "splash_open_ad_preload_attempt",
                    mapOf(
                        "attempt" to launchOpenAdPreloadScheduler.attemptCount,
                        "result" to "sdk_unavailable",
                    ),
                )
                return@runCatching
            }
            val result = AdvertisePageMediator.preloadLaunchAds(this@SplashActivity)
            LogUtil.log(
                "splash_open_ad_preload_attempt",
                mapOf(
                    "attempt" to launchOpenAdPreloadScheduler.attemptCount,
                    "result" to result.name.lowercase(),
                ),
            )
        }.onFailure { throwable ->
            LogUtil.log(
                "splash_open_ad_preload_error",
                mapOf("error" to (throwable.message ?: throwable.javaClass.simpleName)),
            )
        }
    }
}
```

- [ ] **Step 4: Retry throughout the existing bounded wait**

Replace the one-shot `requestedRetryPreload` logic in `waitForLaunchOpenAd` with:

```kotlin
private suspend fun waitForLaunchOpenAd(): Boolean {
    val loadWaitTimeout = if (isNetworkAvailable(this)) {
        OPEN_AD_LOAD_WAIT_TIMEOUT_MS
    } else {
        OPEN_AD_OFFLINE_LOAD_WAIT_TIMEOUT_MS
    }
    val deadline = SystemClock.elapsedRealtime() + loadWaitTimeout
    while (!isFinishing && !isDestroyed && SystemClock.elapsedRealtime() < deadline) {
        if (AdvertisePageMediator.hasCachedLaunchOpenAd()) return true
        tryBeginLaunchOpenAdPreload()
        delay(OPEN_AD_POLL_INTERVAL_MS)
    }
    val hasCachedAd = AdvertisePageMediator.hasCachedLaunchOpenAd()
    if (!hasCachedAd) {
        LogUtil.log(
            "splash_open_ad_load_timeout",
            mapOf(
                "attempts" to launchOpenAdPreloadScheduler.attemptCount,
                "can_request_ads" to AdvertisePageMediator.canRequestAds(this),
                "is_loading" to AdvertisePageMediator.isLaunchOpenAdLoading(),
            ),
        )
    }
    return hasCachedAd
}
```

- [ ] **Step 5: Compile the complete activity integration**

Run:

```powershell
$env:ANDROID_HOME='D:\sdk'
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL` with no unresolved scheduler, UMP, mediator, or SDK symbols.

### Task 5: Full Verification

**Files:**
- Verify all files created or modified in Tasks 1-4.

- [ ] **Step 1: Run all debug unit tests**

```powershell
$env:ANDROID_HOME='D:\sdk'
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```

Expected: all existing tests and the four new scheduler tests pass with zero failures.

- [ ] **Step 2: Build the debug APK**

```powershell
$env:ANDROID_HOME='D:\sdk'
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`; APK exists under `app/build/outputs/apk/debug/`.

- [ ] **Step 3: Verify scope and timeout invariants**

Run:

```powershell
rg -n "OPEN_AD_LOAD_WAIT_TIMEOUT_MS|OPEN_AD_OFFLINE_LOAD_WAIT_TIMEOUT_MS|requestedRetryPreload|LaunchOpenAdPreloadScheduler|canRequestAds" app/src/main app/src/test
```

Expected: timeout constants remain `12_000L` and `2_500L`, `requestedRetryPreload` is absent, and the scheduler plus UMP gate appear only in the intended splash/advertising files.
