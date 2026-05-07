param(
    [string]$OutputPath = "",
    [switch]$JsonOnly,
    [switch]$FailOnMissingNativeToolchain
)

$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$vendorLockPath = Join-Path $root "VENDOR_LOCK.json"
$windowsKitRoot = "C:\Program Files (x86)\Windows Kits\10\Lib\10.0.22621.0"
$ucrtLibPath = Join-Path $windowsKitRoot "ucrt\x64\ucrt.lib"
$libUcrtLibPath = Join-Path $windowsKitRoot "ucrt\x64\libucrt.lib"
$shimMsvcrtPath = Join-Path $root "toolchain-shim\msvcrt.lib"
$msvcSearchRoots = @(
    "C:\Program Files\Microsoft Visual Studio",
    "C:\Program Files (x86)\Microsoft Visual Studio"
) | Where-Object { Test-Path $_ }

function Get-ToolInfo([string]$Name) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue | Select-Object -First 1
    return [ordered]@{
        found = $null -ne $command
        command = if ($command) { $command.Source } else { "" }
    }
}

function Find-Files([string[]]$Roots, [string[]]$Patterns) {
    $results = New-Object System.Collections.Generic.List[string]
    foreach ($rootPath in $Roots) {
        if (-not (Test-Path $rootPath)) {
            continue
        }
        foreach ($pattern in $Patterns) {
            Get-ChildItem -Path $rootPath -Filter $pattern -Recurse -ErrorAction SilentlyContinue | ForEach-Object {
                $results.Add($_.FullName)
            }
        }
    }
    return $results | Sort-Object -Unique
}

$tools = [ordered]@{}
foreach ($toolName in @("cargo", "rustc", "git", "javac", "jar", "jpackage", "cmake")) {
    $tools[$toolName] = Get-ToolInfo $toolName
}

$msvcrtLibs = @()
if (Test-Path $shimMsvcrtPath) {
    $msvcrtLibs += (Resolve-Path $shimMsvcrtPath).Path
}
$msvcrtLibs += Find-Files @($windowsKitRoot, "C:\Program Files", "C:\Program Files (x86)") @("msvcrt.lib", "msvcrt*.lib")
$msvcrtLibs = @($msvcrtLibs | Sort-Object -Unique)

$vcruntimeLibs = @(Find-Files $msvcSearchRoots @("vcruntime.lib", "vcruntime*.lib", "msvcp*.lib"))

$vendorEntries = @()
if (Test-Path $vendorLockPath) {
    $vendorLock = Get-Content -LiteralPath $vendorLockPath -Raw | ConvertFrom-Json
    foreach ($entry in $vendorLock.entries) {
        $vendorDir = Join-Path $root ("third_party\" + $entry.name)
        $localCopy = Join-Path $root ("third_party\" + $entry.name + "-src")
        $present = (Test-Path $vendorDir) -or (Test-Path $localCopy)
        $vendorEntries += [ordered]@{
            name = $entry.name
            status = $entry.status
            pinnedCommit = $entry.pinnedCommit
            present = $present
            expectedPath = if ($entry.status -eq "local_source") { $localCopy } else { $vendorDir }
        }
    }
}

$nativeLinkReady = $tools.cargo.found -and $tools.rustc.found -and (Test-Path $ucrtLibPath) -and ($msvcrtLibs.Count -gt 0) -and ($vcruntimeLibs.Count -gt 0)
$sourceCheckReady = $tools.cargo.found -and $tools.rustc.found
$hubBuildReady = $tools.javac.found -and $tools.jar.found -and $tools.jpackage.found

$report = [ordered]@{
    generatedAt = (Get-Date).ToString("o")
    root = $root
    tools = $tools
    libraries = [ordered]@{
        shimMsvcrtPath = $shimMsvcrtPath
        shimMsvcrtPresent = (Test-Path $shimMsvcrtPath)
        ucrtLibPath = $ucrtLibPath
        ucrtLibPresent = (Test-Path $ucrtLibPath)
        libUcrtLibPath = $libUcrtLibPath
        libUcrtLibPresent = (Test-Path $libUcrtLibPath)
        msvcrtLibs = @($msvcrtLibs)
        vcruntimeLibs = @($vcruntimeLibs)
    }
    vendors = $vendorEntries
    readiness = [ordered]@{
        sourceCheckReady = $sourceCheckReady
        nativeLinkReady = $nativeLinkReady
        hubBuildReady = $hubBuildReady
    }
}

if ($OutputPath -ne "") {
    $outputFile = if ([System.IO.Path]::IsPathRooted($OutputPath)) { $OutputPath } else { Join-Path $root $OutputPath }
    $outputDir = Split-Path -Path $outputFile -Parent
    if ($outputDir) {
        New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
    }
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $outputFile -Encoding UTF8
}

if (-not $JsonOnly) {
    Write-Output "windows-core preflight"
    Write-Output "root: $root"
    Write-Output "source-check-ready: $sourceCheckReady"
    Write-Output "native-link-ready: $nativeLinkReady"
    Write-Output "hub-build-ready: $hubBuildReady"
    Write-Output ("msvcrt libs: " + $msvcrtLibs.Count)
    Write-Output ("vcruntime libs: " + $vcruntimeLibs.Count)
    if ($OutputPath -ne "") {
        Write-Output ("report: " + $outputFile)
    }
}

if ($FailOnMissingNativeToolchain -and -not $nativeLinkReady) {
    exit 1
}

exit 0
