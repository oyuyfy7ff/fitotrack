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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.mikephil.charting.data.BarData
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.stats.NoDataException
import de.tadris.fitness.stats.StatsDataTypes
import de.tadris.fitness.stats.StatsProvider
import de.tadris.fitness.stats.aggregation.AggregationSpan
import de.tadris.fitness.ui.charts.ChartStyles
import de.tadris.fitness.ui.components.SectionHeader
import de.tadris.fitness.ui.statistics.components.HorizontalBarChartComponent
import de.tadris.fitness.ui.statistics.components.TimeSpanSelection

@Composable
fun StatsOverviewScreen() {
    val context = LocalContext.current
    val statsProvider = remember { StatsProvider(context) }
    val instance = remember { Instance.getInstance(context) }

    var distanceData by remember { mutableStateOf<BarData?>(null) }
    var durationData by remember { mutableStateOf<BarData?>(null) }
    var activitiesData by remember { mutableStateOf<BarData?>(null) }

    val updateCharts = { start: Long, span: AggregationSpan ->
        val timeSpan = StatsDataTypes.TimeSpan(start, span.getAggregationEnd(start))
        distanceData = try {
            statsProvider.totalDistances(timeSpan)
        } catch (_: NoDataException) {
            null
        }?.let { BarData(it) }
        
        durationData = try {
            statsProvider.totalDurations(timeSpan)
        } catch (_: NoDataException) {
            null
        }?.let { BarData(it) }
        
        activitiesData = try {
            statsProvider.numberOfActivities(timeSpan)
        } catch (_: NoDataException) {
            null
        }?.let { BarData(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TimeSpanSelection(
            onTimeSpanChanged = { span, start ->
                updateCharts(start, span)
            },
            modifier = Modifier.fillMaxWidth()
        )

        ChartSection(
            title = stringResource(R.string.workoutDistance),
            unit = instance.distanceUnitUtils.distanceUnitSystem.longDistanceUnit,
            data = distanceData
        )

        ChartSection(
            title = stringResource(R.string.workoutDuration),
            unit = stringResource(R.string.timeHourShort),
            data = durationData
        )

        ChartSection(
            title = stringResource(R.string.workoutCount),
            unit = null,
            data = activitiesData
        )
    }
}

@Composable
private fun ChartSection(
    title: String,
    unit: String?,
    data: BarData?
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = title)
        HorizontalBarChartComponent(
            barData = data,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            onChartInit = { chart ->
                if (unit != null) {
                    ChartStyles.setXAxisLabel(chart, unit, chart.context)
                }
            }
        )
    }
}
