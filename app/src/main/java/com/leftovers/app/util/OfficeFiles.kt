package com.leftovers.app.util

import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/*
 * Excel (.xlsx) and Word (.docx) files, written by hand: each is a zip of a few small XML files, so no library is
 * needed (and the F-Droid build stays as it is). Only what the assistant's files use: sheets of text and numbers
 * with a bold header row; a document of headings, paragraphs and tables.
 */

/** One cell of a sheet: text, or a number Excel can add up. */
sealed interface Cell {
    data class Text(val value: String) : Cell
    data class Number(val value: BigDecimal) : Cell
}

/** A sheet: its tab's name, a header row (bold) and the rows under it, and how wide each column is. */
data class Sheet(val name: String, val header: List<String>, val rows: List<List<Cell>>, val widths: List<Int> = emptyList())

/** A part of a Word document. */
sealed interface DocBlock {
    data class Heading(val text: String, val level: Int = 1) : DocBlock
    data class Paragraph(val text: String, val muted: Boolean = false) : DocBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : DocBlock
}

internal fun xml(text: String): String = buildString(text.length) {
    text.forEach { ch ->
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            // Characters XML can't hold at all (most control characters) are left out.
            else -> if (ch == '\t' || ch == '\n' || ch == '\r' || ch >= ' ') append(ch)
        }
    }
}

private fun zip(files: List<Pair<String, String>>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z ->
        files.forEach { (name, body) ->
            z.putNextEntry(ZipEntry(name))
            z.write(body.toByteArray(Charsets.UTF_8))
            z.closeEntry()
        }
    }
    return out.toByteArray()
}

private const val XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

object XlsxWriter {
    /** The workbook as bytes, one tab per sheet. */
    fun write(sheets: List<Sheet>): ByteArray {
        require(sheets.isNotEmpty())
        val names = sheets.mapIndexed { i, s -> sheetName(s.name, i) }
        val files = mutableListOf<Pair<String, String>>()
        files += "[Content_Types].xml" to XML_HEAD +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
            sheets.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" } +
            "</Types>"
        files += "_rels/.rels" to XML_HEAD +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"
        files += "xl/workbook.xml" to XML_HEAD +
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>" +
            names.mapIndexed { i, n -> "<sheet name=\"${xml(n)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("") +
            "</sheets></workbook>"
        files += "xl/_rels/workbook.xml.rels" to XML_HEAD +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            sheets.indices.joinToString("") { "<Relationship Id=\"rId${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>" } +
            "<Relationship Id=\"rId${sheets.size + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
            "</Relationships>"
        // Style 1 is the bold header; style 2 shows amounts with two decimals and thousands separators.
        files += "xl/styles.xml" to XML_HEAD +
            "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
            "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
            "<xf numFmtId=\"4\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs>" +
            "</styleSheet>"
        sheets.forEachIndexed { i, s -> files += "xl/worksheets/sheet${i + 1}.xml" to sheetXml(s) }
        return zip(files)
    }

    /** Excel's rules for a tab's name: at most 31 characters, none of : \ / ? * [ ], and not blank. */
    internal fun sheetName(name: String, index: Int): String =
        name.replace(Regex("[:\\\\/?*\\[\\]]"), " ").trim().take(31).ifBlank { "Sheet${index + 1}" }

    /** A column's letters: 0 is A, 25 is Z, 26 is AA. */
    internal fun column(index: Int): String {
        var n = index + 1
        val s = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; s.insert(0, 'A' + r); n = (n - 1) / 26 }
        return s.toString()
    }

    private fun sheetXml(s: Sheet): String = buildString {
        append(XML_HEAD)
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        // The header stays in view while scrolling.
        append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
        if (s.widths.isNotEmpty()) {
            append("<cols>")
            s.widths.forEachIndexed { i, w -> append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>") }
            append("</cols>")
        }
        append("<sheetData>")
        fun row(n: Int, cells: List<Cell>, header: Boolean) {
            append("<row r=\"$n\">")
            cells.forEachIndexed { i, c ->
                val ref = column(i) + n
                when (c) {
                    is Cell.Text -> append("<c r=\"$ref\" t=\"inlineStr\"${if (header) " s=\"1\"" else ""}><is><t xml:space=\"preserve\">${xml(c.value)}</t></is></c>")
                    is Cell.Number -> append("<c r=\"$ref\" s=\"2\"><v>${c.value.toPlainString()}</v></c>")
                }
            }
            append("</row>")
        }
        row(1, s.header.map { Cell.Text(it) }, header = true)
        s.rows.forEachIndexed { i, r -> row(i + 2, r, header = false) }
        append("</sheetData></worksheet>")
    }
}

object DocxWriter {
    /** The document as bytes. */
    fun write(title: String, blocks: List<DocBlock>): ByteArray {
        val files = mutableListOf<Pair<String, String>>()
        files += "[Content_Types].xml" to XML_HEAD +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>" +
            "</Types>"
        files += "_rels/.rels" to XML_HEAD +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
            "</Relationships>"
        files += "docProps/core.xml" to XML_HEAD +
            "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">" +
            "<dc:title>${xml(title)}</dc:title></cp:coreProperties>"
        files += "word/document.xml" to documentXml(title, blocks)
        return zip(files)
    }

    private fun run(text: String, size: Int, bold: Boolean = false, color: String? = null): String {
        val props = buildString {
            if (bold) append("<w:b/>")
            if (color != null) append("<w:color w:val=\"$color\"/>")
            append("<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/>")
        }
        // Line breaks inside a paragraph stay line breaks.
        return text.split('\n').joinToString("<w:br/>") { "<w:r><w:rPr>$props</w:rPr><w:t xml:space=\"preserve\">${xml(it)}</w:t></w:r>" }
            .let { if (text.isEmpty()) "" else it }
    }

    private fun paragraph(runs: String, after: Int = 120) = "<w:p><w:pPr><w:spacing w:after=\"$after\"/></w:pPr>$runs</w:p>"

    private fun documentXml(title: String, blocks: List<DocBlock>): String = buildString {
        append(XML_HEAD)
        append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>")
        append(paragraph(run(title, 40, bold = true), after = 240))
        blocks.forEach { b ->
            when (b) {
                is DocBlock.Heading -> append(paragraph(run(b.text, if (b.level <= 1) 30 else 26, bold = true), after = 100))
                is DocBlock.Paragraph -> b.text.split("\n\n").forEach { append(paragraph(run(it, 22, color = if (b.muted) "6B6B6B" else null))) }
                is DocBlock.Table -> {
                    append("<w:tbl><w:tblPr><w:tblW w:w=\"5000\" w:type=\"pct\"/>")
                    append("<w:tblBorders><w:top w:val=\"single\" w:sz=\"4\" w:color=\"BFBFBF\"/><w:bottom w:val=\"single\" w:sz=\"4\" w:color=\"BFBFBF\"/>")
                    append("<w:insideH w:val=\"single\" w:sz=\"4\" w:color=\"E0E0E0\"/></w:tblBorders></w:tblPr>")
                    fun cells(values: List<String>, header: Boolean) {
                        append("<w:tr>")
                        values.forEachIndexed { i, v ->
                            // Amounts (every column after the first) line up on the right.
                            val align = if (i > 0) "<w:jc w:val=\"right\"/>" else ""
                            append("<w:tc><w:p><w:pPr><w:spacing w:before=\"60\" w:after=\"60\"/>$align</w:pPr>${run(v, 20, bold = header)}</w:p></w:tc>")
                        }
                        append("</w:tr>")
                    }
                    cells(b.header, header = true)
                    b.rows.forEach { cells(it, header = false) }
                    append("</w:tbl>")
                    append(paragraph("", after = 120))
                }
            }
        }
        append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" w:header=\"0\" w:footer=\"0\" w:gutter=\"0\"/></w:sectPr>")
        append("</w:body></w:document>")
    }
}
