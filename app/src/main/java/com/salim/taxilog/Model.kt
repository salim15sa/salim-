package com.salim.taxilog

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One taxi leg. amount == null means "included in the previous fare". */
data class Trip(
    val id: Long,
    val date: LocalDate,
    val site: String,
    val from: String,
    val to: String,
    val amount: Double?,
    val personnel: List<String>,
    val paidBy: String,
)

val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
val NICE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.US)
val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

fun omr(v: Double): String = String.format(Locale.US, "%.3f OMR", v)
fun num3(v: Double): String = String.format(Locale.US, "%.3f", v)

fun parseAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }

object TripJson {
    fun toJson(list: List<Trip>): String {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("date", t.date.format(ISO))
                put("site", t.site)
                put("from", t.from)
                put("to", t.to)
                if (t.amount != null) put("amount", t.amount)
                put("personnel", JSONArray(t.personnel))
                put("paidBy", t.paidBy)
            })
        }
        return JSONObject().put("app", "TaxiLog").put("version", 1).put("trips", arr).toString(2)
    }

    fun fromJson(text: String): List<Trip> {
        val root = text.trim()
        val arr = if (root.startsWith("[")) JSONArray(root) else JSONObject(root).getJSONArray("trips")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val p = o.optJSONArray("personnel") ?: JSONArray()
            Trip(
                id = o.getLong("id"),
                date = LocalDate.parse(o.getString("date")),
                site = o.optString("site"),
                from = o.optString("from"),
                to = o.optString("to"),
                amount = if (o.has("amount") && !o.isNull("amount")) o.getDouble("amount") else null,
                personnel = (0 until p.length()).map { p.getString(it) },
                paidBy = o.optString("paidBy"),
            )
        }
    }
}

class TripRepo(context: Context) {
    private val file = File(context.filesDir, "trips.json")
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): List<Trip> {
        if (!file.exists()) {
            val seed = SeedData.trips()
            save(seed)
            return seed
        }
        return try {
            sort(TripJson.fromJson(file.readText()))
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(list: List<Trip>) {
        val tmp = File(file.parentFile, "trips.json.tmp")
        tmp.writeText(TripJson.toJson(list))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    var people: List<String>
        get() = prefs.getString("people", null)?.split("\n")?.filter { it.isNotBlank() }
            ?: listOf("Salim", "Shabeer", "Aslam")
        set(v) = prefs.edit().putString("people", v.joinToString("\n")).apply()

    var home: String
        get() = prefs.getString("home", null) ?: "Ghubrah"
        set(v) = prefs.edit().putString("home", v).apply()

    var reportTitle: String
        get() = prefs.getString("title", null) ?: "Site Travel & Expense Tracker"
        set(v) = prefs.edit().putString("title", v).apply()

    companion object {
        fun sort(list: List<Trip>): List<Trip> = list.sortedWith(compareBy({ it.date }, { it.id }))
    }
}
