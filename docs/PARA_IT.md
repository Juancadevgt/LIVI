# LIVI — Información para el equipo de IT

Documento que acompaña al APK `LIVI-x.y.z-release.apk` para su distribución
en dispositivos corporativos administrados por Microsoft Intune.

---

## 1. Resumen ejecutivo

**LIVI** es una app interna de mantenimiento Android que automatiza tres tareas
correctivas que actualmente los usuarios deben hacer manualmente cuando
**Microsoft Power Apps** y **Microsoft Teams** presentan errores:

1. **Borrar caché** de la app afectada (en horarios programados).
2. **Activar modo avión por 10 segundos** (reset de conexión de red).
3. **Próximamente**: detener apps, reiniciar dispositivo, otras tareas que IT solicite.

Estas tres acciones resuelven la mayoría de los tickets de soporte que recibimos
hoy ("Power Apps no carga", "Teams se queda en blanco", etc.).

---

## 2. Datos técnicos de la app

| Campo | Valor |
|---|---|
| **Package name** | `com.livi.maintenance` |
| **Application ID** | `com.livi.maintenance` |
| **Versión actual** | 0.1.0 |
| **Mínimo Android** | 7.0 (API 24) |
| **Target Android** | 14 (API 34) |
| **Firma** | Keystore corporativo (release) |
| **Arquitectura** | Universal (ARM + x86) |
| **Tamaño aprox** | ~12 MB |
| **Repositorio** | https://github.com/Juancadevgt/LIVI (privado) |

---

## 3. Permisos que solicita y justificación

| Permiso | Por qué LIVI lo necesita |
|---|---|
| `RECEIVE_BOOT_COMPLETED` | Re-programar las tareas automáticas tras reinicio del dispositivo |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Ejecutar tareas de mantenimiento en segundo plano de forma confiable |
| `POST_NOTIFICATIONS` | Mostrar resultado de ejecución al usuario |
| `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` | Disparar las tareas en el horario exacto configurado |
| `WAKE_LOCK` | Mantener el dispositivo activo durante una ejecución |
| `QUERY_ALL_PACKAGES` | Listar las apps instaladas para que el admin elija a cuáles aplicar limpieza |
| `WRITE_SECURE_SETTINGS` | Cambiar el estado de modo avión a nivel sistema. **Solo se otorga si el dispositivo es Device Owner** (Intune) o vía ADB en pruebas |
| `BIND_ACCESSIBILITY_SERVICE` | Automatizar UI de Ajustes para borrar caché sin interacción del usuario |
| `BIND_DEVICE_ADMIN` | Operar como Device Owner cuando Intune lo enrole con ese perfil |

**No** solicita: ubicación, contactos, cámara, micrófono, almacenamiento externo,
historial de navegación, ni nada relacionado con datos del usuario.

---

## 4. Datos personales y privacidad

- LIVI **no recolecta, almacena ni transmite** datos personales del usuario.
- Toda la configuración (tareas programadas, historial de ejecuciones) vive
  localmente en una base SQLite (`livi.db`) dentro del sandbox de la app.
- No tiene servidor backend. No hay telemetría externa.
- Cuando se publique en Play Store, se incluirá una política de privacidad
  reflejando exactamente esto.

---

## 5. Cómo subir LIVI a Intune (Line-of-Business app)

### 5.1. Prerrequisitos
- Cuenta de **Microsoft Intune** con permisos de App Manager.
- Dispositivos enrolados en Intune.
- El archivo `.apk` adjunto a este documento.

### 5.2. Pasos

1. Abrir el **Microsoft Endpoint Manager admin center**
   (https://endpoint.microsoft.com)

2. Ir a **Apps → All apps → Add**.

3. En "App type" seleccionar **Line-of-business app**.

4. Subir el archivo **`LIVI-x.y.z-release.apk`**.

5. Completar la info:
   - **Name**: LIVI Mantenimiento
   - **Description**: App interna que automatiza borrado de caché y reset de red
     para corregir errores en Power Apps y Teams.
   - **Publisher**: [Nombre de la empresa]
   - **App version**: 0.1.0
   - **Minimum OS**: Android 7.0
   - **Privacy URL**: (vacío por ahora, no aplica)

6. En **Assignments**, asignar a un **grupo piloto** de 5-10 usuarios primero:
   - Required: para que se instale automáticamente.
   - Available for enrolled devices: si quieren que el usuario decida.

7. Confirmar y esperar el sync de los dispositivos (~15 min).

### 5.3. Validación

En un dispositivo piloto, verificar:
- LIVI aparece como app instalada.
- Al abrirla, muestra la pantalla principal.
- En Ajustes → Accesibilidad, aparece el servicio "LIVI Mantenimiento".
- Si se enroló como **Device Owner**: la tarjeta de permisos de LIVI muestra
  "Device Owner — Activo - funciona bloqueado".

---

## 6. (Opcional) Configurar Device Owner para funcionalidad completa

Si IT enrola los dispositivos como **Android Enterprise Fully Managed**, LIVI
opera en modo privilegiado:
- Las tareas se ejecutan **con el celular bloqueado y pantalla apagada**.
- Modo avión usa la API privilegiada del sistema (no UI).
- Permite tareas futuras: reiniciar dispositivo, gestión avanzada.

Si NO es Device Owner, LIVI funciona perfecto pero requiere que la pantalla
esté **encendida** durante la ejecución (las acciones usan el servicio de
Accesibilidad de Android, que es una solución estándar).

La elección de "Device Owner sí/no" depende de la política corporativa de Intune.
Más detalles en `docs/DEVICE_OWNER.md` del repo.

---

## 7. Soporte y mantenimiento

- **Desarrollador**: Juan C. Dorantes (juancadevgt@gmail.com)
- **Repositorio (privado)**: https://github.com/Juancadevgt/LIVI
- **Versionado**: cada release sube `versionCode` y `versionName`. Las
  actualizaciones se distribuyen subiendo el nuevo `.apk` a Intune (sobrescribe
  la versión instalada manteniendo datos del usuario).
- **Reportar bugs**: crear issue en el repo o contactar al desarrollador.

---

## 8. Checklist final antes del despliegue

- [ ] APK descargado y verificado (firma correcta).
- [ ] Subido a Intune como LOB app.
- [ ] Asignado al grupo piloto.
- [ ] Validado en al menos 1 dispositivo de prueba.
- [ ] Confirmado que no hay alertas de seguridad en Intune.
- [ ] (Opcional) Configurado como Device Owner si la política lo permite.
- [ ] Después de 1 semana de piloto exitoso: ampliar al grupo completo.

---

*Última actualización: junio 2026*
