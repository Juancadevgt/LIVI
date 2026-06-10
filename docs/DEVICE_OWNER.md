# LIVI como Device Owner

LIVI incluye soporte opcional para ser **Device Owner**. Cuando esta capa está activa,
LIVI ejecuta sus tareas con APIs privilegiadas del sistema, sin necesidad de UI,
y funciona aunque el celular esté bloqueado y con pantalla apagada.

Si LIVI no es Device Owner, sigue funcionando con AccessibilityService — solo requiere
pantalla encendida.

---

## ¿Qué desbloquea Device Owner?

| Acción | Sin Device Owner | Con Device Owner |
|---|---|---|
| Modo avión | Simula tap en Quick Settings (necesita pantalla encendida) | `DevicePolicyManager.setGlobalSetting` directo — sin UI, funciona bloqueado |
| Borrar caché | Accessibility navega Ajustes (pantalla encendida) | Accessibility navega Ajustes pero LIVI desactiva el keyguard antes con `setKeyguardDisabled(true)` y lo reactiva al terminar |
| Reiniciar dispositivo | ❌ No posible | ✅ `dpm.reboot()` |
| Configurar políticas avanzadas | ❌ | ✅ |

---

## Activar Device Owner

Device Owner solo se puede establecer en un dispositivo **sin cuentas configuradas**
(recién reseteado de fábrica o nuevo). Hay dos rutas:

### Ruta A — Vía Intune (producción)

Para celulares corporativos administrados por IT:

1. IT configura un **perfil de aprovisionamiento Android Enterprise Fully Managed**
   en Microsoft Intune.
2. El usuario final enrola el dispositivo siguiendo el flujo de Intune
   (escaneo QR o token DPC).
3. IT publica LIVI:
   - **Opción 1**: Subir el `.aab` firmado a **Managed Google Play** (recomendado).
   - **Opción 2**: Subir como **Line-of-Business app** en Intune (`.apk`).
4. IT asigna LIVI al grupo de usuarios y marca la app como **Device Owner DPC**
   en la política del grupo.
5. Al enrolar, LIVI se instala automáticamente como Device Owner — sin reseteo
   manual ni comandos ADB.

### Ruta B — Vía ADB (pruebas/desarrollo)

Para validar Device Owner en un celular de pruebas:

```powershell
# 1. Resetear el celular a fábrica
# 2. Durante la configuración inicial, OMITIR la cuenta Google (saltarlo)
# 3. Conectar el cable USB y activar depuración USB
# 4. Instalar LIVI:
adb install app-debug.apk

# 5. Establecer Device Owner:
adb shell dpm set-device-owner com.livi.maintenance/.privileged.DeviceOwnerReceiver

# 6. (Opcional) Verificar:
adb shell dumpsys device_policy | grep -A 3 "Device Owner"
```

**Errores comunes:**
- `Not allowed to set the device owner because there are already several users on the device`
  → Hay una cuenta Google o usuarios configurados. Resetear de nuevo.
- `Trying to set the device owner, but device owner is already set`
  → Ya hay otra app como Device Owner. Resetear primero.
- `Trying to set the device owner, but the user is not system user`
  → Estás en un usuario secundario. Cambiar al usuario primario.

---

## Verificar el estado desde la app

LIVI muestra el estado de Device Owner en su tarjeta de permisos:

- Si LIVI **no es Device Owner**: la tarjeta solo muestra "Servicio de Accesibilidad".
- Si LIVI **es Device Owner**: aparece adicionalmente "Device Owner — Activo - funciona bloqueado".

Internamente la app llama a `PolicyManager.isDeviceOwner()` que pregunta al sistema.

---

## Desactivar Device Owner

Una app Device Owner **no se puede desinstalar** ni desactivar como administrador
desde Ajustes — es una protección del sistema. Las únicas formas de quitarla son:

- **Reset de fábrica** del dispositivo.
- **`adb shell dpm remove-active-admin com.livi.maintenance/.privileged.DeviceOwnerReceiver`**
  (solo funciona si LIVI tiene `clearDeviceOwnerApp` autorizado — no es nuestro caso).

Es decisión consciente: queremos que IT mantenga control sobre LIVI en dispositivos
corporativos, no que el usuario final pueda desactivarla.

---

## Para IT — Checklist de enrolamiento Intune

- [ ] Crear cuenta Managed Google Play vinculada a Intune.
- [ ] Subir LIVI `.aab` firmado a Managed Google Play (o `.apk` como LOB).
- [ ] Configurar **Android Enterprise** > **Enrollment profiles** > **Fully Managed**.
- [ ] Asignar LIVI como **app requerida** al grupo de usuarios objetivo.
- [ ] En la política del grupo, marcar LIVI como **Device Owner DPC**.
- [ ] Distribuir el QR/token de enrolamiento a los usuarios finales.
- [ ] Validar en un dispositivo piloto antes de roll-out completo.
