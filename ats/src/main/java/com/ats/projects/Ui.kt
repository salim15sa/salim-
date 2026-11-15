@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ats.projects

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun App(state: AppState) {
    val screen = state.screen
    BackHandler(enabled = screen != Screen.Home) {
        state.screen = when (screen) {
            is Screen.Drawing -> Screen.ProjectView(screen.id)
            else -> Screen.Home
        }
    }
    when (screen) {
        Screen.Home -> HomeScreen(state)
        is Screen.ProjectView -> {
            val p = state.project(screen.id)
            if (p == null) state.screen = Screen.Home else ProjectScreen(state, p)
        }
        is Screen.Drawing -> {
            val p = state.project(screen.id)
            if (p == null) state.screen = Screen.Home else DrawingScreen(state, p)
        }
        Screen.Settings -> SettingsScreen(state)
    }
}

/** Single-choice chips; the last chip can add a new value. */
@Composable
fun ChipPicker(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    addLabel: String? = null,
    onAdd: ((String) -> Unit)? = null,
) {
    var adding by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            FilterChip(
                selected = o.equals(selected, true),
                onClick = { onSelect(o) },
                label = { Text(o) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.White,
                ),
            )
        }
        if (addLabel != null && onAdd != null) {
            SuggestionChip(onClick = { adding = true }, label = { Text("+ $addLabel") })
        }
    }
    if (adding && onAdd != null) {
        TextPromptDialog(title = "New ${addLabel ?: ""}".trim(), onDismiss = { adding = false }) {
            adding = false
            if (it.isNotBlank()) onAdd(it.trim())
        }
    }
}

/** Text field with matching rooms shown as tap-to-pick chips. */
@Composable
fun RoomPicker(value: String, onChange: (String) -> Unit, options: List<String>, label: String = "Room / location") {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val q = value.trim().lowercase()
        val matches = options.filter { q.isEmpty() || it.lowercase().contains(q) }
            .filterNot { it.equals(value.trim(), true) }.take(14)
        if (matches.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                matches.forEach { m -> SuggestionChip(onClick = { onChange(m) }, label = { Text(m, fontSize = 12.sp) }) }
            }
        }
    }
}

@Composable
fun TextPromptDialog(title: String, initial: String = "", onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onDone(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Delete", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = BadRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One checklist cell: blank / ✓ / X, tap to cycle. */
@Composable
fun MarkCell(mark: Mark, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (bg, fg, sym) = when (mark) {
        Mark.DONE -> Triple(Color(0xFFE3F6EA), DoneGreen, "✓")
        Mark.NOT -> Triple(BadRed, Color.White, "X")
        Mark.NONE -> Triple(Color.White, Color(0xFF9CA3AF), "")
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(sym.ifEmpty { "·" }, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, color = if (mark == Mark.NOT) Color.White else Color(0xFF6B7280), fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
fun ProgressBar(value: Double, modifier: Modifier = Modifier, height: Int = 8) {
    Box(
        modifier
            .height(height.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFE5E7EB)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(value.toFloat().coerceIn(0f, 1f))
                .height(height.dp)
                .clip(RoundedCornerShape(50))
                .background(if (value >= 0.999) DoneGreen else Teal),
        )
    }
}

@Composable
fun StatusPill(status: String) {
    val color = when (status) {
        "Completed" -> DoneGreen
        "Hold" -> Color(0xFF9CA3AF)
        "Site Not Ready" -> Color(0xFFF59E0B)
        "Handover Stage" -> Color(0xFF6366F1)
        else -> Teal
    }
    Text(
        status,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** Excel / PDF export: open, share or save. [build] runs off the UI thread. */
@Composable
fun ExportDialog(title: String, baseName: String, onDismiss: () -> Unit, build: (pdf: Boolean) -> ByteArray) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    val saveXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_XLSX)) { uri ->
        val b = pending
        if (uri != null && b != null) writeToUri(context, uri, b)
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_PDF)) { uri ->
        val b = pending
        if (uri != null && b != null) writeToUri(context, uri, b)
    }

    fun run(pdf: Boolean, action: String) {
        busy = true
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.Default) { build(pdf) }
                val name = "$baseName.${if (pdf) "pdf" else "xlsx"}"
                val mime = if (pdf) MIME_PDF else MIME_XLSX
                when (action) {
                    "open" -> openFile(context, writeReportFile(context, name, bytes), mime)
                    "share" -> shareFile(context, writeReportFile(context, name, bytes), mime)
                    else -> {
                        pending = bytes
                        if (pdf) savePdf.launch(name) else saveXlsx.launch(name)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(false to "Excel sheet (.xlsx)", true to "PDF (A4, printable)").forEach { (pdf, label) ->
                    Column {
                        Text(label, fontWeight = FontWeight.SemiBold, color = Navy)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { run(pdf, "open") }, enabled = !busy) { Text("Open") }
                            OutlinedButton(onClick = { run(pdf, "share") }, enabled = !busy) { Text("Share") }
                            OutlinedButton(onClick = { run(pdf, "save") }, enabled = !busy) { Text("Save") }
                        }
                    }
                }
                if (busy) Text("Preparing…", color = Color(0xFF6B7280))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun LogoHeader(subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.ats_logo),
            contentDescription = "ATS",
            modifier = Modifier.height(36.dp).width(96.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text("ATS Projects", fontWeight = FontWeight.Bold, color = Navy, fontSize = 18.sp)
            Text(subtitle, fontSize = 12.sp, color = Color(0xFF6B7280))
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280), letterSpacing = 0.8.sp)
}

@Composable
fun SmallCount(n: Int, color: Color) {
    Box(
        Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(50))
            .background(color),
        contentAlignment = Alignment.Center,
    ) { Text("$n", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
}
