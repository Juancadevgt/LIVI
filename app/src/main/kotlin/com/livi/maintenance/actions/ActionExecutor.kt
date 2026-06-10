package com.livi.maintenance.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.livi.maintenance.accessibility.LiviAccessibilityService
import com.livi.maintenance.privileged.PolicyManager
import kotlinx.coroutines.delay

/**
 * Coordina la ejecución de las acciones. Tiene dos caminos:
 *
 * 1. **Device Owner** (cuando IT enrola via Intune): usa DevicePolicyManager
 *    para operaciones privilegiadas — funciona con celular bloqueado.
 *
 * 2. **Accessibility** (celular personal, sin Device Owner): automatiza la UI
 *    de Ajustes y Quick Settings — requiere pantalla encendida.
 *
 * `execute()` detecta automáticamente cuál usar.
 */
class ActionExecutor(
    private val context: Context,
    private val policyManager: PolicyManager = PolicyManager(context)
) {

    suspend fun execute(action: ActionType, targetPackage: String?): Result {
        return when (action) {
            ActionType.CLEAR_CACHE -> clearCache(targetPackage)
            ActionType.AIRPLANE_TOGGLE -> toggleAirplane()
        }
    }

    // -------- Modo avión --------

    private suspend fun toggleAirplane(): Result {
        return if (policyManager.isDeviceOwner()) {
            Log.i(TAG, "toggleAirplane: usando ruta Device Owner")
            toggleAirplaneAsDeviceOwner()
        } else {
            Log.i(TAG, "toggleAirplane: usando ruta Accessibility (no Device Owner)")
            toggleAirplaneViaQuickSettings()
        }
    }

    private suspend fun toggleAirplaneAsDeviceOwner(): Result {
        return try {
            val initialOn = airplaneModeOn() == 1
            policyManager.setAirplaneMode(!initialOn)
            delay(10_000)
            policyManager.setAirplaneMode(initialOn)
            Result.Success
        } catch (t: Throwable) {
            Log.e(TAG, "toggleAirplaneAsDeviceOwner falló", t)
            Result.Failure(t.message ?: "Error Device Owner")
        }
    }

    private suspend fun toggleAirplaneViaQuickSettings(): Result {
        val svc = LiviAccessibilityService.service()
            ?: return Result.Failure("Servicio de Accesibilidad no activado")
        return try {
            val initial = airplaneModeOn()
            Log.i(TAG, "===== toggleAirplane INICIO airplane_mode_on=$initial =====")

            Log.i(TAG, "Tap 1: abriendo Quick Settings...")
            LiviAccessibilityService.setMode(LiviAccessibilityService.Mode.AIRPLANE_TOGGLE_ON)
            svc.openQuickSettings()
            delay(4500)
            LiviAccessibilityService.reset()
            svc.goHome()
            delay(500)
            val afterTap1 = airplaneModeOn()
            Log.i(TAG, "Despues Tap 1: airplane_mode_on=$afterTap1 (esperado distinto de $initial)")

            Log.i(TAG, "Esperando 10 segundos antes del Tap 2...")
            delay(10_000)

            Log.i(TAG, "Tap 2: abriendo Quick Settings...")
            LiviAccessibilityService.setMode(LiviAccessibilityService.Mode.AIRPLANE_TOGGLE_OFF)
            svc.openQuickSettings()
            delay(4500)
            LiviAccessibilityService.reset()
            svc.goHome()
            val afterTap2 = airplaneModeOn()
            Log.i(TAG, "===== toggleAirplane FIN airplane_mode_on=$afterTap2 (esperado $initial) =====")

            if (afterTap2 == initial) Result.Success
            else Result.Failure("Estado final airplane=$afterTap2 != inicial $initial")
        } catch (t: Throwable) {
            Log.e(TAG, "Error en toggle modo avión", t)
            LiviAccessibilityService.reset()
            Result.Failure(t.message ?: "Error desconocido")
        }
    }

    // -------- Borrar caché --------

    private suspend fun clearCache(targetPackage: String?): Result {
        val pkg = requireNotNull(targetPackage) { "CLEAR_CACHE requiere targetPackage" }
        if (!isPackageInstalled(pkg)) {
            return Result.Failure("Paquete no instalado: $pkg")
        }
        if (!LiviAccessibilityService.isConnected()) {
            return Result.Failure("Servicio de Accesibilidad no activado")
        }

        // Si somos Device Owner, desactivamos el keyguard temporalmente para que
        // la automatización funcione con pantalla bloqueada.
        val deviceOwner = policyManager.isDeviceOwner()
        var keyguardWasDisabled = false
        if (deviceOwner) {
            keyguardWasDisabled = policyManager.setKeyguardDisabled(true)
            Log.i(TAG, "clearCache: Device Owner activo, keyguard desactivado=$keyguardWasDisabled")
        }

        try {
            LiviAccessibilityService.setMode(LiviAccessibilityService.Mode.CLEAR_CACHE)
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", pkg, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            delay(7000)
            LiviAccessibilityService.reset()
            return Result.Success
        } finally {
            if (keyguardWasDisabled) {
                policyManager.setKeyguardDisabled(false)
                Log.i(TAG, "clearCache: keyguard restaurado")
            }
        }
    }

    // -------- helpers --------

    private fun airplaneModeOn(): Int =
        try { Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, -1) }
        catch (_: Exception) { -1 }

    private fun isPackageInstalled(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: Exception) {
        false
    }

    sealed class Result {
        data object Success : Result()
        data class Failure(val message: String) : Result()
    }

    companion object { private const val TAG = "LiviActionExecutor" }
}
