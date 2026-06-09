package com.livi.maintenance.scheduler

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.livi.maintenance.LiviApp
import com.livi.maintenance.MainActivity
import com.livi.maintenance.R
import com.livi.maintenance.actions.ActionExecutor

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

        setForeground(buildForegroundInfo())

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

    private fun buildForegroundInfo(): ForegroundInfo {
        val ctx = applicationContext
        val pendingIntent = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(
            ctx, ctx.getString(R.string.notification_channel_id)
        )
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(ctx.getString(R.string.notification_running))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        private const val NOTIFICATION_ID = 4242
    }
}
