package com.ats.projects

/**
 * The pick-lists used everywhere: floors, rooms, systems and their device types.
 * A device type has a name prefix used for auto-numbering (CAM1, CAM2 …).
 */
object Catalog {

    data class DeviceType(val name: String, val prefix: String)
    data class SystemDef(val name: String, val types: List<DeviceType>)

    private fun t(name: String, prefix: String = name) = DeviceType(name, prefix)

    val systems: List<SystemDef> = listOf(
        SystemDef(
            "CCTV",
            listOf(t("Camera", "CAM"), t("Outdoor Camera", "CAM"), t("Indoor Camera", "CAM"), t("NVR"), t("PTZ Camera", "PTZ")),
        ),
        SystemDef(
            "Intercom",
            listOf(t("Outdoor Unit"), t("Indoor Unit", "Indoor unit"), t("Electric Lock"), t("Outdoor Junction Box", "Junction Box")),
        ),
        SystemDef("WIFI", listOf(t("Access Point", "WAP "), t("Outdoor Access Point", "WAP "), t("Router"), t("Switch", "Network Switch"))),
        SystemDef(
            "Access Control",
            listOf(
                t("Card Reader"), t("Face Reader"), t("Electric Lock"), t("Magnetic Lock", "Mag Lock"),
                t("Exit Button"), t("Door Contact"), t("Access Controller", "Controller"), t("Break Glass", "Break Glass"),
            ),
        ),
        SystemDef(
            "KNX",
            listOf(
                t("7\" Touch Screen", "Touch Screen"), t("4\" Touch Screen", "Touch Screen"),
                t("Ceiling Motion Sensor", "Sensor"), t("Wall Motion Sensor", "Sensor"), t("Presence Sensor", "Sensor"),
                t("AC Gateway"), t("1 Gang Switch", "1G Switch"), t("2 Gang Switch", "2G Switch"),
                t("3 Gang Switch", "3G Switch"), t("4 Gang Switch", "4G Switch"), t("6 Gang Switch", "6G Switch"),
                t("Curtain Switch"), t("Dimmer"), t("Thermostat"), t("Smart DB", "DB"), t("KNX DB"),
            ),
        ),
        SystemDef(
            "Lighting & Switches",
            listOf(
                t("1 Gang Switch", "1G Switch"), t("2 Gang Switch", "2G Switch"), t("3 Gang Switch", "3G Switch"),
                t("4 Gang Switch", "4G Switch"), t("Dimmer Switch", "Dimmer"), t("Curtain Switch"), t("Socket"),
            ),
        ),
        SystemDef(
            "Intrusion",
            listOf(
                t("Glass Break Sensor", "Glass break"), t("Motion Sensor", "PIR"), t("Door Contact"),
                t("Intrusion Keypad", "Keypad"), t("Intrusion Siren", "Siren"), t("Intrusion Main Box", "Main Box"),
            ),
        ),
        SystemDef("Data Outlets", listOf(t("Data Point"), t("Data Rack", "Rack"))),
        SystemDef("SMATV", listOf(t("TV Point"), t("Satellite Dish", "Dish"), t("Multiswitch"))),
        SystemDef("Telephone", listOf(t("Telephone Point"))),
        SystemDef("Audio", listOf(t("Ceiling Speaker", "Speaker"), t("Wall Speaker", "Speaker"), t("Amplifier"), t("Volume Control"))),
        SystemDef("Server Room", listOf(t("Rack Main Box"), t("Data Rack", "Rack"), t("UPS"))),
        SystemDef("Fire Alarm", listOf(t("Smoke Detector"), t("Heat Detector"), t("Manual Call Point", "MCP"), t("Sounder"), t("Fire Panel"))),
        SystemDef("Other", listOf(t("Device"))),
    )

    val systemNames: List<String> get() = systems.map { it.name }

    fun system(name: String): SystemDef? = systems.firstOrNull { it.name.equals(name, true) }

    fun types(system: String): List<DeviceType> = system(system)?.types ?: listOf(t("Device"))

    fun type(system: String, type: String): DeviceType =
        types(system).firstOrNull { it.name.equals(type, true) } ?: t(type)

    fun systemIndex(name: String): Int = systems.indexOfFirst { it.name.equals(name, true) }.let { if (it < 0) 999 else it }

    fun typeIndex(system: String, type: String): Int =
        types(system).indexOfFirst { it.name.equals(type, true) }.let { if (it < 0) 999 else it }

    /** Finds the closest catalog system name for free text from the drawing reader. */
    fun matchSystem(raw: String): String {
        val r = raw.trim()
        system(r)?.let { return it.name }
        val l = r.lowercase()
        return when {
            "cctv" in l || "camera" in l -> "CCTV"
            "wifi" in l || "wi-fi" in l || "wap" in l || "access point" in l -> "WIFI"
            "intercom" in l || "vdp" in l || "door phone" in l -> "Intercom"
            "access" in l -> "Access Control"
            "knx" in l || "automation" in l -> "KNX"
            "intrusion" in l || "alarm" in l && "fire" !in l || "security" in l -> "Intrusion"
            "fire" in l -> "Fire Alarm"
            "data" in l || "network" in l -> "Data Outlets"
            "tv" in l || "smatv" in l || "satellite" in l -> "SMATV"
            "phone" in l -> "Telephone"
            "audio" in l || "speaker" in l -> "Audio"
            "switch" in l || "light" in l -> "Lighting & Switches"
            "server" in l || "rack" in l -> "Server Room"
            else -> r.ifEmpty { "Other" }
        }
    }

    val defaultFloors = listOf(
        "Ground Floor", "First Floor", "Second Floor", "Penthouse Floor", "Roof", "Basement", "Outdoor",
    )

    val defaultRooms = listOf(
        "Entrance", "Main Door", "Majlis", "Family Sitting", "Living Room", "Hall", "Dining", "Kitchen",
        "Master Bedroom", "MBR Dressing", "Bedroom 1", "Bedroom 2", "Bedroom 3", "Bedroom 4", "Bedroom 5",
        "Guest Room", "Maid Room", "Driver Room", "Office", "GYM", "Home Cinema", "Corridor", "Passage",
        "Lobby", "Lift Lobby", "Stairs", "Store Room", "Laundry", "Toilet", "Parking", "Pool",
        "Outdoor Front", "Outdoor Back", "Side Right", "Side Left", "Back Right", "Back Left", "Roof",
    )
}
