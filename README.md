# LIVI

App Android (Kotlin + Compose) para automatizar mantenimiento en dispositivos:
borrar caché y datos de Power Apps y Teams, y alternar Modo Avión en horarios programados.

## Estructura

```
LIVI/
├── app/                            Código de la app Android
│   ├── src/main/kotlin/com/livi/maintenance/
│   │   ├── LiviApp.kt              Application (Room + Scheduler + canal de notificación)
│   │   ├── MainActivity.kt
│   │   ├── accessibility/          AccessibilityService que automatiza Ajustes
│   │   ├── actions/                ActionExecutor: borrar caché/datos, modo avión
│   │   ├── data/                   Room (TaskEntity / Dao / Database / Repository)
│   │   ├── scheduler/              WorkManager (LiviWorker + Scheduler + BootReceiver)
│   │   └── ui/                     Compose: MainScreen + AddTaskDialog
│   └── src/main/res/               Layouts/strings/themes
├── scripts/
│   ├── setup-env.ps1               Carga JAVA_HOME / ANDROID_HOME (dot-source)
│   ├── compilar.ps1                Compila debug o release a dist/
│   ├── instalar.ps1                Compila + distribuye (-Adb/-Serve/-OneDrive/-Release)
│   └── primera-vez.ps1             Otorga WRITE_SECURE_SETTINGS por ADB (una sola vez)
└── dist/                           APK/AAB generados
```

## Primera instalación en un celular

1. **Solo la primera vez en tu PC:** instala JDK 17 + cmdline-tools de Android
   (winget install Microsoft.OpenJDK.17, y descarga commandlinetools-win zip).
2. **En el celular:** habilita Opciones de desarrollador y Depuración USB.
3. **Compila e instala por cable:**
   ```powershell
   .\scripts\instalar.ps1 -Adb
   ```
4. **Otorga el permiso de Modo Avión (una sola vez):**
   ```powershell
   .\scripts\primera-vez.ps1
   ```
5. **En la app**, activa el "Servicio de Accesibilidad LIVI" en Ajustes → Accesibilidad.

## Actualizaciones después

Sin cable: `.\scripts\instalar.ps1 -OneDrive` o `.\scripts\instalar.ps1 -Serve`,
y reinstala desde el celular tocando el APK. El permiso del Modo Avión se conserva.

## Distribución a otros usuarios

- **Pruebas internas (no corporativas):** Manda el APK por OneDrive/WhatsApp.
- **Empresa con Intune:** IT publica el APK como Line-of-Business app y enrola el dispositivo
  como Device Owner para que las APIs de gestión funcionen sin necesidad de ADB ni Accessibility.
- **Google Play Store:** `.\scripts\instalar.ps1 -Release` genera el .aab firmado.

## Logs en vivo

```powershell
adb logcat -s LiviA11y:* LiviActionExecutor:* AndroidRuntime:E
```
