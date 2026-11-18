[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$buildFile = Join-Path $repoRoot 'app\build.gradle.kts'
$sourceRoot = Join-Path $repoRoot 'app\src\main'
$rulesFile = Join-Path $repoRoot 'app\proguard-rules.pro'
$rawRoot = Join-Path $sourceRoot 'res\raw'
$imageRoot = Join-Path $sourceRoot 'assets\images'
$failures = [System.Collections.Generic.List[string]]::new()

function Add-Failure {
    param([string]$Message)
    $failures.Add($Message)
}

$buildText = Get-Content -Raw -LiteralPath $buildFile
$composeBuildPatterns = @(
    'libs\.plugins\.kotlin\.compose',
    'compose\s*=\s*true',
    'libs\.androidx\.activity\.compose',
    'libs\.androidx\.compose',
    'libs\.androidx\.ui',
    'libs\.androidx\.material3'
)

foreach ($pattern in $composeBuildPatterns) {
    if ($buildText -match $pattern) {
        Add-Failure "Compose build reference remains: $pattern"
    }
}

$sourceComposeRefs = @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -File -Include *.kt, *.java |
    Select-String -Pattern 'androidx\.compose|@Composable|setContent\s*\{')
foreach ($match in $sourceComposeRefs) {
    Add-Failure "Compose source reference remains: $($match.Path):$($match.LineNumber)"
}

$rulesText = Get-Content -Raw -LiteralPath $rulesFile
$broadKeepPattern = '(?m)^\s*-keep\s+(?:class|interface)\s+(?:androidx|kotlin|kotlinx|com\.google\.android\.material|okhttp3|com\.google\.gson|com\.bumptech\.glide|com\.airbnb\.lottie)\.\*\*\s*\{\s*\*;\s*\}'
$broadKeepMatches = [regex]::Matches($rulesText, $broadKeepPattern)
foreach ($match in $broadKeepMatches) {
    Add-Failure "Broad R8 keep rule remains: $($match.Value.Trim())"
}

$bindingKeepPattern = '(?ms)^\s*-keepclassmembers,allowoptimization\s+class\s+com\.cleanphone\.freespace\.utility\.databinding\.\*\*\s*\{.*?inflate\(android\.view\.LayoutInflater\).*?\}'
if ($rulesText -notmatch $bindingKeepPattern) {
    Add-Failure 'ViewBinding inflate methods are not protected from R8 member-name obfuscation.'
}
$obfuscatedBindingMemberPattern = '(?m)^\s*-keepclassmembers[^\r\n]*allowobfuscation[^\r\n]*\s+class\s+com\.cleanphone\.freespace\.utility\.databinding\.\*\*'
if ($rulesText -match $obfuscatedBindingMemberPattern) {
    Add-Failure 'ViewBinding reflective members incorrectly allow obfuscation.'
}

$imageFiles = @(Get-ChildItem -LiteralPath $imageRoot -File -Filter '*.png')
$duplicateGroups = @($imageFiles |
    ForEach-Object {
        [pscustomobject]@{
            Name = $_.Name
            Hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
        }
    } |
    Group-Object Hash |
    Where-Object Count -gt 1)

foreach ($group in $duplicateGroups) {
    Add-Failure "Duplicate Lottie PNGs remain: $($group.Group.Name -join ', ')"
}

$jsonFiles = @(Get-ChildItem -LiteralPath $rawRoot -File -Filter '*.json')
foreach ($jsonFile in $jsonFiles) {
    try {
        $composition = Get-Content -Raw -LiteralPath $jsonFile.FullName | ConvertFrom-Json
    }
    catch {
        Add-Failure "Invalid Lottie JSON: $($jsonFile.Name): $($_.Exception.Message)"
        continue
    }

    foreach ($asset in @($composition.assets | Where-Object { $_.p })) {
        $imagePath = Join-Path $imageRoot ([string]$asset.p)
        if (-not (Test-Path -LiteralPath $imagePath -PathType Leaf)) {
            Add-Failure "Missing Lottie image: $($jsonFile.Name) -> $($asset.p)"
        }
    }
}

if ($failures.Count -gt 0) {
    Write-Host "APK optimization verification failed ($($failures.Count) issue(s)):" -ForegroundColor Red
    foreach ($failure in $failures) {
        Write-Host " - $failure" -ForegroundColor Red
    }
    exit 1
}

Write-Host "APK optimization verification passed." -ForegroundColor Green
Write-Host "Checked $($jsonFiles.Count) Lottie JSON files and $($imageFiles.Count) PNG files."
