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
 * Notificaciones de tareas pendientes:
 *  - Primera vez (isRetry=false): solo aviso visual. La tarea se ejecuta sola
 *    al próximo desbloqueo.
 *  - Tras un Interrupted del usuario (isRetry=true): aviso + botón "Ejecutar ahora"
 *    para que el usuario pueda reintentar manualmente sin esperar al desbloqueo.
 */
object PendingTaskNotifier {

    fun show(context: Context, task: TaskEntity, isRetry: Boolean = false) {
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

        // PendingIntent para abrir LIVI (toque normal de la notificación)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            task.id.toInt(),
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (isRetry) "Reintentar tarea LIVI" else "Tarea LIVI pendiente"
        val bigText = buildString {
            append(subtitle)
            append("\n\n")
            if (isRetry) {
                append("Cancelada hace ").append(elapsed).append(".\n")
                append("Toca 'Ejecutar ahora' para reintentar manualmente, ")
                append("o se ejecutará al próximo desbloqueo del celular.")
            } else {
                append("Programada hace ").append(elapsed).append(".\n")
                append("Se ejecutará automáticamente cuando desbloquees el celular.")
            }
        }

        val builder = NotificationCompat.Builder(
            context,
            context.getString(R.string.notification_channel_id)
        )
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText("$subtitle · hace $elapsed")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)

        // Solo en reintento se agrega el botón "Ejecutar ahora"
        if (isRetry) {
            val executeIntent = Intent(context, PendingActionReceiver::class.java).apply {
                action = PendingActionReceiver.ACTION_EXECUTE
                putExtra(PendingActionReceiver.EXTRA_TASK_ID, task.id)
            }
            val executePendingIntent = PendingIntent.getBroadcast(
                context,
                task.id.toInt() + 200000,
                executeIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(
                android.R.drawable.ic_media_play,
                "Ejecutar ahora",
                executePendingIntent
            )
        }

        try {
            NotificationManagerCompat.from(context).notify(task.id.toInt(), builder.build())
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
