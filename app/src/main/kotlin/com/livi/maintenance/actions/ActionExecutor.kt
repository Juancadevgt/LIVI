package com.livi.maintenance.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.livi.maintenance.accessibility.LiviAccessibilityService
import kotlinx.coroutines.delay

class ActionExecutor(private val context: Context) {

    suspend fun execute(action: ActionType, targetPackage: String?): Result {
        return when (action) {
            ActionType.CLEAR_CACHE -> openAppDetailsAndClear(
                requireNotNull(targetPackage) { "CLEAR_CACHE requiere targetPackage" },
                LiviAccessibilityService.Mode.CLEAR_CACHE
            )
            ActionType.CLEAR_DATA -> openAppDetailsAndClear(
                requireNotNull(targetPackage) { "CLEAR_DATA requiere targetPackage" },
                LiviAccessibilityService.Mode.CLEAR_DATA
            )
            ActionType.AIRPLANE_TOGGLE -> toggleAirplaneViaQuickSettings()
        }
    }

    private suspend fun openAppDetailsAndClear(
        targetPackage: String,
        mode: LiviAccessibilityService.Mode
    ): Result {
        if (!LiviAccessibilityService.isConnected()) {
            return Result.Failure("Servicio de Accesibilidad no activado")
        }
        if (!isPackageInstalled(targetPackage)) {
            return Result.Failure("Paquete no instalado: $targetPackage")
        }
        LiviAccessibilityService.setMode(mode)
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", targetPackage, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        delay(7000)
        LiviAccessibilityService.reset()
        return Result.Success
    }

    /**
     * En Android 14+ sin Device Owner ni root, escribir Settings.Global.AIRPLANE_MODE_ON
     * cambia el icono pero NO apaga las radios — el sistema ignora el cambio. La única
     * forma confiable es abrir el panel de Quick Settings y simular el tap del usuario
     * sobre el tile "Modo avión", que el sistema acepta como acción humana.
     */
    private suspend fun toggleAirplaneViaQuickSettings(): Result {
        val svc = LiviAccessibilityService.service()
            ?: return Result.Failure("Servicio de Accesibilidad no activado")
        return try {
            val initial = airplaneModeOn()
            Log.i(TAG, "===== toggleAirplane INICIO airplane_mode_on=$initial =====")

            // Tap 1: cambiar al opuesto del estado inicial
            Log.i(TAG, "Tap 1: abriendo Quick Settings...")
            LiviAccessibilityService.setMode(LiviAccessibilityService.Mode.AIRPLANE_TOGGLE_ON)
            svc.openQuickSettings()
            delay(4500)
            LiviAccessibilityService.reset()
            // Cerrar el panel explícitamente por si quedó abierto
            svc.goHome()
            delay(500)
            val afterTap1 = airplaneModeOn()
            Log.i(TAG, "Despues Tap 1: airplane_mode_on=$afterTap1 (esperado distinto de $initial)")

            // Espera 10s
            Log.i(TAG, "Esperando 10 segundos antes del Tap 2...")
            delay(10_000)
            Log.i(TAG, "Estado tras 10s: airplane_mode_on=${airplaneModeOn()}")

            // Tap 2: volver al estado original
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
