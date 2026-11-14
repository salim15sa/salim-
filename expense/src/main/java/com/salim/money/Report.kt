package com.salim.money

import java.time.LocalDate
import java.time.YearMonth

/** Everything a report needs for one period; used by the screen, PDF and Excel. */
class ReportData(
    val title: String,
    val periodLabel: String,
    val currency: String,
    val decimals: Int,
    val start: LocalDate,
    val end: LocalDate,
    val txns: List<Txn>,
    val cat: (String) -> Category,
    /** Monthly budgets scaled to the period length (months), keyed by category id / TOTAL. */
    val budgets: Map<String, Double>,
) {
    val expenses = txns.filter { it.kind == Kind.EXPENSE }
    val incomes = txns.filter { it.kind == Kind.INCOME }
    val income = incomes.sumOf { it.amount }
    val expense = expenses.sumOf { it.amount }
    val net = income - expense
    val savingsRate = if (income > 0) net / income else 0.0

    class Row(val category: Category, val amount: Double, val count: Int, val share: Double, val budget: Double?)

    private fun rows(list: List<Txn>, total: Double) = list.groupBy { it.categoryId }
        .map { (id, l) ->
            val a = l.sumOf { it.amount }
            Row(cat(id), a, l.size, if (total > 0) a / total else 0.0, budgets[id])
        }
        .sortedByDescending { it.amount }

    val expenseRows = rows(expenses, expense)
    val incomeRows = rows(incomes, income)

    val byMethod: List<Pair<String, Double>> = expenses.groupBy { it.method.ifBlank { "Not set" } }
        .map { (k, l) -> k to l.sumOf { it.amount } }.sortedByDescending { it.second }

    /** Days in the period up to today (for the daily average). */
    val daysElapsed: Int
        get() {
            val today = LocalDate.now()
            val last = if (end.isAfter(today)) today else end
            return if (last.isBefore(start)) 0 else (last.toEpochDay() - start.toEpochDay() + 1).toInt()
        }

    val avgPerDay get() = if (daysElapsed > 0) expense / daysElapsed else 0.0

    /** Month-by-month totals (for year reports). */
    val months: List<Triple<YearMonth, Double, Double>>
        get() {
            var m = YearMonth.from(start)
            val out = mutableListOf<Triple<YearMonth, Double, Double>>()
            while (!m.isAfter(YearMonth.from(end))) {
                val l = txns.filter { YearMonth.from(it.date) == m }
                out += Triple(m, l.filter { it.kind == Kind.INCOME }.sumOf { it.amount }, l.filter { it.kind == Kind.EXPENSE }.sumOf { it.amount })
                m = m.plusMonths(1)
            }
            return out
        }

    fun money(v: Double): String = String.format(java.util.Locale.US, "%,.${decimals}f %s", v, currency)

    companion object {
        fun build(state: AppState, start: LocalDate, end: LocalDate, label: String): ReportData {
            val months = (YearMonth.from(end).let { e ->
                val s = YearMonth.from(start)
                (e.year - s.year) * 12 + e.monthValue - s.monthValue + 1
            }).coerceAtLeast(1)
            return ReportData(
                title = state.settings.appName,
                periodLabel = label,
                currency = state.settings.currency,
                decimals = state.settings.decimals,
                start = start,
                end = end,
                txns = state.txns.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
                    .sortedWith(compareBy<Txn> { it.date }.thenBy { it.id }),
                cat = { state.category(it) },
                budgets = state.store.budgets.mapValues { it.value * months },
            )
        }
    }
}
