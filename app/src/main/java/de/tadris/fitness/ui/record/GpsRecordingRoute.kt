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

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import de.tadris.fitness.data.WorkoutCapability
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.tadris.fitness.ui.record.NavigationModeHandler.NavigationMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.WorkoutTypeManager
import de.tadris.fitness.recording.autostart.AutoStartMode
import de.tadris.fitness.util.NotificationHelper

@Composable
fun GpsRecordingRoute(
    workoutTypeId: String,
    isLaunchAction: Boolean,
    onFinish: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToIntervalSets: () -> Unit,
) {
    val context = LocalContext.current
    val vm: GpsRecordingViewModel = viewModel()

    val workoutType = remember(workoutTypeId) {
        WorkoutTypeManager.getInstance().getWorkoutTypeById(context, workoutTypeId)
    }

    LaunchedEffect(workoutTypeId) {
        vm.init(workoutType, isLaunchAction)
        NotificationHelper.requestNotificationPermissionIfNecessary(context as Activity)
    }

    val showOnLockScreen = remember { Instance.getInstance(context).userPreferences.showOnLockScreen }
    RecordingWindowEffects(showOnLockScreen)

    val needsActivityRecognition = workoutType?.repetitionCapability == WorkoutCapability.STEPS &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    // Activity recognition permission (for step tracking on Android Q+)
    val activityPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.restartService()
    }

    fun hasActivityRecognitionPermission() = !needsActivityRecognition ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    var showActivityConsent by remember { mutableStateOf(vm.hasServicePermission() && !hasActivityRecognitionPermission()) }
    if (showActivityConsent) {
        AlertDialog(
            onDismissRequest = { showActivityConsent = false; vm.finish() },
            title = { Text(stringResource(R.string.recordingPermissionNotGrantedTitle)) },
            text = { Text(stringResource(R.string.recordingActivityPermissionMessage)) },
            confirmButton = {
                TextButton(onClick = {
                    showActivityConsent = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                }) { Text(stringResource(R.string.actionGrant)) }
            },
            dismissButton = {
                TextButton(onClick = { showActivityConsent = false; vm.finish() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Location permission
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted && needsActivityRecognition) {
            showActivityConsent = true
        } else if (granted) {
            vm.restartService()
        }
    }

    var showLocationConsent by remember { mutableStateOf(!vm.hasServicePermission()) }
    if (showLocationConsent) {
        AlertDialog(
            onDismissRequest = { showLocationConsent = false; vm.finish() },
            title = { Text(stringResource(R.string.recordingPermissionNotGrantedTitle)) },
            text = { Text(stringResource(R.string.recordingPermissionNotGrantedMessage)) },
            confirmButton = {
                TextButton(onClick = {
                    showLocationConsent = false
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }) { Text(stringResource(R.string.actionGrant)) }
            },
            dismissButton = {
                TextButton(onClick = { showLocationConsent = false; vm.finish() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // No-GPS dialog
    val showNoGpsDialog by vm.showNoGpsDialog.collectAsState()
    if (showNoGpsDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.noGpsTitle)) },
            text = { Text(stringResource(R.string.noGpsMessage)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.dismissNoGpsDialog()
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }) { Text(stringResource(R.string.enable)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissNoGpsDialog(); vm.finish() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    RecordingSharedDialogs(vm, workoutType)

    GpsRecordingScreen(
        vm = vm,
        onFinish = onFinish,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToIntervalSets = onNavigateToIntervalSets,
    )
}

@Composable
fun GpsRecordingScreen(
    vm: GpsRecordingViewModel,
    onFinish: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToIntervalSets: () -> Unit,
) {
    val gpsState by vm.gpsState.collectAsState()
    val satelliteCount by vm.satelliteCount.collectAsState()
    val routePositions by vm.routePositions.collectAsState()
    val currentPosition by vm.currentPosition.collectAsState()
    val canStart by vm.canStart.collectAsState()

    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val mapController = remember { RecordingMapController() }
    val navigationMode by mapController.navigationMode.collectAsState()

    var showCustomDelayDialog by remember { mutableStateOf(false) }

    if (showCustomDelayDialog) {
        CustomDelayDialog(
            onConfirm = { ms -> showCustomDelayDialog = false; vm.beginAutoStart(AutoStartMode.INSTANT, ms) },
            onDismiss = { showCustomDelayDialog = false },
        )
    }

    DisposableEffect(mapController) {
        MapControllerHolder.controller = mapController
        onDispose { MapControllerHolder.controller = null }
    }

    RecordingScaffold(
        vm = vm,
        canStart = canStart,
        gpsState = gpsState,
        onFinish = onFinish,
        onOpenSettings = onNavigateToSettings,
        onManageIntervalSets = onNavigateToIntervalSets,
        onAutoStartCustomDelay = { showCustomDelayDialog = true },
        onAutoStartWaitForGps = { vm.beginAutoStart(AutoStartMode.WAIT_FOR_GPS) },
        onAutoStartOnMove = { vm.beginAutoStart(AutoStartMode.ON_MOVE) },
        mainContent = {
            RecordingMapView(
                routePositions = routePositions,
                currentPosition = currentPosition,
                primaryColor = primaryColor,
                controller = mapController,
                modifier = Modifier.fillMaxSize(),
            )

            // Map cover
            AnimatedVisibility(
                visible = currentPosition == null && routePositions.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        }
                )
            }
        },
        mapOverlays = {
            GpsInfoPill(
                gpsState = gpsState,
                satelliteCount = satelliteCount,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            )
            MapControlButtons(
                mapController = mapController,
                isMapMoved = navigationMode == NavigationMode.Manual,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
            )
        },
    )
}
