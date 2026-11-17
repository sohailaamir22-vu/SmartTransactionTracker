package com.smarttracker.ocr

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime

/** One recognised line of text with its position in the image. */
data class OcrLine(val text: String, val left: Int, val top: Int, val bottom: Int)

data class ParseResult(
    val amount: Double?,
    /** false when the amount is only a guess and the user should double-check it */
    val confident: Boolean,
    val date: LocalDate?,
    val time: LocalTime?,
)

/**
 * Pure-Kotlin (no Android dependency) parser that turns raw OCR text from any payment /
 * banking screenshot (JazzCash, Easypaisa, banks, wallets ...) into amount + date + time.
 */
object OcrParser {
    private const val MON = "(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*"
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private val DATE_YMD = Regex("(?<!\\d)(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})(?!\\d)")
    private val DATE_DMY = Regex("(?<![\\d.])(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4}|\\d{2})(?![\\d:])")
    private val DATE_D_MON_Y = Regex(
        "(?<!\\d)(\\d{1,2})(?:st|nd|rd|th)?[\\s,.-]*$MON[\\s,.-]+(\\d{4}|\\d{2})(?![\\d:])",
        RegexOption.IGNORE_CASE
    )
    private val DATE_MON_D_Y = Regex(
        "(?<![a-z])$MON\\.?\\s*(\\d{1,2})(?:st|nd|rd|th)?\\s*,?\\s*(\\d{4})(?!\\d)",
        RegexOption.IGNORE_CASE
    )
    private val TIME = Regex("(?<!\\d)(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?!\\d)\\s*([AaPp]\\.?\\s?[Mm]\\.?)?")

    private val NUM = Regex("(?<![\\d.,])(\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)(?!\\d)")
    private val CUR_BEFORE = Regex("(?<![a-z])(?:rs\\.?|pkr|\u20A8)\\s*[:\\-]?\\s*$")
    private val CUR_AFTER = Regex("^\\s*(?:rs\\b|pkr\\b|/-)")
    private val STRONG = Regex(
        "\\b(amount|amt|sent|paid|pay|payment|transfer|transferred|debit|debited|credit|credited|received|total|withdrawn|withdrawal|deposit|deposited|purchase)\\b"
    )
    private val FEE_WORDS = Regex(
        "\\b(fee|fees|charge|charges|tax|fed|wht|gst|balance|available|limit|cashback|cash back|reward|rewards|points|discount|commission|surcharge)\\b"
    )
    private val ID_WORDS = Regex("\\b(id|ref|reference|trx|txn|tid|stan|rrn|cnic|account|acc|a/c|iban|mobile|phone|number|cell)\\b")

    /** Joins OCR lines that sit on the same visual row (label on the left, value on the right). */
    fun rowsFromLines(lines: List<OcrLine>): String {
        if (lines.isEmpty()) return ""
        val sorted = lines.sortedBy { (it.top + it.bottom) / 2 }
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (l in sorted) {
            val cy = (l.top + l.bottom) / 2.0
            val row = rows.lastOrNull()
            if (row != null) {
                val rowCy = row.map { (it.top + it.bottom) / 2.0 }.average()
                val h = minOf((l.bottom - l.top).coerceAtLeast(1), row.minOf { (it.bottom - it.top).coerceAtLeast(1) })
                if (kotlin.math.abs(cy - rowCy) <= h * 0.6) {
                    row.add(l)
                    continue
                }
            }
            rows.add(mutableListOf(l))
        }
        return rows.joinToString("\n") { r -> r.sortedBy { it.left }.joinToString("  ") { it.text } }
    }

    private class Found<T>(val value: T, val line: Int)
    private class Cand(val value: Double, val score: Int, val line: Int)

    fun parse(text: String): ParseResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val dates = mutableListOf<Found<LocalDate>>()
        val times = mutableListOf<Found<LocalTime>>()
        val cleaned = ArrayList<String>(lines.size)

        lines.forEachIndexed { i, ln ->
            val chars = ln.toCharArray()
            findDate(ln)?.let { (d, r) ->
                dates += Found(d, i)
                for (k in r) chars[k] = ' '
            }
            for (m in TIME.findAll(ln)) {
                val t = parseTime(m) ?: continue
                times += Found(t, i)
                for (k in m.range) chars[k] = ' '
            }
            cleaned += String(chars)
        }

        val date = dates.firstOrNull()
        val time: LocalTime? = when {
            date != null && times.any { it.line == date.line } -> times.first { it.line == date.line }.value
            date != null && times.any { it.line == date.line + 1 } -> times.first { it.line == date.line + 1 }.value
            // skip the first row: that is normally the phone's status-bar clock
            else -> times.firstOrNull { it.line >= 1 || lines.size == 1 }?.value
        }

        val best = findAmount(cleaned)
        val amount = best?.takeIf { it.score > -3 }
        return ParseResult(amount?.value, (amount?.score ?: 0) >= 4, date?.value, time)
    }

    private fun findAmount(lines: List<String>): Cand? {
        val cands = mutableListOf<Cand>()
        lines.forEachIndexed { i, ln ->
            val low = ln.lowercase()
            val strong = STRONG.containsMatchIn(low)
            val feeWord = FEE_WORDS.containsMatchIn(low)
            val idWord = ID_WORDS.containsMatchIn(low)
            val prevLabelOnly = i > 0 && !strong &&
                STRONG.containsMatchIn(lines[i - 1].lowercase()) && !NUM.containsMatchIn(lines[i - 1])
            for (m in NUM.findAll(ln)) {
                val tok = m.value
                val digits = tok.count { it.isDigit() }
                val hasSep = tok.contains(',') || tok.contains('.')
                if (!hasSep && digits >= 9) continue // phone / account / transaction numbers
                val v = tok.replace(",", "").toDoubleOrNull() ?: continue
                if (v <= 0.0 || v > 1.0e9) continue
                val before = low.substring(0, m.range.first)
                val after = low.substring(m.range.last + 1)
                if (after.trimStart().startsWith("%")) continue // battery level etc.
                val cur = CUR_BEFORE.containsMatchIn(before) || CUR_AFTER.containsMatchIn(after)
                var s = 0
                if (cur) s += 3
                if (strong) s += 4 else if (prevLabelOnly) s += 3
                if (hasSep) s += 1
                if (feeWord) s -= 8
                if (idWord && !cur) s -= 3
                if (!hasSep && digits >= 7 && !cur) s -= 4
                cands += Cand(v, s, i)
            }
        }
        return cands.maxWithOrNull(compareBy<Cand>({ it.score }, { -it.line }))
    }

    private fun findDate(line: String): Pair<LocalDate, IntRange>? {
        for (m in DATE_YMD.findAll(line)) {
            mk(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it to m.range }
        }
        for (m in DATE_D_MON_Y.findAll(line)) {
            mk(m.groupValues[3].toInt(), monthOf(m.groupValues[2]), m.groupValues[1].toInt())?.let { return it to m.range }
        }
        for (m in DATE_MON_D_Y.findAll(line)) {
            mk(m.groupValues[3].toInt(), monthOf(m.groupValues[1]), m.groupValues[2].toInt())?.let { return it to m.range }
        }
        for (m in DATE_DMY.findAll(line)) {
            var d = m.groupValues[1].toInt()
            var mo = m.groupValues[2].toInt()
            if (mo > 12 && d <= 12) { val t = d; d = mo; mo = t } // looks like mm/dd/yyyy
            mk(m.groupValues[3].toInt(), mo, d)?.let { return it to m.range }
        }
        return null
    }

    private fun monthOf(s: String): Int = MONTHS.indexOf(s.lowercase().take(3)) + 1

    private fun mk(y: Int, m: Int, d: Int): LocalDate? = try {
        val yy = if (y < 100) 2000 + y else y
        if (yy in 2000..2100) LocalDate.of(yy, m, d) else null
    } catch (e: DateTimeException) {
        null
    }

    private fun parseTime(m: MatchResult): LocalTime? {
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val sec = m.groupValues[3].ifEmpty { "0" }.toInt()
        val ap = m.groupValues[4].lowercase().filter { it == 'a' || it == 'p' }
        if (min > 59 || sec > 59) return null
        if (ap.isNotEmpty()) {
            if (h !in 1..12) return null
            h = if (ap == "p") (if (h == 12) 12 else h + 12) else (if (h == 12) 0 else h)
        } else if (h > 23) return null
        return LocalTime.of(h, min, sec)
    }
}
