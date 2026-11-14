@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.salim.taxilog

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private enum class Tab(val label: String) { Add("Add trip"), Trips("Trips"), Report("Report"), Settings("Settings") }

@Composable
fun App(state: AppState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        containerColor = Paper,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Taxi Log", fontWeight = FontWeight.Bold)
                        Text(Tab.entries[tab].label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Navy, titleContentColor = Color.White,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.Add -> Icons.Default.Add
                                    Tab.Trips -> Icons.Default.DateRange
                                    Tab.Report -> Icons.Default.Share
                                    Tab.Settings -> Icons.Default.Settings
                                },
                                contentDescription = t.label,
                            )
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize(),
        ) {
            when (Tab.entries[tab]) {
                Tab.Add -> AddScreen(state, snackbar)
                Tab.Trips -> TripsScreen(state)
                Tab.Report -> ReportScreen(state)
                Tab.Settings -> SettingsScreen(state)
            }
        }
    }
}

// ============================================================== Add trip

@Composable
private fun AddScreen(state: AppState, snackbar: SnackbarHostState) {
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val day = LocalDate.parse(date)
    val todays = state.tripsOn(day)
    var editing by remember { mutableStateOf<Trip?>(null) }
    var formKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DayHeader(day, todays) { date = it.toString() }

        key(formKey, date) {
            TripForm(
                state = state,
                date = day,
                initial = null,
                onSave = { t ->
                    state.upsert(t)
                    formKey++
                    scope.launch { snackbar.showSnackbar("Saved: ${t.from} → ${t.to}") }
                },
            )
        }

        if (todays.isNotEmpty()) {
            Text("Legs on ${day.format(SHORT_DATE)}", fontWeight = FontWeight.Bold, color = Navy)
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                todays.forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider(color = Paper)
                    TripRow(t, onClick = { editing = t })
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }

    editing?.let { t ->
        EditDialog(state, t, onDismiss = { editing = null })
    }
}

@Composable
private fun DayHeader(day: LocalDate, todays: List<Trip>, onDate: (LocalDate) -> Unit) {
    var pick by remember { mutableStateOf(false) }
    val total = todays.sumOf { it.amount ?: 0.0 }
    Card(
        colors = CardDefaults.cardColors(containerColor = Navy),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (day == LocalDate.now()) "TODAY" else day.dayOfWeek.name,
                        color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(day.format(NICE_DATE), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { pick = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Icon(Icons.Default.DateRange, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Change")
                }
            }
            Spacer(Modifier.height(14.dp))
            Row {
                Stat("Spent", omr(total), Modifier.weight(1f))
                Stat("Legs", todays.size.toString(), Modifier.weight(1f))
                Stat("Sites", todays.map { it.site }.distinct().size.toString(), Modifier.weight(1f))
            }
        }
    }
    if (pick) DatePick(day, onDismiss = { pick = false }) { onDate(it); pick = false }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DatePick(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val st = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let {
                    onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(st) }
}

/** The input form. initial == null -> new leg with smart defaults. */
@Composable
private fun TripForm(
    state: AppState,
    date: LocalDate,
    initial: Trip?,
    onSave: (Trip) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val dayTrips = state.tripsOn(date).filter { it.id != initial?.id }
    val lastToday = dayTrips.lastOrNull()
    val lastEver = state.trips.lastOrNull()

    var site by rememberSaveable { mutableStateOf(initial?.site ?: lastToday?.site ?: "") }
    var from by rememberSaveable { mutableStateOf(initial?.from ?: lastToday?.to ?: state.home) }
    var to by rememberSaveable { mutableStateOf(initial?.to ?: "") }
    var amount by rememberSaveable { mutableStateOf(initial?.amount?.let { num3(it) } ?: "") }
    var personnel by remember {
        mutableStateOf(initial?.personnel ?: lastToday?.personnel ?: lastEver?.personnel ?: state.people.take(1))
    }
    var paidBy by rememberSaveable {
        mutableStateOf(
            initial?.paidBy?.takeIf { it.isNotBlank() }
                ?: dayTrips.lastOrNull { it.paidBy.isNotBlank() }?.paidBy
                ?: state.people.firstOrNull() ?: "",
        )
    }
    var addPerson by remember { mutableStateOf(false) }
    var editDate by remember { mutableStateOf(date) }
    var pickDate by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (initial != null) {
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DateRange, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(editDate.format(NICE_DATE))
                }
            }

            PlaceField("Site / Job", site, { site = it }, state.sites)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { PlaceField("From", from, { from = it }, state.places) }
                IconButton(onClick = { val f = from; from = to; to = f }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Swap")
                }
            }
            PlaceField("To", to, { to = it }, state.places, extra = listOfNotNull(state.home, site.ifBlank { null }))

            OutlinedTextField(
                value = amount,
                onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' || it == ',' } },
                label = { Text("Amount") },
                suffix = { Text("OMR") },
                placeholder = { Text("Empty = included in previous fare") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text(
                        if (parseAmount(amount) == null) "No amount → shown as \"Included\" (shared fare)"
                        else "Paid by $paidBy",
                    )
                },
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("1.000", "1.200", "1.500", "1.800", "2.000", "3.000")) { v ->
                    SuggestionChip(onClick = { amount = v }, label = { Text(v) })
                }
            }

            Label("Personnel (who travelled)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.people.forEach { p ->
                    val sel = p in personnel
                    FilterChip(
                        selected = sel,
                        onClick = { personnel = if (sel) personnel - p else state.people.filter { it in personnel || it == p } },
                        label = { Text(p) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Navy, selectedLabelColor = Color.White,
                        ),
                    )
                }
                AssistChip(onClick = { addPerson = true }, label = { Text("+ Add") })
            }

            if (parseAmount(amount) != null) {
                Label("Paid by")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.people.forEach { p ->
                        FilterChip(
                            selected = paidBy == p,
                            onClick = { paidBy = p },
                            label = { Text(p) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Amber, selectedLabelColor = Color(0xFF1F2937),
                            ),
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB91C1C)),
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
                Button(
                    onClick = {
                        if (from.isBlank() || to.isBlank()) {
                            Toast.makeText(context, "Please fill From and To", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val amt = parseAmount(amount)
                        onSave(
                            Trip(
                                id = initial?.id ?: state.nextId(),
                                date = if (initial != null) editDate else date,
                                site = site.trim().ifBlank { to.trim() },
                                from = from.trim(),
                                to = to.trim(),
                                amount = amt,
                                personnel = personnel,
                                paidBy = if (amt != null) paidBy else "",
                            ),
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(if (initial == null) "Save trip" else "Update", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (addPerson) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addPerson = false },
            title = { Text("Add person") },
            text = {
                OutlinedTextField(
                    name, { name = it }, singleLine = true, label = { Text("Name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    state.addPerson(name)
                    if (name.isNotBlank()) personnel = personnel + name.trim()
                    addPerson = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addPerson = false }) { Text("Cancel") } },
        )
    }
    if (pickDate) DatePick(editDate, onDismiss = { pickDate = false }) { editDate = it; pickDate = false }
}

@Composable
private fun Label(t: String) = Text(t, fontWeight = FontWeight.SemiBold, color = Navy, fontSize = 13.sp)

@Composable
private fun PlaceField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    options: List<String>,
    extra: List<String> = emptyList(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, "Clear") }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    val q = value.trim()
    val suggestions = (extra + options).distinct()
        .filter { !it.equals(q, true) && (q.isEmpty() || it.contains(q, true)) }
        .take(12)
    if (suggestions.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(suggestions) { s ->
                if (s == extra.firstOrNull()) {
                    SuggestionChip(
                        onClick = { onChange(s) },
                        label = { Text(s, maxLines = 1) },
                        icon = { Icon(Icons.Default.Home, null, Modifier.size(16.dp)) },
                    )
                } else {
                    SuggestionChip(onClick = { onChange(s) }, label = { Text(s, maxLines = 1) })
                }
            }
        }
    }
}

@Composable
private fun TripRow(t: Trip, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${t.from}  →  ${t.to}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(t.site, t.personnel.joinToString(" & ")).filter { it.isNotBlank() }.joinToString("  •  "),
                fontSize = 12.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (t.amount != null) {
                Text(num3(t.amount), fontWeight = FontWeight.Bold, color = Navy)
                Text(t.paidBy, fontSize = 11.sp, color = Color(0xFFB45309))
            } else {
                Text("Included", fontSize = 12.sp, color = Color(0xFF94A3B8))
            }
        }
        Icon(Icons.Default.Edit, null, Modifier.padding(start = 8.dp).size(16.dp), tint = Color(0xFFCBD5E1))
    }
}

@Composable
private fun EditDialog(state: AppState, t: Trip, onDismiss: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.94f)
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Navy, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Edit trip", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            }
            TripForm(
                state = state,
                date = t.date,
                initial = t,
                onSave = { state.upsert(it); onDismiss() },
                onDelete = { confirmDelete = true },
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this trip?") },
            text = { Text("${t.date.format(SHORT_DATE)}: ${t.from} → ${t.to}") },
            confirmButton = {
                TextButton(onClick = { state.delete(t); confirmDelete = false; onDismiss() }) {
                    Text("Delete", color = Color(0xFFB91C1C))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

// ============================================================== Trips list

@Composable
private fun TripsScreen(state: AppState) {
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Trip?>(null) }
    val q = query.trim()
    val filtered = state.trips.filter {
        q.isEmpty() || listOf(it.site, it.from, it.to, it.paidBy, it.personnel.joinToString(" "), it.date.toString())
            .any { f -> f.contains(q, true) }
    }
    val byDay = filtered.groupBy { it.date }.toSortedMap(compareByDescending { it })

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                query, { query = it },
                placeholder = { Text("Search site, place, person…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
        }
        item {
            Text(
                "${filtered.size} legs  •  ${omr(filtered.sumOf { it.amount ?: 0.0 })}",
                color = Color(0xFF64748B), fontSize = 13.sp,
            )
        }
        var lastMonth = ""
        byDay.forEach { (date, list) ->
            val m = date.format(MONTH)
            if (m != lastMonth) {
                lastMonth = m
                val monthTotal = filtered.filter { it.date.format(MONTH) == m }.sumOf { it.amount ?: 0.0 }
                item(key = "m$m") {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m, fontWeight = FontWeight.Bold, color = Navy, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Text(omr(monthTotal), fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                    }
                }
            }
            item(key = "d$date") {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(date.format(NICE_DATE), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(omr(list.sumOf { it.amount ?: 0.0 }), fontWeight = FontWeight.Bold, color = Navy)
                    }
                    list.forEach { t ->
                        HorizontalDivider(color = Paper)
                        TripRow(t) { editing = t }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
    editing?.let { EditDialog(state, it) { editing = null } }
}

// ============================================================== Report

private enum class Range(val label: String) {
    ThisMonth("This month"), LastMonth("Last month"), Last7("Last 7 days"), All("All"), Custom("Custom")
}

@Composable
private fun ReportScreen(state: AppState) {
    val context = LocalContext.current
    var range by rememberSaveable { mutableStateOf(Range.ThisMonth) }
    var customFrom by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    var customTo by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val (from, to) = when (range) {
        Range.ThisMonth -> today.withDayOfMonth(1) to today.with(TemporalAdjusters.lastDayOfMonth())
        Range.LastMonth -> today.minusMonths(1).withDayOfMonth(1) to today.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth())
        Range.Last7 -> today.minusDays(6) to today
        Range.All -> (state.trips.minOfOrNull { it.date } ?: today) to (state.trips.maxOfOrNull { it.date } ?: today)
        Range.Custom -> LocalDate.parse(customFrom) to LocalDate.parse(customTo)
    }
    val label = "${from.format(SHORT_DATE)} – ${to.format(SHORT_DATE)}"
    val data = ReportData.build(state.trips, from, to, state.title, label)
    val baseName = "Taxi_Report_${from.format(ISO)}_to_${to.format(ISO)}"

    var pending by remember { mutableStateOf<ByteArray?>(null) }
    val saveXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_XLSX)) { uri ->
        val b = pending
        if (uri != null && b != null) writeToUri(context, uri, b)
        pending = null
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_PDF)) { uri ->
        val b = pending
        if (uri != null && b != null) writeToUri(context, uri, b)
        pending = null
    }

    fun guard(block: () -> Unit) {
        if (data.legs == 0) {
            Toast.makeText(context, "No trips in this period", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            block()
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Label("Period")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Range.entries.forEach { r ->
                FilterChip(
                    selected = range == r, onClick = { range = r }, label = { Text(r.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Navy, selectedLabelColor = Color.White),
                )
            }
        }
        if (range == Range.Custom) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { pickFrom = true }, modifier = Modifier.weight(1f)) { Text("From: ${from.format(SHORT_DATE)}") }
                OutlinedButton(onClick = { pickTo = true }, modifier = Modifier.weight(1f)) { Text("To: ${to.format(SHORT_DATE)}") }
            }
        }

        // ---- preview
        Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text(state.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(label, color = Amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text("TOTAL EXPENSES", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                Text(omr(data.total), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row {
                    Stat("Legs", data.legs.toString(), Modifier.weight(1f))
                    Stat("Sites", data.sites.toString(), Modifier.weight(1f))
                    Stat("Days", data.days.toString(), Modifier.weight(1f))
                }
            }
        }
        if (data.paidBy.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Label("Who paid")
                    data.paidBy.forEach { (p, v) ->
                        val frac = if (data.total > 0) (v / data.total).toFloat() else 0f
                        Column {
                            Row {
                                Text(p, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(omr(v), fontWeight = FontWeight.Bold, color = Navy)
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .background(Color(0xFFE8EDF5), RoundedCornerShape(4.dp)),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(frac.coerceIn(0f, 1f))
                                        .height(8.dp)
                                        .background(Amber, RoundedCornerShape(4.dp)),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- export
        Label("Get the sheet")
        ExportCard(
            title = "Excel sheet (.xlsx)",
            subtitle = "Styled sheet with Trips + Summary tabs. Opens in Excel / Google Sheets.",
            onOpen = { guard { openFile(context, writeReportFile(context, "$baseName.xlsx", XlsxExporter.build(data)), MIME_XLSX) } },
            onShare = { guard { shareFile(context, writeReportFile(context, "$baseName.xlsx", XlsxExporter.build(data)), MIME_XLSX) } },
            onSave = { guard { pending = XlsxExporter.build(data); saveXlsx.launch("$baseName.xlsx") } },
        )
        ExportCard(
            title = "PDF report",
            subtitle = "A4 printable report – ready for WhatsApp or email.",
            onOpen = { guard { openFile(context, writeReportFile(context, "$baseName.pdf", PdfExporter.build(data)), MIME_PDF) } },
            onShare = { guard { shareFile(context, writeReportFile(context, "$baseName.pdf", PdfExporter.build(data)), MIME_PDF) } },
            onSave = { guard { pending = PdfExporter.build(data); savePdf.launch("$baseName.pdf") } },
        )
        Spacer(Modifier.height(30.dp))
    }

    if (pickFrom) DatePick(from, { pickFrom = false }) { customFrom = it.toString(); pickFrom = false }
    if (pickTo) DatePick(to, { pickTo = false }) { customTo = it.toString(); pickTo = false }
}

@Composable
private fun ExportCard(title: String, subtitle: String, onOpen: () -> Unit, onShare: () -> Unit, onSave: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Navy)
            Text(subtitle, fontSize = 12.sp, color = Color(0xFF64748B))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Open") }
                Button(
                    onClick = onShare, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1F2937)),
                ) { Text("Share") }
                OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }
    }
}

// ============================================================== Settings

@Composable
private fun SettingsScreen(state: AppState) {
    val context = LocalContext.current
    var home by rememberSaveable { mutableStateOf(state.home) }
    var title by rememberSaveable { mutableStateOf(state.title) }
    var newPerson by rememberSaveable { mutableStateOf("") }
    var importConfirm by remember { mutableStateOf<List<Trip>?>(null) }

    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) writeToUri(context, uri, TripJson.toJson(state.trips).toByteArray())
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                importConfirm = TripJson.fromJson(text)
            } catch (e: Exception) {
                Toast.makeText(context, "Not a valid backup file", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SettingsCard("Home / starting place") {
            OutlinedTextField(home, { home = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("New trips start from here (or from the last stop of the day).", fontSize = 12.sp, color = Color(0xFF64748B))
            Button(onClick = { state.updateHome(home); Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() }) { Text("Save") }
        }
        SettingsCard("Report title") {
            OutlinedTextField(title, { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { state.updateTitle(title); Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() }) { Text("Save") }
        }
        SettingsCard("People") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.people.forEach { p ->
                    AssistChip(
                        onClick = { state.removePerson(p) },
                        label = { Text(p) },
                        trailingIcon = { Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp)) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    newPerson, { newPerson = it }, singleLine = true, placeholder = { Text("New name") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { state.addPerson(newPerson); newPerson = "" }) { Text("Add") }
            }
        }
        SettingsCard("Backup") {
            Text(
                "All trips are stored on this phone. Keep a backup file in Drive/WhatsApp so you never lose them.",
                fontSize = 12.sp, color = Color(0xFF64748B),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    backup.launch("TaxiLog_backup_${LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}.json")
                }, modifier = Modifier.weight(1f)) { Text("Export backup") }
                OutlinedButton(onClick = { restore.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.weight(1f)) {
                    Text("Restore")
                }
            }
            Text("${state.trips.size} trips saved", fontSize = 12.sp, color = Color(0xFF64748B))
        }
        Text(
            "Taxi Log • amounts in OMR • ${String.format(Locale.US, "%d", state.trips.size)} records",
            fontSize = 11.sp, color = Color(0xFF94A3B8), modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(30.dp))
    }

    importConfirm?.let { list ->
        AlertDialog(
            onDismissRequest = { importConfirm = null },
            title = { Text("Restore backup?") },
            text = { Text("This replaces the ${state.trips.size} trips on this phone with ${list.size} trips from the backup.") },
            confirmButton = {
                TextButton(onClick = { state.replaceAll(list); importConfirm = null }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { importConfirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Navy)
            content()
        }
    }
}
