package com.varro.hearing

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.varro.hearing.ble.HearingAidManager
import com.varro.hearing.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** App-wide singletons. One GATT connection is shared by UI, service, and automation. */
class HearingApp : Application() {

    val scope = CoroutineScope(SupervisorJob())
    lateinit var aids: HearingAidManager
        private set
    val db: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        aids = HearingAidManager(this)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Hearing aid connection", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val CHANNEL_ID = "ha_connection"
        lateinit var instance: HearingApp
            private set
    }
}

/** Tiny persisted store for the paired aid's address (so automation can reconnect). */
object Prefs {
    private const val FILE = "hearing_prefs"
    private const val KEY_ADDRESS = "aid_address"
    fun saveAddress(ctx: Context, address: String) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_ADDRESS, address).apply()
    fun address(ctx: Context): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_ADDRESS, null)
}
