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

package de.tadris.fitness.ui.statistics

import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.CandleData
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import de.tadris.fitness.R
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.stats.NoDataException
import de.tadris.fitness.stats.StatsProvider
import de.tadris.fitness.stats.WorkoutProperty
import de.tadris.fitness.stats.aggregation.AggregationSpan
import de.tadris.fitness.ui.charts.ChartStyles
import de.tadris.fitness.ui.charts.DataSetStyles
import de.tadris.fitness.ui.statistics.components.CombinedChartComponent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailStatsScreen(
    property: WorkoutProperty,
    summed: Boolean,
    types: List<WorkoutType>,
    initialAggregationSpan: AggregationSpan,
    initialXScale: Float,
    initialXTrans: Float,
    yLabel: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val statsProvider = remember { StatsProvider(context) }
    var aggregationSpan by remember { mutableStateOf(initialAggregationSpan) }
    var combinedData by remember { mutableStateOf<CombinedData?>(null) }
    val initialViewportApplied = remember { booleanArrayOf(false) }
    
    val updateChart = {
        combinedData = try {
            val data = CombinedData()
            if (!summed) {
                val candleSet = statsProvider.getCandleData(aggregationSpan, types, property)
                data.setData(CandleData(candleSet))
                data.setData(LineData(DataSetStyles.applyBackgroundLineStyle(context, StatsProvider.convertCandleToMeanLineData(candleSet))))
            } else {
                val barSet = statsProvider.getSumData(aggregationSpan, types, property)
                data.setData(BarData(barSet))
            }
            data
        } catch (_: NoDataException) {
            null
        }
    }

    LaunchedEffect(aggregationSpan) {
        updateChart()
    }

    val typesTitle = remember(types) {
        if (types.isEmpty()) "" else " - " + types.joinToString(", ") { it.title }
    }
    
    val title = if (!summed) {
        combinedData?.candleData?.dataSets?.firstOrNull()?.label ?: stringResource(property.titleRes)
    } else {
        stringResource(R.string.workoutDistanceSum)
    } + typesTitle

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize().padding(16.dp)) {
            CombinedChartComponent(
                combinedData = combinedData,
                aggregationSpan = aggregationSpan,
                modifier = Modifier.fillMaxSize(),
                yLabel = yLabel,
                onChartInit = { chart ->
                    ChartStyles.animateChart(chart)
                    chart.onChartGestureListener = object : OnChartGestureListener {
                        // Re-aggregate the data according to the visible time range
                        private fun aggregate() {
                            val newSpan = ChartStyles.statsAggregationSpan(chart)
                            if (newSpan != aggregationSpan) aggregationSpan = newSpan
                        }
                        override fun onChartGestureStart(me: MotionEvent?, g: ChartTouchListener.ChartGesture?) {}
                        override fun onChartGestureEnd(me: MotionEvent?, g: ChartTouchListener.ChartGesture?) {}
                        override fun onChartLongPressed(me: MotionEvent?) {}
                        override fun onChartDoubleTapped(me: MotionEvent?) = aggregate()
                        override fun onChartSingleTapped(me: MotionEvent?) {}
                        override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, vX: Float, vY: Float) {}
                        override fun onChartScale(me: MotionEvent?, sX: Float, sY: Float) = aggregate()
                        override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {}
                    }
                },
                onDataApplied = { chart ->
                    // Restore the viewport of the history chart once the data is there
                    if (!initialViewportApplied[0]) {
                        initialViewportApplied[0] = true
                        chart.zoom(initialXScale, 1f, 0f, 0f)
                        chart.moveViewToX(initialXTrans)
                    }
                }
            )
        }
    }
}
