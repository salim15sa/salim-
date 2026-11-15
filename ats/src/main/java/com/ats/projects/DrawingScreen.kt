@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ats.projects

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Found(val d: FoundDevice, val selected: Boolean = true)

@Composable
fun DrawingScreen(state: AppState, p: Project) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var floorHint by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<List<Found>?>(null) }
    var newFloors by remember { mutableStateOf<List<String>>(emptyList()) }
    var newRooms by remember { mutableStateOf<List<String>>(emptyList()) }
    var editIndex by remember { mutableStateOf<Int?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            uri = u
            fileName = displayName(context, u)
            found = null
            error = null
        }
    }

    LaunchedEffect(busy) {
        seconds = 0
        while (busy) {
            delay(1000)
            seconds++
        }
    }

    fun read() {
        val u = uri ?: return
        busy = true
        error = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val (bytes, mime) = readDrawing(context, u)
                    if (bytes.size > 30 * 1024 * 1024) throw DrawingReader.ReadException("File is larger than 30 MB. Export one floor at a time.")
                    DrawingReader.read(state.apiKey, bytes, mime, fileName, floorHint)
                }
                val order = (result.floors + p.orderedFloors + Catalog.defaultFloors).distinct()
                found = result.devices.sortedWith(
                    compareBy<FoundDevice>(
                        { order.indexOf(it.floor).let { i -> if (i < 0) 999 else i } },
                        { it.floor },
                        { Catalog.systemIndex(it.system) },
                        { Catalog.typeIndex(it.system, it.type) },
                        { it.location },
                    ),
                ).map { Found(it) }
                newFloors = result.floors
                newRooms = result.rooms
                if (result.devices.isEmpty()) error = "No devices were found on this drawing."
            } catch (e: DrawingReader.ReadException) {
                error = e.message
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                error = when {
                    "401" in msg || "authentication" in msg.lowercase() -> "The API key was not accepted. Check it in Settings."
                    "Unable to resolve host" in msg || "timeout" in msg.lowercase() -> "No internet connection (or it timed out). Try again."
                    else -> "Could not read the drawing: $msg"
                }
            } finally {
                busy = false
            }
        }
    }

    fun addSelected() {
        val chosen = found.orEmpty().filter { it.selected }.map { it.d }
        val cur = state.project(p.id) ?: return
        val floors = (cur.floors + newFloors + chosen.map { it.floor }).distinct()
        val rooms = (cur.rooms + newRooms + chosen.map { it.location })
            .filter { it.isNotBlank() }.distinctBy { it.lowercase() }
            .filterNot { r -> Catalog.defaultRooms.any { it.equals(r, true) } }
        val floorOrder = floors
        val ordered = chosen.sortedWith(
            compareBy<FoundDevice>({ floorOrder.indexOf(it.floor) }, { Catalog.systemIndex(it.system) }, { Catalog.typeIndex(it.system, it.type) }, { it.location }),
        )
        val counters = mutableMapOf<String, Int>()
        val devices = ordered.map { f ->
            val prefix = Catalog.type(f.system, f.type).prefix
            val n = counters[prefix] ?: cur.nextNumber(prefix)
            counters[prefix] = n + 1
            Device(state.newId(), f.floor, f.system, f.type, "$prefix$n", f.location, emptyMap(), f.note)
        }
        state.upsert(cur.copy(floors = floors, rooms = rooms, devices = cur.devices + devices))
        state.screen = Screen.ProjectView(p.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Read drawing") },
                navigationIcon = { IconButton(onClick = { state.screen = Screen.ProjectView(p.id) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        val list = found
        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = pad.calculateTopPadding() + 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(p.name, fontWeight = FontWeight.Bold, color = Navy)
                        Text(
                            "Pick the drawing (PDF from AutoCAD, or a clear photo). Every device symbol is listed with its floor and room. " +
                                "Check the list, fix anything, then add it to the checklist.",
                            fontSize = 13.sp,
                            color = Color(0xFF4B5563),
                        )
                        if (state.apiKey.isBlank()) {
                            Text("An Anthropic API key is needed for this. Add it in Settings – or add devices by hand.", color = BadRed, fontSize = 13.sp)
                            OutlinedButton(onClick = { state.screen = Screen.Settings }) { Text("Open Settings") }
                        }
                        OutlinedButton(onClick = { pick.launch(arrayOf("application/pdf", "image/*")) }, enabled = !busy) {
                            Text(if (uri == null) "Choose drawing (PDF / photo)" else "Change file")
                        }
                        if (fileName.isNotEmpty()) Text("📄 $fileName", fontSize = 13.sp, color = Navy)
                        SectionLabel("Floor (only if the file is one floor)")
                        ChipPicker(listOf("Auto") + floorOptions(p).take(7), floorHint.ifEmpty { "Auto" }, { floorHint = if (it == "Auto") "" else it })
                        Button(
                            onClick = { read() },
                            enabled = uri != null && !busy && state.apiKey.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Teal),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (list == null) "Read devices from drawing" else "Read again") }
                        if (busy) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.height(22.dp).width(22.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Reading the drawing… ${seconds}s (big drawings take 1–3 minutes)", fontSize = 13.sp)
                            }
                        }
                        error?.let { Text(it, color = BadRed, fontSize = 13.sp) }
                    }
                }
            }
            if (list != null && list.isNotEmpty()) {
                item {
                    val sel = list.count { it.selected }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Found ${list.size} devices", fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.weight(1f))
                        TextButton(onClick = { found = list.map { it.copy(selected = sel != list.size) } }) {
                            Text(if (sel == list.size) "Select none" else "Select all")
                        }
                    }
                    Text(
                        list.groupBy { it.d.system }.entries.joinToString("  •  ") { "${it.key} ${it.value.size}" },
                        fontSize = 12.sp,
                        color = Color(0xFF4B5563),
                    )
                }
                itemsIndexed(list) { i, f ->
                    val prev = list.getOrNull(i - 1)
                    if (prev == null || prev.d.floor != f.d.floor) {
                        Text(f.d.floor, fontWeight = FontWeight.Bold, color = Teal, modifier = Modifier.padding(top = 6.dp))
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { editIndex = i }
                                .padding(end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = f.selected, onCheckedChange = { c -> found = list.mapIndexed { k, x -> if (k == i) x.copy(selected = c) else x } })
                            Column(Modifier.weight(1f)) {
                                Text("${f.d.type}  ·  ${f.d.system}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(f.d.location.ifBlank { "(no room)" }, fontSize = 12.sp, color = Color(0xFF4B5563))
                                if (f.d.note.isNotBlank()) Text(f.d.note, fontSize = 11.sp, color = Color(0xFFB45309))
                            }
                            Text("edit", fontSize = 12.sp, color = Teal)
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { addSelected() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Add ${list.count { it.selected }} devices to checklist")
                    }
                }
            }
        }
    }

    val list = found
    editIndex?.let { i ->
        val f = list?.getOrNull(i)
        if (f == null) editIndex = null else {
            FoundEditDialog(p, f.d, newRooms, onDismiss = { editIndex = null }) { d ->
                found = list.mapIndexed { k, x -> if (k == i) x.copy(d = d) else x }
                editIndex = null
            }
        }
    }
}

@Composable
private fun FoundEditDialog(p: Project, d: FoundDevice, extraRooms: List<String>, onDismiss: () -> Unit, onSave: (FoundDevice) -> Unit) {
    var floor by remember { mutableStateOf(d.floor) }
    var system by remember { mutableStateOf(d.system) }
    var type by remember { mutableStateOf(d.type) }
    var room by remember { mutableStateOf(d.location) }
    val floors = (listOf(d.floor) + floorOptions(p)).distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit device") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Floor")
                ChipPicker(floors, floor, { floor = it }, "floor") { floor = it }
                SectionLabel("System")
                ChipPicker(Catalog.systemNames, system, {
                    system = it
                    type = Catalog.types(it).first().name
                })
                SectionLabel("Device")
                ChipPicker((Catalog.types(system).map { it.name } + type).distinct(), type, { type = it }, "other") { type = it }
                RoomPicker(room, { room = it }, (extraRooms + roomOptions(p)).distinctBy { it.lowercase() })
            }
        },
        confirmButton = { Button(onClick = { onSave(d.copy(floor = floor, system = system, type = type, location = room.trim())) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
