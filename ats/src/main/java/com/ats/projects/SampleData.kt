package com.ats.projects

/** A demo villa (structure of a real KNX & ELV progress checklist) so the app is not empty on first start. */
object SampleData {

    private class Row(val floor: String, val system: String, val type: String, val location: String, val marks: String = "", val note: String = "")

    // marks: 5 chars for First Fix, Conduit, Cable, Installed, Tested – 'v' done, 'x' not, '-' blank
    private val rows = buildList {
        val gf = "Ground Floor"
        listOf(
            "Entrance" to "vx", "Right Front" to "vx", "Parking" to "vvv", "Parking" to "vvv", "Parking" to "vvv",
            "Back Left" to "vvv", "Back Right" to "vx", "Back Right" to "vvv", "Side Right" to "vvv",
            "Side Left" to "vvv", "Pool" to "vvv",
        ).forEach { (loc, m) -> add(Row(gf, "CCTV", "Camera", loc, m)) }
        add(Row(gf, "Intercom", "Outdoor Unit", "Main Door", "xx"))
        add(Row(gf, "Intercom", "Indoor Unit", "Kitchen", "vx"))
        add(Row(gf, "Intercom", "Indoor Unit", "Entrance", "vx"))
        listOf("Family Sitting" to "vvv", "Majlis" to "vvv", "Kitchen" to "vvv", "Outdoor Front" to "vx", "Outdoor Back" to "vx")
            .forEach { (loc, m) -> add(Row(gf, "WIFI", "Access Point", loc, m)) }
        add(Row(gf, "KNX", "7\" Touch Screen", "Entrance", "vvv", "Conduits not completely ready"))
        add(Row(gf, "KNX", "7\" Touch Screen", "Majlis", "vvv"))
        repeat(4) { add(Row(gf, "KNX", "Ceiling Motion Sensor", "Parking", "vv", "In progress")) }
        add(Row(gf, "KNX", "Ceiling Motion Sensor", "Stairs", "vvv"))
        add(Row(gf, "KNX", "AC Gateway", "Family Sitting", "vv"))
        add(Row(gf, "KNX", "AC Gateway", "Family Sitting", "vv"))
        add(Row(gf, "KNX", "AC Gateway", "Majlis", "vv"))
        add(Row(gf, "KNX", "2 Gang Switch", "Entrance"))
        add(Row(gf, "KNX", "Smart DB", "Side Entrance", "xx"))
        listOf("Family Sitting", "Family Sitting", "Family Sitting", "Stairs", "Dining", "Dining", "Passage", "Kitchen", "Majlis")
            .forEach { add(Row(gf, "Intrusion", "Glass Break Sensor", it, "xx")) }
        add(Row(gf, "Intrusion", "Intrusion Keypad", "Entrance", "xx", "Conduits not completely ready"))
        add(Row(gf, "Intrusion", "Intrusion Siren", "Parking", "xx"))
        add(Row(gf, "Intrusion", "Intrusion Siren", "Pool", "xx"))
        add(Row(gf, "Intrusion", "Intrusion Main Box", "Store Room", "xx"))
        add(Row(gf, "Access Control", "Card Reader", "Main Door"))
        add(Row(gf, "Access Control", "Exit Button", "Main Door"))
        add(Row(gf, "Server Room", "Rack Main Box", "Store Room", "xx"))
        listOf("Family Sitting" to "vx", "Family Sitting" to "vx", "Dining" to "xx", "Majlis" to "vx", "Majlis" to "vx", "Driver Room" to "vx")
            .forEach { (loc, m) -> add(Row(gf, "Data Outlets", "Data Point", loc, m)) }
        listOf("Family Sitting", "Majlis", "Dining").forEach { add(Row(gf, "SMATV", "TV Point", it, "vx")) }

        val ff = "First Floor"
        add(Row(ff, "Intercom", "Indoor Unit", "Lift Lobby"))
        listOf("Master Bedroom", "Bedroom 1", "Bedroom 2", "Bedroom 3").forEach { add(Row(ff, "WIFI", "Access Point", it)) }
        listOf("Master Bedroom", "Bedroom 1", "Bedroom 2", "Bedroom 3").forEach { add(Row(ff, "KNX", "4\" Touch Screen", it)) }
        add(Row(ff, "KNX", "Ceiling Motion Sensor", "Stairs"))
        add(Row(ff, "KNX", "Ceiling Motion Sensor", "MBR Dressing"))
        listOf("Master Bedroom", "Corridor", "Bedroom 1", "Bedroom 2", "Bedroom 3").forEach { add(Row(ff, "KNX", "AC Gateway", it)) }
        listOf("Master Bedroom", "Bedroom 1", "Bedroom 2", "Bedroom 3").forEach { add(Row(ff, "KNX", "2 Gang Switch", it)) }
        add(Row(ff, "KNX", "Smart DB", "Store Room"))
        listOf("Master Bedroom", "Master Bedroom", "Bedroom 1", "Bedroom 1", "Bedroom 2", "Bedroom 2", "Bedroom 3", "Bedroom 3")
            .forEach { add(Row(ff, "Data Outlets", "Data Point", it)) }
        listOf("Master Bedroom", "Bedroom 1", "Bedroom 2", "Bedroom 3").forEach { add(Row(ff, "SMATV", "TV Point", it)) }
    }

    fun project(id: Long): Project {
        val counters = mutableMapOf<String, Int>()
        val devices = rows.mapIndexed { i, r ->
            val prefix = Catalog.type(r.system, r.type).prefix
            val n = (counters[prefix] ?: 0) + 1
            counters[prefix] = n
            val marks = mutableMapOf<Stage, Mark>()
            r.marks.forEachIndexed { k, ch ->
                when (ch) {
                    'v' -> marks[Stage.entries[k]] = Mark.DONE
                    'x' -> marks[Stage.entries[k]] = Mark.NOT
                }
            }
            Device(id * 1000 + i, r.floor, r.system, r.type, "$prefix$n", r.location, marks, r.note)
        }
        return Project(
            id = id,
            name = "Sample Villa (demo)",
            client = "Demo client",
            location = "Al Khoud, Muscat",
            systems = "KNX & ELV",
            status = "Cable Pulling",
            latestUpdate = "Ground floor first fix done; conduits pending for intrusion",
            nextStep = "Finish conduit on ground floor, start first floor cable pulling",
            floors = listOf("Ground Floor", "First Floor"),
            devices = devices,
        )
    }
}
