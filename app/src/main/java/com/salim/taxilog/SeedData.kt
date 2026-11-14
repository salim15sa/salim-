package com.salim.taxilog

import java.time.LocalDate

/** Records imported from the "taxi seb" Google Sheet (24 Oct - 12 Nov 2026). */
object SeedData {
    private fun t(id: Long, d: String, site: String, from: String, to: String, amt: Double?, people: String, paid: String) =
        Trip(id, LocalDate.parse(d), site, from, to, amt, people.split(" & ").filter { it.isNotBlank() }, paid)

    fun trips(): List<Trip> = listOf(
        t(1, "2026-10-24", "OIA", "Ghubrah", "OIA", 1.000, "Salim", "Salim"),
        t(2, "2026-10-24", "OIA", "OIA", "Ghubrah", 1.000, "Salim", "Salim"),
        t(3, "2026-10-25", "Bawan 6 Villa", "Ghubrah", "6 Villa", 1.300, "Salim & Shabeer", "Aslam"),
        t(4, "2026-10-25", "Bawan 6 Villa", "Bawan 6 Villa", "Ghubrah", 1.200, "Salim & Shabeer", "Salim"),
        t(5, "2026-10-26", "3 Villa", "Ghubrah", "3 Villa", 3.000, "Salim & Shabeer", "Aslam"),
        t(6, "2026-10-26", "Bawan Parking", "3 Villa", "Bawan Parking", null, "", ""),
        t(7, "2026-10-26", "Bawan 6 Villa", "Bawan Parking", "Bawan 6 Villa", null, "", ""),
        t(8, "2026-10-26", "Bawan 6 Villa", "Bawan 6 Villa", "Ghubrah", null, "", ""),
        t(9, "2026-10-27", "Bawan Parking", "Ghubrah", "Bawan Parking", 3.000, "Salim & Shabeer", "Aslam"),
        t(10, "2026-10-27", "Blood Bank", "Bawan Parking", "Bawan Parking", null, "Salim & Shabeer", ""),
        t(11, "2026-10-27", "Husain Malaha", "Blood Bank", "Husain Malaha", null, "Salim", ""),
        t(12, "2026-10-27", "Husain Malaha", "Husain Malaha", "Ghubrah", null, "Salim", ""),
        t(13, "2026-10-28", "Silicon Sealant", "Ghubrah", "Samail", 3.000, "Salim & Shabeer", "Salim"),
        t(14, "2026-10-28", "Silicon Sealant", "Samail", "Ghubrah", null, "", ""),
        t(15, "2026-10-29", "Moosa Villa", "Ghubrah", "Moosa Villa", 2.000, "Salim & Shabeer", "Salim"),
        t(16, "2026-10-29", "Moosa Villa", "Moosa Villa", "Ghubrah", null, "", ""),
        t(17, "2026-10-31", "Shiham", "Ghubrah", "Shiham", 1.200, "Salim & Shabeer", "Salim"),
        t(18, "2026-10-31", "Al Mouj", "Shiham", "Al Mouj", 2.000, "Salim", "Salim"),
        t(19, "2026-10-31", "Moosa Villa", "Al Mouj", "Moosa Villa", null, "", ""),
        t(20, "2026-10-31", "Moosa Villa", "Moosa Villa", "Ghubrah", null, "", ""),
        t(21, "2026-11-01", "Control Tech", "OIA", "Control Tech", 2.000, "Salim", "Salim"),
        t(22, "2026-11-01", "Bawan 6 Villa", "Control Tech", "Bawan 6 Villa", null, "", ""),
        t(23, "2026-11-01", "ATS Banner", "Bawan 6 Villa", "Gala", null, "", ""),
        t(24, "2026-11-02", "Bawan 6 Villa", "Ghubrah", "Bawan 6 Villa", 1.000, "Salim", "Salim"),
        t(25, "2026-11-02", "Al Mouj", "Bawan 6 Villa", "Al Mouj", 1.400, "Salim", "Salim"),
        t(26, "2026-11-02", "Al Mouj", "Al Mouj", "Ghubrah", 1.800, "Salim", "Salim"),
        t(27, "2026-11-03", "Blood Bank", "Ghubrah", "Blood Bank", 2.000, "Salim & Shabeer", "Salim"),
        t(28, "2026-11-03", "Al Haditha", "Blood Bank", "Al Haditha", null, "Salim", ""),
        t(29, "2026-11-03", "Al Kuwaire Site", "Al Haditha", "Al Kuwaire Site", null, "Salim", ""),
        t(30, "2026-11-03", "Al Kuwaire Site", "Al Kuwaire Site", "Ghubrah", null, "Salim", ""),
        t(31, "2026-11-04", "Al Haditha", "Ghubrah", "Al Haditha", 3.000, "Salim", "Aslam"),
        t(32, "2026-11-04", "Al Mouj", "Al Haditha", "Al Mouj", null, "", ""),
        t(33, "2026-11-04", "Al Mouj", "Al Mouj", "Office", null, "", ""),
        t(34, "2026-11-04", "Bawan 6 Villa", "Office", "Bawan 6 Villa", null, "", ""),
        t(35, "2026-11-04", "Bawan 6 Villa", "Bawan 6 Villa", "Ghubrah", null, "", ""),
        t(36, "2026-11-05", "Shiham", "Ghubrah", "Shiham", 2.000, "Salim & Shabeer", "Aslam"),
        t(37, "2026-11-05", "3 Villa", "Shiham", "3 Villa", null, "", ""),
        t(38, "2026-11-05", "3 Villa", "3 Villa", "Ghubrah", null, "", ""),
        t(39, "2026-11-08", "Nadiya Villa", "Ghubrah", "Nadiya Villa", 1.300, "Salim & Shabeer", "Aslam"),
        t(40, "2026-11-08", "3 Villa", "Nadiya Villa", "3 Villa", 1.800, "Salim & Shabeer", "Aslam"),
        t(41, "2026-11-08", "3 Villa", "3 Villa", "Ghubrah", 1.500, "Salim & Shabeer", "Salim"),
        t(42, "2026-11-09", "3 Villa", "Ghubrah", "3 Villa", 1.600, "Salim & Shabeer", "Aslam"),
        t(43, "2026-11-09", "3 Villa", "3 Villa", "Ghubrah", 1.500, "Salim & Shabeer", "Salim"),
        t(44, "2026-11-10", "3 Villa", "Ghubrah", "3 Villa", 1.600, "Salim & Shabeer", "Salim"),
        t(45, "2026-11-10", "Al Kuwaire", "3 Villa", "Office", 1.700, "Salim & Shabeer", "Salim"),
        t(46, "2026-11-10", "Al Kuwaire", "Al Kuwaire", "Ghubrah", 1.400, "Salim & Shabeer", "Salim"),
        t(47, "2026-11-11", "Dr Malah Villa", "Ghubrah", "Al Kouid", 1.200, "Salim & Shabeer", "Salim"),
        t(48, "2026-11-12", "3 Villa", "Ghubrah", "3 Villa", 1.700, "Salim & Shabeer", "Salim"),
        t(49, "2026-11-12", "Al Mouj", "3 Villa", "Al Mouj", 2.000, "Salim & Shabeer", "Aslam"),
        t(50, "2026-11-12", "Al Mouj", "Al Mouj", "Ghubrah", 1.800, "Salim & Shabeer", "Aslam"),
    )
}
