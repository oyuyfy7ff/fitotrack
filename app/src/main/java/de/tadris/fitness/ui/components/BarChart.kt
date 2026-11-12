/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack
 *
 * FitoTrack is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     FitoTrack is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.tadris.fitness.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tadris.fitness.ui.theme.FitoTrackTheme
import java.text.NumberFormat

data class BarChartData(
    val entries: List<Int>,
    val labels: List<String>,
    val horizontal: Int? = null
)

@Composable
fun BarChart(
    data: BarChartData,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    formatter: (Int) -> String = remember { NumberFormat.getNumberInstance()::format },
) {
    val (entries, labels, horizontal) = data
    val barColor = MaterialTheme.colorScheme.primary
    val barSelectedColor = MaterialTheme.colorScheme.primary.darker()
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val pillColor = MaterialTheme.colorScheme.surfaceVariant
    val pillTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    val maxValue by remember(entries, horizontal) {
        val entriesMax = entries.maxOrNull() ?: 0
        mutableFloatStateOf(maxOf(entriesMax, horizontal ?: 0).coerceAtLeast(1).toFloat())
    }
    var progress by remember { mutableFloatStateOf(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) progress = 1f
    }
    val shownProgress by animateFloatAsState(
        progress,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        label = "barProgress",
    )

    var selectedIndex by remember(entries) { mutableIntStateOf(-1) }

    Canvas(
        modifier = modifier.pointerInput(entries) {
            awaitEachGesture {
                if (entries.isEmpty()) return@awaitEachGesture
                val slotWidth = size.width / entries.size.toFloat()
                fun xToIndex(x: Float) = (x / slotWidth).toInt().coerceIn(0, entries.size - 1)

                val down = awaitFirstDown()
                selectedIndex = xToIndex(down.position.x)

                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                    val position = event.changes.firstOrNull()?.position ?: continue
                    selectedIndex = xToIndex(position.x)
                } while (event.changes.any { it.pressed })

                selectedIndex = -1
            }
        },
    ) {
        val labelAreaHeight = if (labels.isNotEmpty()) 20.dp.toPx() else 0f
        val barAreaHeight = size.height - labelAreaHeight

        if (entries.isNotEmpty()) {
            val slotWidth = size.width / entries.size
            val gap = slotWidth * 0.15f
            val barWidth = slotWidth - gap

            val spread = 4f
            entries.forEachIndexed { index, value ->
                val barProgress =
                    ((shownProgress * (entries.size + spread - 1) - index) / spread).coerceIn(
                        0f, 1f
                    )
                val barHeight = (value.toFloat() / maxValue) * barAreaHeight * barProgress
                if (barHeight <= 0f) return@forEachIndexed

                val left = index * slotWidth + gap / 2
                val top = barAreaHeight - barHeight
                val radius = minOf(8.dp.toPx(), minOf(barWidth / 2, barHeight / 2))

                drawRoundRect(
                    color = if (index == selectedIndex) barSelectedColor else barColor,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }

            // Horizontal reference line
            if (horizontal != null) {
                val lineY = barAreaHeight - (horizontal.toFloat() / maxValue) * barAreaHeight
                val dashPx = 6.dp.toPx()
                drawLine(
                    color = labelColor,
                    start = Offset(0f, lineY),
                    end = Offset(size.width, lineY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx * 0.67f)),
                )
                val hText = formatter(horizontal)
                val hStyle = labelStyle.copy(color = labelColor, fontWeight = FontWeight.SemiBold)
                val hMeasured = textMeasurer.measure(hText, style = hStyle)
                val hX = size.width - hMeasured.size.width
                val hY = (lineY - hMeasured.size.height - 2.dp.toPx()).coerceAtLeast(0f)
                drawText(
                    textMeasurer = textMeasurer,
                    text = hText,
                    topLeft = Offset(hX, hY),
                    style = hStyle,
                )
            }

            // Tooltip pill for selected bar
            if (selectedIndex in entries.indices && entries[selectedIndex] > 0) {
                val value = entries[selectedIndex]
                val pillText = formatter(value)
                val pillStyle = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = pillTextColor,
                )
                val measured = textMeasurer.measure(pillText, style = pillStyle)
                val pillPaddingH = 6.dp.toPx()
                val pillPaddingV = 3.dp.toPx()
                val pillW = measured.size.width + pillPaddingH * 2
                val pillH = measured.size.height + pillPaddingV * 2

                val barProgress =
                    ((shownProgress * (entries.size + spread - 1) - selectedIndex) / spread)
                        .coerceIn(0f, 1f)
                val barHeight = (value.toFloat() / maxValue) * barAreaHeight * barProgress
                val barCenterX = selectedIndex * slotWidth + slotWidth / 2

                val pillLeft = (barCenterX - pillW / 2).coerceIn(0f, size.width - pillW)
                val barTop = barAreaHeight - barHeight
                val pillTop = (barTop - pillH - 4.dp.toPx()).coerceAtLeast(0f)

                drawRoundRect(
                    color = pillColor,
                    topLeft = Offset(pillLeft, pillTop),
                    size = Size(pillW, pillH),
                    cornerRadius = CornerRadius(pillH / 2, pillH / 2),
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = pillText,
                    topLeft = Offset(pillLeft + pillPaddingH, pillTop + pillPaddingV),
                    style = pillStyle,
                )
            }
        }

        val slotWidth = if (entries.isNotEmpty()) size.width / entries.size else 0f
        val labelPadding = slotWidth / 2f
        val labelSpanWidth = size.width - 2 * labelPadding

        labels.forEachIndexed { index, label ->
            val measured = textMeasurer.measure(label, style = labelStyle)
            val fraction = if (labels.size == 1) 0f else index.toFloat() / (labels.size - 1)
            val xCenter = labelPadding + fraction * labelSpanWidth
            val x = (xCenter - measured.size.width / 2f)
                .coerceIn(0f, size.width - measured.size.width)

            drawText(
                textMeasurer = textMeasurer,
                text = label,
                topLeft = Offset(x, barAreaHeight + 4.dp.toPx()),
                style = labelStyle.copy(color = labelColor),
            )
        }
    }
}

private fun Color.darker(factor: Float = 0.75f) =
    copy(red = red * factor, green = green * factor, blue = blue * factor)

@Preview(showBackground = true, name = "Daily (15 min windows)")
@Composable
private fun BarChartDayPreview() {
    val entries = buildList {
        repeat(96) { i ->
            // rough bell-curve around midday
            val hour = i / 4f
            val v = ((-(hour - 12) * (hour - 12) + 100) * 30).toInt().coerceAtLeast(0)
            add(v)
        }
    }
    FitoTrackTheme {
        BarChart(
            data = BarChartData(entries, listOf("0", "6", "12", "18", "24")),
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )
    }
}

@Preview(showBackground = true, name = "Monthly (one bar per day)")
@Composable
private fun BarChartMonthPreview() {
    val entries = listOf(
        4200, 8100, 11300, 6700, 9500, 3200, 7800,
        10200, 5600, 12400, 8900, 4100, 9800, 7300,
        6100, 11800, 3500, 8400, 10700, 5900, 7200,
        9100, 4800, 11500, 6300, 8700, 10000, 5200,
        7600, 9300,
    )
    FitoTrackTheme {
        BarChart(
            data = BarChartData(entries, listOf("1", "8", "15", "22", "30"), horizontal = 10_000),
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )
    }
}

@Preview(showBackground = true, name = "Weekly")
@Composable
private fun BarChartWeekPreview() {
    val entries = listOf(
        4200, 8100, 11300, 6700, 9500, 3200, 7800,
    )
    FitoTrackTheme {
        BarChart(
            data = BarChartData(entries, listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")),
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )
    }
}
