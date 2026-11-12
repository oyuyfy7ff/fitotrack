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

package de.tadris.fitness.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDao {

    @Query("SELECT * FROM step_sample WHERE windowStart = :windowStart")
    fun getStepSample(windowStart: Long): StepSample?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStepSample(sample: StepSample)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStepSamples(samples: List<StepSample>)

    @Query("SELECT * FROM step_sample WHERE windowStart >= :from AND windowStart < :to ORDER BY windowStart ASC")
    fun getStepsInRange(from: Long, to: Long): List<StepSample>

    @Query("SELECT DISTINCT CAST(strftime('%Y', windowStart / 1000, 'unixepoch') AS INTEGER) FROM step_sample")
    fun getDistinctYears(): List<Int>

    @Query("SELECT * FROM step_sample WHERE CAST(strftime('%Y', windowStart / 1000, 'unixepoch') AS INTEGER) = :year ORDER BY windowStart ASC")
    fun getStepsForYear(year: Int): List<StepSample>

    @Query("SELECT COALESCE(SUM(steps), 0) FROM step_sample WHERE windowStart >= :dayStart AND windowStart < :dayEnd")
    fun getTodayStepCountFlow(dayStart: Long, dayEnd: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(activeMinutes), 0) FROM step_sample WHERE windowStart >= :dayStart AND windowStart < :dayEnd")
    fun getTodayActiveMinutesFlow(dayStart: Long, dayEnd: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM step_sample")
    fun countStepSamples(): Long

    @Query("SELECT COUNT(*) > 0 FROM step_sample")
    fun hasAnyStepsFlow(): Flow<Boolean>
}
