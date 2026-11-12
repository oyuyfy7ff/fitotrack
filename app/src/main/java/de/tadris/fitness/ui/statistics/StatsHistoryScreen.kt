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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.MotionEvent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.CandleData
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import de.tadris.fitness.R
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.data.WorkoutTypeManager
import de.tadris.fitness.data.preferences.UserPreferences
import de.tadris.fitness.stats.NoDataException
import de.tadris.fitness.stats.StatsDataProvider
import de.tadris.fitness.stats.StatsProvider
import de.tadris.fitness.stats.WorkoutProperty
import de.tadris.fitness.stats.aggregation.AggregationSpan
import de.tadris.fitness.ui.charts.ChartStyles
import de.tadris.fitness.ui.charts.DataSetStyles
import de.tadris.fitness.ui.components.SectionHeader
import de.tadris.fitness.ui.statistics.components.CombinedChartComponent
import de.tadris.fitness.ui.statistics.components.WorkoutTypeSelector

/** A chart's data together with the unit label for its Y axis. */
private class HistoryChartData(val data: CombinedData, val yLabel: String?)

/** Initial zoom (relative scale) and left edge (x value) of all history charts. */
private class InitialViewport(val zoom: Float, val leftX: Float)

@Composable
fun StatsHistoryScreen() {
    val context = LocalContext.current
    val statsProvider = remember { StatsProvider(context) }
    val preferences = remember { UserPreferences(context) }

    var selectedTypes by remember {
        mutableStateOf(preferences.statisticsSelectedTypes.filterNotNull().ifEmpty {
            WorkoutTypeManager.getInstance().getAllTypes(context)
        })
    }

    // Like the legacy screen: show the span selected on the overview tab, aggregated one level finer.
    val preferredSpan = remember { preferences.statisticsAggregationSpan ?: AggregationSpan.MONTH }
    var aggregationSpan by remember { mutableStateOf(finerAggregationSpan(preferredSpan)) }
    val initialViewport = remember { computeInitialViewport(context, selectedTypes, preferredSpan) }

    // Keeps the x zoom/translation of all charts in sync
    val synchronizer = remember { ChartSynchronizer() }
    val initiallyZoomedCharts = remember { mutableSetOf<CombinedChart>() }

    var speedSwapped by remember { mutableStateOf(false) }
    var distanceSwapped by remember { mutableStateOf(true) }
    var durationSwapped by remember { mutableStateOf(true) }

    var exploreProperty by remember { mutableStateOf(WorkoutProperty.PAUSE_DURATION) }
    var exploreSummed by remember { mutableStateOf(false) }

    val speedProperty = if (speedSwapped) WorkoutProperty.AVG_PACE else WorkoutProperty.AVG_SPEED

    val speedData = remember(selectedTypes, aggregationSpan, speedProperty) {
        loadChartData(context, statsProvider, aggregationSpan, selectedTypes, speedProperty, summed = false)
    }
    val distanceData = remember(selectedTypes, aggregationSpan, distanceSwapped) {
        loadChartData(context, statsProvider, aggregationSpan, selectedTypes, WorkoutProperty.LENGTH, distanceSwapped)
    }
    val durationData = remember(selectedTypes, aggregationSpan, durationSwapped) {
        loadChartData(context, statsProvider, aggregationSpan, selectedTypes, WorkoutProperty.DURATION, durationSwapped)
    }
    val exploreData = remember(selectedTypes, aggregationSpan, exploreProperty, exploreSummed) {
        loadChartData(
            context, statsProvider, aggregationSpan, selectedTypes, exploreProperty,
            summed = exploreSummed && exploreProperty.summable()
        )
    }

    val setupChart = { chart: CombinedChart ->
        chart.isDoubleTapToZoomEnabled = false
        ChartStyles.fixViewPortOffsets(chart, 120f)
        ChartStyles.animateChart(chart)
        chart.onChartGestureListener = OnChartGestureMultiListener(
            arrayListOf(
                synchronizer.addChart(chart),
                HistoryChartGestureListener(
                    onScale = {
                        // Re-aggregate the data according to the visible time range
                        val newSpan = ChartStyles.statsAggregationSpan(chart)
                        if (newSpan != aggregationSpan) aggregationSpan = newSpan
                    }
                )
            )
        )
    }

    val applyInitialViewport = { chart: CombinedChart ->
        if (initialViewport != null && initiallyZoomedCharts.add(chart)) {
            chart.zoom(initialViewport.zoom, 1f, 0f, 0f)
            chart.moveViewToX(initialViewport.leftX)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        WorkoutTypeSelector(
            selectedTypes = selectedTypes,
            onTypesSelected = { types ->
                selectedTypes = types
                preferences.setStatisticsSelectedTypes(ArrayList(selectedTypes))
            },
            modifier = Modifier.fillMaxWidth()
        )

        HistoryChartSection(
            title = stringResource(speedProperty.titleRes),
            onToggle = { speedSwapped = !speedSwapped },
            data = speedData,
            aggregationSpan = aggregationSpan,
            setupChart = setupChart,
            onDataApplied = applyInitialViewport,
        )

        HistoryChartSection(
            title = if (distanceSwapped) stringResource(R.string.workoutDistanceSum) else stringResource(WorkoutProperty.LENGTH.titleRes),
            onToggle = { distanceSwapped = !distanceSwapped },
            data = distanceData,
            aggregationSpan = aggregationSpan,
            setupChart = setupChart,
            onDataApplied = applyInitialViewport,
        )

        HistoryChartSection(
            title = if (durationSwapped) stringResource(R.string.workoutDurationSum) else stringResource(WorkoutProperty.DURATION.titleRes),
            onToggle = { durationSwapped = !durationSwapped },
            data = durationData,
            aggregationSpan = aggregationSpan,
            setupChart = setupChart,
            onDataApplied = applyInitialViewport,
        )

        // Explore Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var expanded by remember { mutableStateOf(false) }
            SectionHeader(
                title = stringResource(exploreProperty.titleRes),
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (exploreProperty.summable()) {
                            IconButton(onClick = { exploreSummed = !exploreSummed }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_swap_vertical),
                                    contentDescription = "Toggle"
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { expanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Select property"
                                )
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }) {
                                WorkoutProperty.entries.forEach { property ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(property.titleRes)) },
                                        onClick = {
                                            exploreProperty = property
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )

            CombinedChartComponent(
                combinedData = exploreData?.data,
                yLabel = exploreData?.yLabel,
                aggregationSpan = aggregationSpan,
                modifier = Modifier.fillMaxWidth().height(250.dp),
                onChartInit = { chart ->
                    setupChart(chart)
                },
                onDataApplied = applyInitialViewport,
            )
        }
    }
}

private fun finerAggregationSpan(span: AggregationSpan): AggregationSpan = when (span) {
    AggregationSpan.ALL -> AggregationSpan.YEAR
    AggregationSpan.YEAR -> AggregationSpan.MONTH
    AggregationSpan.MONTH -> AggregationSpan.WEEK
    else -> AggregationSpan.SINGLE
}

/**
 * Zooms the charts so that the last [span] of recorded workouts is visible,
 * matching the time span selected on the overview tab.
 */
private fun computeInitialViewport(
    context: Context,
    types: List<WorkoutType>,
    span: AggregationSpan,
): InitialViewport? {
    val data = StatsDataProvider(context).getData(WorkoutProperty.LENGTH, types)
    if (data.isEmpty()) return null
    val first = data.first()
    val last = data.last()
    return InitialViewport(
        zoom = (last.time - first.time).toFloat() / span.spanInterval,
        leftX = (last.time - span.spanInterval).toFloat(),
    )
}

private fun loadChartData(
    context: Context,
    statsProvider: StatsProvider,
    span: AggregationSpan,
    types: List<WorkoutType>,
    property: WorkoutProperty,
    summed: Boolean,
): HistoryChartData? = try {
    val data = CombinedData()
    if (summed) {
        data.setData(BarData(statsProvider.getSumData(span, types, property)))
    } else {
        val candleSet = statsProvider.getCandleData(span, types, property)
        data.setData(CandleData(candleSet))
        data.setData(LineData(DataSetStyles.applyBackgroundLineStyle(context, StatsProvider.convertCandleToMeanLineData(candleSet))))
    }
    val yLabel = if (property == WorkoutProperty.START || property == WorkoutProperty.END) {
        null
    } else {
        property.getUnit(context, data.yMax)
    }
    HistoryChartData(data, yLabel)
} catch (_: NoDataException) {
    null
}

private class HistoryChartGestureListener(
    private val onScale: () -> Unit,
) : OnChartGestureListener {
    override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}
    override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}
    override fun onChartLongPressed(me: MotionEvent?) {}
    override fun onChartDoubleTapped(me: MotionEvent?) {}
    override fun onChartSingleTapped(me: MotionEvent?) {}
    override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, vX: Float, vY: Float) {}
    override fun onChartScale(me: MotionEvent?, sX: Float, sY: Float) = onScale()
    override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {}
}

@Composable
private fun HistoryChartSection(
    title: String,
    onToggle: () -> Unit,
    data: HistoryChartData?,
    aggregationSpan: AggregationSpan,
    setupChart: (CombinedChart) -> Unit,
    onDataApplied: (CombinedChart) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = title,
            trailingContent = {
                IconButton(onClick = onToggle) {
                    Icon(
                        painter = painterResource(R.drawable.ic_swap_vertical),
                        contentDescription = "Toggle"
                    )
                }
            }
        )

        CombinedChartComponent(
            combinedData = data?.data,
            yLabel = data?.yLabel,
            aggregationSpan = aggregationSpan,
            modifier = Modifier.fillMaxWidth().height(250.dp),
            onChartInit = { chart -> setupChart(chart) },
            onDataApplied = onDataApplied,
        )
    }
}
