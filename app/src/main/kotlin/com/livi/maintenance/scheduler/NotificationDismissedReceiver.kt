package com.livi.maintenance.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.livi.maintenance.LiviApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Se dispara cuando el usuario descarta la notificación de tarea pendiente.
 * Re-muestra la notificación inmediatamente para que el usuario no la pierda
 * de vista — la única forma de hacerla desaparecer es ejecutar la tarea.
 */
class NotificationDismissedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISSED) return
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val isRetry = intent.getBooleanExtra(EXTRA_IS_RETRY, false)
        if (taskId < 0) return

        Log.i(TAG, "Usuario descartó notificación de tarea $taskId — re-mostrando")
        val app = context.applicationContext as LiviApp
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = app.repository.get(taskId)
                if (task?.pendingExecution != null) {
                    PendingTaskNotifier.show(context, task, isRetry)
                } else {
                    Log.i(TAG, "Tarea $taskId ya no está pendiente — no re-mostrar")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error re-mostrando notificación", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "LiviNotifDismissed"
        const val ACTION_DISMISSED = "com.livi.maintenance.ACTION_NOTIFICATION_DISMISSED"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_IS_RETRY = "is_retry"
    }
}
