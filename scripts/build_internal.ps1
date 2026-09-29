$ErrorActionPreference = "Stop"

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java 17 is required. Install JDK 17 and make sure java is on PATH."
}

if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
    Write-Warning "ANDROID_HOME/ANDROID_SDK_ROOT is not set. Android Studio can still provide the SDK if Gradle detects it through local.properties."
}

Write-Host "Building Re:Fit hardened internal APK..."
& .\gradlew.bat --no-daemon :app:assembleInternal
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed."
}

$apk = Join-Path $PSScriptRoot "..\app\build\outputs\apk\internal\app-internal.apk"
$apk = [System.IO.Path]::GetFullPath($apk)

if (-not (Test-Path $apk)) {
    throw "Build finished but app-internal.apk was not found."
}

$hash = (Get-FileHash -Path $apk -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host ""
Write-Host "APK: $apk"
Write-Host "SHA-256: $hash"
