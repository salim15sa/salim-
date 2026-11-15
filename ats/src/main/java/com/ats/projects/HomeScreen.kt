@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ats.projects

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable
fun HomeScreen(state: AppState) {
    val context = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") }

    val list = state.projects
        .filter { statusFilter == "All" || it.status == statusFilter }
        .filter { query.isBlank() || "${it.name} ${it.client} ${it.location}".contains(query.trim(), true) }
        .sortedByDescending { it.date }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { LogoHeader("${state.projects.size} projects") },
                actions = {
                    IconButton(onClick = { exporting = true }) { Icon(Icons.Filled.Share, "Summary report") }
                    IconButton(onClick = { state.screen = Screen.Settings }) { Icon(Icons.Filled.Settings, "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New project") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = pad.calculateTopPadding() + 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search project, client, location") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, null) }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                val used = listOf("All") + PROJECT_STATUSES.filter { s -> state.projects.any { it.status == s } }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    used.forEach { s ->
                        FilterChip(selected = statusFilter == s, onClick = { statusFilter = s }, label = { Text(s, fontSize = 12.sp) })
                    }
                }
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        "No projects yet. Tap “New project”, then add devices by hand or let the app read your drawing.",
                        color = Color(0xFF6B7280),
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(list, key = { it.id }) { p -> ProjectCard(p) { state.screen = Screen.ProjectView(p.id) } }
        }
    }

    if (creating) {
        val fresh = remember { Project(id = state.newId(), name = "") }
        ProjectInfoDialog(
            initial = fresh,
            isNew = true,
            onDismiss = { creating = false },
        ) { p ->
            creating = false
            state.upsert(p)
            state.screen = Screen.ProjectView(p.id)
        }
    }
    if (exporting) {
        ExportDialog(
            title = "Project summary (all projects)",
            baseName = "ATS_Project_Summary_${LocalDate.now()}",
            onDismiss = { exporting = false },
        ) { pdf ->
            if (pdf) PdfExporter.allProjects(state.projects, Logo.bitmap(context))
            else XlsxExporter.allProjects(state.projects, Logo.png(context))
        }
    }
}

@Composable
private fun ProjectCard(p: Project, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Navy)
                    val sub = listOf(p.client, p.location).filter { it.isNotBlank() }.joinToString("  •  ")
                    if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = Color(0xFF6B7280))
                }
                StatusPill(p.status)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressBar(p.progress, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text(pct(p.progress), fontWeight = FontWeight.Bold, color = Navy, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${p.devices.size} devices  •  ${p.systems}  •  ${p.orderedFloors.size} floors",
                fontSize = 12.sp,
                color = Color(0xFF4B5563),
            )
            if (p.nextStep.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Next: ${p.nextStep}", fontSize = 12.sp, color = Color(0xFF374151), maxLines = 2)
            }
        }
    }
}

/** Create / edit the project details (the columns of the Project Summary sheet) and its floors. */
@Composable
fun ProjectInfoDialog(initial: Project, isNew: Boolean, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, onSave: (Project) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var client by remember { mutableStateOf(initial.client) }
    var location by remember { mutableStateOf(initial.location) }
    var systems by remember { mutableStateOf(initial.systems) }
    var status by remember { mutableStateOf(initial.status) }
    var latest by remember { mutableStateOf(initial.latestUpdate) }
    var next by remember { mutableStateOf(initial.nextStep) }
    var contact by remember { mutableStateOf(initial.contact) }
    var phone by remember { mutableStateOf(initial.phone) }
    var date by remember { mutableStateOf(initial.date.format(SHOW_DATE)) }
    var floors by remember { mutableStateOf(initial.floors) }
    var addFloor by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New project" else "Project details") },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Project name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(client, { client = it }, label = { Text("Client") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(location, { location = it }, label = { Text("Location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SectionLabel("Scope of work")
                ChipPicker(listOf("ELV", "KNX", "ELV & KNX", "ELV & Akubela", "ELV & SMATV"), systems, { systems = it }, "other") { systems = it }
                SectionLabel("Status")
                ChipPicker(PROJECT_STATUSES, status, { status = it })
                SectionLabel("Floors (in order)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    floors.forEach { f ->
                        InputChip(
                            selected = true,
                            onClick = { floors = floors - f },
                            label = { Text(f) },
                            trailingIcon = { Icon(Icons.Filled.Close, "Remove", Modifier.width(16.dp)) },
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Catalog.defaultFloors.filter { it !in floors }.forEach { f ->
                        OutlinedButton(onClick = { floors = floors + f }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+ $f", fontSize = 12.sp) }
                    }
                    TextButton(onClick = { addFloor = true }) { Text("+ other") }
                }
                OutlinedTextField(latest, { latest = it }, label = { Text("Latest update") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(next, { next = it }, label = { Text("Next step") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(contact, { contact = it }, label = { Text("Contact person") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    phone, { phone = it }, label = { Text("Phone") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(date, { date = it }, label = { Text("Date (d/m/yyyy)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete project", color = BadRed) }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    val d = runCatching { LocalDate.parse(date.trim(), SHOW_DATE) }.getOrDefault(initial.date)
                    onSave(
                        initial.copy(
                            name = name.trim(), client = client.trim(), location = location.trim(), systems = systems.trim(),
                            status = status, latestUpdate = latest.trim(), nextStep = next.trim(), contact = contact.trim(),
                            phone = phone.trim(), date = d, floors = floors.ifEmpty { listOf("Ground Floor") },
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (addFloor) {
        TextPromptDialog("New floor", onDismiss = { addFloor = false }) {
            addFloor = false
            if (it.isNotBlank() && it.trim() !in floors) floors = floors + it.trim()
        }
    }
    if (confirmDelete && onDelete != null) {
        ConfirmDialog("Delete project?", "“${initial.name}” and all its ${initial.devices.size} devices will be removed.", onDismiss = { confirmDelete = false }) {
            confirmDelete = false
            onDelete()
        }
    }
}

@Composable
fun SettingsScreen(state: AppState) {
    val context = LocalContext.current
    var key by remember { mutableStateOf(state.apiKey) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_JSON)) { uri ->
        if (uri != null) writeToUri(context, uri, ProjectJson.toJson(state.projects).toByteArray())
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                val list = ProjectJson.fromJson(text)
                state.replaceAll(list)
                Toast.makeText(context, "Restored ${list.size} projects", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Not a valid backup: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { state.screen = Screen.Home }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("Drawing reader (AI)")
            Text(
                "To read devices from a drawing automatically, the app sends the drawing to Claude (Anthropic). " +
                    "Paste your Anthropic API key (from console.anthropic.com). It is stored only on this phone. " +
                    "Without a key you can still add every device by hand.",
                fontSize = 13.sp,
                color = Color(0xFF4B5563),
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("Anthropic API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                state.updateApiKey(key)
                Toast.makeText(context, if (key.isBlank()) "Key removed" else "Key saved", Toast.LENGTH_SHORT).show()
            }) { Text("Save key") }

            Spacer(Modifier.height(8.dp))
            SectionLabel("Backup")
            Text("Save all projects to a file (Drive, WhatsApp…) or restore them on a new phone.", fontSize = 13.sp, color = Color(0xFF4B5563))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { backup.launch("ATS_Projects_backup_${LocalDate.now()}.json") }) { Text("Backup") }
                OutlinedButton(onClick = { restore.launch(arrayOf("application/json", "text/*", "*/*")) }) { Text("Restore") }
            }
            Spacer(Modifier.height(8.dp))
            Text("ATS – Al Arkan Technology Services", fontSize = 12.sp, color = Color(0xFF9CA3AF))
        }
    }
}
