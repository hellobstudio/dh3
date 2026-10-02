package hu.diakh.app

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Minimális, külső könyvtár nélküli .xlsx olvasó/író. */
object Xlsx {

    fun read(ctx: Context, uri: Uri): List<List<String>> {
        var sharedXml: String? = null
        val sheets = sortedMapOf<String, String>()
        val input = ctx.contentResolver.openInputStream(uri) ?: error("Nem olvasható fájl")
        ZipInputStream(input).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val n = e.name
                if (n == "xl/sharedStrings.xml") sharedXml = z.readBytes().decodeToString()
                else if (n.startsWith("xl/worksheets/sheet") && n.endsWith(".xml")) sheets[n] = z.readBytes().decodeToString()
                e = z.nextEntry
            }
        }
        val sheet = sheets["xl/worksheets/sheet1.xml"] ?: sheets.values.firstOrNull() ?: error("Nincs munkalap")
        val shared = sharedXml?.let { parseShared(it) } ?: emptyList()
        return parseSheet(sheet, shared)
    }

    private fun parseShared(xml: String): List<String> {
        val p = Xml.newPullParser()
        p.setInput(StringReader(xml))
        val out = ArrayList<String>()
        var sb: StringBuilder? = null
        var inT = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> sb = StringBuilder()
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> if (inT) sb?.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "t" -> inT = false
                    "si" -> { out.add(sb?.toString() ?: ""); sb = null }
                }
            }
            ev = p.next()
        }
        return out
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else break
        }
        return (n - 1).coerceAtLeast(0)
    }

    private fun parseSheet(xml: String, shared: List<String>): List<List<String>> {
        val p = Xml.newPullParser()
        p.setInput(StringReader(xml))
        val rows = ArrayList<List<String>>()
        var row: MutableList<String>? = null
        var col = 0
        var type: String? = null
        var buf = StringBuilder()
        var inV = false
        var inT = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> row = ArrayList()
                    "c" -> {
                        col = colIndex(p.getAttributeValue(null, "r") ?: "")
                        type = p.getAttributeValue(null, "t")
                        buf = StringBuilder()
                    }
                    "v" -> inV = true
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> if (inV || inT) buf.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "c" -> {
                        val raw = buf.toString()
                        val value = if (type == "s") shared.getOrElse(raw.trim().toIntOrNull() ?: -1) { "" } else raw
                        val r = row
                        if (r != null) {
                            while (r.size < col) r.add("")
                            if (r.size == col) r.add(value) else r[col] = value
                        }
                    }
                    "row" -> { row?.let { rows.add(it) }; row = null }
                }
            }
            ev = p.next()
        }
        return rows
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun write(file: File, rows: List<List<String>>) {
        ZipOutputStream(FileOutputStream(file)).use { z ->
            fun put(name: String, content: String) {
                z.putNextEntry(ZipEntry(name))
                z.write(content.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            val head = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            put("[Content_Types].xml", head +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "</Types>")
            put("_rels/.rels", head +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>")
            put("xl/workbook.xml", head +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<sheets><sheet name=\"Hiányzások\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>")
            put("xl/_rels/workbook.xml.rels", head +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                "</Relationships>")
            val sb = StringBuilder(head)
            sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
            rows.forEachIndexed { i, r ->
                sb.append("<row r=\"${i + 1}\">")
                r.forEachIndexed { j, v ->
                    sb.append("<c r=\"${'A' + j}${i + 1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(v)}</t></is></c>")
                }
                sb.append("</row>")
            }
            sb.append("</sheetData></worksheet>")
            put("xl/worksheets/sheet1.xml", sb.toString())
        }
    }
}
