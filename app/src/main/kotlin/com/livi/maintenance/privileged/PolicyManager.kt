package com.livi.maintenance.privileged

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.getSystemService

/**
 * Wrapper sobre DevicePolicyManager que expone solo lo que LIVI usa.
 * Todas las operaciones devuelven false si LIVI no es Device Owner —
 * el caller debe revisar isDeviceOwner() antes y/o tener un fallback.
 */
class PolicyManager(private val context: Context) {

    private val dpm: DevicePolicyManager? = context.getSystemService()
    private val admin = DeviceOwnerReceiver.componentName(context)

    fun isDeviceOwner(): Boolean = try {
        dpm?.isDeviceOwnerApp(context.packageName) == true
    } catch (_: Exception) { false }

    fun isProfileOwner(): Boolean = try {
        dpm?.isProfileOwnerApp(context.packageName) == true
    } catch (_: Exception) { false }

    fun isAdminActive(): Boolean = try {
        dpm?.isAdminActive(admin) == true
    } catch (_: Exception) { false }

    /**
     * Activa o desactiva el modo avión a nivel de sistema. Solo funciona si LIVI
     * es Device Owner — el cambio dispara los handlers del ConnectivityService y
     * apaga radios reales (a diferencia de Settings.Global directo que solo cambia
     * el icono).
     */
    fun setAirplaneMode(enable: Boolean): Boolean {
        if (!isDeviceOwner() || dpm == null) return false
        return try {
            dpm.setGlobalSetting(admin, Settings.Global.AIRPLANE_MODE_ON, if (enable) "1" else "0")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "setAirplaneMode($enable) falló", t)
            false
        }
    }

    /**
     * Desactiva el lock screen temporalmente. Útil para que LIVI ejecute tareas
     * de Accessibility con el celular bloqueado. Solo Device Owner.
     */
    fun setKeyguardDisabled(disabled: Boolean): Boolean {
        if (!isDeviceOwner() || dpm == null) return false
        return try {
            dpm.setKeyguardDisabled(admin, disabled)
        } catch (t: Throwable) {
            Log.e(TAG, "setKeyguardDisabled($disabled) falló", t)
            false
        }
    }

    /**
     * Reinicia el dispositivo. Solo Device Owner. Android 7+.
     */
    fun reboot(): Boolean {
        if (!isDeviceOwner() || dpm == null) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            dpm.reboot(admin)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "reboot() falló", t)
            false
        }
    }

    companion object { private const val TAG = "LiviPolicyMgr" }
}
