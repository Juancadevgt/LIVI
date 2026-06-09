# scripts\compilar.ps1
# Compila el APK debug y deja una copia en dist\ con timestamp.
# Uso: .\scripts\compilar.ps1 [-Release]

param(
    [switch]$Release
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot

. "$PSScriptRoot\setup-env.ps1"

Set-Location $projectRoot

# Genera el wrapper si no existe
if (-not (Test-Path "$projectRoot\gradlew.bat")) {
    Write-Host "Generando gradle wrapper..." -ForegroundColor Cyan
    & "C:\Gradle\gradle-8.7\bin\gradle.bat" wrapper --gradle-version 8.7 --distribution-type bin
    if ($LASTEXITCODE -ne 0) { throw "Falló la generación del wrapper" }
}

$dist = "$projectRoot\dist"
if (-not (Test-Path $dist)) { New-Item -ItemType Directory -Path $dist | Out-Null }

$timestamp = (Get-Date -Format "yyyyMMdd-HHmm")
$version = (Get-Content "$projectRoot\app\build.gradle.kts" | Select-String 'versionName' | Select-Object -First 1) -replace '.*"([^"]+)".*','$1'

if ($Release) {
    Write-Host "Compilando RELEASE bundle..." -ForegroundColor Cyan
    & "$projectRoot\gradlew.bat" :app:bundleRelease
    if ($LASTEXITCODE -ne 0) { throw "Falló bundleRelease" }
    $src = "$projectRoot\app\build\outputs\bundle\release\app-release.aab"
    $dst = "$dist\LIVI-$version-$timestamp.aab"
} else {
    Write-Host "Compilando DEBUG APK..." -ForegroundColor Cyan
    & "$projectRoot\gradlew.bat" :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Falló assembleDebug" }
    $src = "$projectRoot\app\build\outputs\apk\debug\app-debug.apk"
    $dst = "$dist\LIVI-$version-$timestamp-debug.apk"
}

Copy-Item $src $dst -Force
Write-Host ""
Write-Host "OK: $dst" -ForegroundColor Green
$sizeMb = [Math]::Round((Get-Item $dst).Length / 1MB, 2)
Write-Host "Tamaño: $sizeMb MB"
$dst
