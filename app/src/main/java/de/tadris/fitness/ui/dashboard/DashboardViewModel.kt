package de.tadris.fitness.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.tadris.fitness.Instance
import de.tadris.fitness.data.Workout
import de.tadris.fitness.steps.StepTrackingService
import de.tadris.fitness.ui.components.BarChartData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val workoutDao = Instance.getInstance(app).db.workoutDao()
    private val stepDao = Instance.getInstance(app).db.stepDao()
    private val prefs = Instance.getInstance(app).userPreferences

    val stepTrackingEnabled: Boolean get() = prefs.stepTrackingEnabled

    private val _stepTrackingPromptDismissed = MutableStateFlow(prefs.stepTrackingPromptDismissed)
    val stepTrackingPromptDismissed: StateFlow<Boolean> = _stepTrackingPromptDismissed

    fun dismissStepTrackingPrompt() {
        prefs.stepTrackingPromptDismissed = true
        _stepTrackingPromptDismissed.value = true
    }

    private val _stepGoal = MutableStateFlow(prefs.dailyStepGoal)
    val stepGoal: StateFlow<Int> = _stepGoal

    fun setDailyStepGoal(goal: Int) {
        prefs.dailyStepGoal = goal
        _stepGoal.value = goal
    }

    val todaySteps: StateFlow<Int> = combine(
        stepDao.getTodayStepCountFlow(todayMidnight(), todayMidnight() + DAY_MS),
        StepTrackingService.pendingSteps,
    ) { persisted, pending -> persisted + pending }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val todayActiveMinutes: StateFlow<Int> =
        stepDao.getTodayActiveMinutesFlow(todayMidnight(), todayMidnight() + DAY_MS)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val todayDistanceMeters: StateFlow<Int> = todaySteps
        .map { steps -> (steps * prefs.measurements.stepLength).toInt() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val weeklyStepChart: StateFlow<BarChartData> = combine(
        todaySteps,
        StepTrackingService.pendingSteps,
    ) { _, _ -> buildLast7DaysStepData() }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BarChartData(emptyList(), emptyList())
        )

    val workouts: StateFlow<List<Workout>> = workoutDao.getWorkoutsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weeklyWorkouts: StateFlow<List<Workout>> = workoutDao.getWorkoutsFlow()
        .map { workouts ->
            val rangeStart = todayMidnight() - 6 * DAY_MS
            val rangeEnd = todayMidnight() + DAY_MS
            workouts.filter { it.start >= rangeStart && it.start < rangeEnd }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val last7DaysStartMillis: Long get() = todayMidnight() - 6 * DAY_MS

    private fun buildLast7DaysStepData(): BarChartData {
        val rangeStart = todayMidnight() - 6 * DAY_MS
        val rangeEnd = todayMidnight() + DAY_MS
        val samples = stepDao.getStepsInRange(rangeStart, rangeEnd)

        val byDay = mutableMapOf<Int, Int>()
        for (sample in samples) {
            val day = ((sample.windowStart - rangeStart) / DAY_MS).toInt().coerceIn(0, 6)
            byDay[day] = (byDay[day] ?: 0) + sample.steps
        }

        byDay[6] = (byDay[6] ?: 0) + StepTrackingService.pendingSteps.value

        val entries = List(7) { i -> byDay[i] ?: 0 }
        val shortWeekdays = DateFormatSymbols.getInstance(Locale.getDefault()).shortWeekdays
        val dayCal = Calendar.getInstance()
        dayCal.timeInMillis = rangeStart
        val labels = List(7) { i ->
            val c = dayCal.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, i)
            shortWeekdays[c.get(Calendar.DAY_OF_WEEK)]
        }
        return BarChartData(entries, labels)
    }

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