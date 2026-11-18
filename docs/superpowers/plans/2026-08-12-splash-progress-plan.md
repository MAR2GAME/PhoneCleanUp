# Splash Progress Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the splash progress bar visibly smooth and complete its visual preparation phase in about 4 seconds while preserving all launch-open-ad behavior.

**Architecture:** Keep the existing lifecycle-scoped progress job and startup/ad coroutine unchanged at the orchestration level. Replace the 30-second integer timer with a 16ms frame loop: advance to 90% over 4 seconds, then advance slowly to 99% during any remaining consent/ad wait; the existing final animation remains responsible for 100%.

**Tech Stack:** Kotlin, Android lifecycleScope/coroutines, JUnit source contract tests, Gradle Android unit tests.

---

### Task 1: Lock visual timing and ad invariants with a regression test

**Files:**
- Create: `app/src/test/java/com/cleanphone/freespace/utility/SplashProgressContractTest.kt`
- Read: `app/src/main/java/com/cleanphone/freespace/utility/ui/SplashActivity.kt`

- [ ] **Step 1: Write the failing source contract test**

Assert that `SplashActivity.kt` declares a 90% visual target, a 4-second first phase, a 16ms update interval, and a bounded waiting phase; also assert the online/offline ad wait constants and `completeProgressQuickly()` call remain present.

- [ ] **Step 2: Run the focused test and verify it fails for the missing new timing contract**

Run `./gradlew.bat :app:testDebugUnitTest --tests com.cleanphone.freespace.utility.SplashProgressContractTest --no-daemon --console=plain` with `ANDROID_HOME=D:\sdk`.

Expected: the new test fails because the current source still uses the 30-second `STARTUP_PROGRESS_DURATION_MS` and 80ms interval.

### Task 2: Implement the smooth visual progress loop

**Files:**
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/ui/SplashActivity.kt:71-81,515-528`

- [ ] **Step 1: Replace timing constants**

Set `PROGRESS_UPDATE_INTERVAL = 16L`, add `VISUAL_PROGRESS_TARGET = 90`, set `STARTUP_PROGRESS_DURATION_MS = 4_000L`, and add `WAITING_PROGRESS_DURATION_MS = 8_000L`. Keep `TOTAL_PROGRESS`, `PREPARATION_PROGRESS_MAX`, `FINAL_PROGRESS_DURATION_MS`, and all ad timeout constants unchanged.

- [ ] **Step 2: Update the loop with two monotonic elapsed-time phases**

Compute the first-phase progress as elapsed fraction times 90. After 4 seconds, compute a second-phase fraction over 8 seconds and cap at 99. Update the existing `currentProgress`/binding only when the integer value increases, and keep lifecycle/finishing guards and cancellation behavior unchanged.

- [ ] **Step 3: Run the focused contract test and verify it passes**

Run the same Gradle test command and expect a passing test.

### Task 3: Run regression tests and compile verification

**Files:**
- No additional source changes expected.

- [ ] **Step 1: Run all debug unit tests**

Run `./gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain` with `ANDROID_HOME=D:\sdk`; expect exit code 0.

- [ ] **Step 2: Compile the debug APK**

Run `./gradlew.bat :app:assembleDebug --no-daemon --console=plain` with `ANDROID_HOME=D:\sdk`; expect exit code 0.
