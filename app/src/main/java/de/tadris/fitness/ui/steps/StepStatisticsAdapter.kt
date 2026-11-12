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
import android.content.Context
import de.tadris.fitness.Instance
import de.tadris.fitness.ui.statistics2.CountSample
import de.tadris.fitness.ui.statistics2.StatisticsAdapter
import java.text.NumberFormat

class StepStatisticsAdapter(app: Context) : StatisticsAdapter {

    private val dao = Instance.getInstance(app).db.stepDao()
    private val prefs = Instance.getInstance(app).userPreferences
    private val numberFormat = NumberFormat.getNumberInstance()

    override fun getCountsInRange(start: Long, end: Long): List<CountSample> =
        dao.getStepsInRange(start, end).map { CountSample(it.windowStart, it.steps) }

    override val dailyGoal: Int get() = prefs.dailyStepGoal

    override fun format(count: Int): String = numberFormat.format(count)
}
