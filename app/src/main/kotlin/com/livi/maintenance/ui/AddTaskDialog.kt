package com.livi.maintenance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.livi.maintenance.LiviApp
import com.livi.maintenance.actions.ActionType
import com.livi.maintenance.data.TaskEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as LiviApp

    var action by rememberSaveable { mutableStateOf(ActionType.CLEAR_CACHE) }
    var pkg by rememberSaveable { mutableStateOf<String?>(null) }
    var pkgLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var hour by rememberSaveable { mutableIntStateOf(2) }
    var minute by rememberSaveable { mutableIntStateOf(0) }
    var daysMask by rememberSaveable { mutableIntStateOf(TaskEntity.EVERY_DAY) }
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(20.dp)) {
                Text("Nueva tarea", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                Text("Acción", style = MaterialTheme.typography.labelLarge)
                ActionType.values().forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = a == action, onClick = { action = a })
                        Text(
                            labelFor(a),
                            modifier = Modifier.clickable { action = a }
                        )
                    }
                }

                if (action != ActionType.AIRPLANE_TOGGLE) {
                    Spacer(Modifier.height(12.dp))
                    Text("App objetivo", style = MaterialTheme.typography.labelLarge)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clickable { showPicker = true }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            if (pkg == null) {
                                Text(
                                    "Toca para elegir app...",
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(pkgLabel ?: pkg!!, fontWeight = FontWeight.SemiBold)
                                Text(
                                    pkg!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Hora: %02d:%02d".format(hour, minute), style = MaterialTheme.typography.labelLarge)
                Row {
                    OutlinedTextField(
                        value = hour.toString(),
                        onValueChange = { it.toIntOrNull()?.let { v -> if (v in 0..23) hour = v } },
                        label = { Text("HH") },
                        modifier = Modifier.width(80.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = minute.toString(),
                        onValueChange = { it.toIntOrNull()?.let { v -> if (v in 0..59) minute = v } },
                        label = { Text("MM") },
                        modifier = Modifier.width(80.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("Días", style = MaterialTheme.typography.labelLarge)
                val days = listOf(
                    "L" to TaskEntity.DAY_MON,
                    "M" to TaskEntity.DAY_TUE,
                    "X" to TaskEntity.DAY_WED,
                    "J" to TaskEntity.DAY_THU,
                    "V" to TaskEntity.DAY_FRI,
                    "S" to TaskEntity.DAY_SAT,
                    "D" to TaskEntity.DAY_SUN
                )
                Row {
                    days.forEach { (label, bit) ->
                        FilterChip(
                            selected = (daysMask and bit) != 0,
                            onClick = { daysMask = daysMask xor bit },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                val needsPkg = action != ActionType.AIRPLANE_TOGGLE
                val canSave = !needsPkg || pkg != null
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = canSave,
                        onClick = {
                            onSave(
                                TaskEntity(
                                    action = action,
                                    targetPackage = if (action == ActionType.AIRPLANE_TOGGLE) null else pkg,
                                    hour = hour,
                                    minute = minute,
                                    daysOfWeek = if (daysMask == 0) TaskEntity.EVERY_DAY else daysMask,
                                    enabled = true
                                )
                            )
                        }
                    ) { Text("Guardar") }
                }
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            onDismiss = { showPicker = false },
            onPick = { selected ->
                pkg = selected.packageName
                pkgLabel = selected.label
                showPicker = false
            }
        )
    }
}

private fun labelFor(a: ActionType) = when (a) {
    ActionType.CLEAR_CACHE -> "Borrar caché"
    ActionType.AIRPLANE_TOGGLE -> "Modo avión 10s"
}
