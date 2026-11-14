package com.salim.money

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.createChannel(applicationContext)
        val state = AppState(StoreRepo(applicationContext), applicationContext)
        setContent {
            WalletTheme { App(state) }
        }
    }
}

// ------------------------------------------------------------------ colours

val Night = Color(0xFF0F172A)
val Indigo = Color(0xFF4F46E5)
val Teal = Color(0xFF0D9488)
val Sky = Color(0xFF0284C7)
val Violet = Color(0xFF9333EA)
val Ink = Color(0xFF111827)
val Muted = Color(0xFF6B7280)
val Paper = Color(0xFFF5F6FA)
val IncomeGreen = Color(0xFF10B981)
val ExpenseRed = Color(0xFFF43F5E)
val WarnAmber = Color(0xFFF59E0B)
val Line = Color(0xFFE5E7EB)

fun Category.col(): Color = Color(color)

@Composable
fun WalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Indigo,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE0E7FF),
            onPrimaryContainer = Color(0xFF312E81),
            secondary = Violet,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF3E8FF),
            onSecondaryContainer = Color(0xFF581C87),
            background = Paper,
            surface = Color.White,
            surfaceVariant = Color(0xFFEEF0F8),
            error = ExpenseRed,
        ),
        content = content,
    )
}

// ------------------------------------------------------------------ state

/** One message to show after saving an expense that falls under a budget. */
data class BudgetNotice(val category: Category?, val status: BudgetStatus, val overall: BudgetStatus?)

/** App-wide state; every change is written straight to disk. */
class AppState(private val repo: StoreRepo, private val context: Context) {
    var store by mutableStateOf(repo.load())
        private set

    /** The month shown on Home, Transactions, Budget and Reports. */
    var month by mutableStateOf(YearMonth.now())

    var notice by mutableStateOf<BudgetNotice?>(null)

    val settings get() = store.settings
    val categories get() = store.categories
    val txns get() = store.txns

    private fun commit(s: Store) {
        store = s
        repo.save(s)
    }

    fun category(id: String): Category =
        store.categories.firstOrNull { it.id == id }
            ?: Category(id, "Uncategorised", "❔", 0xFF94A3B8, Kind.EXPENSE)

    fun categoriesOf(kind: Kind) = store.categories.filter { it.kind == kind }

    fun nextId(): Long = maxOf(System.currentTimeMillis(), (store.txns.maxOfOrNull { it.id } ?: 0L) + 1)

    // ---- transactions

    fun upsert(t: Txn) {
        commit(store.copy(txns = sortTxns(store.txns.filter { it.id != t.id } + t)))
        if (t.isPersonalExpense) checkBudgets(t)
    }

    fun delete(t: Txn) = commit(store.copy(txns = store.txns.filter { it.id != t.id }))

    fun restore(t: Txn) = commit(store.copy(txns = sortTxns(store.txns + t)))

    fun txnsIn(m: YearMonth) = store.txns.filter { YearMonth.from(it.date) == m }

    fun income(m: YearMonth) = txnsIn(m).filter { it.kind == Kind.INCOME }.sumOf { it.amount }

    /** My own spending (company-reimbursable expenses are kept apart). */
    fun expense(m: YearMonth) = txnsIn(m).filter { it.isPersonalExpense }.sumOf { it.amount }

    /** Spent for the company in this month (to be paid back). */
    fun claimsIn(m: YearMonth) = txnsIn(m).filter { it.isClaim }

    /** Totals per category for a month, biggest first. Expenses = personal only. */
    fun byCategory(m: YearMonth, kind: Kind = Kind.EXPENSE): List<Pair<Category, Double>> =
        txnsIn(m).filter { if (kind == Kind.EXPENSE) it.isPersonalExpense else it.kind == kind }
            .groupBy { it.categoryId }
            .map { (id, l) -> category(id) to l.sumOf { it.amount } }
            .sortedByDescending { it.second }

    // ---- company claims (reimbursable expenses)

    val pendingClaims get() = store.txns.filter { it.claimPending }
    val pendingClaimTotal get() = pendingClaims.sumOf { it.amount }

    /** Mark claims as paid back on [on]; null puts them back to pending. */
    fun markReimbursed(ids: Set<Long>, on: LocalDate?) = commit(
        store.copy(txns = store.txns.map { if (it.id in ids && it.isClaim) it.copy(reimbursedOn = on) else it }),
    )

    fun markPending(t: Txn) = commit(
        store.copy(txns = store.txns.map { if (it.id == t.id) it.copy(reimbursedOn = null) else it }),
    )

    // ---- loans / people

    val loans get() = store.loans

    fun upsertLoan(l: LoanEntry) = commit(store.copy(loans = sortLoans(store.loans.filter { it.id != l.id } + l)))

    fun deleteLoan(l: LoanEntry) = commit(store.copy(loans = store.loans.filter { it.id != l.id }))

    fun restoreLoan(l: LoanEntry) = commit(store.copy(loans = sortLoans(store.loans + l)))

    fun nextLoanId(): Long = maxOf(System.currentTimeMillis(), (store.loans.maxOfOrNull { it.id } ?: 0L) + 1)

    /** Everyone in the loan book; open balances first, biggest first. */
    fun people(): List<PersonBalance> = store.loans.groupBy { it.person.trim().lowercase() }
        .map { (_, l) ->
            PersonBalance(
                name = l.first().person.trim(),
                gave = l.filter { it.gave }.sumOf { it.amount },
                got = l.filter { !it.gave }.sumOf { it.amount },
                entries = sortLoans(l),
            )
        }
        .sortedWith(compareByDescending<PersonBalance> { kotlin.math.abs(it.balance) > 0.0005 }
            .thenByDescending { kotlin.math.abs(it.balance) })

    fun person(name: String) = people().firstOrNull { it.name.equals(name.trim(), true) }

    val owedToMe get() = people().filter { it.balance > 0.0005 }.sumOf { it.balance }
    val iOwe get() = people().filter { it.balance < -0.0005 }.sumOf { -it.balance }

    fun renamePerson(old: String, new: String) {
        val n = new.trim()
        if (n.isEmpty()) return
        commit(store.copy(loans = store.loans.map { if (it.person.equals(old, true)) it.copy(person = n) else it }))
    }

    fun deletePerson(name: String) =
        commit(store.copy(loans = store.loans.filterNot { it.person.equals(name, true) }))

    // ---- budgets

    fun budgetStatus(key: String, m: YearMonth = month): BudgetStatus? {
        val b = store.budgets[key] ?: return null
        val list = txnsIn(m).filter { it.isPersonalExpense }
        val spent = if (key == TOTAL) list.sumOf { it.amount }
        else list.filter { it.categoryId == key }.sumOf { it.amount }
        return BudgetStatus(key, b, spent, m, LocalDate.now())
    }

    fun setBudget(key: String, amount: Double?) {
        val b = store.budgets.toMutableMap()
        if (amount == null || amount <= 0) b.remove(key) else b[key] = amount
        // Let alerts fire again for the new amount.
        val prefix = YearMonth.now().toString() + "|" + key + "|"
        commit(store.copy(budgets = b, alerted = store.alerted.filterNot { it.startsWith(prefix) }.toSet()))
    }

    private fun checkBudgets(t: Txn) {
        val m = YearMonth.from(t.date)
        val cat = budgetStatus(t.categoryId, m)
        val overall = budgetStatus(TOTAL, m)
        val main = cat ?: overall ?: return
        if (settings.budgetPopup) {
            notice = BudgetNotice(if (cat != null) category(t.categoryId) else null, main, if (cat != null) overall else null)
        }
        // System notification once per month and level (80% and 100%).
        val fired = store.alerted.toMutableSet()
        listOfNotNull(cat, overall).forEach { s ->
            if (s.level == Level.OK) return@forEach
            val tag = "$m|${s.key}|${s.level}"
            if (tag in fired) return@forEach
            fired += tag
            if (settings.notifications) {
                val name = if (s.key == TOTAL) "Monthly budget" else category(s.key).let { "${it.emoji} ${it.name}" }
                Notifier.budget(context, name, s, this)
            }
        }
        if (fired != store.alerted) commit(store.copy(alerted = fired))
    }

    // ---- categories

    fun saveCategory(c: Category) {
        val list = store.categories.toMutableList()
        val i = list.indexOfFirst { it.id == c.id }
        if (i >= 0) list[i] = c else list += c
        commit(store.copy(categories = list))
    }

    /** Deletes a category; its transactions move to the kind's "Other" category. */
    fun deleteCategory(c: Category) {
        val fallback = if (c.kind == Kind.EXPENSE) "other_exp" else "other_inc"
        val cats = store.categories.filter { it.id != c.id }.let { l ->
            if (l.none { it.id == fallback }) l + Defaults.categories.first { it.id == fallback } else l
        }
        commit(
            store.copy(
                categories = cats,
                txns = store.txns.map { if (it.categoryId == c.id) it.copy(categoryId = fallback) else it },
                budgets = store.budgets - c.id,
            ),
        )
    }

    fun usage(c: Category) = store.txns.count { it.categoryId == c.id }

    // ---- settings / backup

    fun updateSettings(s: Settings) = commit(store.copy(settings = s))

    fun replaceAll(s: Store) = commit(s)

    fun resetAll() = commit(Store(Defaults.categories, emptyList(), emptyMap(), store.settings))

    fun shortDays(d: LocalDate): String {
        val n = java.time.temporal.ChronoUnit.DAYS.between(d, LocalDate.now())
        return when {
            n == 0L -> "today"
            n == 1L -> "yesterday"
            n > 1 -> "$n days ago"
            n == -1L -> "tomorrow"
            else -> "in ${-n} days"
        }
    }

    // ---- formatting

    fun money(v: Double, sign: Boolean = false): String {
        val d = settings.decimals
        val body = String.format(Locale.US, "%,.${d}f", kotlin.math.abs(v))
        val s = when {
            sign && v > 0 -> "+"
            v < 0 -> "-"
            else -> ""
        }
        return "$s$body ${settings.currency}"
    }

    fun num(v: Double): String = String.format(Locale.US, "%.${settings.decimals}f", v)
}

// ------------------------------------------------------------------ notifications

object Notifier {
    private const val CHANNEL = "budget"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "Budget alerts", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Tells you when a budget is almost used or exceeded"
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun budget(context: Context, name: String, s: BudgetStatus, state: AppState) {
        if (!canNotify(context)) return
        val title = if (s.level == Level.OVER) "⚠️ $name budget exceeded"
        else "🔔 $name: ${(s.ratio * 100).toInt()}% of budget used"
        val text = if (s.level == Level.OVER)
            "Spent ${state.money(s.spent)} of ${state.money(s.budget)} – over by ${state.money(-s.remaining)}."
        else
            "${state.money(s.remaining)} left for ${s.daysLeft} days – about ${state.money(s.perDay)} per day."
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify((s.key + s.level).hashCode(), n)
        } catch (e: SecurityException) {
            // permission revoked meanwhile
        }
    }
}

// ------------------------------------------------------------------ file helpers

fun writeReportFile(context: Context, name: String, bytes: ByteArray): File {
    val dir = File(context.cacheDir, "reports").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    return File(dir, name).apply { writeBytes(bytes) }
}

fun shareFile(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share"))
}

fun openFile(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(view)
    } catch (e: Exception) {
        shareFile(context, file, mime)
    }
}

fun writeToUri(context: Context, uri: Uri, bytes: ByteArray) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Could not save: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
const val MIME_PDF = "application/pdf"
