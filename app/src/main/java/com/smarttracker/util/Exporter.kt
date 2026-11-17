package com.smarttracker.util

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.smarttracker.data.TransactionEntity
import java.io.OutputStream

/** Builds XLSX / CSV / PDF reports from the stored transactions. */
object Exporter {
    private val HEADER = listOf("Transaction ID", "Amount", "Type", "Date", "Time", "Description", "Screenshot file")

    private fun sorted(list: List<TransactionEntity>) = list.sortedWith(compareBy({ it.timestamp }, { it.id }))

    private fun totals(list: List<TransactionEntity>): Triple<Double, Double, Double> {
        val manual = Fmt.sum(list.filter { !it.isScreenshot }.map { it.amount })
        val shot = Fmt.sum(list.filter { it.isScreenshot }.map { it.amount })
        return Triple(manual, shot, Fmt.sum(list.map { it.amount }))
    }

    fun xlsx(list: List<TransactionEntity>): ByteArray {
        val rows = sorted(list).map { t ->
            listOf<Any?>(t.txnId, t.amount, t.typeLabel, Fmt.date(t.timestamp), Fmt.time(t.timestamp), t.description, t.imageName ?: "")
        }
        val (manual, shot, grand) = totals(list)
        return Tabular.xlsx(
            "Transactions", HEADER, rows,
            listOf("Manual total" to manual, "Screenshot total" to shot, "Grand total" to grand),
            listOf(24, 14, 13, 12, 14, 34, 46),
        )
    }

    fun csv(list: List<TransactionEntity>): ByteArray {
        val rows = sorted(list).map { t ->
            listOf(t.txnId, Fmt.plain(t.amount), t.typeLabel, Fmt.date(t.timestamp), Fmt.time(t.timestamp), t.description, t.imageName ?: "")
        }
        val (manual, shot, grand) = totals(list)
        return Tabular.csv(
            HEADER, rows,
            listOf(
                listOf("Manual total", Fmt.plain(manual)),
                listOf("Screenshot total", Fmt.plain(shot)),
                listOf("Grand total", Fmt.plain(grand)),
            )
        )
    }

    fun pdf(out: OutputStream, list: List<TransactionEntity>) {
        val data = sorted(list)
        val (manual, shot, grand) = totals(list)
        val doc = PdfDocument()
        val pageW = 595
        val pageH = 842
        val margin = 36f

        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true; color = 0xFF0F766E.toInt(); isAntiAlias = true }
        val textPaint = Paint().apply { textSize = 8.5f; color = 0xFF111111.toInt(); isAntiAlias = true }
        val boldPaint = Paint(textPaint).apply { isFakeBoldText = true }
        val rightPaint = Paint(textPaint).apply { textAlign = Paint.Align.RIGHT }
        val rightBold = Paint(boldPaint).apply { textAlign = Paint.Align.RIGHT }
        val linePaint = Paint().apply { color = 0xFFBBBBBB.toInt(); strokeWidth = 0.6f }

        val xId = margin
        val xDate = 148f
        val xTime = 212f
        val xType = 268f
        val xAmtRight = 400f
        val xDesc = 408f
        val descW = pageW - margin - xDesc

        fun fit(s: String, p: Paint, maxW: Float): String {
            if (p.measureText(s) <= maxW) return s
            var t = s
            while (t.isNotEmpty() && p.measureText("$t...") > maxW) t = t.dropLast(1)
            return "$t..."
        }

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun header(canvas: android.graphics.Canvas) {
            canvas.drawText("Transaction ID", xId, y, boldPaint)
            canvas.drawText("Date", xDate, y, boldPaint)
            canvas.drawText("Time", xTime, y, boldPaint)
            canvas.drawText("Type", xType, y, boldPaint)
            canvas.drawText("Amount (Rs)", xAmtRight, y, rightBold)
            canvas.drawText("Description", xDesc, y, boldPaint)
            canvas.drawLine(margin, y + 4, pageW - margin, y + 4, linePaint)
            y += 16f
        }

        fun startPage(first: Boolean) {
            page?.let {
                it.canvas.drawText("Page $pageNo", pageW / 2f - 14f, pageH - 18f, textPaint)
                doc.finishPage(it)
            }
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            page = p
            y = margin
            if (first) {
                p.canvas.drawText("Smart Transaction Tracker - Report", margin, y + 14f, titlePaint)
                y += 32f
                p.canvas.drawText("Generated: " + Fmt.dateTime(System.currentTimeMillis()), margin, y, textPaint)
                y += 14f
                p.canvas.drawText("Transactions: ${data.size}", margin, y, textPaint)
                y += 12f
                p.canvas.drawText("Manual total: ${Fmt.money(manual)}", margin, y, textPaint)
                y += 12f
                p.canvas.drawText("Screenshot total: ${Fmt.money(shot)}", margin, y, textPaint)
                y += 12f
                p.canvas.drawText("Grand total: ${Fmt.money(grand)}", margin, y, boldPaint)
                y += 24f
            }
            header(p.canvas)
        }

        startPage(true)
        for (t in data) {
            if (y > pageH - 50f) startPage(false)
            val c = page!!.canvas
            c.drawText(t.txnId, xId, y, textPaint)
            c.drawText(Fmt.date(t.timestamp), xDate, y, textPaint)
            c.drawText(Fmt.time(t.timestamp), xTime, y, textPaint)
            c.drawText(t.typeLabel, xType, y, textPaint)
            c.drawText(Fmt.plain(t.amount), xAmtRight, y, rightPaint)
            c.drawText(fit(t.description.replace('\n', ' '), textPaint, descW), xDesc, y, textPaint)
            y += 13f
        }
        if (y > pageH - 70f) startPage(false)
        val c = page!!.canvas
        c.drawLine(margin, y, pageW - margin, y, linePaint)
        y += 14f
        c.drawText("Grand total", xType, y, boldPaint)
        c.drawText(Fmt.plain(grand), xAmtRight, y, rightBold)

        page!!.let {
            it.canvas.drawText("Page $pageNo", pageW / 2f - 14f, pageH - 18f, textPaint)
            doc.finishPage(it)
        }
        try {
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }
}
