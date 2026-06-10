package com.livi.maintenance.privileged

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Punto de entrada para que el sistema reconozca a LIVI como Device Admin / Device Owner.
 * Se activa cuando IT enrola el dispositivo via Intune Android Enterprise Fully Managed,
 * o cuando se ejecuta `adb shell dpm set-device-owner com.livi.maintenance/.privileged.DeviceOwnerReceiver`
 * en un celular reseteado sin cuenta Google configurada.
 */
class DeviceOwnerReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "LIVI activada como Device Admin")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "LIVI desactivada como Device Admin")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Desactivar LIVI como administrador deshabilitará las tareas programadas " +
                "que requieren ejecutarse con el celular bloqueado."
    }

    companion object {
        private const val TAG = "LiviDeviceOwner"

        fun componentName(context: Context): ComponentName =
            ComponentName(context, DeviceOwnerReceiver::class.java)
    }
}
