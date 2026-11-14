package com.salim.money

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ReportsScreen(state: AppState) {
    val context = LocalContext.current
    var yearly by rememberSaveable { mutableStateOf(false) }
    val m = state.month
    val data = if (yearly) {
        ReportData.build(state, LocalDate.of(m.year, 1, 1), LocalDate.of(m.year, 12, 31), "Year ${m.year}")
    } else {
        ReportData.build(state, m.atDay(1), m.atEndOfMonth(), m.format(MONTH))
    }
    val fileBase = "${state.settings.appName.replace(Regex("[^A-Za-z0-9]+"), "_")}_" +
        if (yearly) "${m.year}" else m.toString()

    var pending by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val p = pending
        if (uri != null && p != null) writeToUri(context, uri, p.first)
        pending = null
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("📅 Month", !yearly, Indigo) { yearly = false }
            Pill("🗓️ Year ${m.year}", yearly, Indigo) { yearly = true }
        }

        // summary tiles
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Income", state.money(data.income), IncomeGreen, "⬇", Modifier.weight(1f))
            Tile("Expense", state.money(data.expense), ExpenseRed, "⬆", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Savings", state.money(data.net), if (data.net >= 0) Indigo else ExpenseRed, "🏦", Modifier.weight(1f))
            Tile(
                "Saved %", if (data.income > 0) "${(data.savingsRate * 100).toInt()}%" else "–",
                Violet, "📈", Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Avg / day", state.money(data.avgPerDay), WarnAmber, "📊", Modifier.weight(1f))
            Tile("Entries", data.txns.size.toString(), Color(0xFF0EA5E9), "🧾", Modifier.weight(1f))
        }

        // income vs expense trend
        SectionTitle(if (yearly) "Month by month" else "Last 6 months")
        WhiteCard {
            Column(Modifier.padding(16.dp)) {
                val bars = if (yearly) {
                    data.months.map { Triple(it.first.format(MONTH_SHORT).take(1), it.second, it.third) }
                } else {
                    (5 downTo 0).map { i ->
                        val mm = m.minusMonths(i.toLong())
                        Triple(mm.format(MONTH_SHORT), state.income(mm), state.expense(mm))
                    }
                }
                IncomeExpenseBars(bars, highlight = if (yearly) (if (m.year == YearMonth.now().year) YearMonth.now().monthValue - 1 else -1) else 5)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(IncomeGreen, "Income")
                    LegendDot(ExpenseRed, "Expense")
                }
            }
        }

        // daily spending (month view)
        if (!yearly) {
            SectionTitle("Daily spending")
            WhiteCard {
                Column(Modifier.padding(16.dp)) {
                    val days = (1..m.lengthOfMonth()).map { d ->
                        data.expenses.filter { it.date.dayOfMonth == d }.sumOf { it.amount }
                    }
                    val limit = state.store.budgets[TOTAL]?.let { it / m.lengthOfMonth() }
                    DailyBars(days, limit = limit, today = if (m == YearMonth.now()) LocalDate.now().dayOfMonth - 1 else -1)
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("1", fontSize = 10.sp, color = Muted, modifier = Modifier.weight(1f))
                        Text("${m.lengthOfMonth()}", fontSize = 10.sp, color = Muted)
                    }
                    val top = days.withIndex().maxByOrNull { it.value }
                    if (top != null && top.value > 0) {
                        Text(
                            "Highest: ${state.money(top.value)} on ${m.atDay(top.index + 1).format(SHORT_DATE)}",
                            fontSize = 12.sp, color = Muted,
                        )
                    }
                    if (limit != null) LegendDot(WarnAmber, "Daily budget ${state.money(limit)}")
                }
            }
        }

        // by category
        SectionTitle("Expenses by category")
        WhiteCard {
            if (data.expenseRows.isEmpty()) {
                EmptyHint("🧾", "No expenses", "Nothing spent in ${data.periodLabel}.")
            } else {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Donut(data.expenseRows.map { it.category.col() to it.amount }, Modifier.size(180.dp), thickness = 26.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total spent", fontSize = 12.sp, color = Muted)
                                Text(state.num(data.expense), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Ink)
                                Text(state.settings.currency, fontSize = 11.sp, color = Muted)
                            }
                        }
                    }
                    data.expenseRows.forEach { r -> CategoryLine(state, r, data.expenseRows.first().amount) }
                }
            }
        }

        // income sources
        if (data.incomeRows.isNotEmpty()) {
            SectionTitle("Income sources")
            WhiteCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    data.incomeRows.forEach { r -> CategoryLine(state, r, data.incomeRows.first().amount) }
                }
            }
        }

        // payment methods
        if (data.byMethod.isNotEmpty()) {
            SectionTitle("Paid with")
            WhiteCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.byMethod.forEach { (k, v) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(k, color = Ink, modifier = Modifier.width(80.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BudgetBarPlain(v / data.expense, Violet, Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Text(state.money(v), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        }
                    }
                }
            }
        }

        // export
        SectionTitle("Download report")
        WhiteCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${data.periodLabel} • ${data.txns.size} entries", color = Muted, fontSize = 13.sp)
                ExportRow(
                    label = "📄 PDF report",
                    color = ExpenseRed,
                    onOpen = { openFile(context, writeReportFile(context, "$fileBase.pdf", PdfReport.build(data)), MIME_PDF) },
                    onShare = { shareFile(context, writeReportFile(context, "$fileBase.pdf", PdfReport.build(data)), MIME_PDF) },
                    onSave = { pending = PdfReport.build(data) to MIME_PDF; saveLauncher.launch("$fileBase.pdf") },
                )
                ExportRow(
                    label = "📊 Excel sheet",
                    color = IncomeGreen,
                    onOpen = { openFile(context, writeReportFile(context, "$fileBase.xlsx", XlsxReport.build(data)), MIME_XLSX) },
                    onShare = { shareFile(context, writeReportFile(context, "$fileBase.xlsx", XlsxReport.build(data)), MIME_XLSX) },
                    onSave = { pending = XlsxReport.build(data) to MIME_XLSX; saveLauncher.launch("$fileBase.xlsx") },
                )
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun ExportRow(label: String, color: Color, onOpen: () -> Unit, onShare: () -> Unit, onSave: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.Bold, color = Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onOpen, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color),
            ) { Text("Open") }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Share") }
            OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Save") }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, color: Color, emoji: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 13.sp, color = color) }
            Spacer(Modifier.width(8.dp))
            Text(label, color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CategoryLine(state: AppState, r: ReportData.Row, max: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CategoryBadge(r.category, 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(
                    r.category.name, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(state.money(r.amount), fontWeight = FontWeight.Bold, color = Ink, fontSize = 13.sp)
            }
            BudgetBarPlain(if (max > 0) r.amount / max else 0.0, r.category.col())
            Row {
                Text(
                    "${(r.share * 100).let { if (it < 1 && it > 0) "<1" else it.toInt().toString() }}% • ${r.count} entr${if (r.count == 1) "y" else "ies"}",
                    fontSize = 11.sp, color = Muted, modifier = Modifier.weight(1f),
                )
                r.budget?.let { b ->
                    Text(
                        "Budget ${state.money(b)}", fontSize = 11.sp,
                        color = if (r.amount > b) ExpenseRed else IncomeGreen, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
fun BudgetBarPlain(ratio: Double, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)).background(Color(0xFFEDEFF5))) {
        Box(
            Modifier
                .fillMaxWidth(ratio.coerceIn(0.0, 1.0).toFloat())
                .height(7.dp)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}
