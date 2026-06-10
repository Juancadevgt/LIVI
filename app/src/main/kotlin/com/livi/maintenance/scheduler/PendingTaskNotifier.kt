package com.livi.maintenance.scheduler

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.livi.maintenance.LiviApp
import com.livi.maintenance.R
import com.livi.maintenance.actions.ActionType
import com.livi.maintenance.data.TaskEntity
import java.util.concurrent.TimeUnit

/**
 * Notificaciones de tareas pendientes.
 * - Sin botones: el toque sobre la notificación dispara la ejecución directa.
 * - setOngoing(true): el usuario NO puede deslizar para descartarla. Solo se
 *   cierra cuando la tarea complete exitosamente.
 */
object PendingTaskNotifier {

    fun show(context: Context, task: TaskEntity, isRetry: Boolean = false) {
        val app = context.applicationContext as LiviApp
        val actionLabel = when (task.action) {
            ActionType.CLEAR_CACHE -> "Borrar caché"
            ActionType.AIRPLANE_TOGGLE -> "Modo avión 10s"
            ActionType.REBOOT -> "Reiniciar celular"
        }
        val appLabel = task.targetPackage?.let { app.appRepository.getAppLabel(it) }
        val subtitle = if (!appLabel.isNullOrBlank()) "$actionLabel · $appLabel" else actionLabel

        val elapsed = task.pendingExecution?.let {
            formatElapsed(System.currentTimeMillis() - it)
        } ?: "recién"

        // Al tocar la notificación → PendingActionReceiver ejecuta la tarea directamente
        val executeIntent = Intent(context, PendingActionReceiver::class.java).apply {
            action = PendingActionReceiver.ACTION_EXECUTE
            putExtra(PendingActionReceiver.EXTRA_TASK_ID, task.id)
        }
        val executePendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            executeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Si el usuario descarta la notif → NotificationDismissedReceiver la re-muestra
        val dismissIntent = Intent(context, NotificationDismissedReceiver::class.java).apply {
            action = NotificationDismissedReceiver.ACTION_DISMISSED
            putExtra(NotificationDismissedReceiver.EXTRA_TASK_ID, task.id)
            putExtra(NotificationDismissedReceiver.EXTRA_IS_RETRY, isRetry)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.toInt() + 300000,
            dismissIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Compacto: 1 línea de contenido + sub-texto con tiempo. Cabe completo
        // en cualquier celular y se ve idéntico para las 3 acciones.
        val title = if (isRetry) "Aviso de IT — Reintentar" else "Aviso de IT"
        val contentText = subtitle                  // ej: "Borrar caché · Power Apps"
        val subText = "hace $elapsed · Toca para ejecutar"
        val bigText = buildString {
            append(subtitle).append("\n")
            append(if (isRetry) "Cancelada hace " else "Pendiente hace ").append(elapsed).append(".\n")
            append("Toca esta notificación para ejecutar la tarea.")
        }

        val notification = NotificationCompat.Builder(
            context,
            context.getString(R.string.notification_channel_id)
        )
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText(subText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(executePendingIntent)
            .setDeleteIntent(dismissPendingIntent)  // re-muestra si se descarta
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
