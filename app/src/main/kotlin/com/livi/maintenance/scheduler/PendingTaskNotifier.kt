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
 * Notificaciones de tareas pendientes (cuando llega la hora con celular bloqueado).
 * Ambas variantes (primera y reintento) incluyen botón "Ejecutar ahora" para que el
 * usuario pueda completar la tarea directo desde la notificación.
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

        // PendingIntent para ejecutar la tarea (botón "Ejecutar ahora" o toque en la notificación)
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

        // PendingIntent secundario solo para abrir LIVI
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            task.id.toInt() + 200000,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (isRetry) "⚠️ Aviso de IT — Reintentar tarea" else "⚠️ Aviso de IT — Tarea pendiente"
        val bigText = buildString {
            append("Aviso importante de IT\n\n")
            append(subtitle)
            append("\n\n")
            if (isRetry) {
                append("La última ejecución se canceló hace ").append(elapsed).append(".\n\n")
            } else {
                append("Programada hace ").append(elapsed).append(".\n\n")
            }
            append("Toca el botón 'Ejecutar ahora' para completar la tarea de mantenimiento. ")
            append("LIVI abrirá Ajustes, ejecutará la acción y volverá automáticamente.")
        }

        val notification = NotificationCompat.Builder(
            context,
            context.getString(R.string.notification_channel_id)
        )
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText("$subtitle · hace $elapsed")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(executePendingIntent)  // tocar la notif también ejecuta
            .addAction(
                android.R.drawable.ic_media_play,
                "Ejecutar ahora",
                executePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                "Abrir LIVI",
                openPendingIntent
            )
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
