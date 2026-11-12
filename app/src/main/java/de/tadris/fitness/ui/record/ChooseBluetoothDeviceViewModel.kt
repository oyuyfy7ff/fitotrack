package de.tadris.fitness.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.tadris.fitness.R
import de.tadris.fitness.recording.component.HeartRateComponent
import de.tadris.fitness.recording.event.HeartRateConnectionChangeEvent
import de.tadris.fitness.ui.record.BluetoothDeviceScanner.ScannedDevice
import de.tadris.fitness.ui.record.BluetoothDeviceScanner.SignalStrength
import de.tadris.fitness.util.BluetoothDevicePreferences
import de.tadris.fitness.util.BluetoothDevicePreferences.LastUsedDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.time.Duration.Companion.milliseconds

class ChooseBluetoothDeviceViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner: BluetoothDeviceScanner =
        BluetoothDeviceScanner(application, viewModelScope)
    private var scannerJob: Job? = null
    private var scanFailedJob: Job? = null
    private var stopScanJob: Job? = null
    @Volatile
    private var scanError: BluetoothDeviceScanner.BluetoothError? = null

    private var hrState = HeartRateComponent.HeartRateConnectionState.DISCONNECTED


    private val preferences: BluetoothDevicePreferences = BluetoothDevicePreferences(application)

    private val _uiState = MutableStateFlow<UiState>(UiState(null, emptyList(), emptyList(),
        shouldShowUnknownDevices(), scanRunning = true, scanError = null
    ))
    val uiState = _uiState.asStateFlow()

    private fun updateUiState(foundDevices: List<ScannedDevice>) {
        var lastConnectedDevice: BluetoothDeviceRow? = null
        val lastUsedDevice = preferences.getLastUsedDevice(BluetoothDevicePreferences.DEVICE_HEART_RATE)
        val isConnected = hrState == HeartRateComponent.HeartRateConnectionState.CONNECTED
        if (lastUsedDevice != null) {
            // If the HeartRateComponent reports we're connected, then it's connected to the stored
            // last used device, which won't be broadcasting (and thus, won't have a signal strength)
            lastConnectedDevice = lastUsedDevice.toDeviceRow(isConnected)
            val liveLastDevice = foundDevices.firstOrNull {
                it.address == lastUsedDevice.address &&
                it.strength != SignalStrength.NONE
            }
            if (liveLastDevice != null) {
                lastConnectedDevice = liveLastDevice.toDeviceRow(isConnected)
            }
        }
        val showUnknownDevices = shouldShowUnknownDevices()
        val previouslyPairedDevices = foundDevices.filter { it.shouldShow(showUnknownDevices) &&
                it.bonded && it.address != lastConnectedDevice?.address }
            .map { it.toDeviceRow() }.sorted()
        val otherDevices = foundDevices.filter { it.shouldShow(showUnknownDevices) && !it.bonded &&
                it.address != lastConnectedDevice?.address }
            .map { it.toDeviceRow() }.sorted()
        _uiState.value = UiState(lastConnectedDevice, previouslyPairedDevices, otherDevices,
            showUnknownDevices, scannerJob != null, scanError
        )
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onHrConnectionChange(event: HeartRateConnectionChangeEvent) {
        hrState = event.state
        updateUiState(scanner.devices.value)
    }

    fun startScan() {
        stopScanJob?.cancel()
        stopScanJob = null
        if (scannerJob != null) {
            return
        }
        this@ChooseBluetoothDeviceViewModel.scanError = null
        EventBus.getDefault().register(this)
        scanner.startScan()
        scannerJob = viewModelScope.launch {
            scanner.devices.sample(UI_UPDATE_TIME_MS).collect { updateUiState(it) }
        }
        scanFailedJob = viewModelScope.launch {
            scanner.scanFailed.collect { error ->
                if (error != null) {
                    this@ChooseBluetoothDeviceViewModel.scanError = error
                    stopScanNow()
                    updateUiState(scanner.devices.value)
                }
            }
        }
        updateUiState(scanner.devices.value)
    }

    fun stopScan() {
        if (stopScanJob != null) {
            return
        }
        // catch quick dismissal/re-launches and rotations of the dialog by waiting 2 seconds before
        // stopping
        stopScanJob = viewModelScope.launch {
            delay(STOP_GRACE_PERIOD_MS.milliseconds)
            stopScanJob = null
            stopScanNow()
        }
    }

    private fun stopScanNow() {
        scanner.stopScan()
        scannerJob?.cancel()
        scannerJob = null
        scanFailedJob?.cancel()
        scanFailedJob = null
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }

    override fun onCleared() {
        stopScanNow()
        super.onCleared()
    }

    fun ScannedDevice.shouldShow(showUnknownDevices: Boolean) = name != null || showUnknownDevices

    fun invertShouldShowUnknownDevices() {
        preferences.setShouldShowUnknownDevices(!shouldShowUnknownDevices())
        updateUiState(scanner.devices.value)
    }

    private fun shouldShowUnknownDevices() = preferences.getShouldShowUnknownDevices()

    private fun getDisplayName(address: String, name: String?): String {
        val name = name ?: getApplication<Application>().getString(R.string.unknown)
        return "$name ($address)"
    }

    data class BluetoothDeviceRow(
        val address: String,
        val name: String?,
        val displayName: String,
        val strength: SignalStrength,
        val connected: Boolean = false,
    ): Comparable<BluetoothDeviceRow> {
        override fun compareTo(other: BluetoothDeviceRow): Int {
            return compareValuesBy(this, other, {!it.connected }, {it.strength }, { it.displayName})
        }
    }

    private fun createBluetoothDeviceRow(address: String, name: String?, strength: SignalStrength, connected: Boolean) =
        BluetoothDeviceRow(address, name, getDisplayName(address, name), strength, connected)

    private fun ScannedDevice.toDeviceRow(connected: Boolean = false) = createBluetoothDeviceRow(address, name, strength, connected)
    private fun LastUsedDevice.toDeviceRow(connected: Boolean = false) = createBluetoothDeviceRow(address, name, SignalStrength.NONE, connected)

    data class UiState(
        val lastConnectedDevice: BluetoothDeviceRow?,
        val pairedDevices: List<BluetoothDeviceRow>,
        val otherDevices: List<BluetoothDeviceRow>,
        val showUnknownDevices: Boolean,
        val scanRunning: Boolean,
        val scanError: BluetoothDeviceScanner.BluetoothError?)

    companion object {
        private const val UI_UPDATE_TIME_MS = 5000L
        private const val STOP_GRACE_PERIOD_MS = 2000L
    }

}
