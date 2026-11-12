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

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.data.WorkoutTypeManager
import de.tadris.fitness.stats.aggregation.WorkoutTypeFilter
import de.tadris.fitness.ui.components.WorkoutTypePickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import de.tadris.fitness.util.Icon as WorkoutIcon

@Composable
fun WorkoutTypeSelector(
    selectedTypes: List<WorkoutType>,
    onTypesSelected: (List<WorkoutType>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    val allLabel = stringResource(R.string.workoutTypeAll)

    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val pickerTypes by produceState<List<WorkoutType>?>(null) {
        value = withContext(Dispatchers.IO) {
            val workoutDao = Instance.getInstance(context).db.workoutDao()
            val allOption = WorkoutType(WorkoutTypeFilter.ID_ALL, allLabel, 0, primaryColor, "list", 0, "")
            listOf(allOption) + WorkoutTypeManager.getInstance().getAllTypesSorted(context)
                .filter { workoutDao.getLastWorkoutByType(it.id) != null }
        }
    }
    val displayType = remember(selectedTypes, allLabel) {
        if (selectedTypes.size == 1) {
            selectedTypes.first()
        } else {
            // Virtual "All" type
            WorkoutType(
                WorkoutTypeFilter.ID_ALL,
                allLabel,
                0,
                Color.White.hashCode(),
                "list",
                0,
                ""
            )
        }
    }

    Surface(
        onClick = { showDialog = true },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                painter = painterResource(WorkoutIcon.getIcon(displayType.icon)),
                contentDescription = null,
                tint = if (displayType.id == WorkoutTypeFilter.ID_ALL) 
                    MaterialTheme.colorScheme.primary 
                else Color(displayType.color),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = displayType.title,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(android.R.drawable.arrow_down_float),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    if (showDialog) {
        val types = pickerTypes ?: return
        WorkoutTypePickerDialog(
            types = types,
            onDismiss = { showDialog = false },
            onSelect = { selectedType ->
                showDialog = false
                val newTypes = if (selectedType.id == WorkoutTypeFilter.ID_ALL) {
                    WorkoutTypeManager.getInstance().getAllTypes(context)
                } else {
                    listOf(selectedType)
                }
                onTypesSelected(newTypes)
            }
        )
    }
}
