package com.leftovers.app.util

import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * Turns a bank statement or another app's export into rows of cells for [CsvImporter.parseTable], whatever it
 * was saved as: CSV or tab-separated text in any common encoding, an Excel workbook (.xlsx, or the older .xls),
 * the HTML table many banks save with an .xls name, or a PDF statement (read by [PdfStatement]). Everything is
 * read on the phone; nothing is sent anywhere.
 */
object StatementFile {
    sealed interface Result {
        data class Table(val rows: List<List<String>>) : Result
        data class Text(val text: String) : Result
        data class Unsupported(val message: String) : Result
        /** A PDF statement: its text is read with [PdfStatement], which may need the statement's password. */
        class Pdf(val bytes: ByteArray) : Result
    }

    fun read(bytes: ByteArray): Result {
        if (bytes.isEmpty()) return Result.Unsupported("The file is empty.")
        return when {
            bytes.startsWith(0x50, 0x4B, 0x03, 0x04) -> runCatching { Result.Table(readXlsx(bytes)) }
                .getOrElse { Result.Unsupported("Couldn't read this Excel file. Try saving it again as .xlsx or .csv.") }
            bytes.startsWith(0xD0, 0xCF, 0x11, 0xE0) -> runCatching { Result.Table(readXls(bytes)) }
                .getOrElse { Result.Unsupported("Couldn't read this Excel file. Try saving it again as .xlsx or .csv.") }
            bytes.startsWith(0x25, 0x50, 0x44, 0x46) -> Result.Pdf(bytes)
            else -> {
                val text = decodeText(bytes)
                if (text.trimStart().startsWith("<")) Result.Table(readHtmlTable(text)) else Result.Text(text)
            }
        }
    }

    private fun ByteArray.startsWith(vararg b: Int) = size >= b.size && b.indices.all { this[it] == b[it].toByte() }

    /** Text in UTF-8 or UTF-16 (with or without a byte-order mark, as Excel's "Unicode text" saves it). */
    internal fun decodeText(bytes: ByteArray): String = when {
        bytes.startsWith(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        bytes.startsWith(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        bytes.startsWith(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        // No mark, but every other byte is zero: UTF-16 text all the same.
        bytes.size >= 4 && bytes[1] == 0.toByte() && bytes[3] == 0.toByte() && bytes[0] != 0.toByte() -> String(bytes, Charsets.UTF_16LE)
        bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[2] == 0.toByte() && bytes[1] != 0.toByte() -> String(bytes, Charsets.UTF_16BE)
        else -> String(bytes, Charsets.UTF_8)
    }

    // ---- HTML tables (".xls" downloads from many banks are a web page with one table) ----

    internal fun readHtmlTable(html: String): List<List<String>> {
        val cell = Regex("""<t[dh][^>]*>(.*?)</t[dh]>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return Regex("""<tr[^>]*>(.*?)</tr>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(html).map { row ->
            cell.findAll(row.groupValues[1]).map { htmlText(it.groupValues[1]) }.toList()
        }.toList()
    }

    private fun htmlText(raw: String): String = raw
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""<[^>]+>"""), "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        .replace(Regex("""\s+"""), " ")
        .trim()

    // ---- .xlsx: a zip of XML files ----

    internal fun readXlsx(bytes: ByteArray): List<List<String>> {
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val name = entry.name
                if (name == "xl/sharedStrings.xml" || name.startsWith("xl/worksheets/sheet")) files[name] = zip.readBytes()
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        // The first sheet: Excel names them sheet1.xml, sheet2.xml…
        val sheet = files.keys.filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .minByOrNull { it.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }
            ?: error("No sheet")
        return sheetRows(files.getValue(sheet), shared)
    }

    private fun parseXml(bytes: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = false }
        factory.newSAXParser().parse(ByteArrayInputStream(bytes), handler)
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val out = mutableListOf<String>()
        val text = StringBuilder()
        var inText = false
        parseXml(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
                when (qName) {
                    "si" -> text.clear()
                    "t" -> inText = true
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inText) text.append(ch, start, length)
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName) {
                    "t" -> inText = false
                    "si" -> out += text.toString()
                }
            }
        })
        return out
    }

    private fun sheetRows(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val rows = sortedMapOf<Int, MutableMap<Int, String>>()
        var row = 0
        var col = 0
        var type: String? = null
        val value = StringBuilder()
        var inValue = false
        parseXml(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                when (qName) {
                    "row" -> row = attributes.getValue("r")?.toIntOrNull() ?: (row + 1)
                    "c" -> {
                        val ref = attributes.getValue("r").orEmpty()
                        col = ref.takeWhile(Char::isLetter).fold(0) { acc, ch -> acc * 26 + (ch.uppercaseChar() - 'A' + 1) }
                        ref.dropWhile(Char::isLetter).toIntOrNull()?.let { row = it }
                        type = attributes.getValue("t")
                        value.clear()
                    }
                    "v", "t" -> inValue = true
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inValue) value.append(ch, start, length)
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val raw = value.toString()
                        val text = when (type) {
                            "s" -> raw.trim().toIntOrNull()?.let(shared::getOrNull).orEmpty()
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            "inlineStr", "str", "e" -> raw
                            else -> number(raw)
                        }
                        if (text.isNotEmpty()) rows.getOrPut(row) { mutableMapOf() }[col] = text
                    }
                }
            }
        })
        return rows.values.map { cells -> (1..(cells.keys.maxOrNull() ?: 0)).map { cells[it].orEmpty() } }
    }

    /** "46312" stays "46312" and "1250.5" stays "1250.5": no "1.2505E3", so amounts and dates read as typed. */
    private fun number(raw: String): String = raw.trim().toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: raw.trim()

    private fun number(value: Double): String =
        if (value.isNaN() || value.isInfinite()) "" else BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    // ---- .xls: an Excel 97–2003 workbook (BIFF8 records inside a compound file) ----

    internal fun readXls(bytes: ByteArray): List<List<String>> = biffRows(CompoundFile(bytes).stream("Workbook") ?: CompoundFile(bytes).stream("Book") ?: error("No workbook"))

    /** The few parts of the compound-file format needed to pull one stream out of an .xls. */
    private class CompoundFile(private val data: ByteArray) {
        private val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        private val sectorSize = 1 shl buf.getShort(0x1E).toInt()
        private val miniSectorSize = 1 shl buf.getShort(0x20).toInt()
        private val miniCutoff = buf.getInt(0x38)
        private val fat: IntArray = run {
            val fatSectors = mutableListOf<Int>()
            for (i in 0 until 109) buf.getInt(0x4C + i * 4).takeIf { it >= 0 }?.let(fatSectors::add)
            var difat = buf.getInt(0x44)
            var guard = 0
            while (difat >= 0 && guard++ < 10_000) {
                val base = offset(difat)
                val perSector = sectorSize / 4 - 1
                for (i in 0 until perSector) buf.getInt(base + i * 4).takeIf { it >= 0 }?.let(fatSectors::add)
                difat = buf.getInt(base + perSector * 4)
            }
            val out = IntArray(fatSectors.size * sectorSize / 4)
            fatSectors.forEachIndexed { i, sector ->
                val base = offset(sector)
                for (j in 0 until sectorSize / 4) if (base + j * 4 + 4 <= data.size) out[i * sectorSize / 4 + j] = buf.getInt(base + j * 4)
            }
            out
        }

        private fun offset(sector: Int) = (sector + 1) * sectorSize

        private fun chain(start: Int, table: IntArray): List<Int> {
            val out = mutableListOf<Int>()
            var s = start
            while (s >= 0 && s < table.size && out.size < table.size) {
                out += s
                s = table[s]
            }
            return out
        }

        private fun readChain(start: Int, size: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            for (s in chain(start, fat)) {
                val from = offset(s)
                if (from >= data.size) break
                out.write(data, from, minOf(sectorSize, data.size - from))
            }
            return out.toByteArray().copyOf(size)
        }

        private data class Entry(val name: String, val type: Int, val start: Int, val size: Int)

        private val entries: List<Entry> = run {
            val dir = readChain(buf.getInt(0x30), chain(buf.getInt(0x30), fat).size * sectorSize)
            val d = ByteBuffer.wrap(dir).order(ByteOrder.LITTLE_ENDIAN)
            (0 until dir.size / 128).map { i ->
                val base = i * 128
                val nameLength = (d.getShort(base + 0x40).toInt() / 2 - 1).coerceIn(0, 31)
                val name = String(dir, base, nameLength * 2, Charsets.UTF_16LE)
                Entry(name, dir[base + 0x42].toInt(), d.getInt(base + 0x74), d.getInt(base + 0x78))
            }
        }

        fun stream(name: String): ByteArray? {
            val entry = entries.firstOrNull { it.type == 2 && it.name.equals(name, ignoreCase = true) } ?: return null
            if (entry.size >= miniCutoff) return readChain(entry.start, entry.size)
            // Small streams live in the mini stream, in 64-byte pieces.
            val root = entries.first { it.type == 5 }
            val mini = readChain(root.start, root.size)
            val miniFatBytes = readChain(buf.getInt(0x3C), buf.getInt(0x40) * sectorSize)
            val mb = ByteBuffer.wrap(miniFatBytes).order(ByteOrder.LITTLE_ENDIAN)
            val miniFat = IntArray(miniFatBytes.size / 4) { mb.getInt(it * 4) }
            val out = java.io.ByteArrayOutputStream()
            for (s in chain(entry.start, miniFat)) {
                val from = s * miniSectorSize
                if (from >= mini.size) break
                out.write(mini, from, minOf(miniSectorSize, mini.size - from))
            }
            return out.toByteArray().copyOf(entry.size)
        }
    }

    /** Cells of the first worksheet, from the workbook's BIFF8 records. */
    private fun biffRows(stream: ByteArray): List<List<String>> {
        val b = ByteBuffer.wrap(stream).order(ByteOrder.LITTLE_ENDIAN)
        val records = mutableListOf<Pair<Int, ByteArray>>()
        var pos = 0
        while (pos + 4 <= stream.size) {
            val id = b.getShort(pos).toInt() and 0xFFFF
            val len = b.getShort(pos + 2).toInt() and 0xFFFF
            if (pos + 4 + len > stream.size) break
            records += id to stream.copyOfRange(pos + 4, pos + 4 + len)
            pos += 4 + len
        }
        var sst = emptyList<String>()
        val cells = sortedMapOf<Int, MutableMap<Int, String>>()
        var bofs = 0
        var inSheet = false
        var done = false
        var pendingFormula: Pair<Int, Int>? = null
        var i = 0
        while (i < records.size && !done) {
            val (id, rec) = records[i]
            val r = ByteBuffer.wrap(rec).order(ByteOrder.LITTLE_ENDIAN)
            fun put(row: Int, col: Int, text: String) {
                if (inSheet && text.isNotEmpty()) cells.getOrPut(row) { mutableMapOf() }[col] = text
            }
            fun u16(at: Int) = r.getShort(at).toInt() and 0xFFFF
            when (id) {
                0x0809 -> { // BOF: the first is the workbook globals, then one per sheet.
                    bofs++
                    inSheet = bofs >= 2 && rec.size >= 4 && u16(2) == 0x0010
                }
                0x000A -> if (inSheet) done = true // EOF of the first worksheet
                0x00FC -> { // SST, with any CONTINUE records after it
                    val parts = mutableListOf(rec)
                    while (i + 1 < records.size && records[i + 1].first == 0x003C) parts += records[++i].second
                    sst = readSst(parts)
                }
                0x00FD -> if (rec.size >= 10) put(u16(0), u16(2), sst.getOrNull(r.getInt(6)).orEmpty())
                0x0204 -> if (rec.size >= 9) put(u16(0), u16(2), readShortString(rec, 6))
                0x0203 -> if (rec.size >= 14) put(u16(0), u16(2), number(r.getDouble(6)))
                0x027E -> if (rec.size >= 10) put(u16(0), u16(2), number(rk(r.getInt(6))))
                0x00BD -> if (rec.size >= 6) { // MULRK: several RK numbers in a row
                    val row = u16(0)
                    val first = u16(2)
                    val count = (rec.size - 6) / 6
                    for (k in 0 until count) put(row, first + k, number(rk(r.getInt(4 + k * 6 + 2))))
                }
                0x0006 -> if (rec.size >= 14) { // FORMULA: its last result
                    val row = u16(0)
                    val col = u16(2)
                    if (u16(12) == 0xFFFF) {
                        if (rec[6].toInt() == 0) pendingFormula = row to col
                    } else {
                        put(row, col, number(r.getDouble(6)))
                    }
                }
                0x0207 -> pendingFormula?.let { (row, col) ->
                    put(row, col, readShortString(rec, 0))
                    pendingFormula = null
                }
            }
            i++
        }
        return cells.values.map { row -> (0..(row.keys.maxOrNull() ?: -1)).map { row[it].orEmpty() } }
    }

    private fun rk(value: Int): Double {
        val v = if (value and 0x02 != 0) (value shr 2).toDouble()
        else java.lang.Double.longBitsToDouble((value and 0xFFFFFFFC.toInt()).toLong() shl 32)
        return if (value and 0x01 != 0) v / 100 else v
    }

    /** A BIFF8 string with a two-byte length: "LABEL" and formula results. */
    private fun readShortString(rec: ByteArray, at: Int): String {
        if (at + 3 > rec.size) return ""
        val count = (rec[at].toInt() and 0xFF) or ((rec[at + 1].toInt() and 0xFF) shl 8)
        val wide = rec[at + 2].toInt() and 0x01 != 0
        val start = at + 3
        val bytes = if (wide) count * 2 else count
        if (start + bytes > rec.size) return ""
        return if (wide) String(rec, start, bytes, Charsets.UTF_16LE) else String(rec, start, bytes, Charsets.ISO_8859_1)
    }

    /**
     * The shared string table. A string can run on into the next CONTINUE record, and there the characters
     * start again with their own one-byte flag saying whether they're one or two bytes each.
     */
    private fun readSst(parts: List<ByteArray>): List<String> {
        var part = 0
        var pos = 8 // total and unique counts
        fun ensure() {
            while (part < parts.size && pos >= parts[part].size) {
                pos -= parts[part].size; part++
            }
        }
        fun byte(): Int {
            ensure()
            if (part >= parts.size) throw IndexOutOfBoundsException()
            return parts[part][pos++].toInt() and 0xFF
        }
        fun u16() = byte() or (byte() shl 8)
        fun u32() = u16() or (u16() shl 16)
        fun skip(n: Int) { repeat(n) { byte() } }
        val unique = ByteBuffer.wrap(parts[0]).order(ByteOrder.LITTLE_ENDIAN).getInt(4)
        val out = ArrayList<String>(unique.coerceIn(0, 100_000))
        try {
            repeat(unique) {
                val count = u16()
                val flags = byte()
                var wide = flags and 0x01 != 0
                val runs = if (flags and 0x08 != 0) u16() else 0
                val ext = if (flags and 0x04 != 0) u32() else 0
                val text = StringBuilder(count)
                var left = count
                while (left > 0) {
                    ensure()
                    if (part >= parts.size) break
                    val available = parts[part].size - pos
                    val take = minOf(left, if (wide) available / 2 else available)
                    if (take == 0) {
                        // The string goes on in the next record, which starts with a new flag byte.
                        pos = parts[part].size
                        ensure()
                        wide = byte() and 0x01 != 0
                        continue
                    }
                    for (k in 0 until take) text.append(if (wide) (byte() or (byte() shl 8)).toChar() else byte().toChar())
                    left -= take
                    if (left > 0 && pos >= parts[part].size) {
                        part++; pos = 0
                        if (part < parts.size) wide = byte() and 0x01 != 0
                    }
                }
                skip(runs * 4 + ext)
                out += text.toString()
            }
        } catch (_: IndexOutOfBoundsException) {
            // A truncated table: keep the strings read so far.
        }
        return out
    }
}
