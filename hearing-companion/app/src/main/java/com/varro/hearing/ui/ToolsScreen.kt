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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ToolsScreen(vm: HearingViewModel) {
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Tools", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        // ---- A/B blind comparison ----
        Card(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("A/B blind comparison", fontWeight = FontWeight.SemiBold)
                Text("Plays one of two programs at random without telling you which — pick what sounds better, then reveal.")
                var a by remember { mutableStateOf("1") }
                var b by remember { mutableStateOf("2") }
                var revealed by remember { mutableStateOf<String?>(null) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(a, { a = it }, label = { Text("Program A") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(b, { b = it }, label = { Text("Program B") }, modifier = Modifier.weight(1f))
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        revealed = null
                        vm.abPlay(a.toIntOrNull() ?: 1, b.toIntOrNull() ?: 2)
                    }) { Text("Play random") }
                    OutlinedButton(onClick = {
                        revealed = "Now playing program ${vm.abChoice}"
                    }) { Text("Reveal") }
                }
                revealed?.let { Text(it, Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold) }
            }
        }

        // ---- Tone / chime generator ----
        Card(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Tone / chime generator", fontWeight = FontWeight.SemiBold)
                Text("Plays through the phone's audio output. Make the aids the active audio device to hear it in them. Not a GATT command.")
                var freq by remember { mutableStateOf("1000") }
                var dur by remember { mutableStateOf("400") }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(freq, { freq = it }, label = { Text("Hz") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(dur, { dur = it }, label = { Text("ms") }, modifier = Modifier.weight(1f))
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.tones.playTone(freq.toDoubleOrNull() ?: 1000.0, dur.toIntOrNull() ?: 400) }) { Text("Play tone") }
                    OutlinedButton(onClick = { vm.tones.playChime() }) { Text("Chime") }
                    OutlinedButton(onClick = { vm.tones.playSweep() }) { Text("Sweep") }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(250, 500, 1000, 2000, 4000, 8000).forEach { f ->
                        OutlinedButton(onClick = { vm.tones.playTone(f.toDouble(), 350) }) { Text(if (f >= 1000) "${f / 1000}k" else "$f") }
                    }
                }
            }
        }

        // ---- Tinnitus masker ----
        Card(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Tinnitus masking noise", fontWeight = FontWeight.SemiBold)
                Text("Comfort tool only — not medical treatment. Keep the level low.")
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.tones.startNoise() }) { Text("Start noise") }
                    OutlinedButton(onClick = { vm.tones.stopNoise() }) { Text("Stop") }
                }
            }
        }
    }
}
