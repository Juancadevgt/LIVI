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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.livi.maintenance.BuildConfig
import com.livi.maintenance.LiviApp
import com.livi.maintenance.R
import com.livi.maintenance.accessibility.LiviAccessibilityService
import com.livi.maintenance.actions.ActionType
import com.livi.maintenance.admin.AdminMode
import com.livi.maintenance.data.TaskEntity
import com.livi.maintenance.identity.ManualIdentity
import com.livi.maintenance.identity.UserIdentity
import com.livi.maintenance.ui.theme.ThemePreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val tasks by viewModel.tasks.collectAsState()
    val context = LocalContext.current
    val isAdmin = AdminMode.isActive

    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editingId: Long? by rememberSaveable { mutableStateOf(null) }
    var deletingId: Long? by rememberSaveable { mutableStateOf(null) }
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    // Cargar identidad del usuario una sola vez (Intune config + fallback).
    val identity = remember { UserIdentity.load(context) }

    val editing = remember(editingId, tasks) {
        editingId?.let { id -> tasks.find { it.id == id } }
    }
    val deleting = remember(deletingId, tasks) {
        deletingId?.let { id -> tasks.find { it.id == id } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = if (isAdmin) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        titleContentColor = MaterialTheme.colorScheme.onErrorContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else TopAppBarDefaults.topAppBarColors(),
                actions = {
                    ThemeSwitch()
                    if (isAdmin) {
                        IconButton(onClick = { AdminMode.exit() }) {
                            Icon(
                                Icons.Default.LockOpen,
                                contentDescription = stringResource(R.string.admin_exit)
                            )
                        }
                    } else {
                        IconButton(onClick = { showPasswordDialog = true }) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = stringResource(R.string.admin_enter)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            if (isAdmin) {
                AdminBanner()
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { IdentityCard(identity) }
                item { PermissionsCard(context) }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            stringResource(R.string.title_tasks),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (!isAdmin) {
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        stringResource(R.string.managed_by_it),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }

                items(tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        readOnly = !isAdmin,
                        onClick = { if (isAdmin) editingId = task.id },
                        onRun = { viewModel.runNow(task) },
                        onToggle = { if (isAdmin) viewModel.upsert(task.copy(enabled = it)) },
                        onDelete = { if (isAdmin) deletingId = task.id }
                    )
                    HorizontalDivider()
                }

                if (isAdmin) {
                    item {
                        Box(modifier = Modifier.padding(16.dp)) {
                            OutlinedButton(
                                onClick = { showResetDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.admin_factory_reset))
                            }
                        }
                    }
                }

                item { ReportProblemButton(context, identity) }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showAdd && isAdmin) {
        AddTaskDialog(
            onDismiss = { showAdd = false },
            onSave = { newTask ->
                viewModel.upsert(newTask)
                showAdd = false
            }
        )
    }

    val currentlyEditing = editing
    if (currentlyEditing != null && isAdmin) {
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
    if (currentlyDeleting != null && isAdmin) {
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

    if (showPasswordDialog) {
        AdminPasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onSuccess = { showPasswordDialog = false }
        )
    }

    if (showResetDialog && isAdmin) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
            title = { Text(stringResource(R.string.admin_factory_reset)) },
            text = { Text(stringResource(R.string.admin_factory_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    (context.applicationContext as LiviApp).resetToDefaultTasks()
                    showResetDialog = false
                }) { Text("Restablecer") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

/**
 * Interruptor de modo claro/oscuro en la barra superior. El ícono interno
 * cambia (sol/luna) según el estado, y la elección se persiste en SharedPreferences.
 */
@Composable
private fun ThemeSwitch() {
    val context = LocalContext.current
    val isDark = ThemePreference.isDarkMode
    Switch(
        checked = isDark,
        onCheckedChange = { ThemePreference.setDarkMode(context, it) },
        thumbContent = {
            Icon(
                imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = if (isDark) "Modo oscuro" else "Modo claro",
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
        }
    )
    Spacer(Modifier.width(4.dp))
}

@Composable
private fun AdminBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                Icons.Default.LockOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.admin_mode_banner),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun IdentityCard(identity: UserIdentity.Identity) {
    val context = LocalContext.current
    val manualEmail = ManualIdentity.email
    val isAdmin = AdminMode.isActive
    var showDialog by rememberSaveable { mutableStateOf(false) }

    // Prioridad de fuentes: Intune > Manual > Ninguna.
    val effectiveLabel: String
    val effectiveSecondary: String
    val hasManualOnly: Boolean
    val hasNothing: Boolean
    when {
        identity.isIdentified -> {
            effectiveLabel = identity.displayLabel
            effectiveSecondary = identity.secondaryLabel
            hasManualOnly = false
            hasNothing = false
        }
        manualEmail.isNotBlank() -> {
            effectiveLabel = manualEmail
            effectiveSecondary = identity.model
            hasManualOnly = true
            hasNothing = false
        }
        else -> {
            effectiveLabel = identity.displayLabel  // "(Sin identificar)"
            effectiveSecondary = identity.model
            hasManualOnly = false
            hasNothing = true
        }
    }

    // Los controles SOLO aparecen en modo Admin. Usuario común solo visualiza.
    //  - Sin identidad + Admin  → ícono "+" para agregar
    //  - Con manual + Admin     → ícono lápiz para editar
    //  - Con Intune             → sin botón (Intune manda)
    val showAddButton = isAdmin && hasNothing
    val showEditButton = isAdmin && hasManualOnly

    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    effectiveLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    effectiveSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                if (identity.isIdentified) {
                    Text(
                        identity.model,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (showAddButton) {
                IconButton(onClick = { showDialog = true }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Agregar identificador",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (showEditButton) {
                IconButton(onClick = { showDialog = true }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar identificador",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (showDialog) {
        ManualIdentityDialog(
            initialEmail = manualEmail,
            isEditing = hasManualOnly,
            onDismiss = { showDialog = false },
            onSave = { email ->
                ManualIdentity.save(context, email)
                showDialog = false
            }
        )
    }
}

/**
 * Diálogo para que el Admin agregue o edite manualmente el correo del
 * usuario cuando Intune todavía no ha enviado la identidad via App
 * Configuration Policy.
 *
 * Validación: el correo debe contener "@" (el dominio se valida fuera,
 * porque varía por organización).
 */
@Composable
private fun ManualIdentityDialog(
    initialEmail: String,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Person, contentDescription = null) },
        title = { Text(if (isEditing) "Editar identificador" else "Agregar identificador") },
        text = {
            Column {
                Text(
                    "Escribe el correo electronico para identificar este dispositivo.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        errorMessage = null
                    },
                    label = { Text("Correo") },
                    placeholder = { Text("nombre@empresa.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = email.trim()
                when {
                    trimmed.isBlank() -> errorMessage = "El correo no puede estar vacio"
                    !trimmed.contains("@") -> errorMessage = "El correo debe contener @"
                    else -> onSave(trimmed)
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun ReportProblemButton(context: Context, identity: UserIdentity.Identity) {
    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        FilledTonalButton(
            onClick = { sendProblemReport(context, identity) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Email, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.report_problem))
        }
    }
}

private fun sendProblemReport(context: Context, identity: UserIdentity.Identity) {
    val subject = context.getString(
        R.string.report_problem_subject,
        identity.displayLabel
    )
    val body = buildString {
        appendLine(context.getString(R.string.report_problem_body_header))
        appendLine("─".repeat(30))
        appendLine("Usuario: ${identity.displayName.ifBlank { "(sin datos)" }}")
        appendLine("Correo: ${identity.email.ifBlank { "(sin datos)" }}")
        appendLine("UPN: ${identity.upn.ifBlank { "(sin datos)" }}")
        appendLine("Modelo: ${identity.model}")
        if (identity.serial.isNotBlank()) appendLine("Serial: ${identity.serial}")
        appendLine("LIVI versión: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("─".repeat(30))
        appendLine()
        appendLine("Describe el problema aquí:")
        appendLine()
    }
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:${identity.supportEmail}")
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Throwable) {
        // No hay app de correo instalada — silenciar
    }
}

@Composable
private fun AdminPasswordDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.admin_password_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.admin_password_dialog_msg))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.admin_password_hint)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (val result = AdminMode.tryEnter(password)) {
                        is AdminMode.Result.Success -> onSuccess()
                        is AdminMode.Result.WrongPassword -> {
                            errorMessage = context.getString(
                                R.string.admin_password_wrong,
                                result.attemptsLeft
                            )
                            password = ""
                        }
                        is AdminMode.Result.Locked -> {
                            errorMessage = context.getString(
                                R.string.admin_password_locked,
                                result.secondsRemaining
                            )
                            password = ""
                        }
                    }
                }
            ) { Text("Entrar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
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

    Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
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
    readOnly: Boolean,
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
    val rowModifier = if (readOnly) {
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    } else {
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = rowModifier
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
            task.lastResult?.let { result ->
                val ts = task.lastRunAt?.let { formatLastRun(it) }
                val text = if (ts != null) "Última: $result · $ts" else "Última: $result"
                Text(text, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (!readOnly) {
            Switch(checked = task.enabled, onCheckedChange = onToggle)
        } else if (!task.enabled) {
            AssistChip(
                onClick = {},
                label = { Text("Off", style = MaterialTheme.typography.labelSmall) }
            )
        }
        IconButton(onClick = onRun) {
            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.action_run_now))
        }
        if (!readOnly) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}

private val lastRunFormatter = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())

private fun formatLastRun(millis: Long): String =
    lastRunFormatter.format(java.util.Date(millis))

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
