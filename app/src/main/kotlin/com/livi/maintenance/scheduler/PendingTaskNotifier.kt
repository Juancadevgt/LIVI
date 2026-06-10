package com.livi.maintenance.scheduler

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.livi.maintenance.LiviApp
import com.livi.maintenance.MainActivity
import com.livi.maintenance.R
import com.livi.maintenance.actions.ActionType
import com.livi.maintenance.data.TaskEntity
import java.util.concurrent.TimeUnit

/**
 * Muestra notificaciones para tareas pendientes. Solo aviso visual — al tocar
 * abre LIVI, pero la ejecución real se dispara automáticamente al desbloquear
 * el celular (UnlockReceiver) o cuando vuelva a entrar al ciclo del Worker.
 */
object PendingTaskNotifier {

    fun show(context: Context, task: TaskEntity) {
        val app = context.applicationContext as LiviApp
        val actionLabel = when (task.action) {
            ActionType.CLEAR_CACHE -> "Borrar caché"
            ActionType.AIRPLANE_TOGGLE -> "Modo avión 10s"
        }
        val appLabel = task.targetPackage?.let { app.appRepository.getAppLabel(it) }
        val subtitle = if (!appLabel.isNullOrBlank()) "$actionLabel · $appLabel" else actionLabel

        val elapsed = task.pendingExecution?.let {
            formatElapsed(System.currentTimeMillis() - it)
        } ?: "recién"

        // Al tocar la notificación, solo se abre LIVI (no ejecuta la tarea —
        // eso ya lo hace UnlockReceiver al desbloquear).
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            task.id.toInt(),
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val bigText = buildString {
            append(subtitle)
            append("\n\n")
            append("Programada hace ").append(elapsed).append(".\n")
            append("Se ejecutará automáticamente cuando desbloquees el celular.")
        }

        val notification = NotificationCompat.Builder(
            context,
            context.getString(R.string.notification_channel_id)
        )
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Tarea LIVI pendiente")
            .setContentText("$subtitle · hace $elapsed")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(task.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Permiso POST_NOTIFICATIONS no otorgado
        }
    }

    fun cancel(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(taskId.toInt())
    }

    private fun formatElapsed(millis: Long): String {
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val days = TimeUnit.MILLISECONDS.toDays(millis)
        return when {
            days > 0 -> "$days día${if (days != 1L) "s" else ""}"
            hours > 0 -> "$hours hora${if (hours != 1L) "s" else ""}"
            minutes > 0 -> "$minutes minuto${if (minutes != 1L) "s" else ""}"
            seconds > 0 -> "$seconds segundo${if (seconds != 1L) "s" else ""}"
            else -> "recién"
        }
    }
}
