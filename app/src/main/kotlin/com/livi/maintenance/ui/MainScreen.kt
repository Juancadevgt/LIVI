package com.livi.maintenance.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.livi.maintenance.LiviApp
import com.livi.maintenance.R
import com.livi.maintenance.accessibility.LiviAccessibilityService
import com.livi.maintenance.actions.ActionType
import com.livi.maintenance.data.TaskEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val tasks by viewModel.tasks.collectAsState()
    val context = LocalContext.current
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editingId: Long? by rememberSaveable { mutableStateOf(null) }
    var deletingId: Long? by rememberSaveable { mutableStateOf(null) }

    val editing = remember(editingId, tasks) {
        editingId?.let { id -> tasks.find { it.id == id } }
    }
    val deleting = remember(deletingId, tasks) {
        deletingId?.let { id -> tasks.find { it.id == id } }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PermissionsCard(context)
            Text(
                stringResource(R.string.title_tasks),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        onClick = { editingId = task.id },
                        onRun = { viewModel.runNow(task) },
                        onToggle = { viewModel.upsert(task.copy(enabled = it)) },
                        onDelete = { deletingId = task.id }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAdd) {
        AddTaskDialog(
            onDismiss = { showAdd = false },
            onSave = { newTask ->
                viewModel.upsert(newTask)
                showAdd = false
            }
        )
    }

    val currentlyEditing = editing
    if (currentlyEditing != null) {
        AddTaskDialog(
            existing = currentlyEditing,
            onDismiss = { editingId = null },
            onSave = { updated ->
                viewModel.upsert(updated)
                editingId = null
            }
        )
    }

    val currentlyDeleting = deleting
    if (currentlyDeleting != null) {
        AlertDialog(
            onDismissRequest = { deletingId = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("¿Eliminar tarea?") },
            text = {
                Column {
                    Text("Esta acción no se puede deshacer.")
                    Spacer(Modifier.height(8.dp))
                    val actionLabel = when (currentlyDeleting.action) {
                        ActionType.CLEAR_CACHE -> "Borrar caché"
                        ActionType.AIRPLANE_TOGGLE -> "Modo avión 10s"
                        ActionType.REBOOT -> "Reiniciar celular"
                    }
                    val pkgLabel = currentlyDeleting.targetPackage?.let {
                        (context.applicationContext as LiviApp).appRepository.getAppLabel(it)
                    }
                    Text(
                        "$actionLabel${if (pkgLabel != null) " · $pkgLabel" else ""}",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "%02d:%02d · %s".format(
                            currentlyDeleting.hour,
                            currentlyDeleting.minute,
                            daysLabel(currentlyDeleting.daysOfWeek)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(currentlyDeleting)
                        deletingId = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun PermissionsCard(context: Context) {
    val app = context.applicationContext as LiviApp

    val a11yConnected by produceState(initialValue = false) {
        while (true) {
            value = LiviAccessibilityService.isConnected()
            kotlinx.coroutines.delay(1500)
        }
    }
    val notifGranted by produceState(initialValue = checkNotificationPermission(context)) {
        while (true) {
            value = checkNotificationPermission(context)
            kotlinx.coroutines.delay(2000)
        }
    }
    val batteryIgnored by produceState(initialValue = checkBatteryOptimizationIgnored(context)) {
        while (true) {
            value = checkBatteryOptimizationIgnored(context)
            kotlinx.coroutines.delay(2000)
        }
    }
    val isDeviceOwner by produceState(initialValue = false) {
        while (true) {
            value = app.policyManager.isDeviceOwner()
            kotlinx.coroutines.delay(3000)
        }
    }

    Card(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.permissions_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            PermissionRow(
                label = stringResource(R.string.permission_accessibility),
                granted = a11yConnected
            ) {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            PermissionRow(
                label = "Notificaciones",
                granted = notifGranted
            ) {
                // Abre los ajustes de notificación de LIVI para que el usuario active manualmente
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            PermissionRow(
                label = "Sin optimización de batería",
                granted = batteryIgnored
            ) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:" + context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            if (isDeviceOwner) {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(
                        stringResource(R.string.device_owner_status),
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text("Activo - funciona bloqueado") }
                    )
                }
            }
        }
    }
}

private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else true
}

private fun checkBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService<PowerManager>() ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(label, modifier = Modifier.weight(1f))
        if (granted) {
            AssistChip(onClick = {}, label = { Text(stringResource(R.string.granted)) })
        } else {
            FilledTonalButton(onClick = onGrant) { Text(stringResource(R.string.grant)) }
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    onClick: () -> Unit,
    onRun: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as LiviApp
    val pkgLabel = remember(task.targetPackage) {
        task.targetPackage?.let { app.appRepository.getAppLabel(it) }
    }
    val actionLabel = when (task.action) {
        ActionType.CLEAR_CACHE -> stringResource(R.string.task_clear_cache)
        ActionType.AIRPLANE_TOGGLE -> stringResource(R.string.task_airplane_toggle)
        ActionType.REBOOT -> stringResource(R.string.task_reboot)
    }
    val timeStr = "%02d:%02d".format(task.hour, task.minute)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(actionLabel, fontWeight = FontWeight.SemiBold)
                if (task.pendingExecution != null) {
                    Spacer(Modifier.width(6.dp))
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                "Pendiente",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    )
                }
            }
            if (!pkgLabel.isNullOrBlank()) {
                Text(pkgLabel, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "$timeStr · ${daysLabel(task.daysOfWeek)} · ${frequencyLabel(task.repeatWeeks)}",
                style = MaterialTheme.typography.bodySmall
            )
            task.lastResult?.let {
                Text("Última: $it", style = MaterialTheme.typography.labelSmall)
            }
        }
        Switch(checked = task.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onRun) {
            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.action_run_now))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
        }
    }
}

private fun frequencyLabel(weeks: Int): String = when (weeks) {
    1 -> "cada semana"
    2 -> "cada 2 semanas"
    3 -> "cada 3 semanas"
    4 -> "cada 4 semanas"
    else -> "cada $weeks semanas"
}

private fun daysLabel(mask: Int): String {
    if (mask == 0 || mask == TaskEntity.EVERY_DAY) return "Todos los días"
    if (mask == TaskEntity.WEEKDAYS) return "L-V"
    val parts = mutableListOf<String>()
    val days = listOf(
        TaskEntity.DAY_MON to "L",
        TaskEntity.DAY_TUE to "M",
        TaskEntity.DAY_WED to "X",
        TaskEntity.DAY_THU to "J",
        TaskEntity.DAY_FRI to "V",
        TaskEntity.DAY_SAT to "S",
        TaskEntity.DAY_SUN to "D"
    )
    days.forEach { (bit, l) -> if ((mask and bit) != 0) parts.add(l) }
    return parts.joinToString(" ")
}

@Composable
private fun stringResource(id: Int): String =
    androidx.compose.ui.res.stringResource(id = id)
