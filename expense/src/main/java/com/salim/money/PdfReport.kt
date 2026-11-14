package com.salim.money

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Draws a designed A4 report with Android's built-in PdfDocument. */
object PdfReport {
    private const val W = 595
    private const val H = 842
    private const val M = 32f

    private val INDIGO = Color.rgb(0x4F, 0x46, 0xE5)
    private val VIOLET = Color.rgb(0x93, 0x33, 0xEA)
    private val GREEN = Color.rgb(0x10, 0xB9, 0x81)
    private val RED = Color.rgb(0xF4, 0x3F, 0x5E)
    private val INK = Color.rgb(0x11, 0x18, 0x27)
    private val MUTED = Color.rgb(0x6B, 0x72, 0x80)
    private val ZEBRA = Color.rgb(0xF6, 0xF7, 0xFC)
    private val TRACK = Color.rgb(0xED, 0xEF, 0xF5)
    private val LINE = Color.rgb(0xE5, 0xE7, 0xEB)

    private val DATE = DateTimeFormatter.ofPattern("dd MMM", Locale.US)

    private fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    private fun fit(text: String, p: Paint, width: Float): String {
        if (p.measureText(text) <= width) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > width) t = t.dropLast(1)
        return "$t…"
    }

    private fun Canvas.right(text: String, x: Float, y: Float, p: Paint) = drawText(text, x - p.measureText(text), y, p)

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
            c.drawText("${d.title}  •  ${d.periodLabel}", M, H - 18f, p)
            c.right("Generated $generated  •  Page $pageNo", W - M, H - 18f, p)
        }

        fun newPage(first: Boolean) {
            if (pageNo > 0) { footer(); doc.finishPage(page) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page.canvas
            if (first) {
                val band = Paint().apply {
                    shader = LinearGradient(0f, 0f, W.toFloat(), 0f, INDIGO, VIOLET, Shader.TileMode.CLAMP)
                }
                c.drawRect(0f, 0f, W.toFloat(), 96f, band)
                c.drawText(d.title, M, 44f, paint(22f, Color.WHITE, true))
                c.drawText("Income & Expense Report  •  ${d.periodLabel}", M, 66f, paint(11f, Color.argb(220, 255, 255, 255)))
                c.right(d.currency, W - M, 44f, paint(14f, Color.WHITE, true))
                y = 116f
            } else {
                c.drawRect(0f, 0f, W.toFloat(), 6f, fill(INDIGO))
                y = 34f
            }
        }

        fun ensure(h: Float) {
            if (y + h > H - 44f) newPage(false)
        }

        fun section(title: String) {
            ensure(40f)
            y += 8f
            c.drawRoundRect(RectF(M, y, M + 4f, y + 16f), 2f, 2f, fill(INDIGO))
            c.drawText(title, M + 12f, y + 13f, paint(13f, INK, true))
            y += 26f
        }

        newPage(true)

        // ---- summary cards
        val cards = listOf(
            Triple("INCOME", d.money(d.income), GREEN),
            Triple("EXPENSE", d.money(d.expense), RED),
            Triple("SAVINGS", d.money(d.net), if (d.net >= 0) INDIGO else RED),
            Triple("SAVED", if (d.income > 0) "${(d.savingsRate * 100).toInt()}%" else "–", VIOLET),
        )
        val cw = (W - 2 * M - 3 * 10f) / 4
        cards.forEachIndexed { i, (label, value, col) ->
            val x = M + i * (cw + 10f)
            c.drawRoundRect(RectF(x, y, x + cw, y + 56f), 10f, 10f, fill(ZEBRA))
            c.drawRoundRect(RectF(x, y, x + 4f, y + 56f), 2f, 2f, fill(col))
            c.drawText(label, x + 12f, y + 20f, paint(8f, MUTED, true))
            c.drawText(fit(value, paint(12.5f, col, true), cw - 16f), x + 12f, y + 41f, paint(12.5f, col, true))
        }
        y += 70f
        c.drawText(
            "Average spend per day: ${d.money(d.avgPerDay)}   •   ${d.txns.size} entries   •   " +
                "${d.start.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US))} – ${d.end.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US))}",
            M, y, paint(9f, MUTED),
        )
        y += 10f

        // ---- category tables
        fun categoryTable(rows: List<ReportData.Row>, total: Double, color: Int) {
            val p = paint(9.5f, INK)
            val pb = paint(9.5f, INK, true)
            val pm = paint(8.5f, MUTED)
            ensure(22f)
            c.drawRect(M, y, W - M, y + 20f, fill(color))
            val hp = paint(8.5f, Color.WHITE, true)
            c.drawText("CATEGORY", M + 8f, y + 13.5f, hp)
            c.drawText("SHARE", M + 200f, y + 13.5f, hp)
            c.right("ENTRIES", M + 380f, y + 13.5f, hp)
            c.right("BUDGET", W - M - 90f, y + 13.5f, hp)
            c.right("AMOUNT", W - M - 8f, y + 13.5f, hp)
            y += 20f
            rows.forEachIndexed { i, r ->
                ensure(20f)
                if (i % 2 == 1) c.drawRect(M, y, W - M, y + 20f, fill(ZEBRA))
                c.drawCircle(M + 12f, y + 10f, 4f, fill(r.category.color.toInt()))
                c.drawText(fit(r.category.name, p, 160f), M + 22f, y + 13.5f, p)
                val bx = M + 200f
                c.drawRoundRect(RectF(bx, y + 7f, bx + 110f, y + 13f), 3f, 3f, fill(TRACK))
                c.drawRoundRect(RectF(bx, y + 7f, bx + 110f * r.share.toFloat().coerceIn(0f, 1f), y + 13f), 3f, 3f, fill(r.category.color.toInt()))
                c.drawText("${(r.share * 100).toInt()}%", bx + 116f, y + 13.5f, pm)
                c.right(r.count.toString(), M + 380f, y + 13.5f, p)
                r.budget?.let {
                    c.right(d.money(it), W - M - 90f, y + 13.5f, paint(8.5f, if (r.amount > it) RED else GREEN, true))
                }
                c.right(d.money(r.amount), W - M - 8f, y + 13.5f, pb)
                y += 20f
            }
            ensure(22f)
            c.drawLine(M, y, W - M, y, Paint().apply { this.color = color; strokeWidth = 1.2f })
            c.drawText("Total", M + 22f, y + 15f, pb)
            c.right(d.money(total), W - M - 8f, y + 15f, paint(10f, color, true))
            y += 26f
        }

        if (d.expenseRows.isNotEmpty()) {
            section("Expenses by category")
            categoryTable(d.expenseRows, d.expense, RED)
        }
        if (d.incomeRows.isNotEmpty()) {
            section("Income sources")
            categoryTable(d.incomeRows, d.income, GREEN)
        }

        // ---- month by month (year report)
        val months = d.months
        if (months.size > 1) {
            section("Month by month")
            val p = paint(9.5f, INK)
            ensure(20f)
            c.drawRect(M, y, W - M, y + 20f, fill(INDIGO))
            val hp = paint(8.5f, Color.WHITE, true)
            c.drawText("MONTH", M + 8f, y + 13.5f, hp)
            c.right("INCOME", M + 260f, y + 13.5f, hp)
            c.right("EXPENSE", M + 390f, y + 13.5f, hp)
            c.right("SAVINGS", W - M - 8f, y + 13.5f, hp)
            y += 20f
            months.forEachIndexed { i, (mm, inc, exp) ->
                ensure(18f)
                if (i % 2 == 1) c.drawRect(M, y, W - M, y + 18f, fill(ZEBRA))
                c.drawText(mm.format(MONTH), M + 8f, y + 12.5f, p)
                c.right(d.money(inc), M + 260f, y + 12.5f, paint(9.5f, GREEN))
                c.right(d.money(exp), M + 390f, y + 12.5f, paint(9.5f, RED))
                c.right(d.money(inc - exp), W - M - 8f, y + 12.5f, paint(9.5f, if (inc - exp >= 0) INK else RED, true))
                y += 18f
            }
            y += 8f
        }

        // ---- all transactions
        if (d.txns.isNotEmpty()) {
            section("All entries")
            val p = paint(9f, INK)
            val pm = paint(8.5f, MUTED)
            fun head() {
                c.drawRect(M, y, W - M, y + 20f, fill(INK))
                val hp = paint(8.5f, Color.WHITE, true)
                c.drawText("DATE", M + 8f, y + 13.5f, hp)
                c.drawText("CATEGORY", M + 62f, y + 13.5f, hp)
                c.drawText("NOTE", M + 180f, y + 13.5f, hp)
                c.drawText("PAID WITH", M + 380f, y + 13.5f, hp)
                c.right("AMOUNT", W - M - 8f, y + 13.5f, hp)
                y += 20f
            }
            ensure(40f)
            head()
            d.txns.forEachIndexed { i, t ->
                if (y + 18f > H - 44f) { newPage(false); head() }
                if (i % 2 == 1) c.drawRect(M, y, W - M, y + 18f, fill(ZEBRA))
                val cat = d.cat(t.categoryId)
                c.drawText(t.date.format(DATE), M + 8f, y + 12.5f, pm)
                c.drawCircle(M + 66f, y + 9f, 3f, fill(cat.color.toInt()))
                c.drawText(fit(cat.name, p, 104f), M + 74f, y + 12.5f, p)
                c.drawText(fit(t.note, pm, 190f), M + 180f, y + 12.5f, pm)
                c.drawText(fit(t.method, pm, 70f), M + 380f, y + 12.5f, pm)
                val inc = t.kind == Kind.INCOME
                c.right((if (inc) "+" else "-") + d.money(t.amount), W - M - 8f, y + 12.5f, paint(9f, if (inc) GREEN else RED, true))
                y += 18f
            }
        } else {
            section("All entries")
            c.drawText("No entries in this period.", M, y + 10f, paint(10f, MUTED))
        }

        footer()
        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }
}
