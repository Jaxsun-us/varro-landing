package com.varro.hearing.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, DISCOVERING, READY }

/** Direction of a logged GATT message, mirrored from the web prototype. */
enum class LogDir { IN, OUT, EVENT }

data class GattLog(
    val time: Long = System.currentTimeMillis(),
    val dir: LogDir,
    val what: String,
    val hex: String = "",
)

/**
 * Wraps a single hearing-aid GATT connection and exposes the standard Volume Control,
 * Hearing Access and Battery operations. All GATT calls are serialized through a queue
 * because Android allows only one outstanding operation at a time.
 */
@SuppressLint("MissingPermission")
class HearingAidManager(private val appContext: Context) {

    private val tag = "HearingAidManager"
    private var gatt: BluetoothGatt? = null
    private val opQueue = ArrayDeque<() -> Unit>()
    private var opInFlight = false

    private val chars = mutableMapOf<java.util.UUID, BluetoothGattCharacteristic>()

    val state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val deviceName = MutableStateFlow<String?>(null)
    val volume = MutableStateFlow<Int?>(null)        // 0..255
    val muted = MutableStateFlow(false)
    val changeCounter = MutableStateFlow(0)
    val activeProgram = MutableStateFlow<Int?>(null)
    val battery = MutableStateFlow<Int?>(null)       // 0..100
    val logs = MutableSharedFlow<GattLog>(extraBufferCapacity = 256)

    private fun emit(dir: LogDir, what: String, bytes: ByteArray? = null) {
        logs.tryEmit(GattLog(dir = dir, what = what, hex = bytes?.toHex() ?: ""))
    }

    // ---- connection -------------------------------------------------------
    fun connect(address: String) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val device: BluetoothDevice = adapter.getRemoteDevice(address)
        state.value = ConnectionState.CONNECTING
        deviceName.value = device.name
        gatt = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        chars.clear()
        opQueue.clear()
        opInFlight = false
        state.value = ConnectionState.DISCONNECTED
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                state.value = ConnectionState.DISCOVERING
                emit(LogDir.EVENT, "connected")
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                emit(LogDir.EVENT, "disconnected")
                state.value = ConnectionState.DISCONNECTED
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            chars.clear()
            for (svc in g.services) for (c in svc.characteristics) chars[c.uuid] = c
            state.value = ConnectionState.READY
            emit(LogDir.EVENT, "services discovered (${chars.size} characteristics)")
            // Prime readouts and subscribe to live updates.
            chars[HaUuids.VOLUME_STATE]?.let { read(it) ; subscribe(it) }
            chars[HaUuids.HA_ACTIVE_PRESET_INDEX]?.let { read(it); subscribe(it) }
            chars[HaUuids.HA_PRESET_CONTROL_POINT]?.let { subscribe(it) }
            chars[HaUuids.BATTERY_LEVEL]?.let { read(it); subscribe(it) }
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            handleValue(c, value); finishOp()
        }

        @Deprecated("Deprecated for Android 13+, kept for API 26–32")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION") handleValue(c, c.value ?: ByteArray(0)); finishOp()
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            finishOp()
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            handleValue(c, value)
        }

        @Deprecated("Deprecated for Android 13+, kept for API 26–32")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION") handleValue(c, c.value ?: ByteArray(0))
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { finishOp() }
    }

    private fun handleValue(c: BluetoothGattCharacteristic, value: ByteArray) {
        emit(LogDir.IN, c.uuid.short(), value)
        when (c.uuid) {
            HaUuids.VOLUME_STATE -> if (value.size >= 3) {
                volume.value = value[0].toInt() and 0xFF
                muted.value = value[1].toInt() != 0
                changeCounter.value = value[2].toInt() and 0xFF
            }
            HaUuids.HA_ACTIVE_PRESET_INDEX -> if (value.isNotEmpty())
                activeProgram.value = value[0].toInt() and 0xFF
            HaUuids.BATTERY_LEVEL -> if (value.isNotEmpty())
                battery.value = value[0].toInt() and 0xFF
        }
    }

    // ---- public commands --------------------------------------------------
    fun volumeUp() = vcp(HaUuids.VCP_REL_UP)
    fun volumeDown() = vcp(HaUuids.VCP_REL_DOWN)
    fun mute() = vcp(HaUuids.VCP_MUTE)
    fun unmute() = vcp(HaUuids.VCP_UNMUTE)
    fun setVolume(level: Int) = vcp(HaUuids.VCP_SET_ABSOLUTE, byteArrayOf((level.coerceIn(0, 255)).toByte()))

    fun nextProgram() = preset(byteArrayOf(HaUuids.HAS_SET_NEXT))
    fun previousProgram() = preset(byteArrayOf(HaUuids.HAS_SET_PREVIOUS))
    fun setProgram(index: Int) = preset(byteArrayOf(HaUuids.HAS_SET_ACTIVE, index.toByte()))
    fun listPresets() = preset(byteArrayOf(HaUuids.HAS_READ_PRESETS, 0x01, 0xFF.toByte()))

    /** Write an arbitrary payload to any characteristic — powers the capture/replay tool. */
    fun writeRaw(uuid: java.util.UUID, bytes: ByteArray) {
        chars[uuid]?.let { write(it, bytes) }
    }

    private fun vcp(opcode: Byte, extra: ByteArray = ByteArray(0)) {
        val cp = chars[HaUuids.VOLUME_CONTROL_POINT] ?: return
        // Re-read state for a fresh change counter, then write.
        chars[HaUuids.VOLUME_STATE]?.let { read(it) }
        enqueue {
            val payload = byteArrayOf(opcode, changeCounter.value.toByte()) + extra
            doWrite(cp, payload)
        }
    }

    private fun preset(payload: ByteArray) {
        val cp = chars[HaUuids.HA_PRESET_CONTROL_POINT] ?: return
        write(cp, payload)
    }

    // ---- low-level serialized GATT ops -----------------------------------
    private fun read(c: BluetoothGattCharacteristic) = enqueue { gatt?.readCharacteristic(c) }

    private fun write(c: BluetoothGattCharacteristic, bytes: ByteArray) = enqueue { doWrite(c, bytes) }

    private fun doWrite(c: BluetoothGattCharacteristic, bytes: ByteArray) {
        emit(LogDir.OUT, c.uuid.short(), bytes)
        val type =
            if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt?.writeCharacteristic(c, bytes, type)
        } else {
            @Suppress("DEPRECATION") c.value = bytes
            @Suppress("DEPRECATION") c.writeType = type
            @Suppress("DEPRECATION") gatt?.writeCharacteristic(c)
        }
    }

    private fun subscribe(c: BluetoothGattCharacteristic) = enqueue {
        gatt?.setCharacteristicNotification(c, true)
        val cccd = c.getDescriptor(HaUuids.CCCD)
        if (cccd != null) {
            val value =
                if (c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt?.writeDescriptor(cccd, value)
            } else {
                @Suppress("DEPRECATION") cccd.value = value
                @Suppress("DEPRECATION") gatt?.writeDescriptor(cccd)
            }
        } else finishOp()
    }

    @Synchronized private fun enqueue(op: () -> Unit) {
        opQueue.add(op)
        if (!opInFlight) next()
    }

    @Synchronized private fun next() {
        val op = opQueue.poll() ?: return
        opInFlight = true
        try { op() } catch (e: Exception) { Log.e(tag, "op failed", e); finishOp() }
    }

    @Synchronized private fun finishOp() {
        opInFlight = false
        next()
    }
}

private fun ByteArray.toHex(): String = joinToString(" ") { "%02x".format(it) }
private fun java.util.UUID.short(): String {
    val s = toString()
    return if (s.startsWith("0000") && s.endsWith("-0000-1000-8000-00805f9b34fb"))
        "0x" + s.substring(4, 8) else s
}
