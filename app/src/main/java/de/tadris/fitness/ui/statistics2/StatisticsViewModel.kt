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

package de.tadris.fitness.ui.statistics2

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.ui.components.BarChartData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

enum class StatisticsPeriod(val labelRes: Int) {
    DAY(R.string.day),
    WEEK(R.string.week),
    MONTH(R.string.month),
    YEAR(R.string.year),
}

data class StatisticsProgressData(
    val total: Int,
    val dailyAverage: Int,
    val dailyGoal: Int,
)

class StatisticsViewModel(
    app: Application,
    private val adapter: StatisticsAdapter,
) : AndroidViewModel(app) {

    private val prefs = Instance.getInstance(app).userPreferences

    val selectedPeriod = MutableStateFlow(StatisticsPeriod.WEEK)
    val timeFrameOffset = MutableStateFlow(0)

    fun setPeriod(period: StatisticsPeriod) {
        timeFrameOffset.value = 0
        selectedPeriod.value = period
    }

    fun shiftOffset(delta: Int) {
        timeFrameOffset.value += delta
    }

    fun setReferenceDate(dateMillis: Long) {
        timeFrameOffset.value = when (selectedPeriod.value) {
            StatisticsPeriod.DAY -> {
                val targetMidnight = midnightOf(dateMillis)
                ((targetMidnight - todayMidnight()) / DAY_MS).toInt()
            }

            StatisticsPeriod.WEEK -> {
                val currentWeekStart = weekRange(prefs.firstDayOfWeek, 0).first
                val targetWeekStart = weekStartContaining(dateMillis, prefs.firstDayOfWeek)
                ((targetWeekStart - currentWeekStart) / (7 * DAY_MS)).toInt()
            }

            StatisticsPeriod.MONTH -> {
                val now = Calendar.getInstance()
                val target = Calendar.getInstance().also { it.timeInMillis = dateMillis }
                (target.get(Calendar.YEAR) - now.get(Calendar.YEAR)) * 12 +
                        (target.get(Calendar.MONTH) - now.get(Calendar.MONTH))
            }

            StatisticsPeriod.YEAR -> {
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val target = Calendar.getInstance().also { it.timeInMillis = dateMillis }
                target.get(Calendar.YEAR) - currentYear
            }
        }
    }

    val referenceTimestamp: StateFlow<Long> =
        combine(selectedPeriod, timeFrameOffset) { period, offset ->
            when (period) {
                StatisticsPeriod.DAY -> todayMidnight() + offset * DAY_MS
                StatisticsPeriod.WEEK -> weekRange(prefs.firstDayOfWeek, offset).first
                StatisticsPeriod.MONTH -> monthRange(offset).start
                StatisticsPeriod.YEAR -> yearRange(offset).first
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), todayMidnight())

    val chartData: StateFlow<BarChartData> = combine(
        selectedPeriod,
        timeFrameOffset,
    ) { period, offset ->
        buildChartData(period, offset)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BarChartData(emptyList(), emptyList())
        )

    val progressData: StateFlow<StatisticsProgressData> = combine(
        chartData,
        selectedPeriod,
    ) { data, period ->
        val total = data.entries.sum()
        val avg = when (period) {
            StatisticsPeriod.DAY -> total
            else -> {
                val activeDays = data.entries.count { it > 0 }
                if (activeDays > 0) total / activeDays else 0
            }
        }
        StatisticsProgressData(total, avg, adapter.dailyGoal)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StatisticsProgressData(0, 0, adapter.dailyGoal)
    )

    private fun buildChartData(period: StatisticsPeriod, offset: Int): BarChartData {
        return when (period) {
            StatisticsPeriod.DAY -> buildDayData(offset)
            StatisticsPeriod.WEEK -> buildWeekData(offset)
            StatisticsPeriod.MONTH -> buildMonthData(offset)
            StatisticsPeriod.YEAR -> buildYearData(offset)
        }
    }

    private fun buildDayData(offset: Int): BarChartData {
        val dayStart = todayMidnight() + offset * DAY_MS
        val dayEnd = dayStart + DAY_MS
        val samples = adapter.getCountsInRange(dayStart, dayEnd)
        val byWindow = samples.associateBy { windowIndex(it.windowStart, dayStart) }

        val slotCount = 96
        val entries = List(slotCount) { i -> byWindow[i]?.count ?: 0 }
        return BarChartData(entries, listOf("0", "6", "12", "18", "24"), entries.maxOrNull())
    }

    private fun buildWeekData(offset: Int): BarChartData {
        val firstDay = prefs.firstDayOfWeek
        val (weekStart, weekEnd) = weekRange(firstDay, offset)
        val samples = adapter.getCountsInRange(weekStart, weekEnd)

        val byDay = mutableMapOf<Int, Int>()
        for (sample in samples) {
            val day = dayOfWeekIndex(sample.windowStart, weekStart)
            byDay[day] = (byDay[day] ?: 0) + sample.count
        }
        val entries = List(7) { i -> byDay[i] ?: 0 }

        val shortWeekdays = DateFormatSymbols.getInstance(Locale.getDefault()).shortWeekdays
        val cal = Calendar.getInstance()
        cal.timeInMillis = weekStart
        val labels = List(7) { i ->
            val c = cal.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, i)
            shortWeekdays[c.get(Calendar.DAY_OF_WEEK)]
        }
        return BarChartData(entries, labels, adapter.dailyGoal)
    }

    private fun buildMonthData(offset: Int): BarChartData {
        val (monthStart, monthEnd, daysInMonth) = monthRange(offset)
        val samples = adapter.getCountsInRange(monthStart, monthEnd)
        val byDay = mutableMapOf<Int, Int>()
        for (sample in samples) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = sample.windowStart
            val day = cal.get(Calendar.DAY_OF_MONTH) - 1
            byDay[day] = (byDay[day] ?: 0) + sample.count
        }
        val entries = List(daysInMonth) { i -> byDay[i] ?: 0 }
        val labels = (1..daysInMonth step 7).map { it.toString() }
        return BarChartData(entries, labels, adapter.dailyGoal)
    }

    private fun buildYearData(offset: Int): BarChartData {
        val (yearStart, yearEnd) = yearRange(offset)
        val samples = adapter.getCountsInRange(yearStart, yearEnd)
        val byMonth = mutableMapOf<Int, Int>()
        for (sample in samples) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = sample.windowStart
            val month = cal.get(Calendar.MONTH)
            byMonth[month] = (byMonth[month] ?: 0) + sample.count
        }
        val entries = List(12) { i -> byMonth[i] ?: 0 }
        val labels = DateFormatSymbols.getInstance(Locale.getDefault()).shortMonths
            .take(12)
            .mapIndexed { i, name -> if (i % 2 == 0) name else "" }
        return BarChartData(entries, labels, entries.maxOrNull())
    }

    private fun windowIndex(timestamp: Long, dayStart: Long): Int =
        ((timestamp - dayStart) / WINDOW_MS).toInt()

    private fun dayOfWeekIndex(timestamp: Long, weekStart: Long): Int =
        ((timestamp - weekStart) / DAY_MS).toInt().coerceIn(0, 6)

    private fun weekRange(firstDay: Int, offset: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromStart = (dow - firstDay + 7) % 7
        cal.add(Calendar.DAY_OF_YEAR, -daysFromStart + offset * 7)
        val weekStart = cal.timeInMillis
        return weekStart to weekStart + 7 * DAY_MS
    }

    private fun weekStartContaining(dateMillis: Long, firstDay: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = dateMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromStart = (dow - firstDay + 7) % 7
        cal.add(Calendar.DAY_OF_YEAR, -daysFromStart)
        return cal.timeInMillis
    }

    private data class MonthRange(val start: Long, val end: Long, val daysInMonth: Int)

    private fun monthRange(offset: Int): MonthRange {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.MONTH, offset)
        val monthStart = cal.timeInMillis
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return MonthRange(monthStart, monthStart + daysInMonth * DAY_MS, daysInMonth)
    }

    private fun yearRange(offset: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.YEAR, offset)
        cal.set(Calendar.MONTH, Calendar.JANUARY)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val yearStart = cal.timeInMillis
        cal.set(Calendar.MONTH, Calendar.DECEMBER)
        cal.set(Calendar.DAY_OF_MONTH, 31)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        return yearStart to cal.timeInMillis
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val WINDOW_MS = 15 * 60 * 1000L

        fun todayMidnight(): Long {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun midnightOf(millis: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = millis
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }

    class Factory(
        private val app: Application,
        private val adapter: StatisticsAdapter,
    ) : ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return StatisticsViewModel(app, adapter) as T
        }
    }
}
