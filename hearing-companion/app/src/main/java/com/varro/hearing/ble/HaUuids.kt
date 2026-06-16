package com.varro.hearing.ble

import java.util.UUID

/** Standard Bluetooth SIG UUIDs used by hearing aids that support LE Audio. */
object HaUuids {
    private fun u16(short: String): UUID =
        UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")

    // Volume Control Service (VCS)
    val VOLUME_CONTROL_SERVICE: UUID = u16("1844")
    val VOLUME_STATE: UUID = u16("2b7d")          // read/notify: [volume, mute, changeCounter]
    val VOLUME_CONTROL_POINT: UUID = u16("2b7e")  // write: [opcode, changeCounter, (param)]

    // Hearing Access Service (HAS)
    val HEARING_ACCESS_SERVICE: UUID = u16("1854")
    val HA_FEATURES: UUID = u16("2bda")
    val HA_PRESET_CONTROL_POINT: UUID = u16("2bdb") // write/indicate
    val HA_ACTIVE_PRESET_INDEX: UUID = u16("2bdc")  // read/notify

    // Battery Service
    val BATTERY_SERVICE: UUID = u16("180f")
    val BATTERY_LEVEL: UUID = u16("2a19")           // read/notify: [percent 0-100]

    // Client Characteristic Configuration Descriptor (enables notify/indicate)
    val CCCD: UUID = u16("2902")

    // VCS Volume Control Point opcodes
    const val VCP_REL_DOWN: Byte = 0x00
    const val VCP_REL_UP: Byte = 0x01
    const val VCP_UNMUTE_DOWN: Byte = 0x02
    const val VCP_UNMUTE_UP: Byte = 0x03
    const val VCP_SET_ABSOLUTE: Byte = 0x04
    const val VCP_UNMUTE: Byte = 0x05
    const val VCP_MUTE: Byte = 0x06

    // HAS Preset Control Point opcodes
    const val HAS_READ_PRESETS: Byte = 0x01
    const val HAS_SET_ACTIVE: Byte = 0x05
    const val HAS_SET_NEXT: Byte = 0x06
    const val HAS_SET_PREVIOUS: Byte = 0x07
}
