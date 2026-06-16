package com.varro.hearing.automation

import android.content.Context
import com.varro.hearing.HearingApp
import com.varro.hearing.Prefs
import com.varro.hearing.ble.ConnectionState
import com.varro.hearing.data.DiaryEntry
import com.varro.hearing.data.HaAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Applies a single [HaAction] to the aids, connecting first if needed, and records it
 * in the usage diary. Shared by manual taps, schedules, geofences and the A/B tester.
 */
object ActionRunner {

    suspend fun run(context: Context, action: HaAction, param: Int, source: String, detail: String = "") {
        val app = context.applicationContext as HearingApp
        val aids = app.aids

        // Reconnect on demand for background triggers.
        if (aids.state.value != ConnectionState.READY) {
            val address = Prefs.address(context) ?: return logSkipped(app, action, source, "no paired aid")
            aids.connect(address)
            val ready = withTimeoutOrNull(8000) {
                aids.state.first { it == ConnectionState.READY }
            }
            if (ready == null) return logSkipped(app, action, source, "connect timed out")
        }

        when (action) {
            HaAction.VOLUME_UP -> aids.volumeUp()
            HaAction.VOLUME_DOWN -> aids.volumeDown()
            HaAction.MUTE -> aids.mute()
            HaAction.UNMUTE -> aids.unmute()
            HaAction.SET_VOLUME -> aids.setVolume(param)
            HaAction.SET_PROGRAM -> aids.setProgram(param)
            HaAction.NEXT_PROGRAM -> aids.nextProgram()
        }

        app.db.diaryDao().insert(
            DiaryEntry(source = source, action = label(action, param), detail = detail)
        )
    }

    private suspend fun logSkipped(app: HearingApp, action: HaAction, source: String, why: String) {
        app.db.diaryDao().insert(
            DiaryEntry(source = source, action = "Skipped: ${label(action, 0)}", detail = why)
        )
    }

    fun label(action: HaAction, param: Int): String = when (action) {
        HaAction.VOLUME_UP -> "Volume up"
        HaAction.VOLUME_DOWN -> "Volume down"
        HaAction.MUTE -> "Mute"
        HaAction.UNMUTE -> "Unmute"
        HaAction.SET_VOLUME -> "Set volume $param"
        HaAction.SET_PROGRAM -> "Program $param"
        HaAction.NEXT_PROGRAM -> "Next program"
    }
}
