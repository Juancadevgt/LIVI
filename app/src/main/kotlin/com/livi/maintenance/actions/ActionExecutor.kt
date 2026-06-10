package com.livi.maintenance.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.livi.maintenance.accessibility.LiviAccessibilityService
import com.livi.maintenance.privileged.PolicyManager
import kotlinx.coroutines.delay

class ActionExecutor(
    private val context: Context,
    private val policyManager: PolicyManager = PolicyManager(context)
) {

    suspend fun execute(action: ActionType, targetPackage: String?): Result {
        // Resetear flag de éxito antes de cualquier intento
        LiviAccessibilityService.resetSuccess()
        return when (action) {
            ActionType.CLEAR_CACHE -> clearCache(targetPackage)
            ActionType.AIRPLANE_TOGGLE -> toggleAirplane()
        }
    }

    // -------- Modo avión --------

    private suspend fun toggleAirplane(): Result {
        return if (policyManager.isDeviceOwner()) {
            Log.i(TAG, "toggleAirplane: ruta Device Owner")
            toggleAirplaneAsDeviceOwner()
        } else {
            Log.i(TAG, "toggleAirplane: ruta Accessibility")
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
            val tap1Success = LiviAccessibilityService.wasSuccessful()
            LiviAccessibilityService.reset()
            svc.goHome()
            delay(500)
            val afterTap1 = airplaneModeOn()
            Log.i(TAG, "Despues Tap 1: airplane_mode_on=$afterTap1, success=$tap1Success")

            if (!tap1Success) {
                Log.w(TAG, "Tap 1 NO se ejecutó — usuario probablemente canceló")
                return Result.Interrupted("Cancelado antes del primer tap")
            }

            Log.i(TAG, "Esperando 10 segundos antes del Tap 2...")
            delay(10_000)

            // Resetear flag para detectar el segundo tap
            LiviAccessibilityService.resetSuccess()
            Log.i(TAG, "Tap 2: abriendo Quick Settings...")
            LiviAccessibilityService.setMode(LiviAccessibilityService.Mode.AIRPLANE_TOGGLE_OFF)
            svc.openQuickSettings()
            delay(4500)
            val tap2Success = LiviAccessibilityService.wasSuccessful()
            LiviAccessibilityService.reset()
            svc.goHome()
            val afterTap2 = airplaneModeOn()
            Log.i(TAG, "===== toggleAirplane FIN airplane_mode_on=$afterTap2 (esperado $initial), tap2Success=$tap2Success =====")

            when {
                !tap2Success -> Result.Interrupted("Cancelado antes del segundo tap (modo avión quedó activo)")
                afterTap2 != initial -> Result.Interrupted("Estado final airplane=$afterTap2 != inicial $initial")
                else -> Result.Success
            }
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

        val deviceOwner = policyManager.isDeviceOwner()
        var keyguardWasDisabled = false
        if (deviceOwner) {
            keyguardWasDisabled = policyManager.setKeyguardDisabled(true)
            Log.i(TAG, "clearCache: Device Owner, keyguard desactivado=$keyguardWasDisabled")
        }

        try {
            LiviAccessibilityService.setMode(LiviAccessibilityService.Mode.CLEAR_CACHE)
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", pkg, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            delay(12_000)
            val success = LiviAccessibilityService.wasSuccessful()
            LiviAccessibilityService.reset()

            return if (success) {
                Log.i(TAG, "clearCache: tap exitoso")
                Result.Success
            } else {
                Log.w(TAG, "clearCache: timeout sin tap exitoso — usuario probablemente canceló")
                Result.Interrupted("Cancelado por el usuario antes de tocar Borrar caché")
            }
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
        /** La tarea se ejecutó exitosamente. */
        data object Success : Result()

        /** Error técnico real (paquete no instalado, accesibilidad no activa, etc.). NO se reintenta. */
        data class Failure(val message: String) : Result()

        /**
         * El usuario canceló durante la ejecución (presionó Home/Back o cambió de app).
         * Se vuelve a marcar como pendiente para reintentar al próximo desbloqueo.
         */
        data class Interrupted(val message: String) : Result()
    }

    companion object { private const val TAG = "LiviActionExecutor" }
}
