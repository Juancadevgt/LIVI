package com.livi.maintenance.scheduler

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.livi.maintenance.LiviApp
import com.livi.maintenance.data.TaskEntity
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Programador de tareas. Usa OneTimeWorkRequest con `setInitialDelay` para la
 * próxima ejecución y, al completar el worker, vuelve a programar la siguiente
 * iteración. Esto da más control que PeriodicWorkRequest (mínimo 15 min) y
 * permite reaccionar a cambios de horario sin reiniciar la app.
 */
class Scheduler(private val context: Context) {

    fun scheduleAll() {
        val app = context.applicationContext as LiviApp
        kotlinx.coroutines.runBlocking {
            val enabled = app.repository.getEnabled()
            enabled.forEach { schedule(it) }
        }
    }

    fun schedule(task: TaskEntity) {
        if (!task.enabled) {
            cancel(task.id)
            return
        }
        val nextRunMs = nextRunMillis(task) - System.currentTimeMillis()
        val data = Data.Builder().putLong(LiviWorker.KEY_TASK_ID, task.id).build()
        val request = OneTimeWorkRequestBuilder<LiviWorker>()
            .setInitialDelay(nextRunMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(tagFor(task.id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName(task.id), ExistingWorkPolicy.REPLACE, request
        )
    }

    fun runNow(task: TaskEntity) {
        val data = Data.Builder().putLong(LiviWorker.KEY_TASK_ID, task.id).build()
        val request = OneTimeWorkRequestBuilder<LiviWorker>()
            .setInputData(data)
            .addTag(tagFor(task.id) + "_now")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName(task.id) + "_now", ExistingWorkPolicy.REPLACE, request
        )
    }

    fun cancel(taskId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(taskId))
    }

    private fun uniqueName(id: Long) = "livi_task_$id"
    private fun tagFor(id: Long) = "livi_task_tag_$id"

    /**
     * Calcula el próximo momento en milis a partir del cual debe ejecutarse la
     * tarea, considerando `hour`, `minute` y el bitmask `daysOfWeek`.
     */
    private fun nextRunMillis(task: TaskEntity): Long {
        val now = Calendar.getInstance()
        val candidate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, task.hour)
            set(Calendar.MINUTE, task.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Si el bitmask es 0, asumimos cualquier día
        val mask = if (task.daysOfWeek == 0) TaskEntity.EVERY_DAY else task.daysOfWeek
        for (i in 0..7) {
            val day = candidate.get(Calendar.DAY_OF_WEEK)
            val bit = dayOfWeekToBit(day)
            if ((mask and bit) != 0 && candidate.timeInMillis > now.timeInMillis) {
                return candidate.timeInMillis
            }
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }
        return candidate.timeInMillis
    }

    private fun dayOfWeekToBit(dow: Int): Int = when (dow) {
        Calendar.MONDAY -> TaskEntity.DAY_MON
        Calendar.TUESDAY -> TaskEntity.DAY_TUE
        Calendar.WEDNESDAY -> TaskEntity.DAY_WED
        Calendar.THURSDAY -> TaskEntity.DAY_THU
        Calendar.FRIDAY -> TaskEntity.DAY_FRI
        Calendar.SATURDAY -> TaskEntity.DAY_SAT
        Calendar.SUNDAY -> TaskEntity.DAY_SUN
        else -> 0
    }
}
