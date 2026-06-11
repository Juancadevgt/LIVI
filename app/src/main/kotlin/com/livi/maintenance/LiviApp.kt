package com.livi.maintenance

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.getSystemService
import com.livi.maintenance.data.AppDatabase
import com.livi.maintenance.data.AppRepository
import com.livi.maintenance.data.DefaultTasks
import com.livi.maintenance.data.TaskRepository
import com.livi.maintenance.privileged.PolicyManager
import com.livi.maintenance.scheduler.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LiviApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: TaskRepository by lazy { TaskRepository(database.taskDao()) }
    val appRepository: AppRepository by lazy { AppRepository(this) }
    val policyManager: PolicyManager by lazy { PolicyManager(this) }
    val scheduler: Scheduler by lazy { Scheduler(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        seedDefaultTasksIfNeeded()
    }

    /**
     * En la primera ejecución (o tras un cambio de SEED_VERSION), inserta las
     * tareas predefinidas que IT configuró para todos los usuarios y las programa.
     * Esto hace que LIVI funcione "out of the box" tras instalarla por Intune:
     * el usuario solo otorga permisos y ya están las tareas listas.
     */
    private fun seedDefaultTasksIfNeeded() {
        val currentVersion = prefs.getString(KEY_SEED_VERSION, null)
        if (currentVersion == DefaultTasks.SEED_VERSION) return

        appScope.launch {
            DefaultTasks.all().forEach { defaultTask ->
                val newId = repository.upsert(defaultTask)
                repository.get(newId)?.let { scheduler.schedule(it) }
            }
            prefs.edit().putString(KEY_SEED_VERSION, DefaultTasks.SEED_VERSION).apply()
        }
    }

    /**
     * Borra todas las tareas y vuelve a insertar las predefinidas. Usado desde
     * el botón "Restablecer tareas de fábrica" en el modo Admin.
     */
    fun resetToDefaultTasks(onComplete: () -> Unit = {}) {
        appScope.launch {
            repository.getAll().forEach {
                scheduler.cancel(it.id)
                repository.delete(it)
            }
            DefaultTasks.all().forEach { defaultTask ->
                val newId = repository.upsert(defaultTask)
                repository.get(newId)?.let { scheduler.schedule(it) }
            }
            prefs.edit().putString(KEY_SEED_VERSION, DefaultTasks.SEED_VERSION).apply()
            onComplete()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                getString(R.string.notification_channel_id),
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService<NotificationManager>()?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val PREFS_NAME = "livi_prefs"
        private const val KEY_SEED_VERSION = "default_tasks_seed_version"
    }
}
