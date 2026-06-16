package com.varro.hearing.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.varro.hearing.HearingApp
import kotlinx.coroutines.launch

/** Wakes on a scheduled alarm, applies the action, then re-arms the next occurrence. */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Scheduler.EXTRA_ID, -1)
        if (id < 0) return
        val app = context.applicationContext as HearingApp
        val pending = goAsync()
        app.scope.launch {
            try {
                val schedule = app.db.scheduleDao().byId(id) ?: return@launch
                ActionRunner.run(context, schedule.action, schedule.param, "schedule", schedule.label)
                Scheduler.arm(context, schedule) // re-arm for the next matching day
            } finally {
                pending.finish()
            }
        }
    }
}
