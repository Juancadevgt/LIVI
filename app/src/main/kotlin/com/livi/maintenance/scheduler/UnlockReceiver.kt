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
 * Escucha ACTION_USER_PRESENT (cuando el usuario desbloquea el celular).
 * Si hay tareas pendientes que no se pudieron ejecutar a su hora por estar
 * el celular bloqueado, las ejecuta ahora con la pantalla activa.
 */
class UnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
        Log.i(TAG, "Usuario desbloqueó el celular, revisando pendientes")

        val app = context.applicationContext as LiviApp
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pending = app.repository.getPending()
                Log.i(TAG, "${pending.size} tarea(s) pendiente(s)")
                pending.forEach { task ->
                    Log.i(TAG, "Ejecutando tarea pendiente ${task.id} (${task.action})")
                    app.repository.upsert(task.copy(pendingExecution = null))
                    app.scheduler.runNow(task, force = true)
                    PendingTaskNotifier.cancel(context, task.id)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object { private const val TAG = "LiviUnlockReceiver" }
}
