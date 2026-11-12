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

package de.tadris.fitness.steps

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.concurrent.ExecutorService

class StepRegistrar(
    private val sensorManager: SensorManager,
    private val executor: ExecutorService,
    private val onStep: (steps: Int) -> Unit,
) : SensorEventListener {

    private var lastCounterValue = -1f
    private var minuteStart = -1L

    fun start(sensor: Sensor) {
        sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
            StepTrackingService.SENSOR_LATENCY_US,
        )
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val counter = event.values[0]
        executor.submit { process(counter) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun process(counter: Float) {
        val delta = if (lastCounterValue >= 0f) {
            if (counter >= lastCounterValue) {
                (counter - lastCounterValue).toInt()
            } else {
                // counter reset on reboot — treat new value as steps since reboot
                counter.toInt()
            }
        } else {
            0
        }
        lastCounterValue = counter
        if (delta > 0) {
            onStep(delta)
        }
    }
}
