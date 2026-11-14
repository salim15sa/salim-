@file:OptIn(ExperimentalMaterial3Api::class)

package com.salim.money

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

// ------------------------------------------------------------------ icons

private fun icon(name: String, path: String, evenOdd: Boolean = false): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = addPathNodes(path),
        fill = SolidColor(Color.Black),
        pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
    ).build()

object AppIcons {
    val Home = icon("home", "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z")
    val Receipt = icon(
        "receipt",
        "M18 17H6v-2h12v2zm0-4H6v-2h12v2zm0-4H6V7h12v2zM3 22l1.5-1.5L6 22l1.5-1.5L9 22l1.5-1.5L12 22l1.5-1.5L15 22l1.5-1.5L18 22l1.5-1.5L21 22V2l-1.5 1.5L18 2l-1.5 1.5L15 2l-1.5 1.5L12 2l-1.5 1.5L9 2 7.5 3.5 6 2 4.5 3.5 3 2v20z",
    )
    val Target = icon(
        "target",
        "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20zM12 5a7 7 0 1 1 0 14a7 7 0 1 1 0-14zM12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM12 10.5a1.5 1.5 0 1 1 0 3a1.5 1.5 0 1 1 0-3z",
        evenOdd = true,
    )
    val Pie = icon(
        "pie",
        "M11 2v20c-5.07-.5-9-4.79-9-10s3.93-9.5 9-10zm2.03 0v8.99H22c-.47-4.74-4.24-8.52-8.97-8.99zm0 11.01V22c4.74-.47 8.5-4.25 8.97-8.99h-8.97z",
    )
    val Swap = icon("swap", "M6.99 11L3 15l3.99 4v-3H14v-2H6.99v-3zM21 9l-3.99-4v3H10v2h7.01v3L21 9z")
    val People = icon(
        "people",
        "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z",
    )
    val Wallet = icon(
        "wallet",
        "M21 18v1c0 1.1-.9 2-2 2H5c-1.11 0-2-.9-2-2V5c0-1.1.89-2 2-2h14c1.1 0 2 .9 2 2v1h-9c-1.11 0-2 .9-2 2v8c0 1.1.89 2 2 2h9zm-9-2h10V8H12v8zm4-2.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z",
    )
}

// ------------------------------------------------------------------ building blocks

val HeaderBrush = Brush.horizontalGradient(listOf(Night, Color(0xFF1E1B4B), Color(0xFF312E81)))

/** The bank-card style gradient used for the balance card. */
val CardBrush = Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFFC026D3)))

@Composable
fun WhiteCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val m = modifier.fillMaxWidth()
    val colors = CardDefaults.cardColors(containerColor = Color.White)
    val elev = CardDefaults.cardElevation(defaultElevation = 1.dp)
    val shape = RoundedCornerShape(20.dp)
    if (onClick != null) Card(onClick = onClick, modifier = m, colors = colors, elevation = elev, shape = shape) { content() }
    else Card(modifier = m, colors = colors, elevation = elev, shape = shape) { content() }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Round coloured badge with the category emoji. */
@Composable
fun CategoryBadge(c: Category, size: Dp = 44.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(c.col().copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(c.emoji, fontSize = (size.value * 0.48f).sp)
    }
}

@Composable
fun MonthSwitcher(month: YearMonth, onChange: (YearMonth) -> Unit, dark: Boolean = true) {
    val fg = if (dark) Color.White else Ink
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) {
            Icon(Icons.Filled.KeyboardArrowLeft, "Previous month", tint = fg)
        }
        Text(
            month.format(MONTH),
            color = fg, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onChange(YearMonth.now()) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        IconButton(onClick = { onChange(month.plusMonths(1)) }) {
            Icon(Icons.Filled.KeyboardArrowRight, "Next month", tint = fg)
        }
    }
}

/** Rounded progress bar; turns amber at 80% and red when over. */
@Composable
fun BudgetBar(ratio: Double, modifier: Modifier = Modifier, height: Dp = 10.dp, marker: Double? = null) {
    val color = when {
        ratio >= 1.0 -> ExpenseRed
        ratio >= 0.8 -> WarnAmber
        else -> IncomeGreen
    }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(Color(0xFFEDEFF5), cornerRadius = r)
        val w = size.width * ratio.coerceIn(0.0, 1.0).toFloat()
        if (w > 0f) drawRoundRect(color, size = Size(maxOf(w, size.height), size.height), cornerRadius = r)
        marker?.let {
            val x = size.width * it.coerceIn(0.0, 1.0).toFloat()
            drawLine(Ink.copy(alpha = 0.55f), Offset(x, -2f), Offset(x, size.height + 2f), strokeWidth = 3f)
        }
    }
}

/** Donut chart with an optional label in the middle. */
@Composable
fun Donut(
    slices: List<Pair<Color, Double>>,
    modifier: Modifier = Modifier,
    thickness: Dp = 22.dp,
    center: @Composable () -> Unit = {},
) {
    val total = slices.sumOf { it.second }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSizeSafe()) {
            val stroke = thickness.toPx()
            val d = minOf(size.width, size.height) - stroke
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            if (total <= 0.0) {
                drawArc(Color(0xFFEDEFF5), 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
                return@Canvas
            }
            var start = -90f
            val gap = if (slices.size > 1) 1.5f else 0f
            slices.forEach { (c, v) ->
                val sweep = (v / total * 360f).toFloat()
                drawArc(
                    c, start + gap / 2, maxOf(0.5f, sweep - gap), false, topLeft, Size(d, d),
                    style = Stroke(stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        center()
    }
}

private fun Modifier.matchParentSizeSafe(): Modifier = this.fillMaxWidth().fillMaxHeight()

/** Paired income/expense bars for several months. */
@Composable
fun IncomeExpenseBars(
    data: List<Triple<String, Double, Double>>,
    modifier: Modifier = Modifier,
    highlight: Int = -1,
) {
    val max = data.maxOfOrNull { maxOf(it.second, it.third) }?.takeIf { it > 0 } ?: 1.0
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = data.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = minOf(slot * 0.28f, 18.dp.toPx())
            for (i in 1..3) {
                val y = size.height * i / 4
                drawLine(Line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            data.forEachIndexed { i, (_, inc, exp) ->
                val cx = slot * i + slot / 2
                val alpha = if (highlight < 0 || highlight == i) 1f else 0.45f
                val hi = (inc / max * size.height).toFloat()
                val he = (exp / max * size.height).toFloat()
                val r = CornerRadius(bw / 3, bw / 3)
                if (hi > 0) drawRoundRect(
                    IncomeGreen.copy(alpha = alpha), Offset(cx - bw - 2f, size.height - hi), Size(bw, hi), r,
                )
                if (he > 0) drawRoundRect(
                    ExpenseRed.copy(alpha = alpha), Offset(cx + 2f, size.height - he), Size(bw, he), r,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            data.forEachIndexed { i, (label, _, _) ->
                Text(
                    label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp,
                    color = if (i == highlight) Ink else Muted,
                    fontWeight = if (i == highlight) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/** Daily spending bars for one month, with an optional "daily budget" line. */
@Composable
fun DailyBars(values: List<Double>, modifier: Modifier = Modifier, limit: Double? = null, today: Int = -1) {
    val max = (values.maxOrNull() ?: 0.0).let { m -> maxOf(m, limit ?: 0.0) }.takeIf { it > 0 } ?: 1.0
    Canvas(modifier.fillMaxWidth().height(110.dp)) {
        val n = values.size.coerceAtLeast(1)
        val slot = size.width / n
        val bw = slot * 0.62f
        values.forEachIndexed { i, v ->
            val h = (v / max * size.height).toFloat()
            val over = limit != null && v > limit
            val c = when {
                over -> ExpenseRed
                i == today -> Violet
                else -> Indigo.copy(alpha = 0.75f)
            }
            if (h > 0) drawRoundRect(
                c, Offset(slot * i + (slot - bw) / 2, size.height - h), Size(bw, h), CornerRadius(bw / 3, bw / 3),
            )
        }
        limit?.let {
            val y = size.height - (it / max * size.height).toFloat()
            drawLine(
                WarnAmber, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
            )
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = Muted)
    }
}

/** One transaction line. */
@Composable
fun TxnRow(state: AppState, t: Txn, onClick: () -> Unit) {
    val c = state.category(t.categoryId)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(c)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOf(t.note, t.method).filter { it.isNotBlank() }.joinToString(" • ")
            if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (t.isClaim) {
                val paid = t.reimbursedOn != null
                Text(
                    if (paid) "🏢 Company paid back" else "🏢 Company to pay back",
                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = if (paid) IncomeGreen else Sky,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .clip(RoundedCornerShape(50))
                        .background((if (paid) IncomeGreen else Sky).copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            (if (t.kind == Kind.INCOME) "+" else "-") + state.money(t.amount),
            color = when {
                t.kind == Kind.INCOME -> IncomeGreen
                t.isClaim -> Sky
                else -> ExpenseRed
            },
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Round avatar with the person's initials and a colour derived from the name. */
@Composable
fun PersonAvatar(name: String, size: Dp = 44.dp) {
    val colors = listOf(Indigo, Teal, Sky, Violet, Color(0xFFDB2777), Color(0xFFEA580C), Color(0xFF65A30D), Color(0xFF0891B2))
    val c = colors[(name.lowercase().hashCode() and 0x7fffffff) % colors.size]
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.7f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
    }
}

@Composable
fun EmptyHint(emoji: String, title: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(emoji, fontSize = 44.sp)
        Text(title, fontWeight = FontWeight.Bold, color = Ink, fontSize = 16.sp)
        Text(text, color = Muted, textAlign = TextAlign.Center, fontSize = 13.sp)
    }
}

/**
 * A full-screen page drawn inside the app window (instead of a separate dialog
 * window), so the status bar, navigation bar and keyboard insets are always right.
 */
@Composable
fun FullScreen(onClose: () -> Unit, content: @Composable () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(Paper)
            // swallow taps so nothing underneath reacts
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) { content() }
}

@Composable
fun DatePick(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val st = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let {
                    onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(st) }
}
