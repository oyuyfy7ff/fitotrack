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

package de.tadris.fitness.ui.steps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.tadris.fitness.R
import de.tadris.fitness.steps.StepTrackingService
import de.tadris.fitness.ui.settings.ClickableSettingsItem
import de.tadris.fitness.ui.settings.ListPreferenceItem
import de.tadris.fitness.ui.settings.SwitchSettingsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepsSettingsScreen(
    onBack: () -> Unit = {},
) {
    val vm: StepsViewModel = viewModel()
    val serviceRunning by vm.serviceRunning.collectAsStateWithLifecycle()
    val dailyStepGoal by vm.dailyStepGoal.collectAsStateWithLifecycle()
    val autoStartRecordingEnabled by vm.autoStartRecordingEnabled.collectAsStateWithLifecycle()
    val stepActivityThreshold by vm.stepActivityThreshold.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val stepSensorSupported = remember(context) {
        (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
            .getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    var showPermissionDeniedDialog by remember { mutableStateOf(false) }
    var showLocationPermissionDeniedDialog by remember { mutableStateOf(false) }
    var showBackgroundLocationDeniedDialog by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }

    val backgroundLocationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.setAutoStartRecordingEnabled(true)
        } else {
            showBackgroundLocationDeniedDialog = true
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                vm.setAutoStartRecordingEnabled(true)
            }
        } else {
            showLocationPermissionDeniedDialog = true
        }
    }

    fun enableAutoStart() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasBackground = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        when {
            !hasFine && !hasCoarse -> locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )

            !hasBackground -> backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            else -> vm.setAutoStartRecordingEnabled(true)
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val activityRecognitionGranted =
            results[Manifest.permission.ACTIVITY_RECOGNITION] ?: true
        if (activityRecognitionGranted) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, StepTrackingService::class.java)
            )
        } else {
            showPermissionDeniedDialog = true
        }
    }

    fun startTracking() {
        val toRequest = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.ACTIVITY_RECOGNITION)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (toRequest.isEmpty()) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, StepTrackingService::class.java)
            )
        } else {
            permissionsLauncher.launch(toRequest.toTypedArray())
        }
    }

    if (showGoalDialog) {
        StepGoalDialog(
            currentGoal = dailyStepGoal,
            onConfirm = { goal ->
                vm.setDailyStepGoal(goal)
                showGoalDialog = false
            },
            onDismiss = { showGoalDialog = false },
        )
    }

    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            title = { Text(stringResource(R.string.permissionRequiredTitle)) },
            text = { Text(stringResource(R.string.stepPermissionExplanation)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDeniedDialog = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.openSettings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDeniedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showLocationPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showLocationPermissionDeniedDialog = false },
            title = { Text(stringResource(R.string.permissionRequiredTitle)) },
            text = { Text(stringResource(R.string.autoStartLocationPermissionExplanation)) },
            confirmButton = {
                TextButton(onClick = {
                    showLocationPermissionDeniedDialog = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.openSettings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationPermissionDeniedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showBackgroundLocationDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showBackgroundLocationDeniedDialog = false },
            title = { Text(stringResource(R.string.permissionRequiredTitle)) },
            text = { Text(stringResource(R.string.autoStartBackgroundLocationPermissionExplanation)) },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundLocationDeniedDialog = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.openSettings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundLocationDeniedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stepSettings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding) {
            item {
                SwitchSettingsItem(
                    title = stringResource(R.string.stepTrackingTitle),
                    supporting = if (serviceRunning) stringResource(R.string.stepTrackingRunning)
                                 else if (!stepSensorSupported) stringResource(R.string.stepTrackingNotSupported)
                                 else stringResource(R.string.stepTrackingDescription),
                    checked = serviceRunning,
                    enabled = stepSensorSupported,
                    onCheckedChange = { checked ->
                        if (checked) startTracking() else vm.stopTracking()
                    },
                )
            }

            item {
                SwitchSettingsItem(
                    title = stringResource(R.string.autoStartRecordingTitle),
                    supporting = stringResource(R.string.autoStartRecordingDescription),
                    checked = autoStartRecordingEnabled,
                    enabled = serviceRunning,
                    onCheckedChange = { checked ->
                        if (checked) enableAutoStart() else vm.setAutoStartRecordingEnabled(false)
                    },
                )
            }

            if (autoStartRecordingEnabled) {
                item {
                    ListPreferenceItem(
                        title = stringResource(R.string.stepActivityThreshold),
                        entries = stringArrayResource(R.array.step_activity_threshold_entries),
                        entryValues = stringArrayResource(R.array.step_activity_threshold_values),
                        selectedValue = stepActivityThreshold,
                        onValueChange = { value -> vm.setStepActivityThreshold(value) },
                    )
                }
            }

            item {
                ClickableSettingsItem(
                    title = stringResource(R.string.dailyStepGoal),
                    supporting = dailyStepGoal.toString(),
                    onClick = { showGoalDialog = true },
                )
            }
        }
    }
}


@Composable
internal fun StepGoalDialog(
    currentGoal: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(currentGoal.toString()) }
    val parsed = text.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dailyStepGoal)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = parsed == null || parsed <= 0,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.takeIf { it > 0 }?.let(onConfirm) },
                enabled = parsed != null && parsed > 0,
            ) {
                Text(stringResource(R.string.okay))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
