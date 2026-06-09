package com.livi.maintenance.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.livi.maintenance.LiviApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val app = context.applicationContext as LiviApp
            app.scheduler.scheduleAll()
        }
    }
}
