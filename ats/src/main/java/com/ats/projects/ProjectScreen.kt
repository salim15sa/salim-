@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ats.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Remembers the last choices so the next device is pre-filled (same floor / room / system). */
class Defaults {
    var floor by mutableStateOf("")
    var system by mutableStateOf("")
    var type by mutableStateOf("")
    var room by mutableStateOf("")
}

private sealed interface ListItem {
    data class FloorHead(val floor: String, val count: Int, val progress: Double) : ListItem
    data class SystemHead(val floor: String, val system: String, val devices: List<Device>) : ListItem
    data class Row(val d: Device) : ListItem
}

@Composable
fun ProjectScreen(state: AppState, p: Project) {
    val context = LocalContext.current
    val defaults = remember(p.id) { Defaults() }
    var editInfo by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Device?>(null) }
    var groupAction by remember { mutableStateOf<ListItem.SystemHead?>(null) }
    var floorFilter by rememberSaveable(p.id) { mutableStateOf("All") }
    var systemFilter by rememberSaveable(p.id) { mutableStateOf("All") }

    val sorted = p.sortedDevices()
    val shown = sorted.filter { (floorFilter == "All" || it.floor == floorFilter) && (systemFilter == "All" || it.system == systemFilter) }
    val items = buildList {
        shown.groupBy { it.floor }.forEach { (floor, fl) ->
            add(ListItem.FloorHead(floor, fl.size, fl.sumOf { it.doneCount }.toDouble() / (fl.size * Stage.entries.size)))
            fl.groupBy { it.system }.forEach { (system, sl) ->
                add(ListItem.SystemHead(floor, system, sl))
                sl.forEach { add(ListItem.Row(it)) }
            }
        }
    }

    fun setMark(d: Device, s: Stage) {
        state.updateDevices(p.id) { list -> list.map { if (it.id == d.id) it.with(s, it.mark(s).next()) else it } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(p.name, maxLines = 1, fontWeight = FontWeight.Bold)
                        Text(listOf(p.location, p.systems).filter { it.isNotBlank() }.joinToString("  •  "), fontSize = 12.sp, color = Color(0xFF6B7280), maxLines = 1)
                    }
                },
                navigationIcon = { IconButton(onClick = { state.screen = Screen.Home }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { editInfo = true }) { Icon(Icons.Filled.Edit, "Project details") }
                    IconButton(onClick = { exporting = true }) { Icon(Icons.Filled.Share, "Excel / PDF") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = pad.calculateTopPadding() + 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { SummaryCard(p) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    Button(
                        onClick = { state.screen = Screen.Drawing(p.id) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    ) { Text("Read drawing") }
                    Button(onClick = { adding = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add device")
                    }
                }
            }
            if (p.devices.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (listOf("All") + p.orderedFloors.filter { f -> p.devices.any { it.floor == f } }).forEach { f ->
                                FilterChip(selected = floorFilter == f, onClick = { floorFilter = f }, label = { Text(f, fontSize = 12.sp) })
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (listOf("All") + sorted.map { it.system }.distinct()).forEach { s ->
                                FilterChip(selected = systemFilter == s, onClick = { systemFilter = s }, label = { Text(if (s == "All") "All systems" else s, fontSize = 12.sp) })
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "No devices yet.\n• “Read drawing”: pick the PDF or a photo of the drawing and the devices are listed for you.\n• “Add device”: choose floor, system, device and room from the lists.",
                        color = Color(0xFF6B7280),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            items(items, key = {
                when (it) {
                    is ListItem.FloorHead -> "f-${it.floor}"
                    is ListItem.SystemHead -> "s-${it.floor}-${it.system}"
                    is ListItem.Row -> "d-${it.d.id}"
                }
            }) { item ->
                when (item) {
                    is ListItem.FloorHead -> FloorHeader(item)
                    is ListItem.SystemHead -> SystemHeader(item) { groupAction = item }
                    is ListItem.Row -> DeviceRow(item.d, onEdit = { editing = item.d }, onMark = { s -> setMark(item.d, s) })
                }
            }
        }
    }

    if (adding) {
        DeviceEditor(state, p, null, defaults, onDismiss = { adding = false })
    }
    editing?.let { d ->
        DeviceEditor(state, p, d, defaults, onDismiss = { editing = null })
    }
    groupAction?.let { g ->
        GroupMarkDialog(g.floor, g.system, g.devices.size, onDismiss = { groupAction = null }) { stage, mark ->
            val ids = g.devices.map { it.id }.toSet()
            state.updateDevices(p.id) { list -> list.map { if (it.id in ids) it.with(stage, mark) else it } }
            groupAction = null
        }
    }
    if (editInfo) {
        ProjectInfoDialog(
            initial = p,
            isNew = false,
            onDismiss = { editInfo = false },
            onDelete = {
                editInfo = false
                state.screen = Screen.Home
                state.delete(p)
            },
        ) {
            editInfo = false
            state.upsert(it)
        }
    }
    if (exporting) {
        ExportDialog(
            title = "Checklist – ${p.name}",
            baseName = "${safeName(p.name)}_Progress_Checklist",
            onDismiss = { exporting = false },
        ) { pdf ->
            if (pdf) PdfExporter.project(p, Logo.bitmap(context)) else XlsxExporter.project(p, Logo.png(context))
        }
    }
}

@Composable
private fun SummaryCard(p: Project) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${p.devices.size} devices", fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.weight(1f))
                StatusPill(p.status)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressBar(p.progress, Modifier.weight(1f), 10)
                Spacer(Modifier.width(8.dp))
                Text(pct(p.progress), fontWeight = FontWeight.Bold, color = Navy)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Stage.entries.forEach { s ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("${p.stageDone(s)}/${p.devices.size}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Navy)
                        Text(s.short, fontSize = 10.sp, color = Color(0xFF6B7280))
                        val bad = p.stageIssues(s)
                        if (bad > 0) Text("$bad X", fontSize = 10.sp, color = BadRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (p.latestUpdate.isNotBlank() || p.nextStep.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                if (p.latestUpdate.isNotBlank()) Text("Latest: ${p.latestUpdate}", fontSize = 12.sp, color = Color(0xFF374151))
                if (p.nextStep.isNotBlank()) Text("Next: ${p.nextStep}", fontSize = 12.sp, color = Color(0xFF374151))
            }
        }
    }
}

@Composable
private fun FloorHeader(h: ListItem.FloorHead) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Navy)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(h.floor, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("${h.count} devices  •  ${pct(h.progress)}", color = Color(0xFFCDE7EA), fontSize = 12.sp)
    }
}

@Composable
private fun SystemHeader(h: ListItem.SystemHead, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(h.system, fontWeight = FontWeight.Bold, color = Teal, fontSize = 15.sp)
        Spacer(Modifier.width(8.dp))
        SmallCount(h.devices.size, Teal)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onMore) { Icon(Icons.Filled.MoreVert, "Mark all", tint = Color(0xFF6B7280)) }
    }
}

@Composable
private fun DeviceRow(d: Device, onEdit: () -> Unit, onMark: (Stage) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEdit),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(d.name, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                Spacer(Modifier.width(8.dp))
                Text(d.location, color = Color(0xFF4B5563), fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text(d.type, color = Color(0xFF9CA3AF), fontSize = 11.sp, maxLines = 1)
            }
            if (d.note.isNotBlank()) Text(d.note, fontSize = 12.sp, color = Color(0xFFB45309))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Stage.entries.forEach { s -> MarkCell(d.mark(s), s.short, { onMark(s) }, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun GroupMarkDialog(floor: String, system: String, n: Int, onDismiss: () -> Unit, onApply: (Stage, Mark) -> Unit) {
    var stage by remember { mutableStateOf(Stage.FIRST_FIX) }
    var mark by remember { mutableStateOf(Mark.DONE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$system – $floor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Set one stage for all $n devices in this group.", fontSize = 13.sp)
                SectionLabel("Stage")
                ChipPicker(Stage.entries.map { it.label }, stage.label, { l -> stage = Stage.entries.first { it.label == l } })
                SectionLabel("Mark")
                ChipPicker(listOf("✓ Done", "X Not done", "Clear"), when (mark) {
                    Mark.DONE -> "✓ Done"
                    Mark.NOT -> "X Not done"
                    Mark.NONE -> "Clear"
                }, { mark = when (it) { "✓ Done" -> Mark.DONE; "X Not done" -> Mark.NOT; else -> Mark.NONE } })
            }
        },
        confirmButton = { Button(onClick = { onApply(stage, mark) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Add (one or many) or edit a device. Floor, system, device type and room are picked from lists;
 * names are numbered automatically (CAM12, CAM13 …).
 */
@Composable
fun DeviceEditor(state: AppState, p: Project, existing: Device?, defaults: Defaults, onDismiss: () -> Unit) {
    val isNew = existing == null
    val floors = (p.orderedFloors.ifEmpty { listOf("Ground Floor") })
    var floor by remember { mutableStateOf(existing?.floor ?: defaults.floor.takeIf { it in floors } ?: floors.first()) }
    var system by remember { mutableStateOf(existing?.system ?: defaults.system.ifEmpty { "CCTV" }) }
    var type by remember { mutableStateOf(existing?.type ?: defaults.type.takeIf { t -> Catalog.types(system).any { it.name == t } } ?: Catalog.types(system).first().name) }
    var room by remember { mutableStateOf(existing?.location ?: defaults.room) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var qty by remember { mutableIntStateOf(1) }
    var marks by remember { mutableStateOf(existing?.marks ?: emptyMap()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addedCount by remember { mutableIntStateOf(0) }

    val current = state.project(p.id) ?: p
    val preview = if (isNew) current.newNames(system, type, qty) else emptyList()
    val typeOptions = Catalog.types(system).map { it.name }.distinct()

    fun saveNew(): Boolean {
        val loc = room.trim()
        val names = current.newNames(system, type, qty)
        val newDevices = names.map { n ->
            Device(state.newId(), floor, system, type, n, loc, marks, note.trim())
        }
        state.updateDevices(p.id) { it + newDevices }
        state.rememberRoom(p.id, loc)
        if (floor !in current.floors) state.project(p.id)?.let { state.upsert(it.copy(floors = it.floors + floor)) }
        defaults.floor = floor; defaults.system = system; defaults.type = type; defaults.room = loc
        addedCount += newDevices.size
        return true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add device" else "Edit ${existing?.name}") },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionLabel("Floor")
                ChipPicker(floors, floor, { floor = it }, "floor") { f ->
                    floor = f
                    state.project(p.id)?.let { if (f !in it.floors) state.upsert(it.copy(floors = it.floors + f)) }
                }
                SectionLabel("System")
                ChipPicker(Catalog.systemNames, system, {
                    system = it
                    type = Catalog.types(it).first().name
                })
                SectionLabel("Device")
                ChipPicker(typeOptions, type, { type = it }, "other") { type = it }
                SectionLabel("Room / location")
                RoomPicker(room, { room = it }, roomOptions(current))
                if (isNew) {
                    SectionLabel("Quantity")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { if (qty > 1) qty-- }) { Text("–") }
                        Text("$qty", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 16.dp))
                        OutlinedButton(onClick = { if (qty < 99) qty++ }) { Text("+") }
                    }
                    Text(
                        "Will add: " + if (preview.size <= 6) preview.joinToString(", ") else "${preview.first()} … ${preview.last()}",
                        fontSize = 12.sp,
                        color = Navy,
                    )
                } else {
                    OutlinedTextField(name, { name = it }, label = { Text("Device name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
                SectionLabel("Progress")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Stage.entries.forEach { s ->
                        val m = marks[s] ?: Mark.NONE
                        MarkCell(m, s.short, { marks = marks + (s to m.next()) }, Modifier.weight(1f))
                    }
                }
                if (isNew && addedCount > 0) {
                    HorizontalDivider()
                    Text("$addedCount added – keep going or tap Done", fontSize = 12.sp, color = DoneGreen)
                }
                if (!isNew) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete device", color = BadRed) }
                }
            }
        },
        confirmButton = {
            if (isNew) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = {
                        saveNew()
                        qty = 1
                        note = ""
                        marks = emptyMap()
                    }) { Text("Add + next") }
                    Button(onClick = {
                        saveNew()
                        onDismiss()
                    }) { Text("Add") }
                }
            } else {
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val loc = room.trim()
                        state.updateDevices(p.id) { list ->
                            list.map {
                                if (it.id == existing!!.id) it.copy(floor = floor, system = system, type = type, name = name.trim(), location = loc, note = note.trim(), marks = marks) else it
                            }
                        }
                        state.rememberRoom(p.id, loc)
                        onDismiss()
                    },
                ) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (addedCount > 0) "Done" else "Cancel") } },
    )
    if (confirmDelete && existing != null) {
        ConfirmDialog("Delete ${existing.name}?", "This device will be removed from the checklist.", onDismiss = { confirmDelete = false }) {
            confirmDelete = false
            state.updateDevices(p.id) { list -> list.filter { it.id != existing.id } }
            onDismiss()
        }
    }
}
