package com.varro.hearing.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.varro.hearing.data.Schedule
import java.util.Calendar

/** Arms exact alarms for time-of-day rules and re-arms them after each fire. */
object Scheduler {

    const val ACTION_FIRE = "com.varro.hearing.SCHEDULE_FIRE"
    const val EXTRA_ID = "schedule_id"

    fun arm(context: Context, schedule: Schedule) {
        if (!schedule.enabled) { cancel(context, schedule.id); return }
        val am = context.getSystemService(AlarmManager::class.java)
        val triggerAt = nextTrigger(schedule)
        val pi = pendingIntent(context, schedule.id)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pendingIntent(context, id))
    }

    suspend fun armAll(context: Context, schedules: List<Schedule>) {
        schedules.forEach { arm(context, it) }
    }

    private fun pendingIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            context, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Next future timestamp matching the rule's time and day mask (0 = every day). */
    fun nextTrigger(s: Schedule): Long {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, s.hour)
            set(Calendar.MINUTE, s.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        repeat(8) {
            val dowBit = 1 shl (cal.get(Calendar.DAY_OF_WEEK) - 1) // Sun=bit0
            val dayOk = s.daysMask == 0 || (s.daysMask and dowBit) != 0
            if (dayOk && cal.timeInMillis > now.timeInMillis) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
