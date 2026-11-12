package de.tadris.fitness.ui.record

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import de.tadris.fitness.util.PermissionUtils
import de.tadris.fitness.util.WorkoutLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class BluetoothDeviceScanner(context: Context, private val coroutineScope: CoroutineScope) {

    private val appContext: Context = context.applicationContext
    private val deviceMap = ConcurrentHashMap<String, ScannedDevice>()
    private val _devices = MutableStateFlow<List<ScannedDevice>>(emptyList())

    val devices: StateFlow<List<ScannedDevice>> = _devices.asStateFlow()

    private val _scanFailed = MutableStateFlow<BluetoothError?>(null)
    val scanFailed: StateFlow<BluetoothError?> = _scanFailed.asStateFlow()
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private var scanner: BluetoothLeScanner? = null

    private var updateMapJob: Job? = null
    private val callback: ScanCallback = DeviceDialogScanCallback()

    data class ScannedDevice(
        val address: String,
        val name: String?,
        val rssi: Int?,
        val strength: SignalStrength,
        val bonded: Boolean,
        val lastSeen: Long?,
    )

    enum class SignalStrength(val rssi: Int?) {
        HIGH(-70),
        MED(-85),
        LOW(Integer.MIN_VALUE),
        NONE(null)
    }

    private fun ScanResult.toScannedDevice(
        rssi: Int? = null,
        lastSeen: Long? = null,
    ): ScannedDevice = device.toScannedDevice(scanRecord?.deviceName, rssi, lastSeen)

    private fun BluetoothDevice.toScannedDevice(
        fallbackName: String? = null,
        rssi: Int? = null,
        lastSeen: Long? = null,
    ): ScannedDevice {
        val name = (name ?: fallbackName)?.takeIf { it != "null" }
        return ScannedDevice(
            address,
            name,
            rssi,
            getSignalStrength(rssi),
            bondState == BluetoothDevice.BOND_BONDED,
            lastSeen
        )
    }

    private fun addBondedDevices(adapter: BluetoothAdapter) {
        val bonded = try {
            adapter.bondedDevices.orEmpty()
        } catch (e: SecurityException) {
            Log.d("DeviceScanner", "Cannot read bonded devices", e)
            return
        }
        bonded.filter { it.type != BluetoothDevice.DEVICE_TYPE_CLASSIC }
            .forEach { updateDeviceIfNeeded(it.toScannedDevice()) }
    }

    private fun getSignalStrength(rssi: Int?): SignalStrength = when {
            rssi == null -> SignalStrength.NONE
            rssi >= SignalStrength.HIGH.rssi!! -> SignalStrength.HIGH
            rssi >= SignalStrength.MED.rssi!! -> SignalStrength.MED
            else -> SignalStrength.LOW
    }

    fun startScan() {
        stopScan() // restart scan, if called while scan is already running
        _scanFailed.value = null

        if (!PermissionUtils.checkBluetoothScanPermissions(appContext)) {
            _scanFailed.value = BluetoothError.NO_PERMISSION
            return
        }
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            _scanFailed.value = BluetoothError.BLUETOOTH_DISABLED
            return
        }
        this.scanner = scanner
        addBondedDevices(adapter)
        scanner.startScan(
            ArrayList<ScanFilter?>(),
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build(),
            callback
        )
        updateMapJob = coroutineScope.launch {
            while (true) {
                delay(BT_REFRESH_TIME_MS) // throws CancellationException once the job is cancelled
                updateDevicesPeriodic()
            }
        }
    }

    fun stopScan() {
        try {
            scanner?.stopScan(callback)
        } catch (e: IllegalStateException) {
            // thrown on some Android versions if Bluetooth was turned off in the meantime
            Log.d("DeviceScanner", "Failed to stop scan", e)
        }
        scanner = null
        updateMapJob?.cancel()
        updateMapJob = null
    }

    private fun updateDevicesPeriodic() {
        val now = System.currentTimeMillis()
        val absentCutoff = now - BT_ABSENT_TIMEOUT_MS
        val removalCutoff = now - BT_REMOVAL_TIMEOUT_MS
        var changed = false
        for ((address, existingDevice) in deviceMap) {
            if (existingDevice.lastSeen == null) {
                // paired device without a live signal, stays in the list
                continue
            }
            if (existingDevice.lastSeen < removalCutoff) {
                if (existingDevice.bonded) {
                    // fall back to a "no signal" entry instead of dropping a paired device
                    deviceMap[address] = existingDevice.copy(lastSeen = null)
                } else {
                    deviceMap.remove(address)
                }
                changed = true
            } else if (existingDevice.lastSeen < absentCutoff &&
                existingDevice.strength != SignalStrength.NONE) {
                deviceMap[address] = existingDevice.copy(rssi = null, strength = SignalStrength.NONE)
                changed = true
            }
        }
        if (changed) {
            _devices.value = deviceMap.values.toList()
        }
    }

    // We update a device (and the stateflow) when either the strength level changes, when a bonded
    // device (starts with signal strength of NONE) is replaced by a live scan of the same device,
    // or when BT_REFRESH_TIME_MS has passed since the last update of the device
    private fun updateDeviceIfNeeded(device: ScannedDevice) {
        val existingDevice = deviceMap[device.address]
        if (existingDevice != null && existingDevice.lastSeen != null &&
            device.lastSeen == null) {
            // do not update a live scan (has a last seen time) with a bond scan (has none)
            return
        }
        if (existingDevice == null ||
            (existingDevice.strength == SignalStrength.NONE &&
                    device.strength != SignalStrength.NONE)) {
            updateStateFlow(device)
        } else if (existingDevice.lastSeen != null && device.lastSeen != null &&
            (device.lastSeen - existingDevice.lastSeen) >= BT_REFRESH_TIME_MS) {
            updateStateFlow(device)
        } else if (device.strength != existingDevice.strength) {
            updateStateFlow(device)
        }
    }

    private fun updateStateFlow(device: ScannedDevice) {
        deviceMap[device.address] = device
        _devices.value = deviceMap.values.toList()
    }

    private inner class DeviceDialogScanCallback : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            _scanFailed.value = null
            val device = result.device
            if (device != null) {
                if (!deviceMap.containsKey(device.address)) {
                    Log.d(
                        "DeviceScanner",
                        "Found new device: " + device.name + ", " + device.address
                    )
                }
                updateDeviceIfNeeded(result.toScannedDevice(result.rssi, System.currentTimeMillis()))
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            _scanFailed.value = null
            for (result in results) {
                onScanResult(0, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            WorkoutLogger.log("DeviceScanner", "Failed to scan, error code: $errorCode")
            if (errorCode == SCAN_FAILED_ALREADY_STARTED) {
                return
            }
            _scanFailed.value = intToBluetoothError(errorCode)
        }
    }

    private fun intToBluetoothError(code: Int) =
        BluetoothError.entries.firstOrNull{ it.code == code } ?: BluetoothError.OTHER_ERROR
    enum class BluetoothError(val code: Int) {
        TOO_MANY_SCANS(ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY),
        UNSUPPORTED(ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED),
        NO_RESOURCES(ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES),
        OTHER_ERROR(-1),
        NO_PERMISSION(-2),
        BLUETOOTH_DISABLED(-3);
    }

    companion object {
        private const val BT_ABSENT_TIMEOUT_MS = 10000L
        private const val BT_REMOVAL_TIMEOUT_MS = 30000L
        private const val BT_REFRESH_TIME_MS = 500L
    }
}

