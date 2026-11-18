# Scan Files MVVM Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert `ScanFilesActivity` to production-ready MVVM without changing its UI, analytics, advertisement, dialog, EventBus, or navigation behavior.

**Architecture:** `ScanFilesActivity` renders a single immutable state owned by `ScanFilesViewModel`. A `JunkScanRepository` wraps `JunkFileScanner` and publishes deep-copied results, while injected clock and delay functions make the four-second minimum scan display deterministic in unit tests.

**Tech Stack:** Kotlin 2.2, AndroidX Lifecycle ViewModel/LiveData, Kotlin coroutines, ViewBinding, JUnit 4, kotlinx-coroutines-test.

---

## File Structure

- Create `app/src/main/java/com/cleanphone/freespace/utility/ui/scan/ScanFilesState.kt`: immutable phase and UI-state definitions plus deep-copy helpers.
- Create `app/src/main/java/com/cleanphone/freespace/utility/ui/scan/JunkScanRepository.kt`: repository contract and production scanner wrapper.
- Create `app/src/main/java/com/cleanphone/freespace/utility/viewmodel/ScanFilesViewModel.kt`: scan orchestration, timing, selection, and cleanup.
- Modify `app/src/main/java/com/cleanphone/freespace/utility/ui/ScanFilesActivity.kt`: convert to `BaseMvvmActivity` and render ViewModel state.
- Modify `app/src/main/java/com/cleanphone/freespace/utility/adapter/ExpandableListAdapter.kt`: report desired selection without owning the state transition.
- Modify `gradle/libs.versions.toml`: declare the coroutine-test dependency using the existing coroutine version.
- Modify `app/build.gradle.kts`: add coroutine-test to JVM tests.
- Create `app/src/test/java/com/cleanphone/freespace/utility/viewmodel/ScanFilesViewModelTest.kt`: ViewModel state, timing, duplicate-start, selection, failure, and cleanup tests.
- Create `app/src/test/java/com/cleanphone/freespace/utility/ui/scan/ScanFilesStateTest.kt`: deep-copy isolation tests.

### Task 1: Add Test Infrastructure And State Contract

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/test/java/com/cleanphone/freespace/utility/ui/scan/ScanFilesStateTest.kt`
- Create: `app/src/main/java/com/cleanphone/freespace/utility/ui/scan/ScanFilesState.kt`

- [ ] **Step 1: Add the coroutine test dependency**

Add to `[libraries]` in `gradle/libs.versions.toml`:

```toml
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesPlayServices" }
```

Add to `dependencies` in `app/build.gradle.kts`:

```kotlin
testImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 2: Write the failing state-copy tests**

Create tests that construct a category with a child, call `deepCopyCategories()`, mutate both the original group and original child, and assert the copied values do not change. Add a second test that mutates the copy and asserts the original does not change.

Expected public API:

```kotlin
enum class ScanPhase { SCANNING, COMPLETED, FAILED }

data class ScanFilesUiState(
    val phase: ScanPhase = ScanPhase.SCANNING,
    val categories: List<JunkMotherBean> = emptyList(),
    val selectedSizeBytes: Long = 0L,
    val error: ScanError? = null,
)

enum class ScanError { SCAN_FAILED }

internal fun List<JunkMotherBean>.deepCopyCategories(
    showChecks: Boolean? = null,
): List<JunkMotherBean>
```

- [ ] **Step 3: Run the focused test and verify RED**

Run:

```powershell
$env:ANDROID_HOME='D:\sdk'; .\gradlew.bat :app:testDebugUnitTest --tests "com.cleanphone.freespace.utility.ui.scan.ScanFilesStateTest" --no-daemon --console=plain
```

Expected: compilation failure because `ScanFilesUiState`, `ScanPhase`, and `deepCopyCategories` do not exist.

- [ ] **Step 4: Implement the minimal immutable state and deep copy**

`deepCopyCategories` must create a new `JunkMotherBean`, a new mutable child list, and new `JunkChildBean` instances. When `showChecks` is non-null, use that value for every copied group's `isShowCheck`; otherwise preserve the source value.

- [ ] **Step 5: Run the focused test and verify GREEN**

Run the same command. Expected: all `ScanFilesStateTest` tests pass.

### Task 2: Add The Repository Boundary

**Files:**
- Create: `app/src/main/java/com/cleanphone/freespace/utility/ui/scan/JunkScanRepository.kt`
- Create: `app/src/test/java/com/cleanphone/freespace/utility/ui/scan/JunkScanRepositoryContractTest.kt`

- [ ] **Step 1: Write a failing repository contract test around result isolation**

Define the desired contract in the test:

```kotlin
interface JunkScanRepository : AutoCloseable {
    suspend fun scan(categories: List<JunkMotherBean>): List<JunkMotherBean>
    override fun close()
}
```

Test the reusable copying function at the repository boundary: after a fake scanner result is copied, later mutation of the scanner list cannot change the returned list. Keep Android file traversal out of the JVM test.

- [ ] **Step 2: Run the focused test and verify RED**

Expected: compilation failure because `JunkScanRepository` and the production boundary do not exist.

- [ ] **Step 3: Implement `DefaultJunkScanRepository`**

Use this production shape:

```kotlin
class DefaultJunkScanRepository(
    private val applicationContext: Context,
) : JunkScanRepository {
    private var scanner: JunkFileScanner? = null
    private var closed = false

    override suspend fun scan(categories: List<JunkMotherBean>): List<JunkMotherBean> {
        check(!closed) { "Repository is closed" }
        val scannerInput = categories.deepCopyCategories().toMutableList()
        val activeScanner = JunkFileScanner(applicationContext.applicationContext, scannerInput)
        scanner = activeScanner
        return activeScanner.scanJunkFiles().getOrThrow().deepCopyCategories()
    }

    override fun close() {
        if (closed) return
        closed = true
        scanner?.destroy()
        scanner = null
    }
}
```

Use synchronization or equivalent guarding around `scanner` and `closed` if needed so `close()` racing with scan completion remains idempotent. Do not catch `CancellationException` as a normal failure.

- [ ] **Step 4: Run repository/state tests and verify GREEN**

Expected: all scan state and repository contract tests pass.

### Task 3: Implement ViewModel Scan State With TDD

**Files:**
- Create: `app/src/test/java/com/cleanphone/freespace/utility/viewmodel/ScanFilesViewModelTest.kt`
- Create: `app/src/main/java/com/cleanphone/freespace/utility/viewmodel/ScanFilesViewModel.kt`

- [ ] **Step 1: Write the failing happy-path and duplicate-start tests**

Use `StandardTestDispatcher`, `Dispatchers.setMain`, and a fake `JunkScanRepository`. Construct the ViewModel with injected dependencies:

```kotlin
class ScanFilesViewModel(
    application: Application,
    private val repository: JunkScanRepository = DefaultJunkScanRepository(application),
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val waitMs: suspend (Long) -> Unit = { delay(it) },
) : BaseViewModel(application)
```

Tests must assert:

- First `startScan(categories)` publishes `SCANNING` and invokes the repository once.
- A second call during or after the scan does not invoke the repository again.
- Successful results become `COMPLETED`, all categories and children are selected, checks are visible, and the selected total is correct.

- [ ] **Step 2: Run the focused ViewModel tests and verify RED**

Expected: compilation failure because `ScanFilesViewModel` does not exist.

- [ ] **Step 3: Implement minimal start and completion behavior**

Expose read-only LiveData:

```kotlin
private val _uiState = MutableLiveData(ScanFilesUiState())
val uiState: LiveData<ScanFilesUiState> = _uiState
```

Guard `startScan` with a private `hasStarted` flag set synchronously before launching. Invoke the repository in `viewModelScope`; do not create an Activity-owned scope.

- [ ] **Step 4: Verify the happy path passes**

Expected: the initial focused tests pass with no duplicate repository invocation.

- [ ] **Step 5: Write failing timing tests**

Cover both cases:

- Repository returns at time 1,000 ms after a start at 0 ms: `waitMs` receives 3,000 ms.
- Repository returns at time 5,000 ms: `waitMs` is not invoked.

Use a mutable fake clock so the tests do not sleep.

- [ ] **Step 6: Implement the remaining-delay rule**

Use:

```kotlin
val remainingMs = (MIN_SCAN_DISPLAY_TIME_MS - (nowMs() - startedAtMs)).coerceAtLeast(0L)
if (remainingMs > 0L) waitMs(remainingMs)
```

Keep `MIN_SCAN_DISPLAY_TIME_MS = 4_000L` in the ViewModel companion object.

- [ ] **Step 7: Write failing failure and cancellation tests**

Assert a non-cancellation exception publishes `FAILED`, empty categories, zero size, and `ScanError.SCAN_FAILED`. Assert `CancellationException` does not publish `FAILED`.

- [ ] **Step 8: Implement production-safe error handling**

Catch `CancellationException` separately and rethrow it. Log other exceptions with `Log.e(TAG, "Junk scan failed", error)` and publish the safe failed state only after applying the same minimum-display rule.

- [ ] **Step 9: Write failing cleanup test**

Expose ViewModel cleanup to the test through a small test subclass or invoke the lifecycle-clearing path supported by the test setup. Assert repository `close()` runs exactly once.

- [ ] **Step 10: Implement idempotent cleanup and verify GREEN**

Override `onCleared()`, call `repository.close()`, then `super.onCleared()`. Run all `ScanFilesViewModelTest` tests; expected: pass.

### Task 4: Implement Selection State With TDD

**Files:**
- Modify: `app/src/test/java/com/cleanphone/freespace/utility/viewmodel/ScanFilesViewModelTest.kt`
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/viewmodel/ScanFilesViewModel.kt`

- [ ] **Step 1: Write failing group-selection tests**

After a completed scan, call:

```kotlin
viewModel.setGroupChecked(groupIndex = 0, checked = false)
```

Assert the target group and all its children are unchecked, other groups remain unchanged, and `selectedSizeBytes` equals the sum of checked groups. Add tests for reselecting and for deselecting every group.

- [ ] **Step 2: Run and verify RED**

Expected: compilation failure because `setGroupChecked` does not exist.

- [ ] **Step 3: Implement immutable selection updates**

Ignore invalid indexes and ignore selection while the phase is `SCANNING`. Copy every emitted group/child model; never mutate the current state objects in place. Calculate selected size from checked groups.

- [ ] **Step 4: Run all ViewModel/state tests and verify GREEN**

Expected: all focused JVM tests pass.

### Task 5: Make The Adapter A View-Only Selection Reporter

**Files:**
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/adapter/ExpandableListAdapter.kt`
- Create: `app/src/test/java/com/cleanphone/freespace/utility/adapter/ExpandableListAdapterContractTest.kt`

- [ ] **Step 1: Write a source contract test that fails against the current adapter**

Following existing project contract-test style, assert the adapter source no longer assigns `group.isChecked` or child `isCheck` inside the click listener and that the listener signature includes `(groupPosition: Int, checked: Boolean)`.

- [ ] **Step 2: Run and verify RED**

Expected: contract test fails because the adapter currently mutates `JunkMotherBean` and `JunkChildBean` directly.

- [ ] **Step 3: Change the listener contract**

Use:

```kotlin
fun interface OnCheckChangeListener {
    fun onCheckChange(groupPosition: Int, checked: Boolean)
}
```

The group check listener calls `onCheckChange(groupPosition, !group.isChecked)` only. Add `submitGroups(groups: List<JunkMotherBean>)` to replace the adapter's internal list with a mutable copy and notify changes. Retain old public names only if another call site requires binary source compatibility.

- [ ] **Step 4: Run contract tests and compile Kotlin**

Run the focused contract test, then `:app:compileDebugKotlin`. Expected: tests and compilation pass after Activity call sites are updated in the next task; if compilation temporarily fails solely in `ScanFilesActivity`, proceed directly to Task 6 without claiming green for the full module.

### Task 6: Convert ScanFilesActivity To MVVM

**Files:**
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/ui/ScanFilesActivity.kt`
- Create: `app/src/test/java/com/cleanphone/freespace/utility/ScanFilesMvvmContractTest.kt`

- [ ] **Step 1: Write failing Activity source contract tests**

Assert the source:

- Extends `BaseMvvmActivity<ActivityScanFilesBinding, ScanFilesViewModel>`.
- Does not declare `scanJob`, `timeJob`, `scanner`, `isScanning`, `isScanFinished`, or `scanStartTime`.
- Observes `viewModel.uiState`.
- Forwards adapter selection to `viewModel.setGroupChecked`.
- Does not use `printStackTrace()`.

- [ ] **Step 2: Run and verify RED**

Expected: contract tests fail against the current Activity.

- [ ] **Step 3: Convert initialization and observation**

Implement `override fun init()` and perform:

```kotlin
expandableListAdapter = ExpandableListAdapter(this, mutableListOf())
binding.exJunk.setAdapter(expandableListAdapter)
expandableListAdapter.setOnCheckChangeListener { index, checked ->
    viewModel.setGroupChecked(index, checked)
}
viewModel.uiState.observe(this, ::renderState)
viewModel.startScan(createInitialCategories())
```

Preserve analytics parameters, back callback, `safeClick`, rating action, dialog callbacks, advertisement mediator calls, and target Activities.

- [ ] **Step 4: Implement deterministic rendering**

`renderState` submits a deep snapshot to the adapter and switches on the phase. During scanning, keep the existing scanning presentation and disabled button. For completed/failed, show checks and result UI. Format the selected size from `state.selectedSizeBytes`.

Use the latest `uiState.value` in back and clean actions. Do not cache an independent total-size field.

- [ ] **Step 5: Preserve navigation payload behavior**

For selected junk, post `CleanFilesBean(state.selectedSizeBytes, state.categories.deepCopyCategories().toMutableList())`, keep the existing title/content bundle strings, then navigate to `CleanFilesActivity`. For zero size, navigate to `FinishedActivity`. Failed state follows the zero-size safe path.

- [ ] **Step 6: Simplify Activity cleanup**

Release and null only `CleanHintDialog`. Do not cancel ViewModel jobs or destroy the repository/scanner from the Activity.

- [ ] **Step 7: Run Activity contracts and Kotlin compilation**

Expected: `ScanFilesMvvmContractTest` passes and `:app:compileDebugKotlin` succeeds.

### Task 7: Full Verification And Production Build

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run focused tests**

```powershell
$env:ANDROID_HOME='D:\sdk'; .\gradlew.bat :app:testDebugUnitTest --tests "com.cleanphone.freespace.utility.viewmodel.ScanFilesViewModelTest" --tests "com.cleanphone.freespace.utility.ui.scan.*" --tests "com.cleanphone.freespace.utility.ScanFilesMvvmContractTest" --no-daemon --console=plain
```

Expected: all focused tests pass.

- [ ] **Step 2: Run the complete JVM unit suite**

```powershell
$env:ANDROID_HOME='D:\sdk'; .\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL` with no failed tests.

- [ ] **Step 3: Compile and assemble debug**

```powershell
$env:ANDROID_HOME='D:\sdk'; .\gradlew.bat :app:compileDebugKotlin :app:assembleDebug --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL` and a debug APK under `app/build/outputs/apk/debug/`.

- [ ] **Step 4: Assemble the minified release variant**

```powershell
$env:ANDROID_HOME='D:\sdk'; .\gradlew.bat :app:assembleRelease --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`, R8 completes, and a signed release APK exists under `app/build/outputs/apk/release/`.

- [ ] **Step 5: Review the final diff manually**

Confirm no UI resource, analytics event, advertisement area, destination Activity, or EventBus payload was unintentionally changed. Confirm no generated `app/build` artifact is treated as source.

## Commit Note

The workspace is not currently a Git repository, so this plan intentionally omits commit commands. Do not initialize Git or manufacture commit history without user authorization.
