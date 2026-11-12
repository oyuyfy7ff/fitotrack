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

class SampleConsolidator(
    private val persistIntervalMs: Long,
    private val windowSizeMs: Long,
    private val activeMinuteThreshold: Int,
    private val onPendingStepsChanged: (pendingSteps: Int) -> Unit,
    private val onPersistReady: (windowStart: Long, steps: Int, activeMinutes: Int) -> Unit,
) {

    private var pendingSteps = 0
    private var pendingActiveMinutes = 0
    private var lastPersistTime = 0L

    fun onMinuteReceived(steps: Int) {
        pendingSteps += steps
        if(steps > activeMinuteThreshold){
            pendingActiveMinutes++
        }

        onPendingStepsChanged(pendingSteps)
        if (System.currentTimeMillis() - lastPersistTime > persistIntervalMs) {
            persist()
        }
    }

    fun flush() {
        if (pendingSteps > 0) persist()
    }

    private fun persist() {
        if (pendingSteps == 0) return
        val windowStart = currentWindowStart()
        val steps = pendingSteps
        val activeMinutes = pendingActiveMinutes
        pendingSteps = 0
        pendingActiveMinutes = 0
        lastPersistTime = System.currentTimeMillis()
        // Report the persisted steps first, so the pending reset doesn't briefly report a lower total.
        onPersistReady(windowStart, steps, activeMinutes)
        onPendingStepsChanged(0)
    }

    private fun currentWindowStart(): Long {
        val now = System.currentTimeMillis()
        return now - (now % windowSizeMs)
    }
}
