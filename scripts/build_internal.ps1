$ErrorActionPreference = "Stop"

function Get-JavaMajorVersion {
    $versionOutput = & java -version 2>&1 | Out-String
    if ($versionOutput -match '"([0-9]+)') {
        return [int]$Matches[1]
    }
    throw "Could not determine Java version."
}

function Get-AndroidSdkRoot {
    if ($env:ANDROID_SDK_ROOT) { return $env:ANDROID_SDK_ROOT }
    if ($env:ANDROID_HOME) { return $env:ANDROID_HOME }

    $localProperties = Join-Path $PSScriptRoot "..\local.properties"
    if (Test-Path $localProperties) {
        $line = Get-Content $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($line) {
            $sdk = $line.Substring("sdk.dir=".Length)
            $sdk = $sdk -replace '\\:', ':'
            $sdk = $sdk -replace '\\\\', '\'
            return $sdk
        }
    }

    throw "Android SDK not found. Set ANDROID_SDK_ROOT/ANDROID_HOME or configure local.properties."
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java 17 is required. Install JDK 17 and make sure java is on PATH."
}

$javaMajor = Get-JavaMajorVersion
if ($javaMajor -ne 17) {
    throw "Java 17 is required. Current major version: $javaMajor"
}

$sdkRoot = Get-AndroidSdkRoot
$buildToolsRoot = Join-Path $sdkRoot "build-tools"
if (-not (Test-Path $buildToolsRoot)) {
    throw "Android build-tools directory not found: $buildToolsRoot"
}

$aapt = Get-ChildItem $buildToolsRoot -Directory |
    Sort-Object Name -Descending |
    ForEach-Object { Join-Path $_.FullName "aapt.exe" } |
    Where-Object { Test-Path $_ } |
    Select-Object -First 1

if (-not $aapt) {
    throw "aapt.exe not found under Android SDK build-tools."
}

Write-Host "Building Re:Fit hardened internal APK..."
Push-Location (Join-Path $PSScriptRoot "..")
try {
    & .\gradlew.bat --no-daemon :app:assembleInternal
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed."
    }
} finally {
    Pop-Location
}

$apk = Join-Path $PSScriptRoot "..\app\build\outputs\apk\internal\app-internal.apk"
$apk = [System.IO.Path]::GetFullPath($apk)

if (-not (Test-Path $apk)) {
    throw "Build finished but app-internal.apk was not found."
}

Write-Host "Verifying APK hardening..."
$badging = & $aapt dump badging $apk | Out-String
$manifest = & $aapt dump xmltree $apk AndroidManifest.xml | Out-String
$permissions = & $aapt dump permissions $apk | Out-String

$checks = [ordered]@{
    "non-debuggable" = ($badging -notmatch "application-debuggable")
    "no Compose PreviewActivity" = ($manifest -notmatch "androidx\.compose\.ui\.tooling\.PreviewActivity")
    "allowBackup=false" = ($manifest -match "android:allowBackup.*0x0")
    "usesCleartextTraffic=false" = ($manifest -match "android:usesCleartextTraffic.*0x0")
    "no INTERNET permission" = ($permissions -notmatch "android\.permission\.INTERNET")
}

$failed = @()
foreach ($name in $checks.Keys) {
    if ($checks[$name]) {
        Write-Host "[PASS] $name"
    } else {
        Write-Host "[FAIL] $name"
        $failed += $name
    }
}

if ($failed.Count -gt 0) {
    throw "APK hardening verification failed: $($failed -join ', ')"
}

$hash = (Get-FileHash -Path $apk -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host ""
Write-Host "APK: $apk"
Write-Host "SHA-256: $hash"
