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

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import de.tadris.fitness.R

enum class WorkoutCapability(
    val id: String,
    @StringRes val titleRes: Int,
    val repetitionTypeId: Int? = null,
    @PluralsRes val repetitionName: Int = 0,
) {
    GPS("gps", R.string.workoutCapabilityGps),
    STEPS("steps", R.string.workoutCapabilitySteps, RepetitionSample.REPETITION_TYPE_STEP, R.plurals.workoutStep),
    JUMPS("jumps", R.string.workoutCapabilityJumps, RepetitionSample.REPETITION_TYPE_JUMP, R.plurals.workoutJump),
    PUSH_UPS("push_ups", R.string.workoutCapabilityPushUps, RepetitionSample.REPETITION_TYPE_PUSH_UP, R.plurals.workoutPushUp),
    PULL_UPS("pull_ups", R.string.workoutCapabilityPullUps, RepetitionSample.REPETITION_TYPE_PULL_UP, R.plurals.workoutPullUp);

    val isRepetitionCounting: Boolean
        get() = repetitionTypeId != null

    companion object {
        /**
         * Preference mode key used for workouts without GPS capability.
         * Kept as "indoor" for backwards compatibility with existing SharedPreferences.
         */
        const val PREFERENCE_MODE_NON_GPS = "indoor"

        /**
         * Preference mode key used for time-only workouts (no capabilities at all).
         */
        const val PREFERENCE_MODE_TIME_ONLY = "time_only"

        @JvmStatic
        fun fromId(id: String): WorkoutCapability? = entries.find { it.id == id }

        @JvmStatic
        fun parseSet(value: String?): Set<WorkoutCapability> {
            if (value.isNullOrEmpty()) return emptySet()
            return value.split(",").mapNotNull { fromId(it.trim()) }.toSet()
        }

        @JvmStatic
        fun toStorageString(capabilities: Set<WorkoutCapability>): String =
            capabilities.joinToString(",") { it.id }
    }
}
