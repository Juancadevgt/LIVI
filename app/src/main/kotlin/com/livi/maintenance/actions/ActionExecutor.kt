package com.livi.maintenance.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.livi.maintenance.accessibility.LiviAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

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
            ActionType.AIRPLANE_TOGGLE -> toggleAirplane()
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
        // Damos un margen al AccessibilityService para que navegue y haga taps
        delay(7000)
        LiviAccessibilityService.reset()
        return Result.Success
    }

    private suspend fun toggleAirplane(): Result {
        // Requiere que el usuario haya otorgado WRITE_SECURE_SETTINGS vía ADB:
        // adb shell pm grant com.livi.maintenance android.permission.WRITE_SECURE_SETTINGS
        return try {
            val resolver = context.contentResolver
            val ok1 = Settings.Global.putInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 1)
            broadcastAirplaneChange(true)
            withTimeoutOrNull(15000) { delay(10000) }
            val ok2 = Settings.Global.putInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0)
            broadcastAirplaneChange(false)
            if (ok1 && ok2) Result.Success
            else Result.Failure("No se pudo modificar AIRPLANE_MODE_ON (¿falta WRITE_SECURE_SETTINGS?)")
        } catch (t: Throwable) {
            Log.e(TAG, "Error al alternar modo avión", t)
            Result.Failure(t.message ?: "Error desconocido")
        }
    }

    private fun broadcastAirplaneChange(state: Boolean) {
        val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).apply {
            putExtra("state", state)
        }
        try {
            context.sendBroadcast(intent)
        } catch (_: SecurityException) {
            // En algunas versiones está restringido — el cambio de Settings.Global ya basta
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
