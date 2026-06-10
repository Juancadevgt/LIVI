# scripts\generar-keystore.ps1
# Crea el keystore release UNA SOLA VEZ en la vida del proyecto.
# Despues no se vuelve a ejecutar - el mismo keystore se reutiliza siempre.
#
# IMPORTANTE: si pierdes este keystore o las contrasenas, no podras
# actualizar la app en celulares que ya tengan una version anterior.
# Tendrias que desinstalar e instalar nuevo (perdiendo datos/configuracion).
#
# Guarda copias del .jks en: OneDrive corporativo, 1Password, Azure Key Vault,
# o donde sea seguro y respaldado.

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$keystore = Join-Path $projectRoot "livi-release.jks"

if (Test-Path $keystore) {
    Write-Host "El keystore ya existe en: $keystore" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "NO lo regeneres. Si lo borras y haces uno nuevo,"
    Write-Host "los celulares que tengan LIVI instalada no podran actualizarse"
    Write-Host "porque la firma sera distinta."
    Write-Host ""
    Write-Host "Si necesitas las contrasenas, las pusiste tu cuando lo creaste."
    exit 0
}

. "$PSScriptRoot\setup-env.ps1"

$keytool = "$env:JAVA_HOME\bin\keytool.exe"
if (-not (Test-Path $keytool)) {
    Write-Error "No encuentro keytool en $keytool"
    exit 1
}

Write-Host ""
Write-Host "===== GENERANDO KEYSTORE DE RELEASE PARA LIVI =====" -ForegroundColor Cyan
Write-Host ""
Write-Host "Te va a pedir varios datos. Te recomiendo:"
Write-Host "  - Password del keystore: una contrasena fuerte, ANOTALA"
Write-Host "  - Password del alias: PUEDE ser la misma o distinta"
Write-Host "  - Datos del DN (nombre, organizacion): pon datos reales corporativos"
Write-Host "  - Validity: ya esta en 10000 dias (~27 anos, suficiente)"
Write-Host ""

& $keytool -genkey -v `
    -keystore $keystore `
    -alias livi `
    -keyalg RSA `
    -keysize 2048 `
    -validity 10000

if ($LASTEXITCODE -ne 0) {
    Write-Error "Falló keytool."
    exit 1
}

Write-Host ""
Write-Host "===== KEYSTORE GENERADO =====" -ForegroundColor Green
Write-Host "Archivo: $keystore"
Write-Host ""
Write-Host "PROXIMOS PASOS OBLIGATORIOS:" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. Copia el archivo keystore.properties.example a keystore.properties"
Write-Host "   y rellena con las contrasenas que pusiste arriba:"
Write-Host ""
Write-Host "      cp keystore.properties.example keystore.properties"
Write-Host "      # luego editar keystore.properties con tus contrasenas reales"
Write-Host ""
Write-Host "2. RESPALDA el archivo livi-release.jks en un lugar seguro:" -ForegroundColor Red
Write-Host "   - OneDrive corporativo (cifrado)"
Write-Host "   - 1Password / Azure Key Vault"
Write-Host "   - Una USB en cofre fisico"
Write-Host ""
Write-Host "3. NUNCA pongas este .jks en git. .gitignore ya lo excluye."
Write-Host ""
Write-Host "4. Ahora ya puedes compilar release: .\scripts\build-release.ps1"
