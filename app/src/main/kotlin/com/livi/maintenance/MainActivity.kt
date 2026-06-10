package com.livi.maintenance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.livi.maintenance.scheduler.PendingTaskNotifier
import com.livi.maintenance.ui.MainScreen
import com.livi.maintenance.ui.MainViewModel
import com.livi.maintenance.ui.MainViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application as LiviApp)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* el usuario otorga o niega — la UI refleja el estado en la tarjeta de permisos */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Apenas la app vuelve a primer plano (usuario desbloqueó y la abrió, o
        // tocó la notificación), ejecutar todas las tareas pendientes
        // automáticamente. Esto cubre el caso en que UnlockReceiver no se dispara
        // por restricciones de Samsung/Android.
        executePendingTasksIfAny()
    }

    private fun executePendingTasksIfAny() {
        val app = application as LiviApp
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pending = app.repository.getPending()
                if (pending.isEmpty()) return@launch
                Log.i(TAG, "Encontradas ${pending.size} tarea(s) pendiente(s) al volver a primer plano")
                pending.forEach { task ->
                    Log.i(TAG, "Ejecutando tarea pendiente ${task.id} (${task.action})")
                    app.repository.upsert(task.copy(pendingExecution = null))
                    app.scheduler.runNow(task, force = true)
                    PendingTaskNotifier.cancel(this@MainActivity, task.id)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error ejecutando pendientes en onResume", t)
            }
        }
    }

    /**
     * En Android 13+ las notificaciones requieren permiso runtime explícito.
     */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object { private const val TAG = "LiviMainActivity" }
}
