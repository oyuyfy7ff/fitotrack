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

import android.Manifest
import android.content.Context
import android.content.Intent
import de.tadris.fitness.Instance
import de.tadris.fitness.data.WorkoutDao
import de.tadris.fitness.data.WorkoutTypeManager
import de.tadris.fitness.recording.RecorderService
import de.tadris.fitness.recording.RecordingState
import de.tadris.fitness.recording.event.RecordingStateChangeEvent
import de.tadris.fitness.util.PermissionUtils
import de.tadris.fitness.util.WorkoutLogger

enum class StepActivityThreshold(
    val stepsPerMinuteThreshold: Int,
    val requiredActiveMinutes: Int,
) {
    LOW(stepsPerMinuteThreshold = 60, requiredActiveMinutes = 3),
    MEDIUM(stepsPerMinuteThreshold = 80, requiredActiveMinutes = 4),
    HIGH(stepsPerMinuteThreshold = 100, requiredActiveMinutes = 5);

    companion object {
        fun fromPreferenceValue(value: String): StepActivityThreshold =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: MEDIUM
    }
}

class StepActivityRecognizer(
    private val context: Context,
    private val workoutDao: WorkoutDao,
) {

    companion object {
        private const val TAG = "StepActivityRecognizer"
        private const val COOLDOWN_AFTER_STOP_MS = 10 * 60 * 1000L
    }

    private var consecutiveActiveMinutes = 0
    private var lastAutoStartTime = 0L

    private val threshold: StepActivityThreshold
        get() = StepActivityThreshold.fromPreferenceValue(
            Instance.getInstance(context).userPreferences.stepActivityThreshold
        )

    fun onMinuteReceived(stepsInMinute: Int) {
        val threshold = threshold
        consecutiveActiveMinutes =
            if (stepsInMinute > threshold.stepsPerMinuteThreshold) consecutiveActiveMinutes + 1 else 0

        if (consecutiveActiveMinutes >= threshold.requiredActiveMinutes) {
            maybeStartRecording()
        }
    }

    private fun maybeStartRecording() {
        if (isRecordingRunning()) {
            WorkoutLogger.log(TAG, "Not auto-starting: a workout is already running")
            return
        }
        if (!isCooldownElapsed()) {
            WorkoutLogger.log(TAG, "Not auto-starting: cooldown after last workout not elapsed")
            return
        }
        if (!hasLocationPermission()) {
            WorkoutLogger.log(TAG, "Not auto-starting: location permission missing")
            return
        }

        WorkoutLogger.log(
            TAG,
            "Starting automatic workout (threshold=$threshold, consecutiveActiveMinutes=$consecutiveActiveMinutes)"
        )
        startRecording()
        // Require a fresh streak of active minutes and remember the attempt, so a recording that gets
        // auto-stopped and discarded (which leaves no workout in the DB) isn't restarted right away.
        consecutiveActiveMinutes = 0
        lastAutoStartTime = System.currentTimeMillis()
    }

    private fun isRecordingRunning(): Boolean {
        val event = RecordingStateChangeEvent.getCurrentOptional() ?: return false
        val state = event.newState
        return state == RecordingState.RUNNING || state == RecordingState.AUTO_PAUSE || state == RecordingState.MANUAL_PAUSE
    }

    private fun isCooldownElapsed(): Boolean {
        if (System.currentTimeMillis() - lastAutoStartTime < COOLDOWN_AFTER_STOP_MS) return false
        val lastEnd = workoutDao.getLastWorkout()?.end ?: return true
        if (lastEnd == -1L) return false // a workout is currently running according to DB
        return System.currentTimeMillis() - lastEnd > COOLDOWN_AFTER_STOP_MS
    }

    private fun hasLocationPermission(): Boolean {
        val hasFine =
            PermissionUtils.checkPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarse =
            PermissionUtils.checkPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasFine && !hasCoarse) return false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (!PermissionUtils.checkPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            ) return false
        }
        return true
    }

    private fun startRecording() {
        val intent = Intent(context, RecorderService::class.java).apply {
            putExtra(RecorderService.EXTRA_WORKOUT_TYPE, WorkoutTypeManager.WORKOUT_TYPE_ID_AUTO)
            putExtra(RecorderService.EXTRA_AUTO_STARTED, true)
        }
        context.startForegroundService(intent)
    }
}
