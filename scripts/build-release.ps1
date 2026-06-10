# scripts\build-release.ps1
# Compila el APK firmado de release listo para entregar a IT.
# Output: dist\LIVI-<version>-<timestamp>-release.apk

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot

. "$PSScriptRoot\setup-env.ps1"
Set-Location $projectRoot

# Verificar pre-requisitos
if (-not (Test-Path "$projectRoot\livi-release.jks")) {
    Write-Host "Falta livi-release.jks." -ForegroundColor Red
    Write-Host "Ejecuta primero: .\scripts\generar-keystore.ps1"
    exit 1
}
if (-not (Test-Path "$projectRoot\keystore.properties")) {
    Write-Host "Falta keystore.properties." -ForegroundColor Red
    Write-Host "Copia keystore.properties.example a keystore.properties y rellena las contrasenas."
    exit 1
}

Write-Host "===== Compilando APK firmado de RELEASE =====" -ForegroundColor Cyan

& "$projectRoot\gradlew.bat" :app:assembleRelease
if ($LASTEXITCODE -ne 0) {
    Write-Error "Falló la compilación de release."
    exit 1
}

$src = "$projectRoot\app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $src)) {
    Write-Error "No se encontró el APK firmado en $src"
    exit 1
}

# Copiar a dist\ con nombre amigable
$dist = "$projectRoot\dist"
if (-not (Test-Path $dist)) { New-Item -ItemType Directory -Path $dist | Out-Null }

$timestamp = (Get-Date -Format "yyyyMMdd-HHmm")
$version = (Select-String -Path "$projectRoot\app\build.gradle.kts" -Pattern 'versionName\s*=\s*"([^"]+)"' | Select-Object -First 1).Matches.Groups[1].Value
$dst = "$dist\LIVI-$version-$timestamp-release.apk"
Copy-Item $src $dst -Force

$sizeMb = [Math]::Round((Get-Item $dst).Length / 1MB, 2)

Write-Host ""
Write-Host "===== APK FIRMADO LISTO PARA IT =====" -ForegroundColor Green
Write-Host ""
Write-Host "  Ruta:  $dst"
Write-Host "  Tamano: $sizeMb MB"
Write-Host "  Version: $version"
Write-Host ""
Write-Host "PASOS:" -ForegroundColor Cyan
Write-Host "  1. Envia este archivo a IT (por correo, OneDrive corporativo, o SharePoint)."
Write-Host "  2. Junto con docs\PARA_IT.md (instrucciones detalladas de subida a Intune)."
Write-Host "  3. IT subira el APK a Intune como Line-of-Business app y asignara al grupo."
Write-Host ""
