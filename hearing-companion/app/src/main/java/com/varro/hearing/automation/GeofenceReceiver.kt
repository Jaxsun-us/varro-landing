package com.varro.hearing.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.GeofencingEvent
import com.varro.hearing.HearingApp
import kotlinx.coroutines.launch

/** Applies a place's action when the user enters its geofence. */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val triggered = event.triggeringGeofences ?: return
        val app = context.applicationContext as HearingApp
        val pending = goAsync()
        app.scope.launch {
            try {
                for (fence in triggered) {
                    val place = app.db.placeDao().byId(fence.requestId.toLongOrNull() ?: continue) ?: continue
                    ActionRunner.run(context, place.onEnter, place.param, "geofence", place.label)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
