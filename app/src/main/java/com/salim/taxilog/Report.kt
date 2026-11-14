package com.salim.taxilog

import java.time.LocalDate

/** Everything a report needs, computed once from the trips in a date range. */
class ReportData(
    val title: String,
    val rangeLabel: String,
    trips: List<Trip>,
) {
    val trips: List<Trip> = TripRepo.sort(trips)
    val total: Double = this.trips.sumOf { it.amount ?: 0.0 }
    val legs: Int = this.trips.size
    val sites: Int = this.trips.map { it.site.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().size

    val byDay: List<Pair<LocalDate, List<Trip>>> =
        this.trips.groupBy { it.date }.toSortedMap().map { it.key to it.value }
    val days: Int = byDay.size

    /** Who paid how much, biggest first. */
    val paidBy: List<Pair<String, Double>> = this.trips
        .filter { it.amount != null && it.paidBy.isNotBlank() }
        .groupBy { it.paidBy }
        .map { (k, v) -> k to v.sumOf { it.amount ?: 0.0 } }
        .sortedByDescending { it.second }

    fun paidCount(person: String): Int = trips.count { it.amount != null && it.paidBy == person }

    /** Legs each person travelled on. */
    val travelled: List<Pair<String, Int>> = trips.flatMap { it.personnel }
        .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }

    /** site -> (legs, amount) */
    val bySite: List<Triple<String, Int, Double>> = trips.groupBy { it.site.ifBlank { "—" } }
        .map { (k, v) -> Triple(k, v.size, v.sumOf { it.amount ?: 0.0 }) }
        .sortedByDescending { it.third }

    companion object {
        fun build(all: List<Trip>, from: LocalDate?, to: LocalDate?, title: String, label: String) =
            ReportData(
                title, label,
                all.filter { (from == null || !it.date.isBefore(from)) && (to == null || !it.date.isAfter(to)) },
            )
    }
}
