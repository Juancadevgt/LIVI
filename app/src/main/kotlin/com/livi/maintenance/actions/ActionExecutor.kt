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
            // Tap 1: activar
            LiviAccessibilityService.setMode(LiviAccessibilityService.Mode.AIRPLANE_TOGGLE_ON)
            svc.openQuickSettings()
            delay(3500)
            LiviAccessibilityService.reset()

            // Espera 10s con el modo avión activo
            delay(10_000)

            // Tap 2: desactivar
            LiviAccessibilityService.setMode(LiviAccessibilityService.Mode.AIRPLANE_TOGGLE_OFF)
            svc.openQuickSettings()
            delay(3500)
            LiviAccessibilityService.reset()

            Result.Success
        } catch (t: Throwable) {
            Log.e(TAG, "Error en toggle modo avión", t)
            LiviAccessibilityService.reset()
            Result.Failure(t.message ?: "Error desconocido")
        }
    }

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
