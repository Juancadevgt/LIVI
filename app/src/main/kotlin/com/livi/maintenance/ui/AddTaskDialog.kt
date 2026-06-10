package com.livi.maintenance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.livi.maintenance.LiviApp
import com.livi.maintenance.actions.ActionType
import com.livi.maintenance.data.TaskEntity

/**
 * Diálogo para crear o editar una tarea. Si `existing` se proporciona, los
 * campos se pre-llenan y al guardar se conserva el id (UPDATE en Room).
 * Si es null, se crea una nueva (INSERT).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit,
    existing: TaskEntity? = null
) {
    val context = LocalContext.current
    val app = context.applicationContext as LiviApp
    val isEditing = existing != null

    var action by rememberSaveable {
        mutableStateOf(existing?.action ?: ActionType.CLEAR_CACHE)
    }
    var pkg by rememberSaveable { mutableStateOf(existing?.targetPackage) }
    var pkgLabel by rememberSaveable {
        mutableStateOf(existing?.targetPackage?.let { app.appRepository.getAppLabel(it) })
    }
    var hourText by rememberSaveable {
        mutableStateOf("%02d".format(existing?.hour ?: 0))
    }
    var minuteText by rememberSaveable {
        mutableStateOf("%02d".format(existing?.minute ?: 0))
    }
    var daysMask by rememberSaveable {
        mutableIntStateOf(existing?.daysOfWeek?.takeIf { it != 0 } ?: TaskEntity.EVERY_DAY)
    }
    var repeatWeeks by rememberSaveable {
        mutableIntStateOf(existing?.repeatWeeks?.coerceIn(1, 4) ?: 1)
    }
    var showPicker by rememberSaveable { mutableStateOf(false) }

    val hourInt = hourText.toIntOrNull()
    val minuteInt = minuteText.toIntOrNull()
    val hourValid = hourInt != null && hourInt in 0..23
    val minuteValid = minuteInt != null && minuteInt in 0..59

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (isEditing) "Editar tarea" else "Nueva tarea",
                    style = MaterialTheme.typography.titleLarge
                )
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
                Text("Hora (formato 24 h)", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { new ->
                            if (new.length <= 2 && new.all { it.isDigit() }) {
                                hourText = new
                            }
                        },
                        label = { Text("HH") },
                        placeholder = { Text("00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = hourText.isNotEmpty() && !hourValid,
                        modifier = Modifier.width(90.dp)
                    )
                    Text(":", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 8.dp))
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { new ->
                            if (new.length <= 2 && new.all { it.isDigit() }) {
                                minuteText = new
                            }
                        },
                        label = { Text("MM") },
                        placeholder = { Text("00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = minuteText.isNotEmpty() && !minuteValid,
                        modifier = Modifier.width(90.dp)
                    )
                }
                if (hourText.isNotEmpty() && !hourValid) {
                    Text(
                        "Hora debe ser 00-23",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (minuteText.isNotEmpty() && !minuteValid) {
                    Text(
                        "Minutos deben ser 00-59",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("Frecuencia", style = MaterialTheme.typography.labelLarge)
                Row {
                    listOf(1, 2, 3, 4).forEach { n ->
                        FilterChip(
                            selected = repeatWeeks == n,
                            onClick = { repeatWeeks = n },
                            label = {
                                Text(
                                    when (n) {
                                        1 -> "Cada semana"
                                        2 -> "Cada 2 sem"
                                        3 -> "Cada 3 sem"
                                        4 -> "Cada 4 sem (~mes)"
                                        else -> "$n sem"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
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
                val canSave = (!needsPkg || pkg != null) && hourValid && minuteValid
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = canSave,
                        onClick = {
                            onSave(
                                TaskEntity(
                                    id = existing?.id ?: 0,
                                    action = action,
                                    targetPackage = if (action == ActionType.AIRPLANE_TOGGLE) null else pkg,
                                    hour = hourInt ?: 0,
                                    minute = minuteInt ?: 0,
                                    daysOfWeek = if (daysMask == 0) TaskEntity.EVERY_DAY else daysMask,
                                    enabled = existing?.enabled ?: true,
                                    lastRunAt = existing?.lastRunAt,
                                    lastResult = existing?.lastResult,
                                    pendingExecution = existing?.pendingExecution,
                                    repeatWeeks = repeatWeeks
                                )
                            )
                        }
                    ) { Text(if (isEditing) "Actualizar" else "Guardar") }
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
