package com.varro.hearing.automation

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.varro.hearing.data.Place

/** Registers saved places as geofences so entering one can change settings while the app is closed. */
object GeofenceManager {

    @SuppressLint("MissingPermission")
    fun register(context: Context, places: List<Place>) {
        val client = LocationServices.getGeofencingClient(context)
        val fences = places.filter { it.enabled }.map { p ->
            Geofence.Builder()
                .setRequestId(p.id.toString())
                .setCircularRegion(p.latitude, p.longitude, p.radiusMeters)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }
        if (fences.isEmpty()) { client.removeGeofences(pendingIntent(context)); return }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(fences)
            .build()
        client.addGeofences(request, pendingIntent(context))
    }

    fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
}
