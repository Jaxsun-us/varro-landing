package com.varro.hearing.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import com.varro.hearing.data.HaAction
import com.varro.hearing.data.Place
import com.varro.hearing.data.Schedule

@Composable
fun AutomationScreen(vm: HearingViewModel) {
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    var showSchedule by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Automation", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Text("Schedules", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        Text("Runs even when the app is closed.", color = androidx.compose.ui.graphics.Color.Gray)
        schedules.forEach { s ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("%02d:%02d  %s".format(s.hour, s.minute, s.label), fontWeight = FontWeight.SemiBold)
                        Text("${com.varro.hearing.automation.ActionRunner.label(s.action, s.param)}")
                    }
                    Switch(checked = s.enabled, onCheckedChange = { vm.saveSchedule(s.copy(enabled = it)) })
                    TextButton(onClick = { vm.deleteSchedule(s) }) { Text("Delete") }
                }
            }
        }
        OutlinedButton(onClick = { showSchedule = true }, modifier = Modifier.padding(top = 4.dp)) { Text("+ Add schedule") }

        Text("Places (geofences)", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp))
        Text("Switch settings when you arrive somewhere.", color = androidx.compose.ui.graphics.Color.Gray)
        places.forEach { p ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.label, fontWeight = FontWeight.SemiBold)
                        Text("On enter: ${com.varro.hearing.automation.ActionRunner.label(p.onEnter, p.param)}")
                    }
                    TextButton(onClick = { vm.deletePlace(p) }) { Text("Delete") }
                }
            }
        }
        OutlinedButton(
            onClick = { addCurrentPlace(ctx) { lat, lon -> vm.savePlace(Place(label = "Saved place", latitude = lat, longitude = lon, onEnter = HaAction.NEXT_PROGRAM)) } },
            modifier = Modifier.padding(top = 4.dp)
        ) { Text("+ Save current location") }
    }

    if (showSchedule) {
        ScheduleDialog(onDismiss = { showSchedule = false }) { s ->
            vm.saveSchedule(s); showSchedule = false
        }
    }
}

@Composable
private fun ScheduleDialog(onDismiss: () -> Unit, onSave: (Schedule) -> Unit) {
    var hour by remember { mutableStateOf("19") }
    var minute by remember { mutableStateOf("00") }
    var label by remember { mutableStateOf("Evening") }
    var action by remember { mutableStateOf(HaAction.NEXT_PROGRAM) }
    var param by remember { mutableStateOf("0") }
    var menu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    Schedule(
                        label = label, hour = hour.toIntOrNull()?.coerceIn(0, 23) ?: 0,
                        minute = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0, daysMask = 0,
                        action = action, param = param.toIntOrNull() ?: 0
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("New schedule") },
        text = {
            Column {
                OutlinedTextField(label, { label = it }, label = { Text("Label") })
                Row {
                    OutlinedTextField(hour, { hour = it }, label = { Text("Hour") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(minute, { minute = it }, label = { Text("Min") }, modifier = Modifier.weight(1f))
                }
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(action.name)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    HaAction.entries.forEach { a ->
                        DropdownMenuItem(text = { Text(a.name) }, onClick = { action = a; menu = false })
                    }
                }
                if (action == HaAction.SET_VOLUME || action == HaAction.SET_PROGRAM) {
                    OutlinedTextField(param, { param = it }, label = { Text(if (action == HaAction.SET_VOLUME) "Level 0-255" else "Program index") })
                }
            }
        }
    )
}

@SuppressLint("MissingPermission")
private fun addCurrentPlace(ctx: android.content.Context, onLocation: (Double, Double) -> Unit) {
    LocationServices.getFusedLocationProviderClient(ctx).lastLocation
        .addOnSuccessListener { loc -> if (loc != null) onLocation(loc.latitude, loc.longitude) }
}
