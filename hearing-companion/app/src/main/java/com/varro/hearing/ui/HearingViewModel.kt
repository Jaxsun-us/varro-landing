package com.varro.hearing.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.varro.hearing.HearingApp
import com.varro.hearing.Prefs
import com.varro.hearing.automation.ActionRunner
import com.varro.hearing.automation.GeofenceManager
import com.varro.hearing.automation.Scheduler
import com.varro.hearing.audio.ToneGenerator
import com.varro.hearing.ble.BleService
import com.varro.hearing.data.HaAction
import com.varro.hearing.data.Place
import com.varro.hearing.data.Schedule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BondedDevice(val name: String, val address: String)

class HearingViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx get() = getApplication<HearingApp>()
    val aids get() = appCtx.aids
    val tones = ToneGenerator(viewModelScope)

    val diary = appCtx.db.diaryDao().recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val schedules = appCtx.db.scheduleDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val places = appCtx.db.placeDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<BondedDevice> = try {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        adapter.bondedDevices.orEmpty().map { BondedDevice(it.name ?: it.address, it.address) }
    } catch (e: SecurityException) {
        emptyList() // BLUETOOTH_CONNECT not granted yet
    } catch (e: Exception) {
        emptyList()
    }

    fun connect(address: String, name: String? = null) {
        Prefs.saveAddress(appCtx, address)
        aids.connect(address, name)
        appCtx.startService(Intent(appCtx, BleService::class.java))
    }
    fun disconnect() {
        aids.disconnect()
        appCtx.stopService(Intent(appCtx, BleService::class.java))
    }

    // Manual controls route through ActionRunner so they land in the diary too.
    fun apply(action: HaAction, param: Int = 0) =
        viewModelScope.launch { ActionRunner.run(appCtx, action, param, "manual") }

    // ---- automation editing ----------------------------------------------
    fun saveSchedule(s: Schedule) = viewModelScope.launch {
        val id = appCtx.db.scheduleDao().upsert(s)
        Scheduler.arm(appCtx, s.copy(id = if (s.id == 0L) id else s.id))
    }
    fun deleteSchedule(s: Schedule) = viewModelScope.launch {
        Scheduler.cancel(appCtx, s.id); appCtx.db.scheduleDao().delete(s)
    }
    fun savePlace(p: Place) = viewModelScope.launch {
        appCtx.db.placeDao().upsert(p)
        GeofenceManager.register(appCtx, appCtx.db.placeDao().enabled())
    }
    fun deletePlace(p: Place) = viewModelScope.launch {
        appCtx.db.placeDao().delete(p)
        GeofenceManager.register(appCtx, appCtx.db.placeDao().enabled())
    }

    fun clearDiary() = viewModelScope.launch { appCtx.db.diaryDao().clear() }

    // ---- A/B blind comparison --------------------------------------------
    var abChoice: Int = 0 ; private set   // which of the two is currently playing (hidden)
    fun abPlay(programA: Int, programB: Int) {
        abChoice = if (Math.random() < 0.5) programA else programB
        apply(HaAction.SET_PROGRAM, abChoice)
    }
}
