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

package de.tadris.fitness.ui.statistics.components

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.renderer.CombinedChartRenderer
import de.tadris.fitness.stats.aggregation.AggregationSpan
import de.tadris.fitness.ui.charts.ChartStyles

@Composable
fun HorizontalBarChartComponent(
    barData: BarData?,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onChartInit: (HorizontalBarChart) -> Unit = {}
) {
    val textColorInt = textColor.toArgb()
    AndroidView(
        factory = { context ->
            HorizontalBarChart(context).apply {
                ChartStyles.defaultBarChart(this)
                onChartInit(this)
            }
        },
        modifier = modifier,
        update = { chart ->
            chart.description.textColor = textColorInt
            chart.legend.textColor = textColorInt
            if (barData != null) {
                barData.setValueTextColor(textColorInt)
                ChartStyles.horizontalBarChartIconLabel(chart, barData, chart.context)
            } else {
                chart.clear()
                ChartStyles.barChartNoData(chart, chart.context)
            }
            chart.invalidate()
        }
    )
}

@Composable
fun CombinedChartComponent(
    combinedData: CombinedData?,
    aggregationSpan: AggregationSpan,
    modifier: Modifier = Modifier,
    yLabel: String? = null,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onChartInit: (CombinedChart) -> Unit = {},
    onDataApplied: (CombinedChart) -> Unit = {},
) {
    val textColorInt = textColor.toArgb()
    AndroidView(
        factory = { context ->
            CombinedChart(context).apply {
                ChartStyles.defaultLineChart(this, context)
                onChartInit(this)
            }
        },
        modifier = modifier,
        update = { chart ->
            chart.description.textColor = textColorInt
            chart.legend.textColor = textColorInt
            chart.xAxis.textColor = textColorInt
            chart.axisLeft.textColor = textColorInt
            
            // Critical: clear and reset renderers to avoid NullPointerException when switching data types (e.g. BarData to null)
            chart.clear()
            (chart.renderer as? CombinedChartRenderer)?.createRenderers()

            if (combinedData != null) {
                combinedData.setValueTextColor(textColorInt)
                // Set before updating the data so the value marker picks up the unit
                if (yLabel != null) {
                    ChartStyles.setYAxisLabel(chart, yLabel, chart.context)
                    chart.legend.textColor = textColorInt
                }
                ChartStyles.updateStatsHistoryCombinedChartToSpan(
                    chart,
                    combinedData,
                    aggregationSpan,
                    chart.context as Activity
                )
                onDataApplied(chart)
            }
            chart.invalidate()
        }
    )
}
