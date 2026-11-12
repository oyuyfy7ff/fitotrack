package de.tadris.fitness.ui.record

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.tadris.fitness.R
import de.tadris.fitness.ui.record.BluetoothDeviceScanner.BluetoothError
import de.tadris.fitness.ui.record.BluetoothDeviceScanner.SignalStrength
import de.tadris.fitness.ui.theme.signalModerateDark
import de.tadris.fitness.ui.theme.signalModerateLight
import de.tadris.fitness.ui.theme.signalNoneDark
import de.tadris.fitness.ui.theme.signalNoneLight
import de.tadris.fitness.ui.theme.signalStrongDark
import de.tadris.fitness.ui.theme.signalStrongLight
import de.tadris.fitness.ui.theme.signalWeakDark
import de.tadris.fitness.ui.theme.signalWeakLight

@Composable
fun ChooseBluetoothDeviceDialog(
    onSelect: (ChooseBluetoothDeviceViewModel.BluetoothDeviceRow) -> Unit,
    onDismiss: () -> Unit,
) {
    val vm: ChooseBluetoothDeviceViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val context: Context = LocalContext.current


    DisposableEffect(Unit) {
        vm.startScan()
        onDispose { vm.stopScan() }
    }


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.selectBluetoothDevice)) },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        text = {
            Column {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    val error = state.scanError
                    if (error != null) {
                        item { SectionHeader(stringResource(R.string.scanFailed)) }
                        item { Text(text = stringResource(getErrorMessage(error)),
                            color =  MaterialTheme.colorScheme.onSurface) }
                        return@LazyColumn
                    }
                    if (state.scanRunning) {
                        item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                    }
                    var anySections = false
                    state.lastConnectedDevice?.let {
                        item { SectionHeader(stringResource(R.string.lastUsedBluetoothDevice)) }
                        item(key = it.address) { DeviceRow(it) { onRowSelected(context, it, onSelect) } }
                        anySections = true
                    }
                    if (state.pairedDevices.isNotEmpty()) {
                        anySections = true
                        item { SectionHeader(stringResource(R.string.pairedBluetoothDevices))}
                        items(state.pairedDevices, key = { it.address }) { device ->
                            DeviceRow(device) { onRowSelected(context, device, onSelect) }
                        }
                    }
                    if (state.otherDevices.isNotEmpty()) {
                        val anyOtherSections = anySections
                        anySections = true
                        val textId = if (anyOtherSections) R.string.otherBluetoothDevices else R.string.bluetoothDevices
                        item { SectionHeader(stringResource(textId))}
                        items(state.otherDevices, key = { it.address }) { device ->
                            DeviceRow(device) { onRowSelected(context, device, onSelect) }
                        }
                    }

                    if (!anySections && state.scanRunning) {
                        item { SectionHeader(stringResource(R.string.scanning)) }
                    }
                }
                if (state.scanError != null) {
                    return@Column
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = state.showUnknownDevices,
                                onValueChange = { vm.invertShouldShowUnknownDevices() },
                                role = Role.Checkbox,
                            ),
                    ) {
                        Text(stringResource(R.string.showUnnamedBluetoothDevices), modifier = Modifier.weight(1f))
                        Checkbox(checked = state.showUnknownDevices, onCheckedChange = null)
                    }
                }
            }
        }
    )
}

private fun getErrorMessage(error: BluetoothError): Int {
    return when(error) {
        BluetoothError.TOO_MANY_SCANS -> R.string.bluetoothErrorTooManyScans
        BluetoothError.NO_RESOURCES -> R.string.bluetoothErrorHardwareExhausted
        BluetoothError.UNSUPPORTED -> R.string.bluetoothErrorUnsupported
        BluetoothError.OTHER_ERROR -> R.string.bluetoothErrorOther
        BluetoothError.NO_PERMISSION -> R.string.bluetoothErrorNoPermission
        BluetoothError.BLUETOOTH_DISABLED -> R.string.bluetoothErrorDisabled
    }
}

private fun onRowSelected(context: Context,
                  row: ChooseBluetoothDeviceViewModel.BluetoothDeviceRow,
                  onSelect: (ChooseBluetoothDeviceViewModel.BluetoothDeviceRow) -> Unit
) {
    //HRManager will attempt to autoconnect to an unavailable bluetooth device. Notify the user.
    if (row.strength == SignalStrength.NONE && !row.connected) {
        Toast.makeText(context, R.string.bluetoothAutoconnect, Toast.LENGTH_LONG).show()
    }
    onSelect(row)
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 16.dp),
    )
}

@Composable
private fun DeviceRow(deviceRow: ChooseBluetoothDeviceViewModel.BluetoothDeviceRow, onClick: () -> Unit) {
    val noSignal = deviceRow.strength == SignalStrength.NONE && !deviceRow.connected
    val contentColor =
        if (noSignal) MaterialTheme.colorScheme.outline
        else MaterialTheme.colorScheme.onSurface

    ListItem(
        headlineContent = { Text(deviceRow.displayName, color = contentColor) },
        supportingContent = if (noSignal) {
            { Text(stringResource(R.string.bluetoothNoSignal), color = contentColor) }
        } else null,
        trailingContent = { SignalStrengthIcon(deviceRow) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun SignalStrengthIcon(device: ChooseBluetoothDeviceViewModel.BluetoothDeviceRow) {
    val darkModeEnabled = LocalActivity.current?.resources?.configuration?.uiMode?.and(
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    if (device.connected) {
        val tint = if (darkModeEnabled) signalStrongDark else signalStrongLight
        Icon(painter = painterResource(R.drawable.ic_bluetooth_connected), tint = tint,
            contentDescription = stringResource(R.string.connectedBluetoothDevice))
        return
    }
    val drawable = when (device.strength) {
        SignalStrength.HIGH -> R.drawable.ic_signal_3_24
        SignalStrength.MED -> R.drawable.ic_signal_2_24
        SignalStrength.LOW -> R.drawable.ic_signal_1_24
        SignalStrength.NONE -> R.drawable.ic_signal_0_24
    }
    val tint = when (device.strength) {
        SignalStrength.HIGH -> if (darkModeEnabled) signalStrongDark else signalStrongLight
        SignalStrength.MED -> if (darkModeEnabled) signalModerateDark else signalModerateLight
        SignalStrength.LOW -> if (darkModeEnabled) signalWeakDark else signalWeakLight
        SignalStrength.NONE -> if (darkModeEnabled) signalNoneDark else signalNoneLight
    }

    val description = stringResource(when (device.strength) {
        SignalStrength.HIGH -> R.string.bluetoothStrengthStrong
        SignalStrength.MED -> R.string.bluetoothStrengthModerate
        SignalStrength.LOW -> R.string.bluetoothStrengthWeak
        SignalStrength.NONE -> R.string.bluetoothStrengthNone
    })

    Icon(painter = painterResource(drawable), tint = tint, contentDescription = description)
}
