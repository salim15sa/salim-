package com.salim.taxilog

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Draws a designed A4 report with Android's built-in PdfDocument. */
object PdfExporter {
    private const val W = 595
    private const val H = 842
    private const val M = 32f

    private val NAVY = Color.rgb(0x1F, 0x3A, 0x5F)
    private val AMBER = Color.rgb(0xF5, 0x9E, 0x0B)
    private val INK = Color.rgb(0x1F, 0x29, 0x37)
    private val MUTED = Color.rgb(0x64, 0x74, 0x8B)
    private val ZEBRA = Color.rgb(0xF3, 0xF6, 0xFA)
    private val CARD = Color.rgb(0xE8, 0xF0, 0xFE)
    private val DAY = Color.rgb(0xFF, 0xF4, 0xE5)
    private val LINE = Color.rgb(0xD0, 0xD7, 0xE2)

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

    private fun fit(text: String, p: Paint, width: Float): String {
        if (p.measureText(text) <= width) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > width) t = t.dropLast(1)
        return "$t…"
    }

    private class Col(val title: String, val width: Float, val right: Boolean = false, val center: Boolean = false)

    private val cols = listOf(
        Col("Date", 62f, center = true),
        Col("Site", 90f),
        Col("From", 80f),
        Col("To", 80f),
        Col("Amount", 64f, right = true),
        Col("Personnel", 87f),
        Col("Paid By", 68f, center = true),
    )

    fun build(d: ReportData): ByteArray {
        val doc = PdfDocument()
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f
        val generated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.US))

        fun footer() {
            val p = paint(7.5f, MUTED)
            c.drawLine(M, H - 30f, W - M, H - 30f, Paint().apply { color = LINE })
            c.drawText("${d.title}  •  ${d.rangeLabel}", M, H - 18f, p)
            val right = "Generated $generated  •  Page $pageNo"
            c.drawText(right, W - M - p.measureText(right), H - 18f, p)
        }

        fun newPage(first: Boolean) {
            if (pageNo > 0) {
                footer()
                doc.finishPage(page)
            }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page.canvas
            if (first) {
                c.drawRect(0f, 0f, W.toFloat(), 104f, fillPaint(NAVY))
                c.drawRect(0f, 104f, W.toFloat(), 108f, fillPaint(AMBER))
                c.drawText(d.title, M, 46f, paint(21f, Color.WHITE, bold = true))
                c.drawText("Operational Field Log & Reimbursement Summary", M, 66f, paint(10f, Color.rgb(0xC7, 0xD2, 0xE0)))
                c.drawText(d.rangeLabel, M, 86f, paint(10.5f, AMBER, bold = true))
                y = 126f
            } else {
                c.drawRect(0f, 0f, W.toFloat(), 6f, fillPaint(NAVY))
                c.drawText("${d.title} (continued)", M, 30f, paint(10f, NAVY, bold = true))
                y = 44f
            }
        }

        fun ensure(h: Float, redrawHeader: (() -> Unit)? = null) {
            if (y + h > H - 42f) {
                newPage(false)
                redrawHeader?.invoke()
            }
        }

        fun cards(items: List<Triple<String, String, Int>>, perRow: Int) {
            val gap = 10f
            val cw = (W - 2 * M - gap * (perRow - 1)) / perRow
            items.chunked(perRow).forEach { row ->
                ensure(60f)
                row.forEachIndexed { i, (label, value, accent) ->
                    val x = M + i * (cw + gap)
                    c.drawRoundRect(RectF(x, y, x + cw, y + 54f), 8f, 8f, fillPaint(CARD))
                    c.drawRoundRect(RectF(x, y, x + 4f, y + 54f), 2f, 2f, fillPaint(accent))
                    c.drawText(fit(label, paint(7.5f, MUTED, bold = true), cw - 18f), x + 12f, y + 19f, paint(7.5f, MUTED, bold = true))
                    c.drawText(fit(value, paint(15f, NAVY, bold = true), cw - 18f), x + 12f, y + 41f, paint(15f, NAVY, bold = true))
                }
                y += 64f
            }
        }

        fun sectionTitle(t: String) {
            ensure(40f)
            y += 6f
            c.drawText(t, M, y + 12f, paint(11.5f, NAVY, bold = true))
            c.drawRect(M, y + 17f, M + 36f, y + 19.5f, fillPaint(AMBER))
            y += 28f
        }

        // ---------------- page 1: cards
        newPage(true)
        val summaryCards = mutableListOf(Triple("TOTAL EXPENSES", omr(d.total), AMBER))
        d.paidBy.forEach { (p, v) -> summaryCards += Triple("${p.uppercase(Locale.US)} (PAID)", omr(v), NAVY) }
        summaryCards += Triple("TOTAL TRIP LEGS", d.legs.toString(), NAVY)
        summaryCards += Triple("SITES VISITED", d.sites.toString(), NAVY)
        summaryCards += Triple("DAYS", d.days.toString(), NAVY)
        cards(summaryCards, 4)

        // ---------------- trip table
        sectionTitle("Trip log")
        val rowH = 18f
        val tableW = cols.sumOf { it.width.toDouble() }.toFloat()
        val scale = (W - 2 * M) / tableW
        val headP = paint(8f, Color.WHITE, bold = true)
        val cellP = paint(8f, INK)
        val inclP = paint(7.5f, MUTED, italic = true)
        val boldP = paint(8f, INK, bold = true)

        fun drawCells(values: List<String>, p: (Int) -> Paint) {
            var x = M
            cols.forEachIndexed { i, col ->
                val w = col.width * scale
                val pp = p(i)
                val t = fit(values[i], pp, w - 8f)
                val tx = when {
                    col.right -> x + w - 5f - pp.measureText(t)
                    col.center -> x + (w - pp.measureText(t)) / 2
                    else -> x + 5f
                }
                c.drawText(t, tx, y + rowH - 5.5f, pp)
                x += w
            }
        }

        fun tableHeader() {
            c.drawRoundRect(RectF(M, y, W - M, y + 20f), 4f, 4f, fillPaint(NAVY))
            drawCells(cols.map { it.title }) { headP }
            y += 20f
        }

        tableHeader()
        val linePaint = Paint().apply { color = LINE; strokeWidth = 0.6f }
        d.byDay.forEachIndexed { di, (date, list) ->
            list.forEach { t ->
                ensure(rowH) { tableHeader() }
                if (di % 2 == 1) c.drawRect(M, y, W - M, y + rowH, fillPaint(ZEBRA))
                drawCells(
                    listOf(
                        t.date.format(ISO), t.site, t.from, t.to,
                        t.amount?.let { num3(it) } ?: "Included",
                        t.personnel.joinToString(" & "),
                        if (t.amount != null) t.paidBy else "",
                    ),
                ) { i -> if (i == 4 && t.amount == null) inclP else cellP }
                c.drawLine(M, y + rowH, W - M, y + rowH, linePaint)
                y += rowH
            }
            ensure(rowH) { tableHeader() }
            c.drawRect(M, y, W - M, y + rowH, fillPaint(DAY))
            val label = "${date.format(NICE_DATE)}  —  day total (${list.size} legs)"
            c.drawText(fit(label, boldP, 300f), M + 6f, y + rowH - 5.5f, boldP)
            val amtText = omr(list.sumOf { it.amount ?: 0.0 })
            val amtRight = M + cols.take(5).sumOf { it.width.toDouble() }.toFloat() * scale - 5f
            c.drawText(amtText, amtRight - boldP.measureText(amtText), y + rowH - 5.5f, boldP)
            y += rowH + 2f
        }
        ensure(26f) { tableHeader() }
        c.drawRoundRect(RectF(M, y, W - M, y + 24f), 4f, 4f, fillPaint(NAVY))
        val gp = paint(10f, Color.WHITE, bold = true)
        c.drawText("GRAND TOTAL  •  ${d.legs} legs  •  ${d.days} days", M + 8f, y + 16f, gp)
        val gt = omr(d.total)
        c.drawText(gt, W - M - 8f - gp.measureText(gt), y + 16f, paint(10f, AMBER, bold = true))
        y += 34f

        // ---------------- who paid
        if (d.paidBy.isNotEmpty()) {
            sectionTitle("Who paid")
            val barMax = W - 2 * M - 200f
            d.paidBy.forEach { (p, v) ->
                ensure(22f)
                c.drawText(fit(p, boldP, 90f), M, y + 12f, boldP)
                val frac = if (d.total > 0) (v / d.total).toFloat() else 0f
                c.drawRoundRect(RectF(M + 95f, y + 3f, M + 95f + barMax, y + 15f), 6f, 6f, fillPaint(ZEBRA))
                c.drawRoundRect(RectF(M + 95f, y + 3f, M + 95f + barMax * frac, y + 15f), 6f, 6f, fillPaint(NAVY))
                val txt = "${omr(v)}  (${String.format(Locale.US, "%.0f", frac * 100)}%)"
                c.drawText(txt, W - M - cellP.measureText(txt), y + 12f, cellP)
                y += 22f
            }
        }

        // ---------------- by site
        if (d.bySite.isNotEmpty()) {
            sectionTitle("By site")
            d.bySite.forEachIndexed { i, (site, legs, amt) ->
                ensure(rowH)
                if (i % 2 == 1) c.drawRect(M, y, W - M, y + rowH, fillPaint(ZEBRA))
                c.drawText(fit(site, cellP, 250f), M + 6f, y + rowH - 5.5f, cellP)
                val l = "$legs legs"
                c.drawText(l, M + 300f, y + rowH - 5.5f, paint(8f, MUTED))
                val a = omr(amt)
                c.drawText(a, W - M - 6f - boldP.measureText(a), y + rowH - 5.5f, boldP)
                y += rowH
            }
        }

        footer()
        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }
}
