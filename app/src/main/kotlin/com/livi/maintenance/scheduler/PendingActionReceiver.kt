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
 * Recibe el tap del usuario sobre la notificación de tarea pendiente,
 * limpia el flag de pendiente, y reencola la tarea con flag de ejecución
 * forzada (saltea el chequeo de pantalla bloqueada).
 */
class PendingActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EXECUTE) return
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId < 0) return

        Log.i(TAG, "Usuario tocó notificación, ejecutando tarea $taskId")
        val app = context.applicationContext as LiviApp
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = app.repository.get(taskId)
                if (task != null) {
                    // limpiar el pendiente y ejecutar con force=true
                    app.repository.upsert(task.copy(pendingExecution = null))
                    app.scheduler.runNow(task, force = true)
                }
                PendingTaskNotifier.cancel(context, taskId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "LiviPendingAction"
        const val ACTION_EXECUTE = "com.livi.maintenance.ACTION_EXECUTE_TASK"
        const val EXTRA_TASK_ID = "task_id"
    }
}
