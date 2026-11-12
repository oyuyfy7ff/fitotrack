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
package de.tadris.fitness.ui.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import de.tadris.fitness.Instance
import de.tadris.fitness.data.WorkoutCapability
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.data.WorkoutTypeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decides which recording UI (GPS, time only, indoor) is shown depending on the workout type.
 *
 * @param workoutTypeId type to record, or null to resume the currently running workout
 */
@Composable
fun RecordingRoute(
    workoutTypeId: String?,
    isLaunchAction: Boolean,
    onFinish: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToIntervalSets: () -> Unit,
) {
    val context = LocalContext.current
    val workoutType by produceState<WorkoutType?>(null, workoutTypeId) {
        value = if (workoutTypeId != null) {
            WorkoutTypeManager.getInstance().getWorkoutTypeById(context, workoutTypeId)
        } else {
            withContext(Dispatchers.IO) {
                Instance.getInstance(context).db.workoutDao().findRunningWorkout()
                    ?.getWorkoutType(context)
            }.also { if (it == null) onFinish() }
        }
    }

    val type = workoutType ?: return
    when {
        type.hasCapability(WorkoutCapability.GPS) -> GpsRecordingRoute(
            workoutTypeId = type.id,
            isLaunchAction = isLaunchAction,
            onFinish = onFinish,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToIntervalSets = onNavigateToIntervalSets,
        )

        type.isTimeOnly -> TimeOnlyRecordingRoute(
            workoutTypeId = type.id,
            isLaunchAction = isLaunchAction,
            onFinish = onFinish,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToIntervalSets = onNavigateToIntervalSets,
        )

        else -> IndoorRecordingRoute(
            workoutTypeId = type.id,
            isLaunchAction = isLaunchAction,
            onFinish = onFinish,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToIntervalSets = onNavigateToIntervalSets,
        )
    }
}
