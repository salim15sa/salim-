package com.ats.projects

import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes real, styled .xlsx files (Excel, Google Sheets, WPS) with no third-party library.
 * The ATS logo is embedded as a picture in the top-right corner of every sheet.
 */
object XlsxExporter {

    // ---- cell style ids (index into cellXfs) ----
    private const val S_TITLE = 1
    private const val S_INFO = 2
    private const val S_HEAD = 3
    private const val S_C = 4
    private const val S_L = 5
    private const val S_GROUP = 6
    private const val S_DONE = 7
    private const val S_NOT = 8
    private const val S_NUM = 9
    private const val S_PCT = 10
    private const val S_SECTION = 11
    private const val S_TOT = 12
    private const val S_TOT_PCT = 13
    private const val S_FOOT = 14
    private const val S_TITLE_C = 15
    private const val S_TOT_L = 16
    private const val S_INFO_V = 17

    private class Cell(val v: Any?, val s: Int)

    private class Sheet(val name: String) {
        val rows = sortedMapOf<Int, MutableMap<Int, Cell>>()
        val merges = mutableListOf<String>()
        val widths = sortedMapOf<Int, Double>()
        val heights = mutableMapOf<Int, Double>()
        var freezeRow = 0
        var landscape = false

        /** Logo position: top-left cell (0-based col/row) and size in pixels. */
        var logo: IntArray? = null

        fun set(r: Int, c: Int, v: Any?, s: Int) {
            rows.getOrPut(r) { sortedMapOf() }[c] = Cell(v, s)
        }

        /** Merge a block, styling every cell so borders stay intact. */
        fun merge(r1: Int, c1: Int, r2: Int, c2: Int, v: Any?, s: Int) {
            for (r in r1..r2) for (c in c1..c2) set(r, c, if (r == r1 && c == c1) v else null, s)
            if (r2 > r1 || c2 > c1) merges += "${ref(r1, c1)}:${ref(r2, c2)}"
        }
    }

    private fun col(c: Int): String {
        var n = c
        val sb = StringBuilder()
        while (n > 0) {
            val m = (n - 1) % 26
            sb.insert(0, ('A' + m))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun ref(r: Int, c: Int) = "${col(c)}$r"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").filter { it == '\n' || it == '\t' || it >= ' ' }

    private fun stamp() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.US))

    // ------------------------------------------------------------------ public

    /** Project checklist workbook: "Checklist" (like the progress sheet) + "Summary" (progress & quantities). */
    fun project(p: Project, logoPng: ByteArray?): ByteArray =
        zip(listOf(checklistSheet(p), summarySheet(p)), logoPng)

    /** All projects on one sheet, like the "Project Summary" Google Sheet. */
    fun allProjects(list: List<Project>, logoPng: ByteArray?): ByteArray =
        zip(listOf(projectsSheet(list)), logoPng)

    // ------------------------------------------------------------------ sheets

    private fun header(s: Sheet, p: Project, title: String, titleEnd: Int) {
        val info = listOf(
            "Project:" to p.name,
            "Location:" to p.location,
            "Systems:" to p.systems,
            "Date:" to p.date.format(SHOW_DATE),
        )
        info.forEachIndexed { i, (k, v) ->
            s.heights[i + 1] = 17.0
            s.set(i + 1, 1, k, S_INFO)
            s.merge(i + 1, 2, i + 1, 3, v, S_INFO_V)
        }
        s.merge(2, 4, 3, titleEnd, title, S_TITLE_C)
    }

    private fun checklistSheet(p: Project): Sheet {
        val s = Sheet("Checklist")
        // A Floor, B System, C Device, D Location, E..I stages, J Note
        listOf(13.0, 14.0, 17.0, 17.0, 9.0, 9.0, 11.0, 10.0, 9.0, 30.0)
            .forEachIndexed { i, w -> s.widths[i + 1] = w }
        header(s, p, "Project Progress Checklist", 7)
        s.logo = intArrayOf(7, 0, 230, 86) // col H, row 1

        val head = 6
        s.heights[head] = 22.0
        (listOf("Floor", "System", "Device", "Location") + Stage.entries.map { it.label } + "Note")
            .forEachIndexed { i, h -> s.set(head, i + 1, h, S_HEAD) }
        s.freezeRow = head

        var r = head + 1
        val devices = p.sortedDevices()
        if (devices.isEmpty()) {
            s.merge(r, 1, r, 10, "No devices yet", S_C)
            r++
        }
        devices.groupBy { it.floor }.forEach { (floor, fl) ->
            val floorStart = r
            fl.groupBy { it.system }.forEach { (system, sl) ->
                val sysStart = r
                var locStart = r
                sl.forEachIndexed { i, d ->
                    s.set(r, 3, d.name, S_C)
                    Stage.entries.forEachIndexed { k, st ->
                        when (d.mark(st)) {
                            Mark.DONE -> s.set(r, 5 + k, "✓", S_DONE)
                            Mark.NOT -> s.set(r, 5 + k, "X", S_NOT)
                            Mark.NONE -> s.set(r, 5 + k, null, S_C)
                        }
                    }
                    s.set(r, 10, d.note, S_L)
                    if (d.note.length > 34) s.heights[r] = rowHeight(listOf(d.note to 30.0))
                    // merge equal consecutive locations (like the paper sheet)
                    val nextSame = i + 1 < sl.size && sl[i + 1].location.equals(d.location, true) && d.location.isNotBlank()
                    if (!nextSame) {
                        s.merge(locStart, 4, r, 4, d.location, if (r > locStart) S_GROUP else S_C)
                        locStart = r + 1
                    }
                    r++
                }
                s.merge(sysStart, 2, r - 1, 2, system, S_GROUP)
            }
            s.merge(floorStart, 1, r - 1, 1, floor, S_GROUP)
        }

        // totals
        r++
        s.heights[r] = 20.0
        s.merge(r, 1, r, 4, "Completed  (${devices.size} devices)", S_TOT_L)
        Stage.entries.forEachIndexed { k, st ->
            s.set(r, 5 + k, if (devices.isEmpty()) 0.0 else p.stageDone(st).toDouble() / devices.size, S_TOT_PCT)
        }
        s.set(r, 10, "Overall ${pct(p.progress)}", S_TOT)
        r += 2
        s.merge(r, 1, r, 10, "✓ = done    X = not done / issue    blank = pending      •      Generated by ATS Projects on ${stamp()}", S_FOOT)
        return s
    }

    private fun summarySheet(p: Project): Sheet {
        val s = Sheet("Summary")
        listOf(16.0, 22.0, 14.0, 13.0, 12.0, 11.0, 11.0).forEachIndexed { i, w -> s.widths[i + 1] = w }
        header(s, p, "Progress Summary", 5)
        s.logo = intArrayOf(5, 0, 170, 64)
        val devices = p.sortedDevices()
        val n = devices.size

        var r = 6
        s.merge(r, 1, r, 7, "STAGE PROGRESS", S_SECTION); r++
        listOf("Stage", "Done", "Not done (X)", "Pending", "% Done").forEachIndexed { i, h -> s.set(r, i + 1, h, S_HEAD) }
        r++
        Stage.entries.forEach { st ->
            val done = p.stageDone(st)
            val bad = p.stageIssues(st)
            s.set(r, 1, st.label, S_L)
            s.set(r, 2, done.toDouble(), S_NUM)
            s.set(r, 3, bad.toDouble(), S_NUM)
            s.set(r, 4, (n - done - bad).toDouble(), S_NUM)
            s.set(r, 5, if (n == 0) 0.0 else done.toDouble() / n, S_PCT)
            r++
        }
        s.set(r, 1, "Overall", S_TOT_L)
        s.merge(r, 2, r, 4, "$n devices", S_TOT)
        s.set(r, 5, p.progress, S_TOT_PCT)
        r += 3

        s.merge(r, 1, r, 7, "BY FLOOR & SYSTEM", S_SECTION); r++
        listOf("Floor", "System", "Devices", "Cable pulled", "Installed", "Tested", "% Done")
            .forEachIndexed { i, h -> s.set(r, i + 1, h, S_HEAD) }
        r++
        devices.groupBy { it.floor }.forEach { (floor, fl) ->
            val start = r
            fl.groupBy { it.system }.forEach { (system, sl) ->
                s.set(r, 2, system, S_L)
                s.set(r, 3, sl.size.toDouble(), S_NUM)
                s.set(r, 4, sl.count { it.mark(Stage.CABLE) == Mark.DONE }.toDouble(), S_NUM)
                s.set(r, 5, sl.count { it.mark(Stage.INSTALLED) == Mark.DONE }.toDouble(), S_NUM)
                s.set(r, 6, sl.count { it.mark(Stage.TESTED) == Mark.DONE }.toDouble(), S_NUM)
                s.set(r, 7, sl.sumOf { it.doneCount }.toDouble() / (sl.size * Stage.entries.size), S_PCT)
                r++
            }
            s.merge(start, 1, r - 1, 1, floor, S_GROUP)
        }
        r += 2

        s.merge(r, 1, r, 7, "DEVICE QUANTITIES", S_SECTION); r++
        listOf("System", "Device type", "Quantity").forEachIndexed { i, h -> s.set(r, i + 1, h, S_HEAD) }
        r++
        devices.groupBy { it.system }.forEach { (system, sl) ->
            val start = r
            sl.groupBy { it.type }.forEach { (type, tl) ->
                s.set(r, 2, type, S_L)
                s.set(r, 3, tl.size.toDouble(), S_NUM)
                r++
            }
            s.merge(start, 1, r - 1, 1, system, S_GROUP)
        }
        s.merge(r, 1, r, 2, "Total devices", S_TOT_L)
        s.set(r, 3, n.toDouble(), S_TOT)
        r += 2
        s.merge(r, 1, r, 7, "Generated by ATS Projects on ${stamp()}", S_FOOT)
        return s
    }

    private fun projectsSheet(list: List<Project>): Sheet {
        val s = Sheet("Project Summary")
        s.landscape = true
        val cols = listOf(
            "Sl" to 5.0, "Project name" to 24.0, "Client" to 18.0, "Scope of work" to 14.0, "Status" to 17.0,
            "Latest update" to 30.0, "Next step" to 30.0, "Contact person" to 15.0, "Phone" to 12.0,
            "Location" to 13.0, "Devices" to 9.0, "Progress" to 10.0, "Date" to 11.0,
        )
        cols.forEachIndexed { i, (_, w) -> s.widths[i + 1] = w }
        s.heights[2] = 30.0
        s.merge(2, 1, 2, 6, "Project Summary", S_TITLE)
        s.merge(3, 1, 3, 6, "${list.size} projects  •  ${stamp()}", S_FOOT)
        s.logo = intArrayOf(10, 0, 210, 79)
        val head = 6
        s.heights[head] = 22.0
        cols.forEachIndexed { i, (h, _) -> s.set(head, i + 1, h, S_HEAD) }
        s.freezeRow = head
        var r = head + 1
        list.forEachIndexed { i, p ->
            s.set(r, 1, (i + 1).toDouble(), S_NUM)
            s.set(r, 2, p.name, S_L)
            s.set(r, 3, p.client, S_L)
            s.set(r, 4, p.systems, S_L)
            s.set(r, 5, p.status, S_C)
            s.set(r, 6, p.latestUpdate, S_L)
            s.set(r, 7, p.nextStep, S_L)
            s.set(r, 8, p.contact, S_L)
            s.set(r, 9, p.phone, S_C)
            s.set(r, 10, p.location, S_L)
            s.set(r, 11, p.devices.size.toDouble(), S_NUM)
            s.set(r, 12, p.progress, S_PCT)
            s.set(r, 13, p.date.format(SHOW_DATE), S_C)
            s.heights[r] = rowHeight(listOf(p.name to 24.0, p.latestUpdate to 30.0, p.nextStep to 30.0, p.location to 13.0, p.client to 18.0))
            r++
        }
        r++
        s.merge(r, 1, r, 13, "Generated by ATS Projects – Al Arkan Technology Services", S_FOOT)
        return s
    }

    /** Rough row height so wrapped text is not cut: (text, column width) pairs. */
    private fun rowHeight(cells: List<Pair<String, Double>>): Double {
        val lines = cells.maxOf { (t, w) ->
            t.split('\n').sumOf { line -> maxOf(1, Math.ceil(line.length / (w * 1.15)).toInt()) }
        }
        return maxOf(18.0, 13.5 * lines + 4)
    }

    // ------------------------------------------------------------------ package

    private fun zip(sheets: List<Sheet>, logo: ByteArray?): ByteArray {
        val out = ByteArrayOutputStream()
        val withLogo = logo != null
        ZipOutputStream(out).use { zip ->
            fun put(name: String, body: String) = putBytes(zip, name, body.toByteArray(Charsets.UTF_8))
            put("[Content_Types].xml", contentTypes(sheets, withLogo))
            put("_rels/.rels", ROOT_RELS)
            put("xl/workbook.xml", workbook(sheets))
            put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, s ->
                val n = i + 1
                val hasLogo = withLogo && s.logo != null
                put("xl/worksheets/sheet$n.xml", sheetXml(s, hasLogo))
                if (hasLogo) {
                    put("xl/worksheets/_rels/sheet$n.xml.rels", rels("rId1", REL_DRAWING, "../drawings/drawing$n.xml"))
                    put("xl/drawings/drawing$n.xml", drawingXml(s.logo!!))
                    put("xl/drawings/_rels/drawing$n.xml.rels", rels("rId1", REL_IMAGE, "../media/logo.png"))
                }
            }
            if (withLogo) putBytes(zip, "xl/media/logo.png", logo!!)
        }
        return out.toByteArray()
    }

    private fun putBytes(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private const val REL_DRAWING = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing"
    private const val REL_IMAGE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"

    private fun rels(id: String, type: String, target: String) =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="$id" Type="$type" Target="$target"/></Relationships>"""

    private fun drawingXml(pos: IntArray): String {
        val emu = 9525L
        val (c, r, w, h) = pos.toList()
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
            """<xdr:oneCellAnchor><xdr:from><xdr:col>$c</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>$r</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>""" +
            """<xdr:ext cx="${w * emu}" cy="${h * emu}"/>""" +
            """<xdr:pic><xdr:nvPicPr><xdr:cNvPr id="2" name="ATS Logo"/><xdr:cNvPicPr><a:picLocks noChangeAspect="1"/></xdr:cNvPicPr></xdr:nvPicPr>""" +
            """<xdr:blipFill><a:blip r:embed="rId1"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>""" +
            """<xdr:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="${w * emu}" cy="${h * emu}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic>""" +
            """<xdr:clientData/></xdr:oneCellAnchor></xdr:wsDr>"""
    }

    private fun sheetXml(s: Sheet, hasLogo: Boolean): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("""<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>""")
        append("""<sheetViews><sheetView workbookViewId="0" showGridLines="0">""")
        if (s.freezeRow > 0) {
            append("""<pane ySplit="${s.freezeRow}" topLeftCell="A${s.freezeRow + 1}" activePane="bottomLeft" state="frozen"/>""")
        }
        append("</sheetView></sheetViews>")
        append("""<sheetFormatPr defaultRowHeight="18" customHeight="1"/>""")
        append("<cols>")
        s.widths.forEach { (c, w) -> append("""<col min="$c" max="$c" width="$w" customWidth="1"/>""") }
        append("</cols><sheetData>")
        val allRows = (s.rows.keys + s.heights.keys).toSortedSet()
        for (r in allRows) {
            val h = s.heights[r] ?: 18.0
            append("""<row r="$r" ht="$h" customHeight="1">""")
            s.rows[r]?.forEach { (c, cell) ->
                val rf = ref(r, c)
                when (val v = cell.v) {
                    null -> append("""<c r="$rf" s="${cell.s}"/>""")
                    is Number -> append("""<c r="$rf" s="${cell.s}"><v>${Math.round(v.toDouble() * 1_000_000.0) / 1_000_000.0}</v></c>""")
                    else -> append("""<c r="$rf" s="${cell.s}" t="inlineStr"><is><t xml:space="preserve">${esc(v.toString())}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData>")
        if (s.merges.isNotEmpty()) {
            append("""<mergeCells count="${s.merges.size}">""")
            s.merges.forEach { append("""<mergeCell ref="$it"/>""") }
            append("</mergeCells>")
        }
        append("""<pageMargins left="0.35" right="0.35" top="0.45" bottom="0.45" header="0.3" footer="0.3"/>""")
        append("""<pageSetup paperSize="9" orientation="${if (s.landscape) "landscape" else "portrait"}" fitToWidth="1" fitToHeight="0"/>""")
        if (hasLogo) append("""<drawing r:id="rId1"/>""")
        append("</worksheet>")
    }

    private fun contentTypes(sheets: List<Sheet>, logo: Boolean) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        if (logo) append("""<Default Extension="png" ContentType="image/png"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        sheets.forEachIndexed { i, s ->
            val n = i + 1
            append("""<Override PartName="/xl/worksheets/sheet$n.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
            if (logo && s.logo != null) {
                append("""<Override PartName="/xl/drawings/drawing$n.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>""")
            }
        }
        append("</Types>")
    }

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private fun workbook(sheets: List<Sheet>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { i, s -> append("""<sheet name="${esc(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
        append("</sheets>")
        // repeat the table header row on every printed page
        if (sheets.any { it.freezeRow > 0 }) {
            append("<definedNames>")
            sheets.forEachIndexed { i, s ->
                if (s.freezeRow > 0) {
                    append("""<definedName name="_xlnm.Print_Titles" localSheetId="$i">'${esc(s.name)}'!${'$'}${s.freezeRow}:${'$'}${s.freezeRow}</definedName>""")
                }
            }
            append("</definedNames>")
        }
        append("</workbook>")
    }

    private fun workbookRels(n: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..n) append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        append("""<Relationship Id="rId${n + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun font(b: Boolean, i: Boolean, sz: Int, color: String) =
        "<font>${if (b) "<b/>" else ""}${if (i) "<i/>" else ""}<sz val=\"$sz\"/><color rgb=\"$color\"/><name val=\"Calibri\"/><family val=\"2\"/></font>"

    private fun fill(color: String) =
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"$color\"/><bgColor indexed=\"64\"/></patternFill></fill>"

    private fun xf(numFmt: Int, font: Int, fill: Int, border: Int, h: String = "left", wrap: Boolean = false) =
        "<xf numFmtId=\"$numFmt\" fontId=\"$font\" fillId=\"$fill\" borderId=\"$border\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">" +
            "<alignment horizontal=\"$h\" vertical=\"center\"${if (wrap) " wrapText=\"1\"" else ""}${if (h == "left") " indent=\"1\"" else ""}/></xf>"

    private val STYLES: String = run {
        val navy = "FF0E4A5F"
        val fonts = listOf(
            font(false, false, 10, "FF1F2937"), // 0 body
            font(true, false, 10, "FFFFFFFF"),  // 1 header white
            font(true, false, 16, navy),        // 2 title
            font(true, false, 10, "FF1F2937"),  // 3 bold body
            font(false, true, 9, "FF6B7280"),   // 4 footer
            font(true, false, 12, "FF00A651"),  // 5 tick green
            font(true, false, 11, "FFFFFFFF"),  // 6 X white
            font(true, false, 12, navy),        // 7 section
            font(false, false, 10, "FF374151"), // 8 info value
        )
        val fills = listOf(
            "<fill><patternFill patternType=\"none\"/></fill>",
            "<fill><patternFill patternType=\"gray125\"/></fill>",
            fill(navy),        // 2 header
            fill("FFFF0000"),  // 3 red
            fill("FFE6F6F6"),  // 4 teal tint
            fill("FFF3F6FA"),  // 5 group grey
        )
        val line = "style=\"thin\"><color rgb=\"FF000000\"/>"
        val borders = listOf(
            "<border><left/><right/><top/><bottom/><diagonal/></border>",
            "<border><left $line</left><right $line</right><top $line</top><bottom $line</bottom><diagonal/></border>",
            "<border><left/><right/><top/><bottom style=\"medium\"><color rgb=\"FF2FB5B8\"/></bottom><diagonal/></border>",
        )
        val xfs = listOf(
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>", // 0
            xf(0, 2, 0, 0),                     // 1 title
            xf(0, 3, 0, 0),                     // 2 info label
            xf(0, 1, 2, 1, "center", true),     // 3 header
            xf(0, 0, 0, 1, "center", true),     // 4 centered
            xf(0, 0, 0, 1, "left", true),       // 5 left
            xf(0, 3, 0, 1, "center", true),     // 6 merged group
            xf(0, 5, 0, 1, "center"),           // 7 done ✓
            xf(0, 6, 3, 1, "center"),           // 8 not X (red)
            xf(1, 0, 0, 1, "center"),           // 9 integer
            xf(9, 0, 0, 1, "center"),           // 10 percent
            xf(0, 7, 0, 2),                     // 11 section
            xf(0, 3, 4, 1, "center"),           // 12 total
            xf(9, 3, 4, 1, "center"),           // 13 total %
            xf(0, 4, 0, 0),                     // 14 footer
            xf(0, 2, 0, 0, "center"),           // 15 title centered
            xf(0, 3, 4, 1),                     // 16 total label
            xf(0, 8, 0, 0),                     // 17 info value
        )
        buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("""<fonts count="${fonts.size}">${fonts.joinToString("")}</fonts>""")
            append("""<fills count="${fills.size}">${fills.joinToString("")}</fills>""")
            append("""<borders count="${borders.size}">${borders.joinToString("")}</borders>""")
            append("""<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""")
            append("""<cellXfs count="${xfs.size}">${xfs.joinToString("")}</cellXfs>""")
            append("""<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""")
            append("</styleSheet>")
        }
    }
}
