# Interstitial Ad Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route every feature-entry interstitial through the intended area key, wait for SDK initialization without blocking the UI, and log the exact reason when an ad is not shown.

**Architecture:** A pure `InterstitialAreaResolver` owns localized-label mapping, while `AdvertisePageMediator` exposes separate tag and area-key entry points and sequences SDK calls from an Activity lifecycle coroutine. Focused diagnostics are added at the mediator and SDK display boundary without changing policy decisions.

**Tech Stack:** Android, Kotlin 2.2, AndroidX lifecycle coroutines, JUnit 4, Google Mobile Ads, `com.pdffox:advertise` source mirror.

---

The directory is not a Git repository, so commit steps are intentionally omitted.

### Task 1: Protect area resolution with failing tests

**Files:**
- Create: `app/src/test/java/com/cleanphone/freespace/utility/advertise/InterstitialAreaResolverTest.kt`
- Create: `app/src/test/java/com/cleanphone/freespace/utility/InterstitialEntryContractTest.kt`

- [ ] **Step 1: Write resolver tests**

Create tests that construct `FeatureEntryLabels` with deterministic strings and assert that junk clean, notification clean, app usage, network scan, network speed, network usage, process manager, app manager, battery info, and speaker cleaner map to their matching `AdAreaKeys` values. Assert that images, video, audio, APK, large files, screenshots, and an unknown label map to `ENTER_FILE_MANAGE`.

- [ ] **Step 2: Write the entry contract test**

Read `AdvertisePageMediator.kt`, `ToolBoxFragment.kt`, and `TaskListActivity.kt`. Assert that the mediator declares `showFeatureEntryInterstitialByAreaKey`, forwards `areaKey` directly to `showInterstitial`, and that constant-based callers use this method.

- [ ] **Step 3: Verify RED**

Run:

```powershell
$env:ANDROID_HOME='D:\sdk'; .\gradlew.bat :app:testDebugUnitTest --tests '*InterstitialAreaResolverTest' --tests '*InterstitialEntryContractTest' --no-daemon --console=plain
```

Expected: FAIL because the resolver and explicit area-key API do not exist.

### Task 2: Implement explicit routing and fix callers

**Files:**
- Create: `app/src/main/java/com/cleanphone/freespace/utility/advertise/InterstitialAreaResolver.kt`
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/advertise/AdvertisePageMediator.kt`
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/fragment/ToolBoxFragment.kt`
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/ui/TaskListActivity.kt`

- [ ] **Step 1: Add the pure resolver**

Define `FeatureEntryLabels` and `InterstitialAreaResolver.featureEntry(labels, nextTag)`. Use one `when` branch per feature and combine all file-category labels into the file-manager branch.

- [ ] **Step 2: Split the mediator API**

Add `showFeatureEntryInterstitialByTag` and `showFeatureEntryInterstitialByAreaKey`. Keep `showFeatureEntryInterstitial` as a compatibility wrapper to the tag method. Build `FeatureEntryLabels` from Android string resources only inside the mediator.

- [ ] **Step 3: Update constant-based callers**

Change every call that passes `AdAreaKeys.Interstitial.*` to use `showFeatureEntryInterstitialByAreaKey`. Leave localized-string callers on the compatibility/tag API.

- [ ] **Step 4: Verify GREEN**

Run the focused command from Task 1. Expected: PASS.

### Task 3: Sequence requests after SDK initialization

**Files:**
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/advertise/AdvertisePageMediator.kt`
- Test: `app/src/test/java/com/cleanphone/freespace/utility/InterstitialEntryContractTest.kt`

- [ ] **Step 1: Extend the failing source contract**

Assert that `showInterstitial` launches from `activity.lifecycleScope`, calls `SdkInitUtils.awaitInitialization()`, checks `SdkInitUtils.isInitialized()`, and completes the business callback when initialization is unavailable.

- [ ] **Step 2: Verify RED**

Run the focused contract test. Expected: FAIL because initialization sequencing is absent.

- [ ] **Step 3: Implement asynchronous sequencing**

Use `activity.lifecycleScope.launch` and an exactly-once `AtomicBoolean` callback guard. Log `areaKey`, pre-wait state, post-wait state, destroyed Activity state, and thrown failures. Do not call the SDK when initialization failed.

- [ ] **Step 4: Verify GREEN**

Run the focused contract test and resolver tests. Expected: PASS.

### Task 4: Add SDK rejection diagnostics

**Files:**
- Modify: `com/pdffox/adv/adv/ShowInterAd.kt`

- [ ] **Step 1: Inspect each early return**

Confirm the exact branches for coordinator acquisition, policy evaluation, active platform selection, cache use, and AdMob load callbacks.

- [ ] **Step 2: Add reason-specific logs**

Log the `areaKey` and rejection reason at each early return. For `onAdFailedToLoad`, include the Google Mobile Ads error code, domain, message, cause, and response info. Preserve all callbacks and analytics behavior.

- [ ] **Step 3: Compile the SDK integration**

Run:

```powershell
$env:ANDROID_HOME='D:\sdk'; .\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
```

Expected: BUILD SUCCESSFUL.

### Task 5: Full verification

**Files:**
- Modify only when verification exposes a scoped defect.

- [ ] **Step 1: Run all unit tests**

```powershell
$env:ANDROID_HOME='D:\sdk'; .\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```

Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 2: Compile Kotlin**

```powershell
$env:ANDROID_HOME='D:\sdk'; .\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Assemble Debug**

```powershell
$env:ANDROID_HOME='D:\sdk'; .\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Expected: BUILD SUCCESSFUL and an updated Debug APK.

- [ ] **Step 4: Audit requirements**

Re-read the design, inspect changed files, and confirm that no remote policy, rate, frequency, or ad identifier was changed.
