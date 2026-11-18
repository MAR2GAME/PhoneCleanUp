# Scan Files MVVM Design

## Objective

Refactor `ScanFilesActivity` into a production-ready MVVM implementation while preserving the current UI, analytics, advertisements, dialogs, EventBus handoff, and navigation behavior.

The refactor must prevent duplicate scans across Activity recreation, keep scanning work out of the Activity, make selection calculations deterministic, handle cancellation and failures safely, and provide focused unit coverage for the state transitions.

## Scope

Included:

- Move scan execution, minimum display timing, selection state, and total-size calculation into a ViewModel.
- Isolate `JunkFileScanner` behind a repository interface.
- Expose one immutable UI state to the Activity.
- Preserve scan results across configuration-driven Activity recreation through the ViewModel.
- Add production-safe error logging and empty-result fallback behavior.
- Add unit tests for the ViewModel and repository boundary.
- Verify debug and minified release builds.

Excluded:

- Visual redesign or layout changes.
- Replacing the existing `ExpandableListView` or adapter technology.
- Replacing EventBus in the cleanup navigation flow.
- Reworking advertisement policy, analytics naming, or downstream cleanup behavior.
- A project-wide architecture migration.

## Architecture

### Activity

`ScanFilesActivity` will extend `BaseMvvmActivity<ActivityScanFilesBinding, ScanFilesViewModel>` and remain responsible only for Android UI work:

- Inflate and bind views.
- Register click and back-press listeners.
- Observe lifecycle-aware UI state.
- Render scanning, completed, and failed states.
- Refresh the expandable-list adapter from state snapshots.
- Show and release `CleanHintDialog`.
- Perform analytics, advertisement presentation, EventBus publication, and navigation.

The Activity will not own scan jobs, elapsed-time jobs, scan booleans, or the authoritative selected-size value.

### ViewModel

`ScanFilesViewModel` will extend the project's `BaseViewModel` and own:

- The current immutable `ScanFilesUiState`.
- The single scan job for its lifetime.
- The scan start timestamp and minimum-display-time rule.
- Selection updates and selected-size calculation.
- Prevention of duplicate scan starts.
- Cancellation of repository work when the ViewModel is cleared.

The ViewModel will accept a repository, clock, and delay abstraction through a factory-friendly constructor so unit tests do not depend on Android file access or real time.

### Repository

`JunkScanRepository` will define the scan boundary. Its production implementation will create and manage `JunkFileScanner` with the application context, never an Activity context.

The repository will return copied category and child-item snapshots. Scanner-owned mutable lists will not be published directly to the UI. This prevents the scanner, ViewModel, and adapter from mutating the same list concurrently.

The production repository will expose cancellation or close behavior that delegates to `JunkFileScanner.destroy()` exactly once.

### Adapter Boundary

The adapter can continue rendering `JunkMotherBean` for compatibility, but it will no longer be authoritative for selection state. A group check interaction will report the group index and desired checked value to the Activity, which forwards it to the ViewModel.

Each emitted UI state contains a fresh list snapshot. The Activity replaces the adapter data from that snapshot and calls `notifyDataSetChanged()` without performing size calculations itself.

## State Model

`ScanFilesUiState` will contain:

- `phase`: `Scanning`, `Completed`, or `Failed`.
- `categories`: the current immutable category snapshot.
- `selectedSizeBytes`: the sum of selected categories.
- `error`: an internal failure marker suitable for logging and tests, not raw user-facing exception text.

Derived presentation decisions remain simple and deterministic:

- Scanning: spinner visible, result header hidden, clean button disabled, category checks hidden.
- Completed with selected junk: result header visible, clean button enabled, junk-found copy and artwork shown.
- Completed with zero selected junk: result header visible, clean button enabled, clean-device copy, rate action, and artwork shown.
- Failed: scanning stops, an empty completed-style result is rendered, and the user can leave the screen safely.

The four category labels continue to come from Android string resources. The Activity passes the initial category definitions to the ViewModel once. Repeated initialization calls are ignored after state creation or scan start.

## Data Flow

1. The Activity creates the four localized category definitions and calls `viewModel.startScan(categories)`.
2. The ViewModel ignores the call if a scan has already started during its lifetime.
3. The ViewModel publishes `Scanning`, records the start time, and invokes the repository from `viewModelScope`.
4. The repository runs the scanner off the main thread and returns a copied result snapshot.
5. The ViewModel waits only for the remaining portion of the existing four-second minimum display time.
6. The ViewModel marks category checks visible, selects all categories by default, calculates the total, and publishes `Completed`.
7. On group selection, the Activity sends the group index and desired state to the ViewModel.
8. The ViewModel copies the affected group and child items, recalculates `selectedSizeBytes`, and publishes a new state.
9. The Activity renders the new state and uses its current snapshot when creating `CleanFilesBean` for navigation.

Activity recreation observes the existing ViewModel state and does not trigger a second repository call. Process death is allowed to start a new scan because file scan results are transient and potentially stale; they will not be serialized into `SavedStateHandle`.

## Back Press And Navigation

Back behavior remains unchanged:

- During `Scanning`, show the non-cleanable scanning dialog.
- After completion with selected junk, show the clean-hint dialog.
- After completion with no selected junk, show the return-home interstitial and navigate home.
- In failed state, treat the result as empty and allow the safe return-home path.

The clean button is disabled during scanning. Click handling will re-check the latest state before navigation so rapid or stale clicks cannot start cleanup early.

Navigation remains an Activity concern because it depends on dialogs, advertisements, EventBus, and Activity APIs. No replaying navigation event will be stored in persistent ViewModel state, avoiding duplicate navigation after Activity recreation.

## Error Handling And Lifecycle

- `CancellationException` propagates as cancellation and is not converted to a scan failure.
- Repository or scanner failures are logged with Android logging and mapped to `Failed` with an empty snapshot.
- Raw exception messages are not displayed to users.
- `printStackTrace()` is removed from this flow.
- The Activity releases only dialog and view resources in `onDestroy()`.
- The ViewModel cancels its coroutine automatically and closes the repository in `onCleared()`.
- Repository close is idempotent so cancellation and cleanup cannot destroy the scanner twice.
- Scanner access uses application context to avoid retaining a destroyed Activity.

The existing scanner's per-category timeout behavior remains unchanged. A category timeout produces an empty category and does not fail the entire scan.

## Testing

ViewModel unit tests will use a fake repository, fake clock, and controllable dispatcher/delay to verify:

- A second `startScan` call does not invoke the repository twice.
- Fast scans remain in `Scanning` until four seconds have elapsed.
- Slow scans publish `Completed` immediately after the repository result when the minimum time has already elapsed.
- Successful results select all categories and calculate the correct total.
- Selecting and deselecting groups updates child selection and selected size.
- Deselecting every category produces zero selected size.
- Repository failure produces `Failed` with an empty safe state.
- Cancellation does not publish a false failure.
- Repository cleanup runs when the ViewModel is cleared.

Focused adapter or state-copy tests will verify that emitted snapshots do not share mutable child lists with scanner results.

Verification commands will include the relevant unit-test task, Kotlin compilation, debug assembly, and minified release assembly.

## Production Acceptance Criteria

- The page looks and navigates the same as before the refactor.
- Analytics parameters, advertisement entry points, and EventBus payload behavior remain unchanged.
- Rotating or recreating the Activity during a scan does not start another scan.
- No Activity context is retained by scan work.
- Scanning and timing jobs are not owned by the Activity.
- Selection totals always match the current selected category snapshot.
- Scan failure leaves the screen usable and does not crash or expose exception details.
- All new focused tests pass.
- Debug and release variants compile, and the minified release APK assembles successfully.
