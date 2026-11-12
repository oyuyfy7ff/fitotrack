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
package de.tadris.fitness.ui.dashboard

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.Workout
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.ui.steps.StepGoalDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onWorkoutClick: (Workout) -> Unit = {},
    onDeleteWorkout: (Workout) -> Unit = {},
    onRecord: () -> Unit = {},
    onRecordLast: (WorkoutType) -> Unit = {},
    onEnterWorkout: () -> Unit = {},
    onImport: () -> Unit = {},
    onSettings: () -> Unit = {},
    onStatistics: () -> Unit = {},
    onStepDetails: () -> Unit = {},
    onActiveMinuteStats: () -> Unit = {},
) {
    val context = LocalContext.current
    val stepSensorSupported = remember(context) {
        (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
            .getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }
    val vm: DashboardViewModel = viewModel()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val lastWorkoutType = remember(workouts) { workouts.firstOrNull()?.getWorkoutType(context) }

    val todaySteps by vm.todaySteps.collectAsStateWithLifecycle()
    val stepGoal by vm.stepGoal.collectAsStateWithLifecycle()
    val todayActiveMinutes by vm.todayActiveMinutes.collectAsStateWithLifecycle()
    val todayDistanceMeters by vm.todayDistanceMeters.collectAsStateWithLifecycle()
    val weeklyStepChart by vm.weeklyStepChart.collectAsStateWithLifecycle()
    val weeklyWorkouts by vm.weeklyWorkouts.collectAsStateWithLifecycle()
    val stepTrackingPromptDismissed by vm.stepTrackingPromptDismissed.collectAsStateWithLifecycle()

    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    var workoutToDelete by remember { mutableStateOf<Workout?>(null) }
    var showGoalDialog by rememberSaveable { mutableStateOf(false) }
    val userPreferences = Instance.getInstance(context).userPreferences
    var showFirstStartDialog by rememberSaveable {
        val isFirstStart = userPreferences.firstStart
        if (isFirstStart) userPreferences.firstStart = false
        mutableStateOf(isFirstStart)
    }

    if (showGoalDialog) {
        StepGoalDialog(
            currentGoal = stepGoal,
            onConfirm = { goal ->
                vm.setDailyStepGoal(goal)
                showGoalDialog = false
            },
            onDismiss = { showGoalDialog = false },
        )
    }

    if (showFirstStartDialog) {
        FirstStartDialog(
            onSettings = { showFirstStartDialog = false; onSettings() },
            onDismiss = { showFirstStartDialog = false },
        )
    }

    if (workoutToDelete != null) {
        AlertDialog(
            onDismissRequest = { workoutToDelete = null },
            title = { Text(stringResource(R.string.deleteWorkout)) },
            text = { Text(stringResource(R.string.deleteWorkoutMessage)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteWorkout(workoutToDelete!!)
                    workoutToDelete = null
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { workoutToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FitoTrack") },
                actions = {
                    IconButton(onClick = onStatistics) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bar_chart),
                            contentDescription = stringResource(R.string.workout_statistics),
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            DashboardFab(
                expanded = fabExpanded,
                lastWorkoutType = lastWorkoutType,
                onToggle = { fabExpanded = !fabExpanded },
                onRecord = { fabExpanded = false; onRecord() },
                onRecordLast = { type -> fabExpanded = false; onRecordLast(type) },
                onEnterWorkout = { fabExpanded = false; onEnterWorkout() },
                onImport = { fabExpanded = false; onImport() },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 88.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (vm.stepTrackingEnabled || todaySteps > 0) {
                item {
                    StepsTodayCard(
                        todaySteps = todaySteps,
                        stepGoal = stepGoal,
                        todayActiveMinutes = todayActiveMinutes,
                        todayDistanceMeters = todayDistanceMeters,
                        weeklyStepChart = weeklyStepChart,
                        onGoalClick = { showGoalDialog = true },
                        onActiveMinuteClick = onActiveMinuteStats,
                    )
                }
            } else if (stepSensorSupported && !stepTrackingPromptDismissed) {
                item {
                    StepTrackingPromptCard(
                        onMore = onStepDetails,
                        onDismiss = { vm.dismissStepTrackingPrompt() },
                    )
                }
            }
            if (weeklyWorkouts.isNotEmpty()) {
                item {
                    ThisWeekCard(
                        weeklyWorkouts = weeklyWorkouts,
                        rangeStartMillis = vm.last7DaysStartMillis,
                    )
                }
            }
            if (workouts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.hintAddWorkout),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp, start = 16.dp, end = 16.dp),
                    )
                }
            } else {
                items(workouts, key = { it.id }) { workout ->
                    WorkoutListItem(
                        workout = workout,
                        onClick = { onWorkoutClick(workout) },
                        onLongClick = { workoutToDelete = workout },
                    )
                }
            }
        }
    }
}
