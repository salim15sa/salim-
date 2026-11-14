package com.salim.money

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Kind { EXPENSE, INCOME }

data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val color: Long,
    val kind: Kind,
)

data class Txn(
    val id: Long,
    val kind: Kind,
    val amount: Double,
    val categoryId: String,
    val date: LocalDate,
    val note: String,
    val method: String,
)

data class Settings(
    val appName: String = "My Wallet",
    val currency: String = "OMR",
    val decimals: Int = 3,
    val budgetPopup: Boolean = true,
    val notifications: Boolean = true,
    val methods: List<String> = listOf("Cash", "Card", "Bank", "Wallet"),
)

/** Everything the app stores, saved as one JSON file. */
data class Store(
    val categories: List<Category>,
    val txns: List<Txn>,
    /** Monthly budget per category id. The key [TOTAL] is the overall monthly budget. */
    val budgets: Map<String, Double>,
    val settings: Settings,
    /** "yyyy-MM|catId|level" alerts already notified, so each one fires only once. */
    val alerted: Set<String> = emptySet(),
)

const val TOTAL = "__total__"

val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
val NICE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.US)
val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
val DAY_HEAD: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.US)
val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
val MONTH_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.US)

fun parseAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }

object Defaults {
    val categories = listOf(
        Category("food", "Food", "🍔", 0xFFF97316, Kind.EXPENSE),
        Category("grocery", "Grocery", "🛒", 0xFF84CC16, Kind.EXPENSE),
        Category("travel", "Travel", "🚕", 0xFFEAB308, Kind.EXPENSE),
        Category("fuel", "Fuel", "⛽", 0xFF78716C, Kind.EXPENSE),
        Category("home", "Home & Rent", "🏠", 0xFF6366F1, Kind.EXPENSE),
        Category("bills", "Bills", "💡", 0xFF0EA5E9, Kind.EXPENSE),
        Category("phone", "Mobile & Net", "📱", 0xFF06B6D4, Kind.EXPENSE),
        Category("tools", "Tools", "🛠️", 0xFF64748B, Kind.EXPENSE),
        Category("health", "Hospital", "🏥", 0xFFEF4444, Kind.EXPENSE),
        Category("shopping", "Shopping", "🛍️", 0xFFEC4899, Kind.EXPENSE),
        Category("family", "Family / Send home", "👨‍👩‍👧", 0xFF8B5CF6, Kind.EXPENSE),
        Category("education", "Education", "📚", 0xFF14B8A6, Kind.EXPENSE),
        Category("fun", "Entertainment", "🎬", 0xFFD946EF, Kind.EXPENSE),
        Category("other_exp", "Other", "📦", 0xFF94A3B8, Kind.EXPENSE),

        Category("salary", "Salary", "💼", 0xFF10B981, Kind.INCOME),
        Category("bonus", "Bonus", "🎁", 0xFF22C55E, Kind.INCOME),
        Category("overtime", "Overtime", "⏱️", 0xFF059669, Kind.INCOME),
        Category("business", "Side business", "📈", 0xFF0D9488, Kind.INCOME),
        Category("refund", "Refund", "↩️", 0xFF3B82F6, Kind.INCOME),
        Category("other_inc", "Other income", "💰", 0xFF65A30D, Kind.INCOME),
    )

    /** Colours offered when the user creates a category. */
    val palette = listOf(
        0xFFF97316, 0xFFEAB308, 0xFF84CC16, 0xFF10B981, 0xFF14B8A6, 0xFF06B6D4,
        0xFF0EA5E9, 0xFF3B82F6, 0xFF6366F1, 0xFF8B5CF6, 0xFFD946EF, 0xFFEC4899,
        0xFFEF4444, 0xFF78716C, 0xFF64748B, 0xFF0F766E,
    )

    val emojis = listOf(
        "🍔", "☕", "🍕", "🛒", "🚕", "🚌", "✈️", "⛽", "🚗", "🏠", "💡", "💧", "📱", "🌐",
        "🛠️", "🔧", "🏥", "💊", "🛍️", "👕", "👟", "💄", "✂️", "👨‍👩‍👧", "🎓", "📚", "🎬", "🎮",
        "⚽", "🏋️", "🎁", "💳", "🧾", "🏦", "💼", "💰", "📈", "⏱️", "↩️", "🐾", "🙏", "📦",
    )
}

object StoreJson {
    fun toJson(s: Store): String {
        val cats = JSONArray()
        s.categories.forEach { c ->
            cats.put(JSONObject().apply {
                put("id", c.id); put("name", c.name); put("emoji", c.emoji)
                put("color", c.color); put("kind", c.kind.name)
            })
        }
        val txns = JSONArray()
        s.txns.forEach { t ->
            txns.put(JSONObject().apply {
                put("id", t.id); put("kind", t.kind.name); put("amount", t.amount)
                put("cat", t.categoryId); put("date", t.date.format(ISO))
                put("note", t.note); put("method", t.method)
            })
        }
        val budgets = JSONObject()
        s.budgets.forEach { (k, v) -> budgets.put(k, v) }
        val st = s.settings
        val settings = JSONObject().apply {
            put("appName", st.appName); put("currency", st.currency); put("decimals", st.decimals)
            put("budgetPopup", st.budgetPopup); put("notifications", st.notifications)
            put("methods", JSONArray(st.methods))
        }
        return JSONObject()
            .put("app", "MyWallet").put("version", 1)
            .put("categories", cats).put("transactions", txns)
            .put("budgets", budgets).put("settings", settings)
            .put("alerted", JSONArray(s.alerted.toList()))
            .toString(2)
    }

    fun fromJson(text: String): Store {
        val o = JSONObject(text)
        val ca = o.optJSONArray("categories") ?: JSONArray()
        val cats = (0 until ca.length()).map { i ->
            val c = ca.getJSONObject(i)
            Category(
                id = c.getString("id"),
                name = c.optString("name"),
                emoji = c.optString("emoji", "📦"),
                color = c.optLong("color", 0xFF94A3B8),
                kind = runCatching { Kind.valueOf(c.optString("kind")) }.getOrDefault(Kind.EXPENSE),
            )
        }.ifEmpty { Defaults.categories }
        val ta = o.optJSONArray("transactions") ?: JSONArray()
        val txns = (0 until ta.length()).map { i ->
            val t = ta.getJSONObject(i)
            Txn(
                id = t.getLong("id"),
                kind = runCatching { Kind.valueOf(t.optString("kind")) }.getOrDefault(Kind.EXPENSE),
                amount = t.getDouble("amount"),
                categoryId = t.optString("cat"),
                date = LocalDate.parse(t.getString("date")),
                note = t.optString("note"),
                method = t.optString("method"),
            )
        }
        val bo = o.optJSONObject("budgets") ?: JSONObject()
        val budgets = bo.keys().asSequence().associateWith { bo.getDouble(it) }
        val so = o.optJSONObject("settings") ?: JSONObject()
        val d = Settings()
        val ma = so.optJSONArray("methods")
        val settings = Settings(
            appName = so.optString("appName", d.appName).ifBlank { d.appName },
            currency = so.optString("currency", d.currency).ifBlank { d.currency },
            decimals = so.optInt("decimals", d.decimals).coerceIn(0, 3),
            budgetPopup = so.optBoolean("budgetPopup", d.budgetPopup),
            notifications = so.optBoolean("notifications", d.notifications),
            methods = ma?.let { a -> (0 until a.length()).map { a.getString(it) } }?.ifEmpty { null } ?: d.methods,
        )
        val al = o.optJSONArray("alerted") ?: JSONArray()
        val alerted = (0 until al.length()).map { al.getString(it) }.toSet()
        return Store(cats, sortTxns(txns), budgets, settings, alerted)
    }
}

fun sortTxns(list: List<Txn>): List<Txn> =
    list.sortedWith(compareByDescending<Txn> { it.date }.thenByDescending { it.id })

class StoreRepo(context: Context) {
    private val file = File(context.filesDir, "wallet.json")

    fun load(): Store {
        if (!file.exists()) return Store(Defaults.categories, emptyList(), emptyMap(), Settings())
        return try {
            StoreJson.fromJson(file.readText())
        } catch (e: Exception) {
            // Keep the unreadable file aside instead of overwriting it.
            file.copyTo(File(file.parentFile, "wallet.broken.json"), overwrite = true)
            Store(Defaults.categories, emptyList(), emptyMap(), Settings())
        }
    }

    fun save(s: Store) {
        val tmp = File(file.parentFile, "wallet.json.tmp")
        tmp.writeText(StoreJson.toJson(s))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}

// ------------------------------------------------------------------ budget maths

enum class Level { OK, WARN, OVER }

/** How a monthly budget stands for one month. */
data class BudgetStatus(
    val key: String,
    val budget: Double,
    val spent: Double,
    val month: YearMonth,
    val today: LocalDate,
) {
    val remaining: Double get() = budget - spent
    val ratio: Double get() = if (budget <= 0) 0.0 else spent / budget
    val level: Level
        get() = when {
            ratio >= 1.0 -> Level.OVER
            ratio >= 0.8 -> Level.WARN
            else -> Level.OK
        }

    /** Days left in the month counting today; whole month for a future month, 0 for a past one. */
    val daysLeft: Int
        get() {
            val cur = YearMonth.from(today)
            return when {
                month == cur -> month.lengthOfMonth() - today.dayOfMonth + 1
                month.isAfter(cur) -> month.lengthOfMonth()
                else -> 0
            }
        }

    /** What can still be spent per day for the rest of the month. */
    val perDay: Double get() = if (daysLeft <= 0) 0.0 else maxOf(0.0, remaining) / daysLeft

    /** Even spread of the budget over the whole month. */
    val plannedPerDay: Double get() = budget / month.lengthOfMonth()

    /** Where spending "should" be by today if spread evenly. */
    val expectedByToday: Double
        get() {
            val days = month.lengthOfMonth()
            val elapsed = days - daysLeft + 1
            return budget * elapsed.coerceIn(0, days) / days
        }
}
