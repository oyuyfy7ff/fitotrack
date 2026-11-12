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

package de.tadris.fitness.ui.steps

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.tadris.fitness.Instance
import de.tadris.fitness.steps.StepTrackingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

class StepsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = Instance.getInstance(app).db.stepDao()
    private val prefs = Instance.getInstance(app).userPreferences

    val serviceRunning: StateFlow<Boolean> = StepTrackingService.isRunning

    private val _dailyStepGoal = MutableStateFlow(prefs.dailyStepGoal)
    val dailyStepGoal: StateFlow<Int> = _dailyStepGoal

    fun setDailyStepGoal(goal: Int) {
        prefs.dailyStepGoal = goal
        _dailyStepGoal.value = goal
    }

    private val _autoStartRecordingEnabled = MutableStateFlow(prefs.autoStartRecordingEnabled)
    val autoStartRecordingEnabled: StateFlow<Boolean> = _autoStartRecordingEnabled

    fun setAutoStartRecordingEnabled(enabled: Boolean) {
        prefs.autoStartRecordingEnabled = enabled
        _autoStartRecordingEnabled.value = enabled
    }

    private val _stepActivityThreshold = MutableStateFlow(prefs.stepActivityThreshold)
    val stepActivityThreshold: StateFlow<String> = _stepActivityThreshold

    fun setStepActivityThreshold(threshold: String) {
        prefs.stepActivityThreshold = threshold
        _stepActivityThreshold.value = threshold
    }

    fun stopTracking() {
        prefs.stepTrackingEnabled = false
        getApplication<Application>().stopService(
            Intent(getApplication(), StepTrackingService::class.java)
        )
    }

    val todaySteps: StateFlow<Int> = combine(
        dao.getTodayStepCountFlow(todayMidnight(), todayMidnight() + DAY_MS),
        StepTrackingService.pendingSteps,
    ) { persisted, pending -> persisted + pending }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        fun todayMidnight(): Long {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }
}
