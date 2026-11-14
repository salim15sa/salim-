@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.salim.money

import android.content.Intent
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val EPS = 0.0005

@Composable
fun DuesScreen(
    state: AppState,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    openTxn: (Txn) -> Unit,
    openLoan: (LoanEdit) -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(0) }
    var openPerson by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        // segmented switch
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .padding(4.dp),
        ) {
            listOf("🏢  Company claims", "🤝  People & loans").forEachIndexed { i, label ->
                val sel = page == i
                Text(
                    label,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) Indigo else Color.Transparent)
                        .clickable { page = i }
                        .padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                    color = if (sel) Color.White else Muted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
        if (page == 0) ClaimsPage(state, snackbar, scope, openTxn)
        else PeoplePage(state, openLoan) { openPerson = it }
    }

    openPerson?.let { name ->
        val p = state.person(name)
        if (p == null) LaunchedEffect(name) { openPerson = null }
        else PersonDetail(state, p, snackbar, scope, openLoan, onRenamed = { openPerson = it }) { openPerson = null }
    }
}

// ============================================================== company claims

@Composable
private fun ClaimsPage(state: AppState, snackbar: SnackbarHostState, scope: CoroutineScope, openTxn: (Txn) -> Unit) {
    val pending = state.pendingClaims
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var askDate by remember { mutableStateOf(false) }
    val received = state.txns.filter { it.isClaim && it.reimbursedOn != null }
    val selTotal = pending.filter { it.id in selected }.sumOf { it.amount }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (selected.isEmpty()) 100.dp else 170.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                GradientSummary(
                    brush = Brush.linearGradient(listOf(Color(0xFF0369A1), Sky, Color(0xFF06B6D4))),
                    label = "Company owes you",
                    value = state.money(state.pendingClaimTotal),
                    note = if (pending.isEmpty()) "Nothing pending 🎉"
                    else "${pending.size} expense${if (pending.size == 1) "" else "s"} • oldest ${state.shortDays(pending.minOf { it.date })}",
                    left = "Received this year" to state.money(
                        received.filter { it.reimbursedOn!!.year == LocalDate.now().year }.sumOf { it.amount },
                    ),
                    right = "Claimed this month" to state.money(state.claimsIn(java.time.YearMonth.now()).sumOf { it.amount }),
                )
            }
            item {
                Text(
                    "Add taxi or work expenses with “Company will pay me back” turned on. They stay here until the " +
                        "company pays; then select them and tap “Mark as received”. They never count in your own expenses.",
                    fontSize = 12.sp, color = Muted,
                )
            }
            if (pending.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Pending", Modifier.weight(1f))
                        TextButton(onClick = {
                            selected = if (selected.size == pending.size) emptySet() else pending.map { it.id }.toSet()
                        }) { Text(if (selected.size == pending.size) "Clear" else "Select all") }
                    }
                }
                item {
                    WhiteCard {
                        Column {
                            pending.forEachIndexed { i, t ->
                                if (i > 0) HorizontalDivider(color = Paper)
                                ClaimRow(state, t, selected = t.id in selected, onToggle = {
                                    selected = if (t.id in selected) selected - t.id else selected + t.id
                                }, onOpen = { openTxn(t) })
                            }
                        }
                    }
                }
            } else {
                item {
                    WhiteCard { EmptyHint("🏢", "No pending claims", "Company expenses you add will show up here until they're paid back.") }
                }
            }
            if (received.isNotEmpty()) {
                item { SectionTitle("Paid back") }
                received.groupBy { it.reimbursedOn!! }.toSortedMap(compareByDescending<LocalDate> { it }).forEach { (day, l) ->
                    item(key = "r$day") {
                        WhiteCard {
                            Column {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("✅", fontSize = 20.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Received ${day.format(SHORT_DATE)}", fontWeight = FontWeight.Bold, color = Ink)
                                        Text("${l.size} expense${if (l.size == 1) "" else "s"}", fontSize = 12.sp, color = Muted)
                                    }
                                    Text(state.money(l.sumOf { it.amount }), fontWeight = FontWeight.Bold, color = IncomeGreen)
                                }
                                l.forEach { t ->
                                    HorizontalDivider(color = Paper)
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { openTxn(t) }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val c = state.category(t.categoryId)
                                        Text(
                                            "${t.date.format(SHORT_DATE)}  •  ${c.emoji} ${t.note.ifBlank { c.name }}",
                                            fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(state.money(t.amount), fontSize = 12.sp, color = Ink)
                                        TextButton(onClick = { state.markReimbursed(setOf(t.id), null) }) {
                                            Text("Undo", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selected.isNotEmpty()) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 90.dp, bottom = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Night)
                    .padding(14.dp),
            ) {
                Text("${selected.size} selected • ${state.money(selTotal)}", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { askDate = true },
                    colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("✅  Mark as received", fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (askDate) {
        DatePick(LocalDate.now(), onDismiss = { askDate = false }) { d ->
            val ids = selected
            val total = selTotal
            state.markReimbursed(ids, d)
            askDate = false
            selected = emptySet()
            scope.launch {
                val r = snackbar.showSnackbar(
                    "Received ${state.money(total)} from company", actionLabel = "Undo", duration = SnackbarDuration.Long,
                )
                if (r == SnackbarResult.ActionPerformed) state.markReimbursed(ids, null)
            }
        }
    }
}

@Composable
private fun ClaimRow(state: AppState, t: Txn, selected: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val c = state.category(t.categoryId)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Sky.copy(alpha = 0.07f) else Color.Transparent)
            .clickable(onClick = onOpen)
            .padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected, onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = Sky),
        )
        CategoryBadge(c, 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.note.ifBlank { c.name }, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${t.date.format(SHORT_DATE)} • ${state.shortDays(t.date)}", fontSize = 12.sp, color = Muted)
        }
        Text(state.money(t.amount), fontWeight = FontWeight.Bold, color = Sky)
    }
}

@Composable
private fun GradientSummary(
    brush: Brush,
    label: String,
    value: String,
    note: String,
    left: Pair<String, String>,
    right: Pair<String, String>,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(brush)
            .padding(20.dp),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text(note, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(left, right).forEach { (k, v) ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(10.dp),
                ) {
                    Text(k, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                    Text(v, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ============================================================== people & loans

@Composable
private fun PeoplePage(state: AppState, openLoan: (LoanEdit) -> Unit, openPerson: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val people = state.people().filter { query.isBlank() || it.name.contains(query.trim(), true) }
    val open = people.filter { kotlin.math.abs(it.balance) > EPS }
    val settled = people.filter { kotlin.math.abs(it.balance) <= EPS }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            val net = state.owedToMe - state.iOwe
            GradientSummary(
                brush = Brush.linearGradient(listOf(Color(0xFF115E59), Teal, Color(0xFF10B981))),
                label = "People owe you",
                value = state.money(state.owedToMe),
                note = "Net after what you owe: ${state.money(net, sign = true)}",
                left = "You owe others" to state.money(state.iOwe),
                right = "People" to "${state.people().size}",
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { openLoan(LoanEdit(null, "", gave = true)) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                ) { Text("🤝  I gave", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { openLoan(LoanEdit(null, "", gave = false)) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WarnAmber),
                ) { Text("🙏  I got", fontWeight = FontWeight.Bold) }
            }
        }
        if (state.people().size > 5) item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search people") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true, shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, unfocusedBorderColor = Line,
                ),
            )
        }
        if (people.isEmpty()) item {
            WhiteCard {
                EmptyHint(
                    "🤝", "No loans yet",
                    "“I gave” when you lend money or pay back a debt.\n“I got” when you borrow or someone returns your money.",
                )
            }
        }
        if (open.isNotEmpty()) {
            item { SectionTitle("Open balances") }
            item {
                WhiteCard {
                    Column {
                        open.forEachIndexed { i, p ->
                            if (i > 0) HorizontalDivider(color = Paper)
                            PersonRow(state, p) { openPerson(p.name) }
                        }
                    }
                }
            }
        }
        if (settled.isNotEmpty()) {
            item { SectionTitle("Settled") }
            item {
                WhiteCard {
                    Column {
                        settled.forEachIndexed { i, p ->
                            if (i > 0) HorizontalDivider(color = Paper)
                            PersonRow(state, p) { openPerson(p.name) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonRow(state: AppState, p: PersonBalance, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(p.name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val due = p.nextDue
            Text(
                when {
                    due != null && due.isBefore(LocalDate.now()) -> "⏰ Overdue since ${due.format(SHORT_DATE)}"
                    due != null -> "Due ${state.shortDays(due)}"
                    else -> "Last: ${p.last?.let { state.shortDays(it) } ?: "-"} • ${p.entries.size} entr${if (p.entries.size == 1) "y" else "ies"}"
                },
                fontSize = 12.sp,
                color = if (due != null && due.isBefore(LocalDate.now())) ExpenseRed else Muted,
            )
        }
        BalanceLabel(state, p.balance)
    }
}

@Composable
private fun BalanceLabel(state: AppState, balance: Double) {
    Column(horizontalAlignment = Alignment.End) {
        when {
            balance > EPS -> {
                Text(state.money(balance), fontWeight = FontWeight.ExtraBold, color = Teal)
                Text("owes you", fontSize = 11.sp, color = Teal)
            }
            balance < -EPS -> {
                Text(state.money(-balance), fontWeight = FontWeight.ExtraBold, color = WarnAmber)
                Text("you owe", fontSize = 11.sp, color = WarnAmber)
            }
            else -> Text("Settled ✓", fontWeight = FontWeight.Bold, color = Muted, fontSize = 13.sp)
        }
    }
}

// ---------------------------------------------------------------- person detail

@Composable
private fun PersonDetail(
    state: AppState,
    p: PersonBalance,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    openLoan: (LoanEdit) -> Unit,
    onRenamed: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmSettle by remember { mutableStateOf(false) }
    val b = p.balance

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(Paper)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(HeaderBrush)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 8.dp, end = 8.dp, bottom = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { rename = true }) { Icon(Icons.Filled.Edit, "Rename", tint = Color.White) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete", tint = Color.White) }
                }
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PersonAvatar(p.name, 58.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(p.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            when {
                                b > EPS -> "owes you ${state.money(b)}"
                                b < -EPS -> "you owe ${state.money(-b)}"
                                else -> "All settled ✓"
                            },
                            color = when {
                                b > EPS -> Color(0xFF5EEAD4)
                                b < -EPS -> Color(0xFFFCD34D)
                                else -> Color.White.copy(alpha = 0.7f)
                            },
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Given ${state.money(p.gave)} • Received ${state.money(p.got)}",
                            color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                        )
                    }
                }
            }

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionChip("🤝", "I gave", Teal, Modifier.weight(1f)) { openLoan(LoanEdit(null, p.name, gave = true)) }
                        ActionChip("🙏", "I got", WarnAmber, Modifier.weight(1f)) { openLoan(LoanEdit(null, p.name, gave = false)) }
                    }
                }
                if (kotlin.math.abs(b) > EPS) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionChip("✅", "Settle up", IncomeGreen, Modifier.weight(1f)) { confirmSettle = true }
                        ActionChip("📲", "Remind", Indigo, Modifier.weight(1f)) {
                            val text = if (b > 0) {
                                "Hi ${p.name}, just a friendly reminder about the ${state.money(b)} pending with you. Thanks!"
                            } else {
                                "Hi ${p.name}, I haven't forgotten the ${state.money(-b)} I owe you. I'll settle it soon. Thanks!"
                            }
                            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                            context.startActivity(Intent.createChooser(send, "Send reminder"))
                        }
                    }
                }
                item { SectionTitle("History") }
                item {
                    WhiteCard {
                        Column {
                            var running = 0.0
                            // running balance is computed oldest → newest, shown newest first
                            val withBal = p.entries.reversed().map { e ->
                                running += if (e.gave) e.amount else -e.amount
                                e to running
                            }.reversed()
                            withBal.forEachIndexed { i, (e, bal) ->
                                if (i > 0) HorizontalDivider(color = Paper)
                                LoanRow(state, e, bal) { openLoan(LoanEdit(e, e.person, e.gave)) }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.navigationBarsPadding().height(20.dp)) }
            }
        }
    }

    if (rename) {
        var name by remember { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, singleLine = true, shape = RoundedCornerShape(14.dp))
            },
            confirmButton = {
                TextButton(onClick = {
                    state.renamePerson(p.name, name)
                    rename = false
                    if (name.isNotBlank()) onRenamed(name.trim())
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } },
        )
    }

    if (confirmSettle) {
        AlertDialog(
            onDismissRequest = { confirmSettle = false },
            title = { Text("Settle with ${p.name}?") },
            text = {
                Text(
                    if (b > 0) "Records that ${p.name} paid you back ${state.money(b)} today."
                    else "Records that you paid ${p.name} ${state.money(-b)} today.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    state.upsertLoan(
                        LoanEntry(state.nextLoanId(), p.name, gave = b < 0, amount = kotlin.math.abs(b), date = LocalDate.now(), note = "Settled"),
                    )
                    confirmSettle = false
                    scope.launch { snackbar.showSnackbar("Settled with ${p.name}") }
                }) { Text("Settle", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmSettle = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${p.name}?") },
            text = { Text("All ${p.entries.size} entries with ${p.name} will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    state.deletePerson(p.name)
                    confirmDelete = false
                    onClose()
                }) { Text("Delete", color = ExpenseRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ActionChip(emoji: String, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun LoanRow(state: AppState, e: LoanEntry, balanceAfter: Double, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background((if (e.gave) Teal else WarnAmber).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Text(if (e.gave) "↗" else "↙", color = if (e.gave) Teal else WarnAmber, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.note.ifBlank { if (e.gave) "You gave" else "You got" },
                fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val extra = e.due?.let { " • due ${it.format(SHORT_DATE)}" } ?: ""
            Text("${e.date.format(SHORT_DATE)}$extra", fontSize = 12.sp, color = Muted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (e.gave) "Gave " else "Got ") + state.money(e.amount),
                fontWeight = FontWeight.Bold, color = if (e.gave) Teal else WarnAmber,
            )
            Text(
                when {
                    balanceAfter > EPS -> "Bal ${state.money(balanceAfter)} owed to you"
                    balanceAfter < -EPS -> "Bal ${state.money(-balanceAfter)} you owe"
                    else -> "Bal settled"
                },
                fontSize = 10.sp, color = Muted,
            )
        }
    }
}

// ---------------------------------------------------------------- loan editor

@Composable
fun LoanEditor(state: AppState, edit: LoanEdit, snackbar: SnackbarHostState, scope: CoroutineScope, onClose: () -> Unit) {
    val initial = edit.entry
    var gave by remember { mutableStateOf(edit.gave) }
    var person by remember { mutableStateOf(edit.person) }
    var amount by remember { mutableStateOf(initial?.amount?.let { state.num(it) } ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var due by remember { mutableStateOf(initial?.due) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var pickDate by remember { mutableStateOf(false) }
    var pickDue by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val accent = if (gave) Teal else WarnAmber
    val people = state.people()
    val existing = people.firstOrNull { it.name.equals(person.trim(), true) }

    fun save() {
        val a = parseAmount(amount)
        when {
            person.isBlank() -> { error = "Enter the person's name"; return }
            a == null -> { error = "Enter an amount"; return }
        }
        val name = existing?.name ?: person.trim()
        val l = LoanEntry(initial?.id ?: state.nextLoanId(), name, gave, a!!, date, note.trim(), due)
        state.upsertLoan(l)
        val b = state.person(name)?.balance ?: 0.0
        scope.launch {
            snackbar.showSnackbar(
                when {
                    b > EPS -> "$name now owes you ${state.money(b)}"
                    b < -EPS -> "You now owe $name ${state.money(-b)}"
                    else -> "All settled with $name ✓"
                },
            )
        }
        onClose()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(Paper).imePadding()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.75f))))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 8.dp, end = 8.dp, bottom = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
                    Text(
                        if (initial == null) "Loan / money with a person" else "Edit entry",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f),
                    )
                    if (initial != null) {
                        IconButton(onClick = {
                            state.deleteLoan(initial)
                            onClose()
                            scope.launch {
                                val r = snackbar.showSnackbar("Entry deleted", actionLabel = "Undo", duration = SnackbarDuration.Long)
                                if (r == SnackbarResult.ActionPerformed) state.restoreLoan(initial)
                            }
                        }) { Icon(Icons.Filled.Delete, "Delete", tint = Color.White) }
                    }
                }
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(4.dp),
                ) {
                    listOf(true to "🤝 I gave", false to "🙏 I got").forEach { (g, label) ->
                        val sel = gave == g
                        Text(
                            label,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(if (sel) Color.White else Color.Transparent)
                                .clickable { gave = g }
                                .padding(vertical = 8.dp),
                            textAlign = TextAlign.Center,
                            color = if (sel) accent else Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(
                    if (gave) "Lent money, or paid back money I owed" else "Borrowed money, or got my money back",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp),
                )
                TextField(
                    value = amount,
                    onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' || it == ',' }; error = null },
                    placeholder = { Text("0", fontSize = 40.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold) },
                    suffix = { Text(state.settings.currency, color = Color.White, fontSize = 18.sp) },
                    textStyle = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.White,
                        unfocusedIndicatorColor = Color.White.copy(alpha = 0.5f),
                        cursorColor = Color.White,
                    ),
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = person,
                    onValueChange = { person = it.take(30); error = null },
                    label = { Text("Person") },
                    leadingIcon = { if (person.isNotBlank()) PersonAvatar(person, 28.dp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
                )
                val suggestions = people.filter { person.isBlank() || (it.name.contains(person.trim(), true) && !it.name.equals(person.trim(), true)) }.take(8)
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { p ->
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White)
                                    .clickable { person = p.name }
                                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PersonAvatar(p.name, 24.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(p.name, fontSize = 13.sp, color = Ink)
                            }
                        }
                    }
                }
                existing?.let { p ->
                    // show what the balance will be after this entry
                    val a = parseAmount(amount) ?: 0.0
                    val old = initial?.let { if (it.gave) it.amount else -it.amount } ?: 0.0
                    val after = p.balance - old + if (gave) a else -a
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Now", fontSize = 11.sp, color = Muted)
                            BalanceLabel(state, p.balance)
                        }
                        Text("→", fontSize = 20.sp, color = Muted)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("After this", fontSize = 11.sp, color = Muted)
                            BalanceLabel(state, after)
                        }
                    }
                }

                Text("Date", fontWeight = FontWeight.Bold, color = Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Today", date == LocalDate.now(), Indigo) { date = LocalDate.now() }
                    OutlinedButton(onClick = { pickDate = true }, shape = RoundedCornerShape(50)) {
                        Icon(Icons.Filled.DateRange, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(date.format(SHORT_DATE), fontSize = 13.sp)
                    }
                }
                Text("Return by (optional)", fontWeight = FontWeight.Bold, color = Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickDue = true }, shape = RoundedCornerShape(50)) {
                        Icon(Icons.Filled.DateRange, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(due?.format(SHORT_DATE) ?: "Set due date", fontSize = 13.sp)
                    }
                    if (due != null) TextButton(onClick = { due = null }) { Text("Clear") }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    placeholder = { Text(if (gave) "e.g. Lent for rent" else "e.g. Borrowed for ticket") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
                )
                Text(
                    "Loans are not counted as income or expense – they are tracked here as money owed.",
                    fontSize = 12.sp, color = Muted,
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                error?.let { Text("⚠️ $it", color = ExpenseRed, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)) }
                Button(
                    onClick = { save() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                ) { Text("Save", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }

    if (pickDate) DatePick(date, onDismiss = { pickDate = false }) { date = it; pickDate = false }
    if (pickDue) DatePick(due ?: LocalDate.now().plusDays(30), onDismiss = { pickDue = false }) { due = it; pickDue = false }
}

