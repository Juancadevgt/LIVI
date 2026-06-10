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
        if (!LiviAccessibilityService.isConnected()) {
            return Result.Failure("Servicio de Accesibilidad no activado")
        }
        val svc = LiviAccessibilityService.service()
            ?: return Result.Failure("Servicio de Accesibilidad nulo")
        return try {
            val initial = airplaneModeOn()
            Log.i(TAG, "===== toggleAirplane INICIO airplane_mode_on=$initial =====")

            // ===== Tap 1: ACTIVAR =====
            Log.i(TAG, "Tap 1: abriendo Settings.AIRPLANE_MODE_SETTINGS...")
            LiviAccessibilityService.resetSuccess()
            LiviAccessibilityService.setMode(LiviAccessibilityService.Mode.AIRPLANE_TOGGLE_ON)
            openAirplaneSettings()
            delay(4500)  // tiempo para que cargue la pantalla y el service haga tap
            val tap1Success = LiviAccessibilityService.wasSuccessful()
            LiviAccessibilityService.reset()
            Log.i(TAG, "Tap 1 result: success=$tap1Success airplane=${airplaneModeOn()}")

            if (!tap1Success) {
                Log.w(TAG, "Tap 1 NO se ejecutó — switch no detectado")
                svc.goHome()
                return Result.Interrupted("No se pudo activar el modo avión (switch no encontrado)")
            }

            // SALIR de la pantalla tras activar
            Log.i(TAG, "Saliendo de la pantalla tras Tap 1...")
            svc.goHome()
            delay(800)

            // ===== ESPERA 10 SEGUNDOS =====
            Log.i(TAG, "Esperando 10 segundos en modo avión activo...")
            delay(10_000)

            // ===== Tap 2: DESACTIVAR =====
            Log.i(TAG, "Tap 2: re-abriendo Settings.AIRPLANE_MODE_SETTINGS...")
            LiviAccessibilityService.resetSuccess()
            LiviAccessibilityService.setMode(LiviAccessibilityService.Mode.AIRPLANE_TOGGLE_OFF)
            openAirplaneSettings()
            delay(4500)
            val tap2Success = LiviAccessibilityService.wasSuccessful()
            LiviAccessibilityService.reset()
            Log.i(TAG, "Tap 2 result: success=$tap2Success airplane=${airplaneModeOn()}")

            // SALIR de la pantalla tras desactivar
            Log.i(TAG, "Saliendo de la pantalla tras Tap 2...")
            svc.goHome()

            val afterTap2 = airplaneModeOn()
            Log.i(TAG, "===== toggleAirplane FIN airplane_mode_on=$afterTap2 (esperado $initial), tap2Success=$tap2Success =====")

            when {
                !tap2Success -> Result.Interrupted("No se pudo desactivar el modo avión (quedó activo)")
                afterTap2 != initial -> Result.Interrupted("Estado final airplane=$afterTap2 != inicial $initial")
                else -> Result.Success
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error en toggle modo avión", t)
            LiviAccessibilityService.reset()
            LiviAccessibilityService.service()?.goHome()
            Result.Failure(t.message ?: "Error desconocido")
        }
    }

    /**
     * Abre la pantalla de Ajustes específica del modo avión. Funciona desde
     * background (BroadcastReceiver tocado desde notificación) a diferencia de
     * GLOBAL_ACTION_QUICK_SETTINGS que Samsung restringe.
     */
    private fun openAirplaneSettings() {
        val intent = Intent("android.settings.AIRPLANE_MODE_SETTINGS").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (t: Throwable) {
            // Fallback: pantalla de redes inalámbricas
            Log.w(TAG, "ACTION_AIRPLANE_MODE_SETTINGS no soportado, usando WIRELESS_SETTINGS", t)
            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
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
