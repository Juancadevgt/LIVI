package com.livi.maintenance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.livi.maintenance.admin.AdminMode
import com.livi.maintenance.ui.MainScreen
import com.livi.maintenance.ui.MainViewModel
import com.livi.maintenance.ui.MainViewModelFactory
import com.livi.maintenance.ui.theme.LiviTheme
import com.livi.maintenance.ui.theme.ThemePreference

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application as LiviApp)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* el usuario otorga o niega — la UI refleja el estado en la tarjeta de permisos */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePreference.load(this)
        maybeRequestNotificationPermission()
        setContent {
            LiviTheme(darkTheme = ThemePreference.isDarkMode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    /**
     * Salir del modo Admin cuando la app pasa a background. Así, si IT entra a
     * Admin en el celular del usuario y se distrae, al cambiar de app el modo
     * se cierra solo y el usuario no puede modificar tareas accidentalmente.
     */
    override fun onPause() {
        super.onPause()
        AdminMode.exit()
    }

    // Nota: el onResume ya NO ejecuta pendientes automáticamente. Una tarea pendiente
    // solo se ejecuta cuando:
    //   - El usuario toca la notificación
    //   - El usuario toca "▶ Probar ahora" en la lista de tareas
    //   - Llega su próxima programación con celular desbloqueado

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

}
