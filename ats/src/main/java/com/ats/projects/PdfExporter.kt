package com.ats.projects

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A4 PDF reports drawn with Android's PdfDocument, ATS logo on every page. */
object PdfExporter {
    private const val M = 24f

    private val NAVY = Color.rgb(0x0E, 0x4A, 0x5F)
    private val TEAL = Color.rgb(0x2F, 0xB5, 0xB8)
    private val INK = Color.rgb(0x1F, 0x29, 0x37)
    private val MUTED = Color.rgb(0x6B, 0x72, 0x80)
    private val GREEN = Color.rgb(0x00, 0xA6, 0x51)
    private val RED = Color.rgb(0xFF, 0x00, 0x00)
    private val TINT = Color.rgb(0xE6, 0xF6, 0xF6)
    private val ZEBRA = Color.rgb(0xF5, 0xF7, 0xFA)

    private fun paint(size: Float, color: Int, bold: Boolean = false, italic: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(
                Typeface.SANS_SERIF,
                when {
                    bold && italic -> Typeface.BOLD_ITALIC
                    bold -> Typeface.BOLD
                    italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                },
            )
        }

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private val linePaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 0.6f }

    private fun fit(text: String, p: Paint, width: Float): String {
        if (p.measureText(text) <= width) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > width) t = t.dropLast(1)
        return "$t…"
    }

    /** Splits text into at most [maxLines] lines that fit [width]. */
    private fun wrap(text: String, p: Paint, width: Float, maxLines: Int): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        var cur = ""
        for (w in words) {
            val t = if (cur.isEmpty()) w else "$cur $w"
            if (p.measureText(t) <= width || cur.isEmpty()) cur = t else {
                lines += cur
                cur = w
            }
        }
        if (cur.isNotEmpty()) lines += cur
        if (lines.size > maxLines) {
            val keep = lines.take(maxLines).toMutableList()
            keep[maxLines - 1] = fit(keep[maxLines - 1] + " " + lines.drop(maxLines).joinToString(" "), p, width)
            return keep
        }
        return lines.map { fit(it, p, width) }
    }

    private fun centerText(c: Canvas, text: String, r: RectF, p: Paint, maxLines: Int = 2) {
        val lines = wrap(text, p, r.width() - 4f, maxLines)
        val lh = p.textSize * 1.15f
        var y = r.centerY() - (lines.size - 1) * lh / 2 + p.textSize * 0.35f
        lines.forEach {
            c.drawText(it, r.centerX() - p.measureText(it) / 2, y, p)
            y += lh
        }
    }

    private fun leftText(c: Canvas, text: String, r: RectF, p: Paint, maxLines: Int = 2) {
        val lines = wrap(text, p, r.width() - 6f, maxLines)
        val lh = p.textSize * 1.15f
        var y = r.centerY() - (lines.size - 1) * lh / 2 + p.textSize * 0.35f
        lines.forEach {
            c.drawText(it, r.left + 3f, y, p)
            y += lh
        }
    }

    private fun drawTick(c: Canvas, r: RectF) {
        val s = minOf(r.width(), r.height()) * 0.32f
        val cx = r.centerX()
        val cy = r.centerY()
        val path = Path().apply {
            moveTo(cx - s, cy)
            lineTo(cx - s * 0.3f, cy + s * 0.7f)
            lineTo(cx + s, cy - s * 0.8f)
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN; style = Paint.Style.STROKE; strokeWidth = 1.6f })
    }

    private fun drawLogo(c: Canvas, logo: Bitmap?, right: Float, top: Float, width: Float) {
        if (logo == null) {
            c.drawText("ATS", right - 60f, top + 30f, paint(26f, NAVY, bold = true))
            return
        }
        val h = width * logo.height / logo.width
        c.drawBitmap(logo, null, RectF(right - width, top, right, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun stamp() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.US))

    private fun bytes(doc: PdfDocument): ByteArray {
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    // ================================================================== project checklist

    private class Col(val title: String, val w: Float)

    private val COLS = listOf(
        Col("Floor", 56f), Col("System", 62f), Col("Device", 70f), Col("Location", 74f),
    ) + Stage.entries.map { Col(it.label, 40f) } + Col("Note", 85f)

    private class Line(val d: Device, val h: Float)

    fun project(p: Project, logo: Bitmap?): ByteArray {
        val w = 595
        val h = 842
        val doc = PdfDocument()
        val generated = stamp()
        val devices = p.sortedDevices()
        val notePaint = paint(6.8f, INK)
        val lines = devices.map { d ->
            val n = if (d.note.isBlank()) 1 else wrap(d.note, notePaint, COLS.last().w - 6f, 3).size
            Line(d, maxOf(14f, 9f * n + 5f))
        }

        val tableTop = 104f
        val headH = 20f
        val bottom = h - 40f
        // paginate
        val pages = mutableListOf<List<Line>>()
        var cur = mutableListOf<Line>()
        var y = tableTop + headH
        lines.forEach { l ->
            if (y + l.h > bottom && cur.isNotEmpty()) {
                pages += cur
                cur = mutableListOf()
                y = tableTop + headH
            }
            cur += l
            y += l.h
        }
        pages += cur
        val totalPages = pages.size + 1

        fun pageHeader(c: Canvas, title: String) {
            val lp = paint(8.5f, INK, bold = true)
            val vp = paint(8.5f, INK)
            listOf("Project:" to p.name, "Location:" to p.location, "Systems:" to p.systems, "Date:" to p.date.format(SHOW_DATE))
                .forEachIndexed { i, (k, v) ->
                    val ty = M + 14f + i * 12f
                    c.drawText(k, M, ty, lp)
                    c.drawText(fit(v, vp, 150f), M + 46f, ty, vp)
                }
            val tp = paint(13f, INK, bold = true)
            c.drawText(title, w / 2f - tp.measureText(title) / 2 + 10f, M + 34f, tp)
            drawLogo(c, logo, w - M, M - 2f, 150f)
        }

        fun footer(c: Canvas, pageNo: Int) {
            val fp = paint(7f, MUTED)
            c.drawLine(M, h - 28f, w - M, h - 28f, Paint().apply { color = TEAL; strokeWidth = 1f })
            c.drawText("ATS – Al Arkan Technology Services  •  ${p.name}", M, h - 16f, fp)
            val right = "Generated $generated  •  Page $pageNo of $totalPages"
            c.drawText(right, w - M - fp.measureText(right), h - 16f, fp)
        }

        val cellP = paint(7.2f, INK)
        val groupP = paint(7.6f, INK, bold = true)
        val headP = paint(7.4f, Color.WHITE, bold = true)
        val xP = paint(8f, Color.WHITE, bold = true)

        pages.forEachIndexed { pi, rows ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pi + 1).create())
            val c = page.canvas
            pageHeader(c, "Project Progress Checklist")

            // header row
            var x = M
            COLS.forEach { col ->
                val r = RectF(x, tableTop, x + col.w, tableTop + headH)
                c.drawRect(r, fillPaint(NAVY))
                c.drawRect(r, linePaint)
                centerText(c, col.title, r, headP)
                x += col.w
            }
            val xs = COLS.runningFold(M) { acc, col -> acc + col.w }

            if (rows.isEmpty()) {
                val r = RectF(M, tableTop + headH, xs.last(), tableTop + headH + 20f)
                c.drawRect(r, linePaint)
                centerText(c, "No devices yet", r, cellP)
            }

            // row tops
            val tops = rows.runningFold(tableTop + headH) { acc, l -> acc + l.h }

            /** Draws a merged cell for column [ci] over runs of equal [key]. */
            fun spans(ci: Int, key: (Device) -> String, label: (Device) -> String, paintFor: Paint) {
                var i = 0
                while (i < rows.size) {
                    var j = i
                    while (j + 1 < rows.size && key(rows[j + 1].d) == key(rows[i].d) && label(rows[i].d).isNotBlank()) j++
                    val r = RectF(xs[ci], tops[i], xs[ci + 1], tops[j + 1])
                    c.drawRect(r, linePaint)
                    val lines = maxOf(1, ((r.height() - 2f) / (paintFor.textSize * 1.15f)).toInt()).coerceAtMost(4)
                    centerText(c, label(rows[i].d), r, if (j > i) paintFor else cellP, lines)
                    i = j + 1
                }
            }
            spans(0, { it.floor }, { it.floor }, groupP)
            spans(1, { it.floor + "|" + it.system }, { it.system }, groupP)
            spans(3, { it.floor + "|" + it.system + "|" + it.location.lowercase() }, { it.location }, groupP)

            rows.forEachIndexed { ri, l ->
                val top = tops[ri]
                val bot = tops[ri + 1]
                val dr = RectF(xs[2], top, xs[3], bot)
                c.drawRect(dr, linePaint)
                centerText(c, l.d.name, dr, cellP, 1)
                Stage.entries.forEachIndexed { k, st ->
                    val r = RectF(xs[4 + k], top, xs[5 + k], bot)
                    when (l.d.mark(st)) {
                        Mark.DONE -> drawTick(c, r)
                        Mark.NOT -> {
                            c.drawRect(r, fillPaint(RED))
                            centerText(c, "X", r, xP, 1)
                        }
                        Mark.NONE -> {}
                    }
                    c.drawRect(r, linePaint)
                }
                val nr = RectF(xs[9], top, xs[10], bot)
                c.drawRect(nr, linePaint)
                if (l.d.note.isNotBlank()) leftText(c, l.d.note, nr, notePaint, 3)
            }
            footer(c, pi + 1)
            doc.finishPage(page)
        }

        // ---- summary page
        val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, totalPages).create())
        val c = page.canvas
        pageHeader(c, "Progress Summary")
        y = 112f
        val n = devices.size

        fun section(t: String) {
            c.drawText(t, M, y, paint(11f, NAVY, bold = true))
            c.drawRect(M, y + 4f, w - M, y + 5.5f, fillPaint(TEAL))
            y += 20f
        }

        // overall
        val cards = listOf("DEVICES" to "$n", "OVERALL" to pct(p.progress), "STATUS" to p.status, "FLOORS" to "${p.orderedFloors.size}")
        val cw = (w - 2 * M - 3 * 10f) / 4
        cards.forEachIndexed { i, (k, v) ->
            val x = M + i * (cw + 10f)
            c.drawRoundRect(RectF(x, y, x + cw, y + 46f), 7f, 7f, fillPaint(TINT))
            c.drawRoundRect(RectF(x, y, x + 4f, y + 46f), 2f, 2f, fillPaint(TEAL))
            c.drawText(k, x + 12f, y + 16f, paint(7f, MUTED, bold = true))
            c.drawText(fit(v, paint(13f, NAVY, bold = true), cw - 18f), x + 12f, y + 35f, paint(13f, NAVY, bold = true))
        }
        y += 66f

        section("STAGE PROGRESS")
        val barX = M + 90f
        val barW = w - M - barX - 120f
        Stage.entries.forEach { st ->
            val done = p.stageDone(st)
            val bad = p.stageIssues(st)
            c.drawText(st.label, M, y + 9f, paint(8.5f, INK, bold = true))
            c.drawRoundRect(RectF(barX, y, barX + barW, y + 12f), 6f, 6f, fillPaint(ZEBRA))
            if (n > 0) {
                val dw = barW * done / n
                val bw = barW * bad / n
                if (dw > 0) c.drawRoundRect(RectF(barX, y, barX + dw, y + 12f), 6f, 6f, fillPaint(GREEN))
                if (bw > 0) c.drawRect(RectF(barX + dw, y, barX + dw + bw, y + 12f), fillPaint(RED))
            }
            val txt = "$done/$n done" + if (bad > 0) "  •  $bad X" else ""
            c.drawText(txt, barX + barW + 8f, y + 9f, paint(8f, INK))
            y += 20f
        }
        y += 14f

        section("DEVICE QUANTITIES")
        val qCols = listOf(110f, 170f, 60f, 60f, 60f, 87f)
        val qHead = listOf("System", "Device type", "Qty", "Installed", "Tested", "Floors")
        var x = M
        qHead.forEachIndexed { i, t ->
            val r = RectF(x, y, x + qCols[i], y + 16f)
            c.drawRect(r, fillPaint(NAVY))
            c.drawRect(r, linePaint)
            centerText(c, t, r, headP, 1)
            x += qCols[i]
        }
        y += 16f
        var zebra = false
        loop@ for ((system, sl) in devices.groupBy { it.system }) {
            for ((type, tl) in sl.groupBy { it.type }) {
                if (y > h - 60f) {
                    c.drawText("… more in the Excel file", M, y + 12f, paint(8f, MUTED, italic = true))
                    break@loop
                }
                val vals = listOf(
                    system, type, "${tl.size}",
                    "${tl.count { it.mark(Stage.INSTALLED) == Mark.DONE }}",
                    "${tl.count { it.mark(Stage.TESTED) == Mark.DONE }}",
                    tl.map { it.floor }.distinct().joinToString(", ") { it.replace(" Floor", "") },
                )
                x = M
                vals.forEachIndexed { i, v ->
                    val r = RectF(x, y, x + qCols[i], y + 14f)
                    if (zebra) c.drawRect(r, fillPaint(ZEBRA))
                    c.drawRect(r, linePaint)
                    if (i < 2) leftText(c, v, r, cellP, 1) else centerText(c, v, r, cellP, 1)
                    x += qCols[i]
                }
                zebra = !zebra
                y += 14f
            }
        }
        footer(c, totalPages)
        doc.finishPage(page)
        return bytes(doc)
    }

    // ================================================================== all projects

    fun allProjects(list: List<Project>, logo: Bitmap?): ByteArray {
        val w = 842
        val h = 595
        val doc = PdfDocument()
        val generated = stamp()
        val cols = listOf(
            "Sl" to 22f, "Project" to 120f, "Client" to 80f, "Scope" to 62f, "Status" to 78f,
            "Latest update" to 150f, "Next step" to 150f, "Contact" to 60f, "Location" to 58f, "Progress" to 14f + 0f,
        )
        // give remaining width to Progress
        val used = cols.dropLast(1).sumOf { it.second.toDouble() }.toFloat()
        val widths = cols.dropLast(1).map { it.second } + (w - 2 * M - used)
        val xs = widths.runningFold(M) { a, b -> a + b }
        val cellP = paint(7.2f, INK)
        val headP = paint(7.6f, Color.WHITE, bold = true)
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f

        fun newPage() {
            if (pageNo > 0) {
                val fp = paint(7f, MUTED)
                c.drawText("ATS – Al Arkan Technology Services  •  Generated $generated  •  Page $pageNo", M, h - 16f, fp)
                doc.finishPage(page)
            }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
            c = page.canvas
            c.drawText("Project Summary", M, M + 22f, paint(18f, NAVY, bold = true))
            c.drawText("${list.size} projects  •  $generated", M, M + 38f, paint(9f, MUTED))
            drawLogo(c, logo, w - M, M - 6f, 140f)
            y = 82f
            cols.forEachIndexed { i, (t, _) ->
                val r = RectF(xs[i], y, xs[i + 1], y + 20f)
                c.drawRect(r, fillPaint(NAVY))
                c.drawRect(r, linePaint)
                centerText(c, t, r, headP, 1)
            }
            y += 20f
        }

        newPage()
        list.forEachIndexed { i, p ->
            val rowH = 30f
            if (y + rowH > h - 34f) newPage()
            val vals = listOf(
                "${i + 1}", p.name, p.client, p.systems, p.status, p.latestUpdate, p.nextStep,
                listOf(p.contact, p.phone).filter { it.isNotBlank() }.joinToString("\n"), p.location, "",
            )
            vals.forEachIndexed { k, v ->
                val r = RectF(xs[k], y, xs[k + 1], y + rowH)
                if (i % 2 == 1) c.drawRect(r, fillPaint(ZEBRA))
                c.drawRect(r, linePaint)
                when (k) {
                    0, 3, 4 -> centerText(c, v, r, cellP, 3)
                    9 -> {
                        val bw = r.width() - 12f
                        val br = RectF(r.left + 6f, r.top + 8f, r.left + 6f + bw, r.top + 14f)
                        c.drawRoundRect(br, 3f, 3f, fillPaint(Color.rgb(0xE5, 0xE7, 0xEB)))
                        c.drawRoundRect(RectF(br.left, br.top, br.left + (bw * p.progress).toFloat(), br.bottom), 3f, 3f, fillPaint(GREEN))
                        val t = "${pct(p.progress)} • ${p.devices.size} dev"
                        val tp = paint(6.8f, INK)
                        c.drawText(t, r.centerX() - tp.measureText(t) / 2, r.top + 25f, tp)
                    }
                    else -> leftText(c, v.replace("\n", " • "), r, cellP, 3)
                }
            }
            y += rowH
        }
        val fp = paint(7f, MUTED)
        c.drawText("ATS – Al Arkan Technology Services  •  Generated $generated  •  Page $pageNo", M, h - 16f, fp)
        doc.finishPage(page)
        return bytes(doc)
    }
}
