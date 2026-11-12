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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.tadris.fitness.Instance
import de.tadris.fitness.data.preferences.UserPreferences
import de.tadris.fitness.stats.aggregation.AggregationSpan
import de.tadris.fitness.ui.statistics.DateFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Calendar
import java.util.GregorianCalendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSpanSelection(
    onTimeSpanChanged: (AggregationSpan, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = remember { UserPreferences(context) }
    val instance = remember { Instance.getInstance(context) }
    
    var selectedAggregationSpan by remember { 
        mutableStateOf(preferences.statisticsAggregationSpan ?: AggregationSpan.MONTH) 
    }
    
    // Local midnight of the selected day
    var selectedDateMillis by remember {
        mutableLongStateOf(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }
    
    val dateFormatter = remember(selectedAggregationSpan) { DateFormatter(selectedAggregationSpan) }
    
    var dropdownExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val notifyChange = { span: AggregationSpan, time: Long ->
        val cal = GregorianCalendar().apply {
            timeInMillis = time
            firstDayOfWeek = instance.userPreferences.firstDayOfWeek
            // Spans start at midnight
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val normalizedTime = when (span) {
            AggregationSpan.DAY -> cal.timeInMillis
            AggregationSpan.WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.timeInMillis
            }
            AggregationSpan.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.timeInMillis
            }
            AggregationSpan.YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.timeInMillis
            }
            AggregationSpan.ALL -> {
                cal.set(Calendar.YEAR, 0)
                cal.timeInMillis
            }
            else -> cal.timeInMillis
        }
        onTimeSpanChanged(span, normalizedTime)
    }

    // Initial notification
    LaunchedEffect(Unit) {
        notifyChange(selectedAggregationSpan, selectedDateMillis)
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            // The material DatePicker works with UTC midnight timestamps
            initialSelectedDateMillis = Instant.ofEpochMilli(selectedDateMillis)
                .atZone(ZoneId.systemDefault()).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val localMidnight = Instant.ofEpochMilli(utcMillis)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        selectedDateMillis = localMidnight
                        notifyChange(selectedAggregationSpan, localMidnight)
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                SelectorButton(
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    text = stringResource(selectedAggregationSpan.title),
                    icon = Icons.Default.ArrowDropDown,
                    onClick = { dropdownExpanded = true }
                )
                
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    AggregationSpan.entries.filter { it != AggregationSpan.SINGLE }.forEach { span ->
                        DropdownMenuItem(
                            text = { Text(stringResource(span.title)) },
                            onClick = {
                                selectedAggregationSpan = span
                                preferences.statisticsAggregationSpan = span
                                dropdownExpanded = false
                                notifyChange(span, selectedDateMillis)
                            }
                        )
                    }
                }
            }

            // Date Picker
            if (selectedAggregationSpan != AggregationSpan.ALL) {
                VerticalDivider(modifier = Modifier.height(24.dp))
                
                SelectorButton(
                    text = dateFormatter.format(GregorianCalendar().apply { timeInMillis = selectedDateMillis }),
                    icon = Icons.Default.DateRange,
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SelectorButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
