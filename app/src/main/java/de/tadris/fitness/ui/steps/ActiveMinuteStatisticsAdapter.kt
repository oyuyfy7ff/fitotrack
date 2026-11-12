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
import de.tadris.fitness.Instance
import de.tadris.fitness.ui.statistics2.CountSample
import de.tadris.fitness.ui.statistics2.StatisticsAdapter

class ActiveMinuteStatisticsAdapter(app: Application) : StatisticsAdapter {

    private val dao = Instance.getInstance(app).db.stepDao()

    override fun getCountsInRange(start: Long, end: Long): List<CountSample> =
        dao.getStepsInRange(start, end).map { CountSample(it.windowStart, it.activeMinutes) }

    override val dailyGoal: Int = 30

    override fun format(count: Int): String = "$count min"
}
