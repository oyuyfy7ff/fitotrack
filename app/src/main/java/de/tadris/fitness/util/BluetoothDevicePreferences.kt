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
package de.tadris.fitness.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class BluetoothDevicePreferences(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("bluetooth", Context.MODE_PRIVATE)

    fun getLastUsedDevice(deviceType: String): LastUsedDevice? {
        val address = preferences.getString(getDeviceAddressPref(deviceType), null)
        val name = preferences.getString(getDeviceNamePref(deviceType), null)
        return if (address != null) LastUsedDevice(address, name) else null
    }

    fun setLastUsedDevice(deviceType: String, lastUsedDevice: LastUsedDevice) {
        preferences.edit { putString(getDeviceAddressPref(deviceType), lastUsedDevice.address)}
        preferences.edit { putString(getDeviceNamePref(deviceType), lastUsedDevice.name)}
    }

    fun setLastUsedDevice(deviceType: String, address: String, name: String?) {
        setLastUsedDevice(deviceType, LastUsedDevice(address, name))
    }

    fun getShouldShowUnknownDevices(): Boolean {
        return preferences.getBoolean(SHOW_UNKNOWN_BT_DEVICES, false)
    }

    fun setShouldShowUnknownDevices(shouldShow: Boolean) {
        preferences.edit { putBoolean(SHOW_UNKNOWN_BT_DEVICES, shouldShow) }
    }

    data class LastUsedDevice(val address: String, val name: String?)

    companion object {
        const val DEVICE_HEART_RATE: String = "hr"
        const val DEVICE_NAME_SUFFIX: String = "_name"

        const val SHOW_UNKNOWN_BT_DEVICES: String = "show_unknown_bt_devices"

        fun getDeviceAddressPref(deviceType: String) = deviceType
        fun getDeviceNamePref(deviceType: String) = "$deviceType$DEVICE_NAME_SUFFIX"
    }
}
