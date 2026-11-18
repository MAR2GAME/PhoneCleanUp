# Splash Progress Feedback Design

## Goal

Shorten the perceived splash preparation cycle and make the progress bar visually smooth without changing consent, ad preload, ad timeout, or ad display behavior.

## Design

`SplashActivity` keeps the existing startup coroutine and launch-open-ad state machine. Only the visual progress loop changes:

- Progress begins as soon as the startup sequence is allowed to begin, preserving the existing notification-permission gate.
- The first phase animates from the current value to `90%` over approximately 4 seconds using frame-paced updates (`16ms`) and a monotonic elapsed-time calculation.
- While consent/ad preparation is still pending, a second phase advances slowly from `90%` toward `99%`. This avoids a frozen-looking bar without falsely indicating that startup has completed.
- `completeProgressQuickly()` remains the only path to `100%`; it is called after the existing ad preparation stage finishes or fails/times out.
- Existing ad wait limits remain unchanged: 12 seconds online, 2.5 seconds offline, and the existing cached-ad display path is untouched.

## State and cancellation

The progress job remains lifecycle-scoped and is cancelled in `onDestroy()` and before the final completion animation. Every update checks the existing finishing/destroyed guards. The loop never calls ad APIs and cannot cause an ad to be skipped or shown earlier.

## Testing

Add a contract test for the source-level timing invariants:

- the visual target is `90%`, not `99%`;
- the visual phase is shorter than the current 30-second preparation timeout;
- the ad wait constants remain unchanged;
- completion still uses the existing final animation path.

Run the focused unit tests and a debug compilation after implementation.
