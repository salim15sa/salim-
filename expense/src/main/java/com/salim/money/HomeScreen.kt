package com.salim.money

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeScreen(state: AppState, open: (Txn) -> Unit, add: (Kind) -> Unit, goTo: (Tab) -> Unit) {
    val m = state.month
    val income = state.income(m)
    val expense = state.expense(m)
    val balance = income - expense
    val list = state.txnsIn(m)
    val today = LocalDate.now()
    val isCurrent = m == YearMonth.now()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Balance card sits half on the gradient header.
        Box {
            Box(Modifier.fillMaxWidth().height(70.dp).background(HeaderBrush))
            BalanceCard(state, income, expense, balance)
        }

        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // quick add
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickButton("➖", "Add expense", ExpenseRed, Modifier.weight(1f)) { add(Kind.EXPENSE) }
                QuickButton("➕", "Add income", IncomeGreen, Modifier.weight(1f)) { add(Kind.INCOME) }
            }

            // today's spend
            if (isCurrent) {
                val spentToday = list.filter { it.date == today && it.kind == Kind.EXPENSE }.sumOf { it.amount }
                val total = state.budgetStatus(TOTAL)
                WhiteCard {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📅", fontSize = 30.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Spent today", color = Muted, fontSize = 12.sp)
                            Text(state.money(spentToday), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink)
                        }
                        if (total != null && total.daysLeft > 0) {
                            // What today was allowed, from the budget left this morning.
                            val allowance = maxOf(0.0, total.remaining + spentToday) / total.daysLeft
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Today's limit", color = Muted, fontSize = 12.sp)
                                Text(
                                    state.money(allowance),
                                    fontWeight = FontWeight.Bold,
                                    color = if (spentToday > allowance) ExpenseRed else IncomeGreen,
                                )
                            }
                        }
                    }
                }
            }

            // budget alerts
            val alerts = (state.store.budgets.keys)
                .mapNotNull { state.budgetStatus(it) }
                .filter { it.level != Level.OK }
                .sortedByDescending { it.ratio }
            if (alerts.isNotEmpty()) {
                SectionTitle("Budget alerts", action = "See all", onAction = { goTo(Tab.Budget) })
                alerts.forEach { s -> AlertStrip(state, s) }
            }

            // spending by category
            val cats = state.byCategory(m)
            SectionTitle("Where your money went", action = "Report", onAction = { goTo(Tab.Reports) })
            WhiteCard {
                if (cats.isEmpty()) {
                    EmptyHint("🧾", "No expenses yet", "Tap “Add” to record your first expense for ${m.format(MONTH)}.")
                } else {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Donut(
                            cats.map { it.first.col() to it.second },
                            Modifier.size(130.dp),
                            thickness = 18.dp,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Spent", fontSize = 11.sp, color = Muted)
                                Text(state.num(expense), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                                Text(state.settings.currency, fontSize = 10.sp, color = Muted)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            cats.take(5).forEach { (c, v) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c.col()))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "${c.emoji} ${c.name}", fontSize = 12.sp, color = Ink, modifier = Modifier.weight(1f),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text("${(v / expense * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                                }
                            }
                            if (cats.size > 5) Text("+${cats.size - 5} more", fontSize = 11.sp, color = Muted)
                        }
                    }
                }
            }

            // recent transactions
            SectionTitle("Recent transactions", action = "View all", onAction = { goTo(Tab.Txns) })
            WhiteCard {
                if (list.isEmpty()) {
                    EmptyHint("👛", "Nothing here yet", "Add your salary and daily expenses to see them here.")
                } else {
                    Column {
                        list.take(6).forEachIndexed { i, t ->
                            if (i > 0) HorizontalDivider(color = Paper)
                            TxnRow(state, t) { open(t) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(90.dp))
        }
    }
}

@Composable
private fun BalanceCard(state: AppState, income: Double, expense: Double, balance: Double) {
    WhiteCard(Modifier.padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 12.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("Balance this month", color = Muted, fontSize = 13.sp)
            Text(
                state.money(balance),
                fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                color = if (balance < 0) ExpenseRed else Ink,
            )
            if (income > 0) {
                val saved = (balance / income * 100).toInt()
                Text(
                    if (saved >= 0) "You saved $saved% of your income" else "Spending is more than income",
                    fontSize = 12.sp, color = if (saved >= 0) IncomeGreen else ExpenseRed, fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat("⬇ Income", state.money(income), IncomeGreen, Modifier.weight(1f))
                MiniStat("⬆ Expense", state.money(expense), ExpenseRed, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(12.dp),
    ) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QuickButton(emoji: String, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.8f))))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AlertStrip(state: AppState, s: BudgetStatus) {
    val over = s.level == Level.OVER
    val color = if (over) ExpenseRed else WarnAmber
    val name = if (s.key == TOTAL) "💰 Whole month" else state.category(s.key).let { "${it.emoji} ${it.name}" }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (over) "🚨" else "⚠️", fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Bold, color = Ink)
            Text(
                if (over) "Over budget by ${state.money(-s.remaining)}"
                else "${(s.ratio * 100).toInt()}% used • ${state.money(s.remaining)} left" +
                    if (s.daysLeft > 0) " • ${state.money(s.perDay)}/day" else "",
                fontSize = 12.sp, color = Ink.copy(alpha = 0.8f),
            )
        }
    }
}
