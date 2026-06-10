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

/**
 * Muestra notificaciones para tareas pendientes (que no se pudieron ejecutar
 * porque el celular estaba bloqueado y LIVI no es Device Owner).
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

        // Intent que ejecuta la tarea al tocar la notificación o el botón "Ejecutar"
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

        // Intent que solo abre la app (por si el usuario quiere ver/editar)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            task.id.toInt() + 100000,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(
            context,
            context.getString(R.string.notification_channel_id)
        )
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Tarea LIVI pendiente")
            .setContentText("$subtitle · Toca para ejecutar")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(executePendingIntent)
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
            // Permiso POST_NOTIFICATIONS no otorgado — silenciosamente
        }
    }

    fun cancel(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(taskId.toInt())
    }
}
