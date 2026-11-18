[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$node = Get-Command node -ErrorAction Stop
$script = Join-Path $PSScriptRoot 'deduplicate-lottie-assets.mjs'

& $node.Source $script
if ($LASTEXITCODE -ne 0) {
    throw "Lottie deduplication failed with exit code $LASTEXITCODE"
}
