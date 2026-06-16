package com.varro.hearing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.varro.hearing.ble.ConnectionState
import com.varro.hearing.data.HaAction

@Composable
fun RemoteScreen(vm: HearingViewModel) {
    val state by vm.aids.state.collectAsStateWithLifecycle()
    val name by vm.aids.deviceName.collectAsStateWithLifecycle()
    val volume by vm.aids.volume.collectAsStateWithLifecycle()
    val muted by vm.aids.muted.collectAsStateWithLifecycle()
    val program by vm.aids.activeProgram.collectAsStateWithLifecycle()
    val battery by vm.aids.battery.collectAsStateWithLifecycle()
    val error by vm.aids.lastError.collectAsStateWithLifecycle()
    val services by vm.aids.discovered.collectAsStateWithLifecycle()
    val eventLog by vm.aids.events.collectAsStateWithLifecycle()

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Remote", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Card(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    when (state) {
                        ConnectionState.READY -> "Connected: ${name ?: "aids"}"
                        ConnectionState.DISCONNECTED -> "Not connected"
                        else -> state.name.lowercase().replaceFirstChar { it.uppercase() }
                    },
                    fontWeight = FontWeight.SemiBold
                )
                battery?.let { Text("Battery: $it%") }
                program?.let { Text("Program: $it") }
                volume?.let { Text("Volume: $it / 255${if (muted) " (muted)" else ""}") }
                error?.let {
                    Text(
                        "⚠ $it",
                        color = androidx.compose.ui.graphics.Color(0xFFEF4444),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (state != ConnectionState.READY) {
                        Button(onClick = { vm.aids.retry() }, modifier = Modifier.padding(top = 6.dp)) {
                            Text("Retry connect")
                        }
                    }
                }

                if (state == ConnectionState.READY) {
                    OutlinedButton(onClick = { vm.disconnect() }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Disconnect")
                    }
                } else {
                    // Load lazily (after permission) so first launch never touches Bluetooth without it.
                    var devices by remember { mutableStateOf(emptyList<BondedDevice>()) }
                    Text("Paired devices:", Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = { devices = vm.bondedDevices() }, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Show paired devices")
                    }
                    if (devices.isEmpty()) Text("Tap above after granting Bluetooth permission. Pair your aids in Android Settings first.")
                    devices.forEach { d ->
                        OutlinedButton(onClick = { vm.connect(d.address, d.name) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text("Connect ${d.name}")
                        }
                    }
                }
            }
        }

        // Phone-routed volume: uses Android's audio system over the connection the phone
        // already has to the aids. Works without our own BLE connection — no 147 conflict.
        Card(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Phone volume", fontWeight = FontWeight.SemiBold)
                Text(
                    "Adjusts the aids through the phone's audio (works while they're connected to the phone).",
                    fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.Gray
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.phoneVolumeDown() }) { Text("– Vol") }
                    Button(onClick = { vm.phoneVolumeUp() }) { Text("+ Vol") }
                    OutlinedButton(onClick = { vm.phoneMuteToggle() }) { Text("Mute") }
                }
            }
        }

        val enabled = state == ConnectionState.READY

        Text("Direct (GATT) volume", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.apply(HaAction.VOLUME_DOWN) }, enabled = enabled) { Text("– Vol") }
            Button(onClick = { vm.apply(HaAction.VOLUME_UP) }, enabled = enabled) { Text("+ Vol") }
            OutlinedButton(onClick = { vm.apply(HaAction.MUTE) }, enabled = enabled) { Text("Mute") }
            OutlinedButton(onClick = { vm.apply(HaAction.UNMUTE) }, enabled = enabled) { Text("Unmute") }
        }

        var sliderPos by remember(volume) { mutableFloatStateOf((volume ?: 128).toFloat()) }
        Text("Set level: ${sliderPos.toInt()}")
        Slider(
            value = sliderPos, onValueChange = { sliderPos = it },
            valueRange = 0f..255f, enabled = enabled,
            onValueChangeFinished = { vm.apply(HaAction.SET_VOLUME, sliderPos.toInt()) }
        )

        Text("Program", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.apply(HaAction.SET_PROGRAM, ((program ?: 1) - 1).coerceAtLeast(0)) }, enabled = enabled) { Text("‹ Prev") }
            Button(onClick = { vm.apply(HaAction.NEXT_PROGRAM) }, enabled = enabled) { Text("Next ›") }
            OutlinedButton(onClick = { vm.aids.listPresets() }, enabled = enabled) { Text("List") }
        }

        // ---- Diagnostics: what the aids actually expose + live event log ----
        if (services.isNotEmpty() || eventLog.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Diagnostics", fontWeight = FontWeight.SemiBold)
                    if (services.isNotEmpty()) {
                        Text("GATT services found:", Modifier.padding(top = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        services.forEach { Text(it, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp) }
                    }
                    if (eventLog.isNotEmpty()) {
                        Text("Event log:", Modifier.padding(top = 10.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        eventLog.takeLast(16).forEach {
                            Text(it, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
