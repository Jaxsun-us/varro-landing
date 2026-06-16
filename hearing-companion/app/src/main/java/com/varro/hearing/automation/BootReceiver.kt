package com.varro.hearing.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.varro.hearing.HearingApp
import kotlinx.coroutines.launch

/** Re-arms all schedules and geofences after the phone reboots. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as HearingApp
        val pending = goAsync()
        app.scope.launch {
            try {
                Scheduler.armAll(context, app.db.scheduleDao().enabled())
                GeofenceManager.register(context, app.db.placeDao().enabled())
            } finally {
                pending.finish()
            }
        }
    }
}
