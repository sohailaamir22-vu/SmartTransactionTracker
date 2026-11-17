package com.smarttracker.util

import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Dependency-free CSV and XLSX writers. */
object Tabular {

    fun csv(header: List<String>, rows: List<List<String>>, footer: List<List<String>> = emptyList()): ByteArray {
        val sb = StringBuilder("\uFEFF") // BOM so Excel opens UTF-8 correctly
        fun line(cells: List<String>) = sb.append(cells.joinToString(",") { esc(it) }).append("\r\n")
        line(header)
        rows.forEach { line(it) }
        if (footer.isNotEmpty()) {
            line(emptyList())
            footer.forEach { line(it) }
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun esc(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    // ---------------------------------------------------------------- XLSX

    /**
     * @param rows cells are String or Number (Number gets the #,##0.00 style)
     * @param summary extra "label -> amount" lines written under the table
     */
    fun xlsx(
        sheetName: String,
        header: List<String>,
        rows: List<List<Any?>>,
        summary: List<Pair<String, Double>>,
        widths: List<Int>,
    ): ByteArray {
        val sheet = StringBuilder()
        sheet.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sheet.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sheet.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        sheet.append("<cols>")
        widths.forEachIndexed { i, w -> sheet.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        sheet.append("</cols><sheetData>")

        var r = 1
        sheet.append("""<row r="$r">""")
        header.forEachIndexed { c, h -> sheet.append(cell(c, r, h, 1)) }
        sheet.append("</row>")
        for (row in rows) {
            r++
            sheet.append("""<row r="$r">""")
            row.forEachIndexed { c, v -> sheet.append(cell(c, r, v, if (v is Number) 2 else 0)) }
            sheet.append("</row>")
        }
        if (summary.isNotEmpty()) {
            r++ // blank spacer row
            for ((label, value) in summary) {
                r++
                sheet.append("""<row r="$r">""")
                sheet.append(cell(0, r, label, 3))
                sheet.append(cell(1, r, value, 4))
                sheet.append("</row>")
            }
        }
        sheet.append("</sheetData></worksheet>")

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(name: String, content: String) {
                z.putNextEntry(ZipEntry(name))
                z.write(content.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            put("[Content_Types].xml", CONTENT_TYPES)
            put("_rels/.rels", ROOT_RELS)
            put("xl/workbook.xml", workbook(sheetName))
            put("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            put("xl/styles.xml", STYLES)
            put("xl/worksheets/sheet1.xml", sheet.toString())
        }
        return out.toByteArray()
    }

    private fun cell(col: Int, row: Int, v: Any?, style: Int): String {
        val ref = "${('A' + col)}$row"
        return when (v) {
            null -> ""
            is Double -> """<c r="$ref" s="$style"><v>${BigDecimal.valueOf(v).toPlainString()}</v></c>"""
            is Number -> """<c r="$ref" s="$style"><v>$v</v></c>"""
            else -> """<c r="$ref" t="inlineStr" s="$style"><is><t xml:space="preserve">${xml(v.toString())}</t></is></c>"""
        }
    }

    private fun xml(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch.code < 0x20 && ch != '\n' && ch != '\t' -> {} // illegal in XML 1.0
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun workbook(name: String) =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${xml(name)}" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    private const val CONTENT_TYPES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""

    private const val ROOT_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private const val WORKBOOK_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""

    // style ids: 0 normal, 1 header, 2 money, 3 bold, 4 bold money
    private const val STYLES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="3"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF0F766E"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="5"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="2" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="4" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
