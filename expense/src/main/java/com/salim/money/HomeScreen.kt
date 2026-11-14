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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeScreen(
    state: AppState,
    open: (Txn) -> Unit,
    add: (Kind, Boolean) -> Unit,
    openLoan: (LoanEdit) -> Unit,
    goTo: (Tab) -> Unit,
) {
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
        // Balance card sits half on the dark header.
        Box {
            Box(Modifier.fillMaxWidth().height(96.dp).background(HeaderBrush))
            BalanceCard(state, income, expense, balance)
        }

        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // money waiting outside: company claims and loans
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val claims = state.pendingClaims
                DueTile("🏢", "Company owes", state.money(state.pendingClaimTotal), "${claims.size} pending", Sky, Modifier.weight(1f)) {
                    goTo(Tab.Dues)
                }
                val people = state.people()
                DueTile(
                    "🤝", "People owe me", state.money(state.owedToMe),
                    "${people.count { it.balance > 0.0005 }} people", Teal, Modifier.weight(1f),
                ) { goTo(Tab.Dues) }
                DueTile(
                    "🙏", "I owe", state.money(state.iOwe),
                    "${people.count { it.balance < -0.0005 }} people", WarnAmber, Modifier.weight(1f),
                ) { goTo(Tab.Dues) }
            }

            // quick add
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickButton("💸", "Expense", ExpenseRed, Modifier.weight(1f)) { add(Kind.EXPENSE, false) }
                QuickButton("💰", "Income", IncomeGreen, Modifier.weight(1f)) { add(Kind.INCOME, false) }
                QuickButton("🏢", "Company", Sky, Modifier.weight(1f)) { add(Kind.EXPENSE, true) }
            }

            // loans due soon / overdue
            val dueSoon = state.people().filter { p ->
                p.nextDue?.let { !it.isAfter(LocalDate.now().plusDays(7)) } == true
            }
            if (dueSoon.isNotEmpty()) {
                SectionTitle("Money due", action = "Dues", onAction = { goTo(Tab.Dues) })
                WhiteCard {
                    Column {
                        dueSoon.forEachIndexed { i, p ->
                            if (i > 0) HorizontalDivider(color = Paper)
                            val overdue = p.nextDue!!.isBefore(LocalDate.now())
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { openLoan(LoanEdit(null, p.name, gave = false)) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PersonAvatar(p.name, 38.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.SemiBold, color = Ink)
                                    Text(
                                        (if (overdue) "Overdue – was due " else "Due ") + state.shortDays(p.nextDue!!),
                                        fontSize = 12.sp, color = if (overdue) ExpenseRed else Muted,
                                    )
                                }
                                Text(state.money(p.balance), fontWeight = FontWeight.Bold, color = Teal)
                            }
                        }
                    }
                }
            }

            // today's spend
            if (isCurrent) {
                val spentToday = list.filter { it.date == today && it.isPersonalExpense }.sumOf { it.amount }
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
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = 14.dp)
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(26.dp), spotColor = Indigo)
            .clip(RoundedCornerShape(26.dp))
            .background(CardBrush),
    ) {
        // soft decorative circles
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.08f), radius = size.height * 0.75f, center = Offset(size.width * 0.95f, 0f))
            drawCircle(Color.White.copy(alpha = 0.06f), radius = size.height * 0.5f, center = Offset(size.width * 0.75f, size.height * 1.05f))
        }
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Saved this month", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (income > 0) {
                    val saved = (balance / income * 100).toInt()
                    Text(
                        if (saved >= 0) "$saved% saved" else "Overspent",
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text(
                state.money(balance),
                fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
            )
            Text(
                "Income minus my own expenses (company & loans shown separately)",
                fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat("↓  Income", state.money(income), Modifier.weight(1f))
                MiniStat("↑  My expenses", state.money(expense), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(12.dp),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DueTile(emoji: String, label: String, value: String, sub: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 16.sp) }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 11.sp, color = Muted, maxLines = 1)
        Text(value, fontSize = 13.sp, color = color, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, fontSize = 10.sp, color = Muted, maxLines = 1)
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
