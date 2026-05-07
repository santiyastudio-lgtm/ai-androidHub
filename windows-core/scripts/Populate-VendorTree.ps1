param(
    [switch]$SkipLocalCopy
)

$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$lockPath = Join-Path $root "VENDOR_LOCK.json"
$thirdPartyRoot = Join-Path $root "third_party"

if (-not (Test-Path $lockPath)) {
    throw "VENDOR_LOCK.json not found at $lockPath"
}

New-Item -ItemType Directory -Force -Path $thirdPartyRoot | Out-Null
$vendorLock = Get-Content -LiteralPath $lockPath -Raw | ConvertFrom-Json

foreach ($entry in $vendorLock.entries) {
    if ($entry.status -eq "planned_vendor") {
        $destination = Join-Path $thirdPartyRoot $entry.name
        if (-not (Test-Path (Join-Path $destination ".git"))) {
            if (Test-Path $destination) {
                throw "Destination exists but is not a git checkout: $destination"
            }
            git clone --filter=blob:none $entry.url $destination
        }

        git -C $destination fetch --depth 1 origin $entry.pinnedCommit
        git -C $destination checkout --detach $entry.pinnedCommit
        Write-Output ("vendored " + $entry.name + " @ " + $entry.pinnedCommit)
        continue
    }

    if ($entry.status -eq "local_source" -and -not $SkipLocalCopy) {
        $localCpp = Join-Path $root "..\neuron-packet\src\main\cpp"
        $destination = Join-Path $thirdPartyRoot ($entry.name + "-src")
        if (-not (Test-Path $localCpp)) {
            Write-Warning ("local source missing: " + $localCpp)
            continue
        }
        New-Item -ItemType Directory -Force -Path $destination | Out-Null
        Copy-Item -Path (Join-Path $localCpp "*") -Destination $destination -Recurse -Force
        Write-Output ("copied local source " + $entry.name + " -> " + $destination)
    }
}

Write-Output "vendor population completed"
