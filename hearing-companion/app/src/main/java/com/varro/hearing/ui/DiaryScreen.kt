package com.varro.hearing.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiaryScreen(vm: HearingViewModel) {
    val diary by vm.diary.collectAsStateWithLifecycle()
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Usage diary", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Every change, manual or automated. Bring it to your audiologist.")
        OutlinedButton(onClick = { vm.clearDiary() }, modifier = Modifier.padding(vertical = 8.dp)) { Text("Clear") }
        LazyColumn(Modifier.fillMaxSize()) {
            items(diary) { e ->
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(e.action, fontWeight = FontWeight.SemiBold)
                        val detail = if (e.detail.isNotBlank()) " · ${e.detail}" else ""
                        Text("${fmt.format(Date(e.time))} · ${e.source}$detail", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
