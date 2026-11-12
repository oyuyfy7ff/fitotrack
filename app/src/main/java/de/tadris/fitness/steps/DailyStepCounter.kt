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

import de.tadris.fitness.data.StepDao
import java.util.Calendar

class DailyStepCounter(
    private val dao: StepDao,
    private val onTodayStepsChanged: (Int) -> Unit,
) {

    private var persistedTodaySteps = 0
    private var currentPending = 0
    private var currentDayId = -1

    val todaySteps: Int get() = persistedTodaySteps + currentPending

    // Call once on the background thread before starting the sensor.
    fun initialize() {
        currentDayId = todayDayId()
        persistedTodaySteps = loadTodayFromDb()
        fireCallback()
    }

    // Called whenever SampleConsolidator reports new pending steps.
    fun onPendingStepsChanged(pending: Int) {
        if (handleDayRollover()) return
        currentPending = pending
        fireCallback()
    }

    // Called whenever SampleConsolidator successfully persists a window.
    // Keeps the running total consistent so the notification doesn't dip after a persist flush.
    fun onStepsPersisted(steps: Int) {
        persistedTodaySteps += steps
    }

    private fun handleDayRollover(): Boolean {
        val day = todayDayId()
        if (day == currentDayId) return false
        currentDayId = day
        persistedTodaySteps = 0
        currentPending = 0
        fireCallback()
        return true
    }

    private fun loadTodayFromDb(): Int {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val dayEnd = cal.timeInMillis
        return dao.getStepsInRange(dayStart, dayEnd).sumOf { it.steps }
    }

    private fun todayDayId(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    }

    private fun fireCallback() = onTodayStepsChanged(todaySteps)
}
