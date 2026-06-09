# scripts\instalar.ps1
# Compila + distribuye/instala el APK con la opción que prefieras.
#
# Ejemplos:
#   .\scripts\instalar.ps1 -Adb              # Compila e instala vía cable USB (rápido para desarrollo)
#   .\scripts\instalar.ps1 -Serve            # Compila y sirve en http://IP:8000 con código QR
#   .\scripts\instalar.ps1 -OneDrive         # Compila y copia a tu OneDrive
#   .\scripts\instalar.ps1 -Release -OneDrive # Bundle firmado a OneDrive
#   .\scripts\instalar.ps1                   # Solo compila (lo encuentras en .\dist\)

param(
    [switch]$Adb,
    [switch]$Serve,
    [switch]$OneDrive,
    [switch]$Release,
    [int]$ServePort = 8000,
    [string]$OneDrivePath = "$env:OneDrive\LIVI"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot

# 1) Compilar
$apk = & "$PSScriptRoot\compilar.ps1" @(if ($Release) { "-Release" })
$apk = ($apk | Select-Object -Last 1).ToString()
if (-not (Test-Path $apk)) { throw "No se encontró el APK compilado: $apk" }

# 2) Distribuir según opción
$didSomething = $false

if ($Adb) {
    $didSomething = $true
    . "$PSScriptRoot\setup-env.ps1"
    Write-Host ""
    Write-Host "── Instalando vía ADB ──" -ForegroundColor Cyan
    $devices = (adb devices | Select-Object -Skip 1) -match '\tdevice$'
    if (-not $devices) {
        Write-Warning "No hay dispositivo conectado por USB."
        Write-Host "  Conecta el celular con depuración USB activada y vuelve a intentar."
        Write-Host "  Verificar con: adb devices"
    } else {
        adb install -r $apk
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Instalado en el dispositivo." -ForegroundColor Green
            Write-Host "Abriendo la app..."
            adb shell am start -n com.livi.maintenance/.MainActivity
        }
    }
}

if ($OneDrive) {
    $didSomething = $true
    if (-not $env:OneDrive) {
        Write-Warning "Variable OneDrive no definida; salta este paso."
    } else {
        if (-not (Test-Path $OneDrivePath)) { New-Item -ItemType Directory -Path $OneDrivePath -Force | Out-Null }
        $dst = Join-Path $OneDrivePath (Split-Path $apk -Leaf)
        Copy-Item $apk $dst -Force
        Write-Host ""
        Write-Host "── Copiado a OneDrive ──" -ForegroundColor Cyan
        Write-Host "  $dst"
        Write-Host "  Abre OneDrive en el celular y toca el archivo para instalar."
    }
}

if ($Serve) {
    $didSomething = $true
    $ip = (Get-NetIPAddress -AddressFamily IPv4 -PrefixOrigin Dhcp -ErrorAction SilentlyContinue |
           Select-Object -First 1 -ExpandProperty IPAddress)
    if (-not $ip) {
        $ip = (Get-NetIPAddress -AddressFamily IPv4 |
               Where-Object { $_.IPAddress -notmatch '^(127|169)\.' } |
               Select-Object -First 1 -ExpandProperty IPAddress)
    }
    $serveDir = Split-Path $apk -Parent
    $url = "http://${ip}:${ServePort}/" + (Split-Path $apk -Leaf)
    $qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + [Uri]::EscapeDataString($url)
    Write-Host ""
    Write-Host "── Servidor HTTP local ──" -ForegroundColor Cyan
    Write-Host "  URL:  $url"
    Write-Host "  QR:   $qrUrl  (ábrelo en el navegador de tu PC y escanéalo)"
    Write-Host ""
    Write-Host "  En el celular: abre $url en el navegador, descarga y toca el archivo."
    Write-Host "  Asegúrate que celular y PC están en la misma red Wi-Fi."
    Write-Host "  Para detener el servidor: Ctrl+C"
    Write-Host ""
    Start-Process "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=$([Uri]::EscapeDataString($url))"
    # Servidor simple en PowerShell
    Push-Location $serveDir
    try {
        $listener = New-Object System.Net.HttpListener
        $listener.Prefixes.Add("http://+:$ServePort/")
        $listener.Start()
        Write-Host "Sirviendo en puerto $ServePort. Esperando descarga..."
        while ($listener.IsListening) {
            $ctx = $listener.GetContext()
            $filename = [IO.Path]::GetFileName($ctx.Request.Url.LocalPath)
            $filepath = Join-Path $serveDir $filename
            if (Test-Path $filepath) {
                $bytes = [IO.File]::ReadAllBytes($filepath)
                $ctx.Response.ContentType = "application/vnd.android.package-archive"
                $ctx.Response.Headers.Add("Content-Disposition", "attachment; filename=`"$filename`"")
                $ctx.Response.ContentLength64 = $bytes.Length
                $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
                Write-Host "  Servido: $filename ($([Math]::Round($bytes.Length / 1MB, 2)) MB)" -ForegroundColor Green
            } else {
                $ctx.Response.StatusCode = 404
            }
            $ctx.Response.Close()
        }
    } finally {
        if ($listener.IsListening) { $listener.Stop() }
        Pop-Location
    }
}

if (-not $didSomething) {
    Write-Host ""
    Write-Host "APK listo en: $apk" -ForegroundColor Green
    Write-Host "Opciones para enviarlo al celular:"
    Write-Host "  - WhatsApp / Telegram / correo: adjunta el APK desde Explorador"
    Write-Host "  - .\scripts\instalar.ps1 -Adb       (cable USB)"
    Write-Host "  - .\scripts\instalar.ps1 -Serve     (Wi-Fi + QR)"
    Write-Host "  - .\scripts\instalar.ps1 -OneDrive  (sube a OneDrive)"
}
