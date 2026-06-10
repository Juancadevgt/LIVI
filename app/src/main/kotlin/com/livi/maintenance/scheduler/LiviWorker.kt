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
 * Worker que ejecuta una tarea programada. Si el celular está bloqueado
 * (pantalla apagada o keyguard activo) y LIVI no es Device Owner, NO ejecuta
 * la acción ahora — la marca como pendiente y muestra una notificación.
 *
 * Cuando el usuario desbloquea el celular (UnlockReceiver) o toca la
 * notificación (PendingActionReceiver), la tarea se ejecuta con flag
 * force=true que saltea esta verificación.
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
        val canExecuteNow = force || canExecuteOnCurrentScreenState(ctx, app)

        if (!canExecuteNow) {
            Log.i(TAG, "Celular bloqueado/dormido y sin Device Owner — diferiendo tarea ${task.id}")
            app.repository.upsert(task.copy(pendingExecution = System.currentTimeMillis()))
            PendingTaskNotifier.show(ctx, task)
            return Result.success()
        }

        // Ejecutar normalmente
        val executor = ActionExecutor(ctx)
        val result = executor.execute(task.action, task.targetPackage)
        val resultText = when (result) {
            is ActionExecutor.Result.Success -> "OK"
            is ActionExecutor.Result.Failure -> "ERR: ${result.message}"
        }
        app.repository.upsert(
            task.copy(
                lastRunAt = System.currentTimeMillis(),
                lastResult = resultText,
                pendingExecution = null
            )
        )
        // Por si había notificación pendiente, limpiarla
        PendingTaskNotifier.cancel(ctx, task.id)
        return Result.success()
    }

    /**
     * Devuelve true si LIVI puede ejecutar la acción ahora mismo:
     *  - Si es Device Owner: SIEMPRE puede (incluso bloqueado lo maneja DPM)
     *  - Si NO es Device Owner: requiere pantalla encendida y desbloqueado
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
