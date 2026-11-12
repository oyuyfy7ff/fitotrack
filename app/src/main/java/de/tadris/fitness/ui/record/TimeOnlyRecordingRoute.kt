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

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.data.WorkoutTypeManager
import de.tadris.fitness.util.Icon
import de.tadris.fitness.util.NotificationHelper

@Composable
fun TimeOnlyRecordingRoute(
    workoutTypeId: String,
    isLaunchAction: Boolean,
    onFinish: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToIntervalSets: () -> Unit,
) {
    val context = LocalContext.current
    val vm: TimeOnlyRecordingViewModel = viewModel()

    val workoutType = remember(workoutTypeId) {
        WorkoutTypeManager.getInstance().getWorkoutTypeById(context, workoutTypeId)
    }

    LaunchedEffect(workoutTypeId) {
        vm.init(workoutType, isLaunchAction)
        NotificationHelper.requestNotificationPermissionIfNecessary(context as Activity)
    }

    val showOnLockScreen = remember { Instance.getInstance(context).userPreferences.showOnLockScreen }
    RecordingWindowEffects(showOnLockScreen)

    ActivityRecognitionPermissionDialog(vm)

    RecordingSharedDialogs(vm, workoutType)

    TimeOnlyRecordingScreen(
        vm = vm,
        onFinish = onFinish,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToIntervalSets = onNavigateToIntervalSets,
    )
}

@Composable
fun TimeOnlyRecordingScreen(
    vm: TimeOnlyRecordingViewModel,
    onFinish: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToIntervalSets: () -> Unit,
) {
    val workoutType by vm.currentWorkoutType.collectAsState()
    val values by vm.values.collectAsState()

    RecordingScaffold(
        vm = vm,
        onFinish = onFinish,
        onOpenSettings = onNavigateToSettings,
        onManageIntervalSets = onNavigateToIntervalSets,
        mainContent = {
            TimeOnlyContent(
                workoutType = workoutType,
                currentHeartRate = values.currentHeartRate,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

/**
 * The elapsed time is already shown prominently in the bottom sheet, so the main area shows
 * the workout type and, if a heart rate sensor is connected, the current heart rate.
 */
@Composable
private fun TimeOnlyContent(
    workoutType: WorkoutType?,
    currentHeartRate: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 80.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (workoutType != null) {
                val iconResId = Icon.getIcon(workoutType.icon)
                Surface(
                    shape = CircleShape,
                    color = Color(workoutType.color),
                    modifier = Modifier.size(120.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (iconResId != 0) {
                            Icon(
                                painter = painterResource(iconResId),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(64.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = workoutType.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
            }
            if (currentHeartRate > 0) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = currentHeartRate.toString(),
                    fontSize = 72.sp,
                    lineHeight = 72.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.unitHeartBeatsPerMinute),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
