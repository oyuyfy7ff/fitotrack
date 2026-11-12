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

package de.tadris.fitness.ui.statistics2

import android.app.Application
import android.app.DatePickerDialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.ui.components.BarChart
import de.tadris.fitness.util.UserDateTimeUtils
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val DAY_MS = 24 * 60 * 60 * 1000L

@Composable
fun StatisticsScreen(
    adapter: StatisticsAdapter,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as Application
    val vm: StatisticsViewModel = viewModel(
        key = adapter::class.java.name,
        factory = StatisticsViewModel.Factory(app, adapter),
    )
    val selectedPeriod by vm.selectedPeriod.collectAsStateWithLifecycle()
    val timeFrameOffset by vm.timeFrameOffset.collectAsStateWithLifecycle()
    val referenceTimestamp by vm.referenceTimestamp.collectAsStateWithLifecycle()
    val chartData by vm.chartData.collectAsStateWithLifecycle()
    val progressData by vm.progressData.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PeriodChipRow(
            selected = selectedPeriod,
            onSelect = { vm.setPeriod(it) },
        )

        TimeFrameNavigationRow(
            period = selectedPeriod,
            referenceTimestamp = referenceTimestamp,
            canShiftForward = timeFrameOffset < 0,
            onShift = { vm.shiftOffset(it) },
            onSetDate = { vm.setReferenceDate(it) },
        )

        StatisticsProgressCard(
            data = progressData,
            period = selectedPeriod,
            formatter = adapter::format,
            modifier = Modifier.fillMaxWidth(),
        )

        key(chartData.entries) {
            BarChart(
                data = chartData,
                animate = true,
                formatter = adapter::format,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )
        }
    }
}

@Composable
private fun StatisticsProgressCard(
    data: StatisticsProgressData,
    period: StatisticsPeriod,
    formatter: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    if (period == StatisticsPeriod.YEAR) {
        Card(modifier = modifier) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.stepProgressTotal),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatter(data.total),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        return
    }

    val progress = if (data.dailyGoal > 0) {
        (data.dailyAverage.toFloat() / data.dailyGoal).coerceIn(0f, 1f)
    } else 0f
    val pct = (progress * 100).toInt()
    val displayedProgress by animateFloatAsState(
        progress,
        ProgressIndicatorDefaults.ProgressAnimationSpec
    )

    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    if (period != StatisticsPeriod.DAY) {
                        Text(
                            text = stringResource(R.string.stepProgressAvgPerDay),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${formatter(data.dailyAverage)} / ${formatter(data.dailyGoal)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LinearProgressIndicator(
                progress = { displayedProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            if (period != StatisticsPeriod.DAY) {
                Text(
                    text = "${stringResource(R.string.stepProgressTotal)}: ${formatter(data.total)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PeriodChipRow(
    selected: StatisticsPeriod,
    onSelect: (StatisticsPeriod) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatisticsPeriod.entries.forEach { period ->
            FilterChip(
                selected = selected == period,
                onClick = { onSelect(period) },
                label = { Text(stringResource(period.labelRes)) },
            )
        }
    }
}

@Composable
private fun TimeFrameNavigationRow(
    period: StatisticsPeriod,
    referenceTimestamp: Long,
    canShiftForward: Boolean,
    onShift: (Int) -> Unit,
    onSetDate: (Long) -> Unit,
) {
    val context = LocalContext.current
    val dateTimeUtils =
        remember { UserDateTimeUtils(Instance.getInstance(context).userPreferences) }

    val todayStr = stringResource(R.string.today)
    val yesterdayStr = stringResource(R.string.yesterday)
    val weekLabelTemplate = stringResource(R.string.weekNumberLabel)

    val todayMidnight = remember { StatisticsViewModel.todayMidnight() }

    val label = remember(period, referenceTimestamp) {
        when (period) {
            StatisticsPeriod.DAY -> when (referenceTimestamp) {
                todayMidnight -> todayStr
                todayMidnight - DAY_MS -> yesterdayStr
                else -> dateTimeUtils.formatDate(Date(referenceTimestamp))
            }

            StatisticsPeriod.WEEK -> {
                val cal = Calendar.getInstance()
                    .also { it.timeInMillis = referenceTimestamp + 3 * DAY_MS }
                String.format(weekLabelTemplate, cal.get(Calendar.WEEK_OF_YEAR))
            }

            StatisticsPeriod.MONTH -> {
                val cal = Calendar.getInstance().also { it.timeInMillis = referenceTimestamp }
                val month = cal.get(Calendar.MONTH)
                val year = cal.get(Calendar.YEAR)
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val symbols = DateFormatSymbols.getInstance(Locale.getDefault())
                if (year == currentYear) symbols.months[month]
                else "${symbols.shortMonths[month]} $year"
            }

            StatisticsPeriod.YEAR -> {
                val cal = Calendar.getInstance().also { it.timeInMillis = referenceTimestamp }
                cal.get(Calendar.YEAR).toString()
            }
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showYearPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onShift(-1) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
        }
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .clickable {
                    if (period == StatisticsPeriod.YEAR) showYearPicker = true
                    else showDatePicker = true
                },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        IconButton(onClick = { onShift(+1) }, enabled = canShiftForward) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }

    if (showDatePicker) {
        val cal = Calendar.getInstance().also { it.timeInMillis = referenceTimestamp }
        DisposableEffect(referenceTimestamp) {
            val dialog = DatePickerDialog(
                context,
                { _, year, month, day ->
                    val c = Calendar.getInstance()
                    c.set(year, month, day, 0, 0, 0)
                    c.set(Calendar.MILLISECOND, 0)
                    onSetDate(c.timeInMillis)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH),
            )
            dialog.datePicker.maxDate = System.currentTimeMillis()
            dialog.setOnDismissListener { showDatePicker = false }
            dialog.show()
            onDispose { if (dialog.isShowing) dialog.dismiss() }
        }
    }

    if (showYearPicker) {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val selectedYear = remember(referenceTimestamp) {
            Calendar.getInstance().also { it.timeInMillis = referenceTimestamp }.get(Calendar.YEAR)
        }
        YearPickerDialog(
            selectedYear = selectedYear,
            yearRange = (currentYear - 10)..currentYear,
            onYearSelected = { year ->
                val c = Calendar.getInstance()
                c.set(Calendar.YEAR, year)
                c.set(Calendar.MONTH, Calendar.JANUARY)
                c.set(Calendar.DAY_OF_MONTH, 1)
                c.set(Calendar.HOUR_OF_DAY, 0)
                c.set(Calendar.MINUTE, 0)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                onSetDate(c.timeInMillis)
                showYearPicker = false
            },
            onDismiss = { showYearPicker = false },
        )
    }
}

@Composable
private fun YearPickerDialog(
    selectedYear: Int,
    yearRange: IntRange,
    onYearSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val years = remember(yearRange) { yearRange.toList().reversed() }
    val listState = rememberLazyListState()

    LaunchedEffect(selectedYear) {
        val index = years.indexOf(selectedYear)
        if (index >= 0) listState.scrollToItem(index)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.selectYear)) },
        text = {
            LazyColumn(state = listState) {
                items(years) { year ->
                    Text(
                        text = year.toString(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onYearSelected(year) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (year == selectedYear) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
