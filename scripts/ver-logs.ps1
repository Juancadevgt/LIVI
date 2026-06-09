# scripts\ver-logs.ps1
# Muestra en vivo los logs de LIVI desde el celular conectado por USB.
# Requiere depuración USB activada y cable conectado.

. "$PSScriptRoot\setup-env.ps1"

$devices = adb devices | Select-String -Pattern "device$" | Where-Object { $_ -notmatch "List of" }
if (-not $devices) {
    Write-Host "No hay dispositivo conectado." -ForegroundColor Yellow
    Write-Host "Conecta el celular por cable USB con depuración USB activada."
    exit 1
}

Write-Host "Limpiando log..." -ForegroundColor Cyan
adb logcat -c

Write-Host "Logs en vivo (Ctrl+C para detener)" -ForegroundColor Green
Write-Host "Filtrando: LiviA11y, LiviActionExecutor, AndroidRuntime errors"
Write-Host ""

adb logcat -s LiviA11y:V LiviActionExecutor:V AndroidRuntime:E `
    --format="time" `
    | ForEach-Object {
        if ($_ -match " E ") { Write-Host $_ -ForegroundColor Red }
        elseif ($_ -match " W ") { Write-Host $_ -ForegroundColor Yellow }
        elseif ($_ -match " I ") { Write-Host $_ -ForegroundColor Cyan }
        else { Write-Host $_ }
    }
