# APK Size Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce the signed release APK below its 39.73 MiB baseline without changing application flows or Lottie image bytes.

**Architecture:** Remove the unused Compose stack, restore effective R8 shrinking through narrow reflection rules, and canonicalize byte-identical Lottie PNGs while rewriting their structured JSON references. A PowerShell invariant checker guards the configuration and asset graph before the release build and APK-level verification measure the result.

**Tech Stack:** Android Gradle Plugin 8.12, Gradle 8.13, Kotlin 2.2, R8, ViewBinding/DataBinding, Lottie JSON, PowerShell 5.1, Android `apksigner`.

---

## File Map

- Create `tools/verify-apk-optimization.ps1`: static invariant checks for Compose, R8, duplicate PNGs, and Lottie references.
- Create `tools/deduplicate-lottie-assets.ps1`: deterministic, structured Lottie JSON reference rewrite and exact-duplicate deletion.
- Modify `app/build.gradle.kts`: remove unused Compose plugin, build feature, and dependencies.
- Modify three Kotlin files: remove unused Compose imports only.
- Replace `app/proguard-rules.pro`: retain project reflection/serialization entry points without keeping whole libraries.
- Modify `app/src/main/res/raw/*.json`: update only Lottie image filenames through parsed JSON objects.
- Delete exact duplicate files from `app/src/main/assets/images`: retain one canonical byte-identical file per SHA-256 group.

### Task 1: Add the optimization invariant test

**Files:**
- Create: `tools/verify-apk-optimization.ps1`

- [ ] **Step 1: Write a failing static verification script**

The script must resolve the repository root from `$PSScriptRoot`, collect failures, and exit nonzero when any invariant fails. It must check:

```powershell
$buildText = Get-Content -Raw $buildFile
$sourceComposeRefs = @(Get-ChildItem $sourceRoot -Recurse -File -Include *.kt,*.java |
    Select-String -Pattern 'androidx\.compose|@Composable|setContent\s*\{')

$composeBuildPatterns = @(
    'libs\.plugins\.kotlin\.compose',
    'compose\s*=\s*true',
    'libs\.androidx\.compose',
    'libs\.androidx\.ui',
    'libs\.androidx\.material3'
)

$broadKeepPattern = '(?m)^\s*-keep\s+(?:class|interface)\s+(?:androidx|kotlin|kotlinx|com\.google\.android\.material|okhttp3|com\.google\.gson|com\.bumptech\.glide|com\.airbnb\.lottie)\.\*\*\s*\{\s*\*;\s*\}'
```

For Lottie assets, parse every `app/src/main/res/raw/*.json` with `ConvertFrom-Json`, read each `assets[].p`, and assert `app/src/main/assets/images/<p>` exists. Hash every PNG with SHA-256 and fail if any group has more than one file.

- [ ] **Step 2: Run the script and verify RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\verify-apk-optimization.ps1
```

Expected: exit code 1 with failures for Compose configuration/source imports, broad R8 keep rules, and exact duplicate Lottie PNGs.

### Task 2: Remove unused Compose dependencies

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/adapter/AppUsageAdapter.kt`
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/view/LargeFileDialog.kt`
- Modify: `app/src/main/java/com/cleanphone/freespace/utility/ui/TaskListActivity.kt`

- [ ] **Step 1: Remove Compose configuration**

Remove `alias(libs.plugins.kotlin.compose)`, `compose = true`, the Compose BOM, `androidx.ui`, `androidx.ui.graphics`, `androidx.ui.tooling.preview`, `androidx.material3`, Compose Android test dependencies, and Compose debug dependencies. Preserve ViewBinding and all non-Compose dependencies.

- [ ] **Step 2: Remove the three unused imports**

Delete only:

```kotlin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
```

- [ ] **Step 3: Verify there is no production Compose usage**

Run:

```powershell
rg -n "androidx\.compose|@Composable|setContent\s*\{" app\src\main app\build.gradle.kts
```

Expected: no matches and `rg` exit code 1.

### Task 3: Replace broad R8 rules with runtime-specific rules

**Files:**
- Modify: `app/proguard-rules.pro`

- [ ] **Step 1: Preserve only required metadata and framework contracts**

Replace the current duplicated rules with this policy:

```proguard
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable
-allowaccessmodification

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
```

Keep Android components by constructor rather than all members. Preserve XML custom-view constructors. Preserve ViewBinding reflection targets with class-name and `inflate` member rules for `com.cleanphone.freespace.utility.databinding.**`. Preserve EventBus `@Subscribe` methods. Preserve Gson-serialized fields in `com.cleanphone.freespace.utility.bean.**` and fields annotated with `@SerializedName`. Retain `@Keep` semantics and log removal. Rely on dependency consumer rules for third-party implementations.

- [ ] **Step 2: Verify broad keep rules are gone**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\verify-apk-optimization.ps1
```

Expected: the broad-R8-rule failure is gone; the script remains RED only because duplicate PNGs still exist.

### Task 4: Deduplicate Lottie image assets through structured references

**Files:**
- Create: `tools/deduplicate-lottie-assets.ps1`
- Modify: `app/src/main/res/raw/*.json`
- Delete: exact duplicate `app/src/main/assets/images/*.png` files selected by the script

- [ ] **Step 1: Implement deterministic deduplication**

The script must:

1. Group PNGs by SHA-256.
2. Choose the lexicographically smallest filename as the canonical file.
3. Build a duplicate-to-canonical filename map.
4. Parse every Lottie JSON with `ConvertFrom-Json`.
5. Replace only `assets[].p` values found in the map.
6. Serialize valid compact UTF-8 JSON with sufficient depth.
7. Reparse each written JSON and assert every non-image composition property remains semantically present.
8. Delete only mapped duplicate PNGs after all JSON writes succeed.
9. Print the removed file count and byte total.

- [ ] **Step 2: Execute deduplication**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\deduplicate-lottie-assets.ps1
```

Expected: 49 duplicate files removed and approximately 8.47 MiB removed from source assets.

- [ ] **Step 3: Verify GREEN for static invariants**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\verify-apk-optimization.ps1
```

Expected: exit code 0; no Compose references, no prohibited broad keep rules, no duplicate PNG hashes, and no missing Lottie image references.

### Task 5: Compile and test the optimized release

**Files:**
- Generated: `app/build/outputs/apk/release/app-release.apk`
- Generated: `app/build/outputs/mapping/release/*`

- [ ] **Step 1: Compile Kotlin release sources offline**

Run:

```powershell
$env:ANDROID_HOME = 'D:\sdk'
& 'C:\Users\weiyu\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat' :app:compileReleaseKotlin --offline --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run release unit tests offline**

Run the same Gradle executable with `:app:testReleaseUnitTest`. Expected: `BUILD SUCCESSFUL`, including `NO-SOURCE` if this project has no release unit tests.

- [ ] **Step 3: Assemble the signed release offline**

Run the same Gradle executable with `:app:assembleRelease`. Expected: `BUILD SUCCESSFUL` and a new `app/build/outputs/apk/release/app-release.apk`.

### Task 6: Verify signature, package contents, and measured reduction

**Files:**
- Inspect: `app/build/outputs/apk/release/app-release.apk`

- [ ] **Step 1: Verify the APK signature**

Run:

```powershell
& 'D:\sdk\build-tools\35.0.0\apksigner.bat' verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk
```

Expected: signature verification succeeds and at least one APK signature scheme reports `true`.

- [ ] **Step 2: Re-run all static invariants after generation**

Run `tools/verify-apk-optimization.ps1`. Expected: exit code 0.

- [ ] **Step 3: Measure APK categories**

Open the APK with `System.IO.Compression.ZipFile`, total compressed lengths for DEX, `assets/images`, `res`, native libraries, and other files, then compare against:

```text
Baseline APK:          39.73 MiB
Baseline DEX:          24.16 MiB
Baseline asset images: 10.41 MiB
```

Expected: final APK is below 39.73 MiB, asset images are reduced by about 8.47 MiB before ZIP alignment overhead, and DEX is lower after Compose removal and effective shrinking.

- [ ] **Step 4: Report runtime-test limits**

Run `adb devices`. If no device or emulator is in `device` state, report that build, shrinker, signature, and static resource verification passed but full UI smoke testing was not performed.

## Version Control Note

This workspace is not a Git repository (`git rev-parse --show-toplevel` fails), so task commits cannot be created. Do not initialize a repository or manufacture commit history as part of this optimization.
