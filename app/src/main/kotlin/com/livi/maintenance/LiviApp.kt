package com.livi.maintenance

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.content.getSystemService
import com.livi.maintenance.data.AppDatabase
import com.livi.maintenance.data.TaskRepository
import com.livi.maintenance.scheduler.Scheduler

class LiviApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: TaskRepository by lazy { TaskRepository(database.taskDao()) }
    val scheduler: Scheduler by lazy { Scheduler(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
}
