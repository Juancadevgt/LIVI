# scripts\setup-env.ps1
# Carga JAVA_HOME, ANDROID_HOME y PATH para esta sesión de PowerShell.
# Dot-sourcing requerido: . .\scripts\setup-env.ps1

$ErrorActionPreference = "Stop"

function Find-Jdk17 {
    $candidates = @(
        "$env:ProgramFiles\Microsoft\jdk-17*",
        "$env:ProgramFiles\Eclipse Adoptium\jdk-17*",
        "$env:ProgramFiles\Java\jdk-17*"
    )
    foreach ($pattern in $candidates) {
        $dir = Get-ChildItem -Path (Split-Path $pattern) -Directory -ErrorAction SilentlyContinue |
               Where-Object { $_.FullName -like $pattern } |
               Select-Object -First 1
        if ($dir) { return $dir.FullName }
    }
    return $null
}

$jdk = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { $env:JAVA_HOME } else { Find-Jdk17 }
if (-not $jdk) { throw "JDK 17 no encontrado. Instala con: winget install -e --id Microsoft.OpenJDK.17" }

$sdk = if ($env:ANDROID_HOME -and (Test-Path "$env:ANDROID_HOME\cmdline-tools\latest\bin")) { $env:ANDROID_HOME } else { "C:\Android\Sdk" }
if (-not (Test-Path "$sdk\cmdline-tools\latest\bin")) {
    throw "Android SDK no encontrado en $sdk. Reinstala cmdline-tools."
}

$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk

$add = @(
    "$jdk\bin",
    "$sdk\platform-tools",
    "$sdk\cmdline-tools\latest\bin",
    "$sdk\build-tools\34.0.0"
)
foreach ($p in $add) {
    if ($env:Path -notlike "*$p*") { $env:Path = "$p;$env:Path" }
}

Write-Host "JAVA_HOME = $env:JAVA_HOME" -ForegroundColor Green
Write-Host "ANDROID_HOME = $env:ANDROID_HOME" -ForegroundColor Green
