# APK Size Optimization Design

## Goal

Reduce the signed release APK from 39.73 MiB while preserving the current application behavior, page implementation, Lottie animation timing, and release signing configuration.

## Baseline

- Signed APK: `app/release/app-release.apk`, 39.73 MiB.
- DEX: 24.16 MiB (60.8%).
- `assets/images`: 10.41 MiB (26.2%).
- Android resources: 3.47 MiB.
- Release already enables R8 minification and resource shrinking.
- The application uses XML layouts, ViewBinding, and DataBinding. It contains no `@Composable` function or Compose `setContent` call.
- Compose appears only in three unused imports.
- The R8 configuration broadly keeps AndroidX, Kotlin, coroutines, Material, OkHttp, Gson, Glide, Lottie, DataBinding, and other libraries.
- Of 91 Lottie PNG files, 49 are exact duplicate copies. Keeping one file per SHA-256 group can remove 8.47 MiB before APK packaging.

## Scope

### Build configuration

- Remove the Kotlin Compose plugin from the application module.
- Disable the Compose build feature.
- Remove Compose BOM, UI, graphics, tooling preview, Material 3, Compose test, tooling, and test-manifest dependencies.
- Keep the existing XML/ViewBinding/DataBinding UI stack.
- Remove the three unused Compose imports from source files.

### R8 configuration

- Keep `isMinifyEnabled = true` and `isShrinkResources = true`.
- Replace broad whole-library keep rules with narrow rules required by actual runtime behavior.
- Rely on library-provided consumer ProGuard rules for AndroidX, Kotlin, coroutines, OkHttp, Glide, Lottie, and Google Play libraries.
- Retain project-specific rules for:
  - Android manifest components and custom views instantiated by framework or XML.
  - ViewBinding/DataBinding classes resolved through reflection by the base activity and fragment classes.
  - EventBus methods annotated with `@Subscribe`.
  - Gson model fields and generic signatures used by the application.
  - Parcelable creators, enum helpers, native methods, Java serialization hooks, and annotated `@Keep` members.
  - Source line information used for release crash diagnosis.
- Do not add `-dontshrink`, `-dontoptimize`, or whole-package `{ *; }` rules for third-party libraries.

### Lottie asset deduplication

- Calculate SHA-256 for each PNG under `app/src/main/assets/images`.
- For each identical group, choose one stable canonical filename.
- Rewrite every `p` field in the 12 Lottie JSON resources so duplicate names point to the canonical filename.
- Delete only image copies whose bytes exactly match the retained canonical image.
- Do not resize, recompress, recolor, or otherwise transform image data.
- Do not change Lottie layers, frame rates, dimensions, timing, or composition metadata.

## Safety Constraints

- Every Lottie image reference must resolve to an existing file after deduplication.
- No source import may reference Compose after dependency removal.
- Release assembly must complete with R8 and resource shrinking enabled.
- The produced APK must contain the expected application ID and a valid release signature.
- Existing user-authored files unrelated to this optimization must remain unchanged.
- Signing credential storage is outside this optimization scope.

## Verification

1. Add a repository-local PowerShell verification script for package-size invariants.
2. Before implementation, run it against the current project and confirm it fails because Compose, broad keep rules, and duplicate images are present.
3. After implementation, run the script and confirm:
   - no production Compose references or Compose build dependencies remain;
   - prohibited broad R8 rules are absent;
   - no exact duplicate Lottie PNG remains;
   - every Lottie JSON image reference resolves.
4. Run release unit tests when available.
5. Run `:app:assembleRelease` with the installed Gradle 8.13 distribution and Android SDK.
6. Verify the signed APK using Android build tools.
7. Recompute APK category sizes and compare the final signed APK against the 39.73 MiB baseline.

## Acceptance Criteria

- Release build succeeds without Compose dependencies.
- Static optimization checks pass.
- Lottie references are complete and contain no byte-identical duplicate images.
- Release APK signature verification succeeds.
- Final signed APK is smaller than 39.73 MiB.
- No application feature, page flow, animation timing, or visual image bytes are deliberately changed.

## Limitations

Build, static reference, and signature verification cannot prove every runtime interaction. If no Android device or emulator is connected, the result will explicitly report that full device smoke testing was not performed.
