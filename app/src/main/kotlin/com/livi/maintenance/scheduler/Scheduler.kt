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
 * iteración.
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
        val data = Data.Builder()
            .putLong(LiviWorker.KEY_TASK_ID, task.id)
            .putBoolean(LiviWorker.KEY_FORCE, false)
            .putString(LiviWorker.KEY_ORIGIN, ORIGIN_AUTOMATICA)
            .build()
        val request = OneTimeWorkRequestBuilder<LiviWorker>()
            .setInitialDelay(nextRunMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(tagFor(task.id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName(task.id), ExistingWorkPolicy.REPLACE, request
        )
    }

    /**
     * Ejecuta la tarea AHORA sin esperar al próximo horario.
     * @param force si true, salta el chequeo de pantalla bloqueada (usado cuando
     *              el usuario tocó la notificación o desbloqueó el celular).
     */
    fun runNow(task: TaskEntity, force: Boolean = false) {
        val data = Data.Builder()
            .putLong(LiviWorker.KEY_TASK_ID, task.id)
            .putBoolean(LiviWorker.KEY_FORCE, force)
            .putString(LiviWorker.KEY_ORIGIN, ORIGIN_MANUAL)
            .build()
        val request = OneTimeWorkRequestBuilder<LiviWorker>()
            .setInputData(data)
            .addTag(tagFor(task.id) + "_now")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName(task.id) + "_now", ExistingWorkPolicy.REPLACE, request
        )
    }

    /**
     * Calcula el próximo timestamp en que se ejecutaría una tarea, sin programar
     * nada. Útil para telemetría (saber "cuándo le toca la próxima").
     */
    fun nextRunMillisFor(task: TaskEntity): Long = nextRunMillis(task)

    fun cancel(taskId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(taskId))
    }

    private fun uniqueName(id: Long) = "livi_task_$id"
    private fun tagFor(id: Long) = "livi_task_tag_$id"

    /**
     * Próximo momento en milis considerando `hour`, `minute`, bitmask `daysOfWeek`
     * y la frecuencia `repeatWeeks` (1=semanal, 2=quincenal, 3=cada 3 sem, 4=mensual aprox).
     *
     * Si `repeatWeeks > 1`, asegura que entre ejecuciones pasen al menos
     * `repeatWeeks` semanas desde `lastRunAt`.
     */
    private fun nextRunMillis(task: TaskEntity): Long {
        val nowMs = System.currentTimeMillis()

        // Si hay repetición de N semanas y ya se ejecutó antes, el punto de
        // partida no es "ahora" sino "última + N semanas"
        val earliestMs = if (task.repeatWeeks > 1 && task.lastRunAt != null) {
            maxOf(nowMs, task.lastRunAt + task.repeatWeeks * 7L * 24L * 3600_000L)
        } else nowMs

        val candidate = Calendar.getInstance().apply {
            timeInMillis = earliestMs
            set(Calendar.HOUR_OF_DAY, task.hour)
            set(Calendar.MINUTE, task.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Si la hora ya pasó en el día earliestMs, avanzar al día siguiente
        if (candidate.timeInMillis <= earliestMs) {
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }

        val mask = if (task.daysOfWeek == 0) TaskEntity.EVERY_DAY else task.daysOfWeek
        for (i in 0..7) {
            val day = candidate.get(Calendar.DAY_OF_WEEK)
            val bit = dayOfWeekToBit(day)
            if ((mask and bit) != 0 && candidate.timeInMillis > nowMs) {
                return candidate.timeInMillis
            }
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }
        return candidate.timeInMillis
    }

    companion object {
        const val ORIGIN_AUTOMATICA = "AUTOMATICA"
        const val ORIGIN_MANUAL = "MANUAL"
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
