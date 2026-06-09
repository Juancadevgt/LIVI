package com.livi.maintenance.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.livi.maintenance.LiviApp
import com.livi.maintenance.actions.ActionExecutor

/**
 * Worker que ejecuta una tarea programada. Tareas cortas (10-20s) — no requiere
 * foreground service. Si se necesita ejecución garantizada con pantalla bloqueada
 * durante varios minutos, agregar setForeground con tipo "specialUse" Y declarar
 * el SystemForegroundService de WorkManager en el manifest con esa propiedad.
 */
class LiviWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId < 0) return Result.failure()

        val app = applicationContext as LiviApp
        val task = app.repository.get(taskId) ?: return Result.failure()
        if (!task.enabled) return Result.success()

        val executor = ActionExecutor(applicationContext)
        val result = executor.execute(task.action, task.targetPackage)
        val resultText = when (result) {
            is ActionExecutor.Result.Success -> "OK"
            is ActionExecutor.Result.Failure -> "ERR: ${result.message}"
        }
        app.repository.upsert(
            task.copy(lastRunAt = System.currentTimeMillis(), lastResult = resultText)
        )
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
    }
}
