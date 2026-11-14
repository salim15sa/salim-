@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.salim.money

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

// ============================================================== list

@Composable
fun TransactionsScreen(state: AppState, open: (Txn) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var catId by rememberSaveable { mutableStateOf<String?>(null) }

    val monthList = state.txnsIn(state.month)
    val filtered = monthList.filter { t ->
        (kind == null || (kind == "CLAIM" && t.isClaim) || (kind == Kind.EXPENSE.name && t.isPersonalExpense) ||
            (kind == Kind.INCOME.name && t.kind == Kind.INCOME)) &&
            (catId == null || t.categoryId == catId) &&
            (query.isBlank() || run {
                val c = state.category(t.categoryId)
                listOf(c.name, t.note, t.method, state.num(t.amount)).any { it.contains(query.trim(), true) }
            })
    }
    val usedCats = monthList.map { it.categoryId }.distinct().map { state.category(it) }
        .filter { kind == null || it.kind.name == kind || (kind == "CLAIM" && it.kind == Kind.EXPENSE) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search note, category, amount…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                    unfocusedBorderColor = Line,
                ),
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("All", kind == null, Indigo) { kind = null; catId = null }
                Pill("Expenses", kind == Kind.EXPENSE.name, ExpenseRed) { kind = Kind.EXPENSE.name; catId = null }
                Pill("Income", kind == Kind.INCOME.name, IncomeGreen) { kind = Kind.INCOME.name; catId = null }
                Pill("🏢 Company", kind == "CLAIM", Sky) { kind = "CLAIM"; catId = null }
            }
        }
        if (usedCats.size > 1) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(usedCats, key = { it.id }) { c ->
                    Pill("${c.emoji} ${c.name}", catId == c.id, c.col()) { catId = if (catId == c.id) null else c.id }
                }
            }
        }
        item {
            val inc = filtered.filter { it.kind == Kind.INCOME }.sumOf { it.amount }
            val exp = filtered.filter { it.isPersonalExpense }.sumOf { it.amount }
            val claim = filtered.filter { it.isClaim }.sumOf { it.amount }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(14.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${filtered.size} entries", color = Muted, fontSize = 12.sp)
                    Text(state.money(inc - exp, sign = true), fontWeight = FontWeight.Bold, color = Ink)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("+" + state.money(inc), color = IncomeGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("-" + state.money(exp), color = ExpenseRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (claim > 0) Text("🏢 " + state.money(claim), color = Sky, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (filtered.isEmpty()) item {
            WhiteCard { EmptyHint("🔍", "No transactions", "Nothing matches for ${state.month.format(MONTH)}.") }
        }
        filtered.groupBy { it.date }.forEach { (day, l) ->
            item(key = "d$day") {
                val net = l.sumOf { if (it.kind == Kind.INCOME) it.amount else if (it.isClaim) 0.0 else -it.amount }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp)) {
                    Text(
                        if (day == LocalDate.now()) "Today" else if (day == LocalDate.now().minusDays(1)) "Yesterday" else day.format(DAY_HEAD),
                        fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f),
                    )
                    Text(state.money(net, sign = true), color = if (net < 0) ExpenseRed else IncomeGreen, fontSize = 13.sp)
                }
            }
            item(key = "l$day") {
                WhiteCard {
                    Column {
                        l.forEachIndexed { i, t ->
                            if (i > 0) HorizontalDivider(color = Paper)
                            TxnRow(state, t) { open(t) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, maxLines = 1) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White,
            selectedContainerColor = color,
            selectedLabelColor = Color.White,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected,
            borderColor = Line, selectedBorderColor = color,
        ),
    )
}

// ============================================================== editor

@Composable
fun TxnEditor(state: AppState, editor: Editor, snackbar: SnackbarHostState, scope: CoroutineScope, onClose: () -> Unit) {
    val initial = (editor as? Editor.Edit)?.txn

    var kind by remember { mutableStateOf(initial?.kind ?: (editor as Editor.New).kind) }
    var amount by remember { mutableStateOf(initial?.amount?.let { state.num(it) } ?: "") }
    var catId by remember { mutableStateOf(initial?.categoryId) }
    var date by remember {
        mutableStateOf(
            initial?.date ?: state.month.let { m ->
                // New entries go to today, or the 1st of another month being viewed.
                if (m == java.time.YearMonth.now()) LocalDate.now() else m.atDay(1)
            },
        )
    }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var reimb by remember { mutableStateOf(initial?.reimbursable ?: (editor as? Editor.New)?.reimbursable ?: false) }
    var method by remember { mutableStateOf(initial?.method ?: state.settings.methods.firstOrNull() ?: "") }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val accent = when {
        kind == Kind.INCOME -> IncomeGreen
        reimb -> Sky
        else -> ExpenseRed
    }
    val cats = state.categoriesOf(kind)
    // Most used categories first.
    val counts = state.txns.groupingBy { it.categoryId }.eachCount()
    val sortedCats = cats.sortedByDescending { counts[it.id] ?: 0 }

    // Notes used before with this category, for one-tap reuse.
    val notes = state.txns.filter { it.categoryId == catId && it.note.isNotBlank() }
        .groupingBy { it.note }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(8)

    fun save(again: Boolean) {
        val a = parseAmount(amount)
        when {
            a == null -> { error = "Enter an amount"; return }
            catId == null -> { error = "Choose a category"; return }
        }
        val t = Txn(
            id = initial?.id ?: state.nextId(),
            kind = kind,
            amount = a!!,
            categoryId = catId!!,
            date = date,
            note = note.trim(),
            method = method,
            reimbursable = kind == Kind.EXPENSE && reimb,
            reimbursedOn = if (kind == Kind.EXPENSE && reimb) initial?.reimbursedOn else null,
        )
        state.upsert(t)
        val c = state.category(t.categoryId)
        scope.launch {
            snackbar.showSnackbar("Saved ${c.emoji} ${c.name} • ${state.money(t.amount)}", duration = SnackbarDuration.Short)
        }
        if (again) {
            amount = ""; note = ""; error = null
        } else {
            onClose()
        }
    }

    FullScreen(onClose) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Paper)
                .imePadding(),
        ) {
            // header with amount
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.75f))))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 8.dp, end = 8.dp, bottom = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
                    Text(
                        if (initial != null) "Edit entry"
                        else if (kind == Kind.INCOME) "New income"
                        else if (reimb) "New company expense" else "New expense",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f),
                    )
                    if (initial != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete", tint = Color.White) }
                    }
                    TextButton(onClick = { save(again = false) }) {
                        Text("SAVE", color = Color.White, fontWeight = FontWeight.ExtraBold)
                    }
                }
                // expense / income switch
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(4.dp),
                ) {
                    listOf(Kind.EXPENSE to "Expense", Kind.INCOME to "Income").forEach { (k, label) ->
                        val sel = kind == k
                        Text(
                            label,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(if (sel) Color.White else Color.Transparent)
                                .clickable {
                                    if (kind != k) { kind = k; catId = null }
                                }
                                .padding(vertical = 8.dp),
                            textAlign = TextAlign.Center,
                            color = if (sel) accent else Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                TextField(
                    value = amount,
                    onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' || it == ',' }; error = null },
                    placeholder = {
                        Text("0", fontSize = 40.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                    },
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
                Text("Category", fontWeight = FontWeight.Bold, color = Ink)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 4,
                ) {
                    val padded: List<Category?> = sortedCats + List((4 - sortedCats.size % 4) % 4) { null }
                    padded.forEach { c ->
                        if (c == null) {
                            Spacer(Modifier.weight(1f))
                            return@forEach
                        }
                        val sel = c.id == catId
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (sel) c.col().copy(alpha = 0.18f) else Color.White)
                                .border(
                                    BorderStroke(if (sel) 2.dp else 1.dp, if (sel) c.col() else Line),
                                    RoundedCornerShape(16.dp),
                                )
                                .clickable {
                                    catId = c.id; error = null
                                    // Remember: if this category was last used for the company, suggest it again.
                                    if (initial == null && kind == Kind.EXPENSE) {
                                        state.txns.firstOrNull { it.categoryId == c.id }?.let { if (it.isClaim) reimb = true }
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(c.emoji, fontSize = 24.sp)
                            Text(
                                c.name, fontSize = 11.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }

                if (kind == Kind.EXPENSE) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (reimb) Sky.copy(alpha = 0.10f) else Color.White)
                            .border(BorderStroke(1.dp, if (reimb) Sky else Line), RoundedCornerShape(16.dp))
                            .clickable { reimb = !reimb }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🏢", fontSize = 24.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Company will pay me back", fontWeight = FontWeight.Bold, color = Ink)
                            Text(
                                if (reimb) "Kept apart from my own expenses until the company pays"
                                else "Turn on for taxi / work costs you claim from the company",
                                fontSize = 12.sp, color = Muted,
                            )
                            val paidOn = initial?.reimbursedOn
                            if (reimb && paidOn != null) {
                                Text("✅ Paid back on ${paidOn.format(SHORT_DATE)}", fontSize = 12.sp, color = IncomeGreen)
                            }
                        }
                        Switch(checked = reimb, onCheckedChange = { reimb = it })
                    }
                }

                // live budget hint for the chosen category
                catId?.takeIf { kind == Kind.EXPENSE && !reimb }?.let { id ->
                    val m = java.time.YearMonth.from(date)
                    state.budgetStatus(id, m)?.let { s ->
                        val pending = parseAmount(amount) ?: 0.0
                        val already = if (initial != null && initial.categoryId == id && java.time.YearMonth.from(initial.date) == m) initial.amount else 0.0
                        val after = s.copy(spent = s.spent - already + pending)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("🎯 Budget for this category", fontSize = 12.sp, color = Muted)
                            BudgetBar(after.ratio)
                            Text(
                                if (after.remaining >= 0) "After this: ${state.money(after.remaining)} left of ${state.money(after.budget)}" +
                                    (if (after.daysLeft > 0) " • ${state.money(after.perDay)}/day" else "")
                                else "After this you'll be over by ${state.money(-after.remaining)}",
                                fontSize = 12.sp,
                                color = if (after.remaining >= 0) Ink else ExpenseRed,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Text("Date", fontWeight = FontWeight.Bold, color = Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val today = LocalDate.now()
                    Pill("Today", date == today, Indigo) { date = today }
                    Pill("Yesterday", date == today.minusDays(1), Indigo) { date = today.minusDays(1) }
                    OutlinedButton(onClick = { pickDate = true }, shape = RoundedCornerShape(50)) {
                        Icon(Icons.Filled.DateRange, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(date.format(SHORT_DATE), fontSize = 13.sp)
                    }
                }

                Text("Paid with", fontWeight = FontWeight.Bold, color = Ink)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.settings.methods.forEach { mth ->
                        Pill(mth, method == mth, Violet) { method = if (method == mth) "" else mth }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    placeholder = { Text(if (kind == Kind.INCOME) "e.g. November salary" else "e.g. Lunch at office") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                    ),
                )
                if (notes.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        notes.forEach { n ->
                            Text(
                                n, fontSize = 12.sp, color = Indigo,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFFE0E7FF))
                                    .clickable { note = n }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // bottom actions
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                error?.let {
                    Text("⚠️ $it", color = ExpenseRed, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (initial == null) {
                        OutlinedButton(
                            onClick = { save(again = true) },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("Save & add more") }
                    }
                    Button(
                        onClick = { save(again = false) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                    ) { Text("Save", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
            }
        }
    }

    if (pickDate) DatePick(date, onDismiss = { pickDate = false }) { date = it; pickDate = false }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("${state.category(initial.categoryId).name} • ${state.money(initial.amount)} on ${initial.date.format(SHORT_DATE)}") },
            confirmButton = {
                TextButton(onClick = {
                    state.delete(initial)
                    confirmDelete = false
                    onClose()
                    scope.launch {
                        val r = snackbar.showSnackbar("Entry deleted", actionLabel = "Undo", duration = SnackbarDuration.Long)
                        if (r == SnackbarResult.ActionPerformed) state.restore(initial)
                    }
                }) { Text("Delete", color = ExpenseRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

