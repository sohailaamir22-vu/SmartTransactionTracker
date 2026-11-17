package com.smarttracker.data

import com.smarttracker.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class Stats(
    val count: Int,
    val manualCount: Int,
    val shotCount: Int,
    val manual: Double,
    val shot: Double,
    val total: Double,
    val today: Double,
    val month: Double,
    val last7: List<Pair<String, Double>>,
    val last6Months: List<Pair<String, Double>>,
)

fun computeStats(list: List<TransactionEntity>, today: LocalDate = LocalDate.now()): Stats {
    val zone = ZoneId.systemDefault()
    fun dayOf(t: TransactionEntity): LocalDate = Instant.ofEpochMilli(t.timestamp).atZone(zone).toLocalDate()

    val manualList = list.filter { !it.isScreenshot }
    val shotList = list.filter { it.isScreenshot }
    val perDay = list.groupBy { dayOf(it) }.mapValues { e -> Fmt.sum(e.value.map { it.amount }) }
    val perMonth = list.groupBy { YearMonth.from(dayOf(it)) }.mapValues { e -> Fmt.sum(e.value.map { it.amount }) }

    val last7 = (6 downTo 0).map { n ->
        val d = today.minusDays(n.toLong())
        "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.dayOfMonth}" to (perDay[d] ?: 0.0)
    }
    val thisMonth = YearMonth.from(today)
    val last6 = (5 downTo 0).map { n ->
        val ym = thisMonth.minusMonths(n.toLong())
        ym.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) to (perMonth[ym] ?: 0.0)
    }
    return Stats(
        count = list.size,
        manualCount = manualList.size,
        shotCount = shotList.size,
        manual = Fmt.sum(manualList.map { it.amount }),
        shot = Fmt.sum(shotList.map { it.amount }),
        total = Fmt.sum(list.map { it.amount }),
        today = perDay[today] ?: 0.0,
        month = perMonth[thisMonth] ?: 0.0,
        last7 = last7,
        last6Months = last6,
    )
}
