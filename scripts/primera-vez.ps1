# scripts\primera-vez.ps1
# Otorga el permiso WRITE_SECURE_SETTINGS necesario para alternar Modo Avión.
# Se hace UNA sola vez por celular (mientras LIVI siga instalada).
# Requiere que el celular esté conectado por USB con depuración activada.

. "$PSScriptRoot\setup-env.ps1"

Write-Host "Buscando dispositivos conectados..." -ForegroundColor Cyan
$devices = adb devices | Select-String -Pattern "device$" | Where-Object { $_ -notmatch "List of" }
if (-not $devices) {
    Write-Host ""
    Write-Host "No hay dispositivo conectado." -ForegroundColor Yellow
    Write-Host "Pasos:"
    Write-Host "  1. Habilita Opciones de desarrollador (toca 7 veces Número de compilación)"
    Write-Host "  2. Activa Depuración USB en Opciones de desarrollador"
    Write-Host "  3. Conecta el cable USB"
    Write-Host "  4. Acepta el diálogo 'Permitir depuración USB' en el celular"
    Write-Host "  5. Ejecuta de nuevo este script"
    exit 1
}
Write-Host "Dispositivo encontrado." -ForegroundColor Green

$pkg = "com.livi.maintenance"
$installed = adb shell pm list packages | Select-String "package:$pkg"
if (-not $installed) {
    Write-Host ""
    Write-Host "LIVI aún no está instalada en el celular." -ForegroundColor Yellow
    Write-Host "Primero ejecuta: .\scripts\instalar.ps1 -Adb"
    exit 1
}

Write-Host "Otorgando WRITE_SECURE_SETTINGS..." -ForegroundColor Cyan
adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS
if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "Listo. El Modo Avión 10s ya funciona en este celular." -ForegroundColor Green
    Write-Host "Este permiso se mantiene mientras no desinstales LIVI."
} else {
    Write-Host ""
    Write-Host "Falló al otorgar el permiso." -ForegroundColor Red
    Write-Host "En Samsung asegúrate de tener activado 'USB debugging (Security settings)' en Opciones de desarrollador."
}
