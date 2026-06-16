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
    val lastError = MutableStateFlow<String?>(null)
    val discovered = MutableStateFlow<List<String>>(emptyList()) // GATT services/characteristics
    val events = MutableStateFlow<List<String>>(emptyList())      // live event log for on-screen diagnostics

    private var lastAddress: String? = null
    private var retries = 0
    private val maxRetries = 5
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var scanning = false
    private var targetName: String? = null
    private val scanTimeoutMs = 12000L
    private val scanTimeout = Runnable { onScanTimeout() }

    private fun emit(dir: LogDir, what: String, bytes: ByteArray? = null) {
        logs.tryEmit(GattLog(dir = dir, what = what, hex = bytes?.toHex() ?: ""))
        val tag = when (dir) { LogDir.IN -> "◀" ; LogDir.OUT -> "▶" ; LogDir.EVENT -> "•" }
        val line = "$tag $what${bytes?.let { "  " + it.toHex() } ?: ""}"
        events.value = (events.value + line).takeLast(80)
    }

    // ---- connection -------------------------------------------------------
    // Scan-then-connect, mirroring Chrome's Web Bluetooth: hearing aids rotate their
    // BLE address, so we find the live advertisement and connect to THAT device object
    // rather than the stored bonded address (which yields "busy"/147).
    fun connect(address: String, name: String? = null) {
        handler.removeCallbacksAndMessages(null)
        lastAddress = address
        targetName = name
        retries = 0
        lastError.value = null
        startScan()
    }

    /** Manual retry from the UI; restarts the scan/back-off sequence. */
    fun retry() {
        if (lastAddress == null) return
        handler.removeCallbacksAndMessages(null)
        retries = 0
        lastError.value = null
        startScan()
    }

    private fun startScan() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            lastError.value = "Bluetooth is off — turn it on and tap Retry."
            state.value = ConnectionState.DISCONNECTED
            return
        }
        state.value = ConnectionState.CONNECTING
        emit(LogDir.EVENT, "scanning for hearing aid…")
        val settings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner!!.startScan(null, settings, scanCallback)
            scanning = true
            handler.postDelayed(scanTimeout, scanTimeoutMs)
        } catch (e: Exception) {
            lastError.value = "Scan failed: ${e.message}"
            state.value = ConnectionState.DISCONNECTED
        }
    }

    private fun stopScan() {
        handler.removeCallbacks(scanTimeout)
        if (scanning) {
            try { scanner?.stopScan(scanCallback) } catch (e: Exception) {}
            scanning = false
        }
    }

    private val scanCallback = object : android.bluetooth.le.ScanCallback() {
        override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
            val dev = result.device
            if (matchesTarget(dev)) {
                emit(LogDir.EVENT, "found ${safeName(dev) ?: dev.address}")
                stopScan()
                openGatt(dev, autoConnect = false)
            }
        }
        override fun onScanFailed(errorCode: Int) {
            scanning = false
            lastError.value = "Scan failed (code $errorCode). Tap Retry."
            state.value = ConnectionState.DISCONNECTED
        }
    }

    private fun matchesTarget(dev: BluetoothDevice): Boolean {
        val a = lastAddress
        if (a != null && dev.address.equals(a, ignoreCase = true)) return true
        val n = safeName(dev) ?: return false
        val t = targetName
        if (t != null && n.equals(t, ignoreCase = true)) return true
        return n.contains("phonak", ignoreCase = true)
    }

    private fun safeName(dev: BluetoothDevice): String? =
        try { dev.name } catch (e: SecurityException) { null }

    private fun onScanTimeout() {
        if (!scanning) return
        stopScan()
        if (retries < maxRetries) {
            retries++
            lastError.value = "Aid not advertising yet — retry $retries/$maxRetries…"
            handler.postDelayed({ startScan() }, 1500L)
        } else {
            lastError.value = "Couldn't find the hearing aid advertising. Make sure it's on, close to the phone, and not in a call. Tap Retry."
            state.value = ConnectionState.DISCONNECTED
        }
    }

    private fun openGatt(device: BluetoothDevice, autoConnect: Boolean) {
        state.value = ConnectionState.CONNECTING
        deviceName.value = safeName(device)
        gatt?.close() // close any stale client before opening a new one
        gatt = device.connectGatt(appContext, autoConnect, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        handler.removeCallbacksAndMessages(null)
        retries = maxRetries // stop any in-flight retry loop
        stopScan()
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
            if (status != BluetoothGatt.GATT_SUCCESS) {
                emit(LogDir.EVENT, "connect failed status=$status")
                lastError.value = "Connect failed: ${gattStatusText(status)} (status $status)"
                g.close()
                if (gatt === g) gatt = null
                state.value = ConnectionState.DISCONNECTED
                // "Busy / too many connections" (147), 133, and timeouts often clear once a
                // Bluetooth slot frees up — back off and keep retrying with patient autoConnect.
                if (lastAddress != null && retries < maxRetries) {
                    retries++
                    val delayMs = 1500L * retries
                    lastError.value = "${gattStatusText(status)} — retrying ($retries/$maxRetries)…"
                    emit(LogDir.EVENT, "retry $retries in ${delayMs}ms")
                    handler.postDelayed({ startScan() }, delayMs) // re-scan for the live advert
                } else if (lastAddress != null) {
                    lastError.value = "${gattStatusText(status)} (status $status). Tap Retry."
                }
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                retries = 0
                lastError.value = null
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
            val list = mutableListOf<String>()
            for (svc in g.services) {
                list.add("▸ service ${svc.uuid.short()}")
                for (c in svc.characteristics) {
                    chars[c.uuid] = c
                    val p = c.properties
                    val f = buildString {
                        if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) append("R")
                        if (p and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) append("W")
                        if (p and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) append("N")
                    }
                    list.add("    ${c.uuid.short()} [$f]")
                }
            }
            discovered.value = list
            val hasVcs = chars.containsKey(HaUuids.VOLUME_CONTROL_POINT)
            val hasHas = chars.containsKey(HaUuids.HA_PRESET_CONTROL_POINT)
            state.value = ConnectionState.READY
            emit(LogDir.EVENT, "discovered ${chars.size} chars · volume:${if (hasVcs) "yes" else "NO"} · programs:${if (hasHas) "yes" else "NO"}")
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
        val cp = chars[HaUuids.VOLUME_CONTROL_POINT]
        if (cp == null) {
            lastError.value = "These aids don't expose the standard Volume Control (0x1844) on this connection."
            emit(LogDir.EVENT, "no Volume Control Point (0x2b7e)")
            return
        }
        // Re-read state for a fresh change counter, then write.
        chars[HaUuids.VOLUME_STATE]?.let { read(it) }
        enqueue {
            val payload = byteArrayOf(opcode, changeCounter.value.toByte()) + extra
            doWrite(cp, payload)
        }
    }

    private fun preset(payload: ByteArray) {
        val cp = chars[HaUuids.HA_PRESET_CONTROL_POINT]
        if (cp == null) {
            lastError.value = "These aids don't expose the standard Hearing Access programs (0x1854) on this connection."
            emit(LogDir.EVENT, "no Preset Control Point (0x2bdb)")
            return
        }
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

/** Friendly text for the common Android GATT connection status codes. */
private fun gattStatusText(status: Int): String = when (status) {
    8 -> "link lost / out of range (timeout)"
    19 -> "the aid closed the connection (it refused)"
    22 -> "phone ended the connection"
    34 -> "connection timeout"
    62 -> "connection failed to establish"
    133 -> "generic GATT failure (133) — retrying"
    147 -> "too many connections / busy"
    else -> "error code $status"
}

private fun ByteArray.toHex(): String = joinToString(" ") { "%02x".format(it) }
private fun java.util.UUID.short(): String {
    val s = toString()
    return if (s.startsWith("0000") && s.endsWith("-0000-1000-8000-00805f9b34fb"))
        "0x" + s.substring(4, 8) else s
}
