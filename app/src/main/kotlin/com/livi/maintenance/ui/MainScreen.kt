package com.livi.maintenance.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
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
                        onRun = { viewModel.runNow(task) },
                        onToggle = { viewModel.upsert(task.copy(enabled = it)) },
                        onDelete = { viewModel.delete(task) }
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
}

@Composable
private fun PermissionsCard(context: Context) {
    val a11yConnected by produceState(initialValue = false) {
        while (true) {
            value = LiviAccessibilityService.isConnected()
            kotlinx.coroutines.delay(1500)
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
        }
    }
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
    }
    val timeStr = "%02d:%02d".format(task.hour, task.minute)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(actionLabel, fontWeight = FontWeight.SemiBold)
            if (!pkgLabel.isNullOrBlank()) {
                Text(pkgLabel, style = MaterialTheme.typography.bodySmall)
            }
            Text("$timeStr · ${daysLabel(task.daysOfWeek)}", style = MaterialTheme.typography.bodySmall)
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
