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
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.tadris.fitness.R
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.util.PermissionUtils

@Composable
fun RecordingSharedDialogs(vm: RecordingViewModel, workoutType: WorkoutType) {
    val context = LocalContext.current
    // Bluetooth permission + device chooser
    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> /* user can re-tap Bluetooth after granting */ }

    val showChooseBluetoothDevice by vm.showChooseBluetoothDevice.collectAsState()
    var bluetoothReady by remember { mutableStateOf(false) }
    LaunchedEffect(showChooseBluetoothDevice) {
        bluetoothReady = false
        if (!showChooseBluetoothDevice) return@LaunchedEffect
        if (!PermissionUtils.checkBluetoothScanPermissions(context)) {
            btPermissionLauncher.launch(PermissionUtils.bluetoothScanPermissions)
            vm.dismissChooseBluetoothDevice()
            return@LaunchedEffect
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            (context as? Activity)?.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            vm.dismissChooseBluetoothDevice()
            return@LaunchedEffect
        }
        bluetoothReady = true
    }

    if (showChooseBluetoothDevice && bluetoothReady) {
        ChooseBluetoothDeviceDialog(
            onSelect = { row -> vm.onBtDeviceSelected(row.address, row.name) },
            onDismiss = { vm.dismissChooseBluetoothDevice() },
        )
    }

    // Info slot selection dialog
    val selectInfoSlot by vm.selectInfoDialogSlot.collectAsState()
    LaunchedEffect(selectInfoSlot) {
        val slot = selectInfoSlot ?: return@LaunchedEffect
        val activity = context as? Activity ?: return@LaunchedEffect
        SelectWorkoutInformationDialog(activity, workoutType, slot) { s, info ->
            vm.onInfoSlotSelected(s, info)
        }.show()
        vm.dismissSelectInfoDialog()
    }
}

@Composable
fun ActivityRecognitionPermissionDialog(vm: RecordingViewModel) {
    val activityPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.restartService()
    }

    var showActivityConsentDialog by remember {
        mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !vm.hasServicePermission())
    }
    if (showActivityConsentDialog) {
        AlertDialog(
            onDismissRequest = { showActivityConsentDialog = false; vm.finish() },
            title = { Text(stringResource(R.string.recordingPermissionNotGrantedTitle)) },
            text = { Text(stringResource(R.string.recordingActivityPermissionMessage)) },
            confirmButton = {
                TextButton(onClick = {
                    showActivityConsentDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                }) { Text(stringResource(R.string.actionGrant)) }
            },
            dismissButton = {
                TextButton(onClick = { showActivityConsentDialog = false; vm.finish() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
