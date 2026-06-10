package com.livi.maintenance.scheduler

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.livi.maintenance.LiviApp
import com.livi.maintenance.actions.ActionExecutor

/**
 * Worker que ejecuta una tarea programada.
 *
 * Si el celular está bloqueado y LIVI no es Device Owner → difiere (no ejecuta).
 * Si se ejecuta pero el usuario cancela (Home/Back) → re-marca pendiente.
 * Si se ejecuta y completa → marca OK.
 *
 * El ciclo de reintentos termina cuando el resultado es Success o Failure técnico.
 */
class LiviWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        val force = inputData.getBoolean(KEY_FORCE, false)
        if (taskId < 0) return Result.failure()

        val app = applicationContext as LiviApp
        val task = app.repository.get(taskId) ?: return Result.failure()
        if (!task.enabled) return Result.success()

        val ctx = applicationContext

        // ¿Se puede ejecutar ahora? (pantalla activa + desbloqueado, o Device Owner)
        if (!force && !canExecuteOnCurrentScreenState(ctx, app)) {
            Log.i(TAG, "Celular bloqueado/dormido sin Device Owner — diferiendo tarea ${task.id}")
            // Preservar el timestamp original si ya estaba pendiente
            val pendingTs = task.pendingExecution ?: System.currentTimeMillis()
            app.repository.upsert(task.copy(pendingExecution = pendingTs))
            // Primera notificación: solo aviso, sin botón
            PendingTaskNotifier.show(ctx, task.copy(pendingExecution = pendingTs), isRetry = false)
            return Result.success()
        }

        // Ejecutar la acción
        val executor = ActionExecutor(ctx)
        val result = executor.execute(task.action, task.targetPackage)

        when (result) {
            is ActionExecutor.Result.Success -> {
                Log.i(TAG, "Tarea ${task.id} OK")
                app.repository.upsert(
                    task.copy(
                        lastRunAt = System.currentTimeMillis(),
                        lastResult = "OK",
                        pendingExecution = null
                    )
                )
                PendingTaskNotifier.cancel(ctx, task.id)
            }

            is ActionExecutor.Result.Failure -> {
                Log.w(TAG, "Tarea ${task.id} FAILURE: ${result.message}")
                app.repository.upsert(
                    task.copy(
                        lastRunAt = System.currentTimeMillis(),
                        lastResult = "ERR: ${result.message}",
                        pendingExecution = null
                    )
                )
                PendingTaskNotifier.cancel(ctx, task.id)
            }

            is ActionExecutor.Result.Interrupted -> {
                Log.w(TAG, "Tarea ${task.id} INTERRUMPIDA por usuario: ${result.message}")
                // Re-marcar como pendiente; el timestamp se actualiza para reflejar
                // el momento del reintento (no la programación original)
                val retryTs = System.currentTimeMillis()
                app.repository.upsert(
                    task.copy(
                        pendingExecution = retryTs,
                        lastResult = "Cancelada — reintentar al desbloquear"
                    )
                )
                // Notificación de reintento: incluye botón "Ejecutar ahora"
                PendingTaskNotifier.show(ctx, task.copy(pendingExecution = retryTs), isRetry = true)
            }
        }
        return Result.success()
    }

    /**
     * true si LIVI puede ejecutar la acción ahora mismo:
     *  - Device Owner: SIEMPRE
     *  - Resto: requiere pantalla encendida Y desbloqueado
     */
    private fun canExecuteOnCurrentScreenState(ctx: Context, app: LiviApp): Boolean {
        if (app.policyManager.isDeviceOwner()) return true
        val keyguard = ctx.getSystemService<KeyguardManager>()
        val power = ctx.getSystemService<PowerManager>()
        val isLocked = keyguard?.isKeyguardLocked == true
        val isInteractive = power?.isInteractive == true
        return isInteractive && !isLocked
    }

    companion object {
        private const val TAG = "LiviWorker"
        const val KEY_TASK_ID = "task_id"
        const val KEY_FORCE = "force_execute"
    }
}
