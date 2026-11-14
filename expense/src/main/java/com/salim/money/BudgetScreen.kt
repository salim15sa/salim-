package com.salim.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BudgetScreen(state: AppState) {
    var editKey by remember { mutableStateOf<String?>(null) }
    val m = state.month
    val expenseCats = state.categoriesOf(Kind.EXPENSE)
    val withBudget = expenseCats.filter { state.store.budgets.containsKey(it.id) }
    val without = expenseCats.filter { !state.store.budgets.containsKey(it.id) }
    val spentBy = state.byCategory(m).associate { it.first.id to it.second }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            val total = state.budgetStatus(TOTAL)
            WhiteCard(onClick = { editKey = TOTAL }) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💰", fontSize = 28.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Whole month budget", fontWeight = FontWeight.Bold, color = Ink, fontSize = 16.sp)
                            Text("All expenses together", color = Muted, fontSize = 12.sp)
                        }
                        Text(if (total == null) "Set" else "Edit", color = Indigo, fontWeight = FontWeight.Bold)
                    }
                    if (total == null) {
                        Text("Tap to set how much you want to spend in a month.", color = Muted, fontSize = 13.sp)
                    } else {
                        BudgetDetails(state, total)
                    }
                }
            }
        }
        item {
            val sum = withBudget.sumOf { state.store.budgets[it.id] ?: 0.0 }
            SectionTitle(
                if (withBudget.isEmpty()) "Category budgets" else "Category budgets • ${state.money(sum)}",
            )
        }
        if (withBudget.isEmpty()) item {
            WhiteCard {
                EmptyHint(
                    "🎯", "No category budgets yet",
                    "Tap a category below, e.g. 🍔 Food = 60 ${state.settings.currency} a month. " +
                        "You'll get a message with how much is left and how much you can spend per day.",
                )
            }
        }
        items(withBudget, key = { it.id }) { c ->
            val s = state.budgetStatus(c.id)!!
            WhiteCard(onClick = { editKey = c.id }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(c, 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.Bold, color = Ink)
                            Text("${state.money(s.spent)} of ${state.money(s.budget)}", color = Muted, fontSize = 12.sp)
                        }
                        StatusTag(s)
                    }
                    BudgetDetails(state, s)
                }
            }
        }
        if (without.isNotEmpty()) {
            item { SectionTitle("Add a budget") }
            items(without, key = { "n" + it.id }) { c ->
                WhiteCard(onClick = { editKey = c.id }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(c, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, color = Ink, fontWeight = FontWeight.SemiBold)
                            spentBy[c.id]?.let {
                                Text("Spent ${state.money(it)} this month", color = Muted, fontSize = 12.sp)
                            }
                        }
                        Text("+ Set", color = Indigo, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    editKey?.let { key ->
        BudgetDialog(state, key) { editKey = null }
    }
}

@Composable
private fun StatusTag(s: BudgetStatus) {
    val (text, color) = when (s.level) {
        Level.OVER -> "Over" to ExpenseRed
        Level.WARN -> "${(s.ratio * 100).toInt()}%" to WarnAmber
        Level.OK -> "${(s.ratio * 100).toInt()}%" to IncomeGreen
    }
    Text(
        text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Bar plus the numbers that matter: left, days, per-day allowance. */
@Composable
fun BudgetDetails(state: AppState, s: BudgetStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BudgetBar(s.ratio, marker = if (s.daysLeft > 0) s.expectedByToday / s.budget else null)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Fact(
                if (s.remaining >= 0) "Left" else "Over by",
                state.money(kotlin.math.abs(s.remaining)),
                if (s.remaining >= 0) IncomeGreen else ExpenseRed,
                Modifier.weight(1f),
            )
            Fact("Days left", s.daysLeft.toString(), Indigo, Modifier.weight(0.7f))
            Fact("Per day", state.money(s.perDay), Violet, Modifier.weight(1f))
        }
        if (s.daysLeft > 0) {
            val ahead = s.spent - s.expectedByToday
            Text(
                when {
                    s.remaining < 0 -> "🚨 Budget is finished. Stop spending here until next month."
                    ahead > 0 -> "⚠️ ${state.money(ahead)} ahead of plan (plan: ${state.money(s.plannedPerDay)}/day)."
                    else -> "✅ On track – ${state.money(-ahead)} under plan (plan: ${state.money(s.plannedPerDay)}/day)."
                },
                fontSize = 12.sp,
                color = if (s.remaining < 0) ExpenseRed else if (ahead > 0) Color(0xFFB45309) else Color(0xFF047857),
            )
        }
    }
}

@Composable
private fun Fact(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.09f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun BudgetDialog(state: AppState, key: String, onDismiss: () -> Unit) {
    val current = state.store.budgets[key]
    var text by remember { mutableStateOf(current?.let { state.num(it) } ?: "") }
    val title = if (key == TOTAL) "💰 Whole month budget" else state.category(key).let { "${it.emoji} ${it.name}" }
    val days = state.month.lengthOfMonth()
    val v = parseAmount(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Monthly limit (repeats every month)", color = Muted, fontSize = 13.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { s -> text = s.filter { it.isDigit() || it == '.' || it == ',' } },
                    suffix = { Text(state.settings.currency) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                if (v != null) {
                    Text("≈ ${state.money(v / days)} per day • ${state.money(v * 7 / days)} per week", color = Indigo, fontSize = 13.sp)
                }
                if (key != TOTAL) {
                    // Last month's spending helps choose a realistic number.
                    val last = state.byCategory(state.month.minusMonths(1)).firstOrNull { it.first.id == key }?.second
                    if (last != null) Text("Last month you spent ${state.money(last)}.", color = Muted, fontSize = 12.sp)
                } else {
                    val inc = state.income(state.month)
                    if (inc > 0) Text("Income this month: ${state.money(inc)}", color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { state.setBudget(key, v); onDismiss() }, enabled = v != null) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { state.setBudget(key, null); onDismiss() }) { Text("Remove", color = ExpenseRed) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        containerColor = Color.White,
    )
}

