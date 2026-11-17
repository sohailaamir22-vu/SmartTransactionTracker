package com.smarttracker.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

object Fmt {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val TIME = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.US)
    private val NICE_DATE = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH)
    private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    fun money(v: Double): String = "Rs " + DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)).format(v)
    fun plain(v: Double): String = DecimalFormat("0.00", DecimalFormatSymbols(Locale.US)).format(v)
    fun amountField(v: Double): String = DecimalFormat("0.##", DecimalFormatSymbols(Locale.US)).format(v)

    fun toLocal(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
    fun date(ms: Long): String = DATE.format(toLocal(ms))
    fun time(ms: Long): String = TIME.format(toLocal(ms))
    fun dateTime(ms: Long): String = "${date(ms)}  ${time(ms)}"
    fun niceDate(d: LocalDate): String = NICE_DATE.format(d)
    fun monthName(d: LocalDate): String = MONTH.format(d)

    fun toMillis(d: LocalDate, t: LocalTime): Long = LocalDateTime.of(d, t).atZone(zone).toInstant().toEpochMilli()
    fun startOfDayMillis(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Material date pickers report UTC midnight; convert to the calendar date the user tapped. */
    fun utcMillisToDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
    fun dateToUtcMillis(d: LocalDate): Long = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** Exact decimal sum, rounded to 2 places – no floating point drift in totals. */
    fun sum(values: List<Double>): Double =
        values.fold(BigDecimal.ZERO) { acc, v -> acc + BigDecimal.valueOf(v) }
            .setScale(2, RoundingMode.HALF_UP).toDouble()

    fun parseAmount(s: String): Double? {
        val v = s.replace(",", "").trim().toDoubleOrNull() ?: return null
        if (!v.isFinite() || v <= 0.0 || v >= 1.0e12) return null
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    fun newTxnId(ms: Long): String =
        "TXN-" + DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US).format(toLocal(ms)) + "-" +
            UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
}
