package com.ats.projects

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Work stages tracked for every device – same columns as the "Project Progress Checklist". */
enum class Stage(val label: String, val short: String) {
    FIRST_FIX("First Fix", "1st Fix"),
    CONDUIT("Conduit", "Conduit"),
    CABLE("Cable Pulled", "Cable"),
    INSTALLED("Installed", "Install"),
    TESTED("Tested", "Test"),
}

/** Cell value in the checklist: blank, ✓ done, ✗ problem / not ready. */
enum class Mark(val symbol: String) {
    NONE(""), DONE("✓"), NOT("✗");

    fun next(): Mark = when (this) {
        NONE -> DONE
        DONE -> NOT
        NOT -> NONE
    }
}

data class Device(
    val id: Long,
    val floor: String,
    val system: String,
    val type: String,
    val name: String,
    val location: String,
    val marks: Map<Stage, Mark> = emptyMap(),
    val note: String = "",
) {
    fun mark(s: Stage): Mark = marks[s] ?: Mark.NONE
    fun with(s: Stage, m: Mark) = copy(marks = marks + (s to m))
    val doneCount: Int get() = Stage.entries.count { mark(it) == Mark.DONE }
}

val PROJECT_STATUSES = listOf(
    "Site Not Ready",
    "Conduit / First Fix",
    "Cable Pulling",
    "Installation",
    "Testing & Commissioning",
    "Handover Stage",
    "Hold",
    "Completed",
)

data class Project(
    val id: Long,
    val name: String,
    val client: String = "",
    val location: String = "",
    val systems: String = "ELV & KNX",
    val status: String = PROJECT_STATUSES[0],
    val latestUpdate: String = "",
    val nextStep: String = "",
    val contact: String = "",
    val phone: String = "",
    val date: LocalDate = LocalDate.now(),
    val floors: List<String> = Catalog.defaultFloors.take(2),
    val rooms: List<String> = emptyList(),
    val devices: List<Device> = emptyList(),
) {
    /** Done stage cells / all stage cells, 0..1. */
    val progress: Double
        get() = if (devices.isEmpty()) 0.0
        else devices.sumOf { it.doneCount }.toDouble() / (devices.size * Stage.entries.size)

    fun stageDone(s: Stage) = devices.count { it.mark(s) == Mark.DONE }
    fun stageIssues(s: Stage) = devices.count { it.mark(s) == Mark.NOT }

    /** Floors in the project's order, then any extra floor used by a device. */
    val orderedFloors: List<String>
        get() = (floors + devices.map { it.floor }).distinct()

    /** Devices in checklist order: floor → system (catalog order) → type → number. */
    fun sortedDevices(): List<Device> {
        val fo = orderedFloors
        return devices.sortedWith(
            compareBy<Device>(
                { fo.indexOf(it.floor) },
                { Catalog.systemIndex(it.system) },
                { it.system },
                { Catalog.typeIndex(it.system, it.type) },
                { it.type },
                { trailingNumber(it.name) },
                { it.name },
            ),
        )
    }

    /** Next free number for a name prefix, e.g. CAM12 after CAM11 (7" and 4" touch screens share one series). */
    fun nextNumber(prefix: String): Int =
        (devices.filter { namePrefix(it.name).equals(prefix.trim(), true) }.maxOfOrNull { trailingNumber(it.name) } ?: 0) + 1

    /** Builds [count] new device names for a type: "CAM12", "CAM13" … */
    fun newNames(system: String, type: String, count: Int): List<String> {
        val prefix = Catalog.type(system, type).prefix
        val first = nextNumber(prefix)
        return (0 until count).map { "$prefix${first + it}" }
    }
}

fun trailingNumber(s: String): Int = Regex("(\\d+)\\s*$").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: 0

fun namePrefix(s: String): String = s.replace(Regex("\\d+\\s*$"), "").trim()

val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
val SHOW_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d/M/yyyy", Locale.US)
val NICE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

fun pct(v: Double): String = "${Math.round(v * 100)}%"

// ------------------------------------------------------------------ json

object ProjectJson {
    fun toJson(list: List<Project>): String {
        val arr = JSONArray()
        list.forEach { arr.put(project(it)) }
        return JSONObject().put("app", "ATS Projects").put("version", 1).put("projects", arr).toString(1)
    }

    fun fromJson(text: String): List<Project> {
        val t = text.trim()
        val arr = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).getJSONArray("projects")
        return (0 until arr.length()).map { readProject(arr.getJSONObject(it)) }
    }

    private fun project(p: Project) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("client", p.client)
        put("location", p.location)
        put("systems", p.systems)
        put("status", p.status)
        put("latestUpdate", p.latestUpdate)
        put("nextStep", p.nextStep)
        put("contact", p.contact)
        put("phone", p.phone)
        put("date", p.date.format(ISO))
        put("floors", JSONArray(p.floors))
        put("rooms", JSONArray(p.rooms))
        put("devices", JSONArray().apply { p.devices.forEach { put(device(it)) } })
    }

    private fun device(d: Device) = JSONObject().apply {
        put("id", d.id)
        put("floor", d.floor)
        put("system", d.system)
        put("type", d.type)
        put("name", d.name)
        put("location", d.location)
        put("note", d.note)
        val m = JSONObject()
        d.marks.forEach { (s, v) -> if (v != Mark.NONE) m.put(s.name, v.name) }
        put("marks", m)
    }

    private fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

    private fun readProject(o: JSONObject) = Project(
        id = o.getLong("id"),
        name = o.optString("name"),
        client = o.optString("client"),
        location = o.optString("location"),
        systems = o.optString("systems"),
        status = o.optString("status", PROJECT_STATUSES[0]),
        latestUpdate = o.optString("latestUpdate"),
        nextStep = o.optString("nextStep"),
        contact = o.optString("contact"),
        phone = o.optString("phone"),
        date = runCatching { LocalDate.parse(o.optString("date")) }.getOrDefault(LocalDate.now()),
        floors = strings(o.optJSONArray("floors")),
        rooms = strings(o.optJSONArray("rooms")),
        devices = o.optJSONArray("devices")?.let { a -> (0 until a.length()).map { readDevice(a.getJSONObject(it)) } }
            ?: emptyList(),
    )

    private fun readDevice(o: JSONObject): Device {
        val m = o.optJSONObject("marks")
        val marks = mutableMapOf<Stage, Mark>()
        if (m != null) {
            Stage.entries.forEach { s ->
                val v = m.optString(s.name, "")
                runCatching { Mark.valueOf(v) }.getOrNull()?.let { marks[s] = it }
            }
        }
        return Device(
            id = o.getLong("id"),
            floor = o.optString("floor"),
            system = o.optString("system"),
            type = o.optString("type"),
            name = o.optString("name"),
            location = o.optString("location"),
            marks = marks,
            note = o.optString("note"),
        )
    }
}
