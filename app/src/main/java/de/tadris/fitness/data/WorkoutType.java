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

package de.tadris.fitness.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.Set;

import de.tadris.fitness.R;

@Entity(tableName = "workout_type")
public class WorkoutType implements Serializable {

    /**
     * String-ID
     */
    @PrimaryKey
    @NonNull
    public String id;

    /**
     * Display name of the activity type
     */
    public String title;

    /**
     * Minimum distance between 2 samples when used in GPS-workouts
     */
    @ColumnInfo(name = "min_distance")
    public int minDistance;

    /**
     * Color int
     */
    public int color;

    /**
     * Icon id of the workout type
     *
     * @see de.tadris.fitness.util.Icon
     */
    public String icon;

    /**
     * Metabolic equivalent of task:
     * This is a measure for how much energy is burned while doing an activity.
     */
    @ColumnInfo(name = "met")
    public int MET;

    /**
     * Comma-separated list of {@link WorkoutCapability} IDs (e.g. "gps,steps").
     *
     * @see WorkoutCapability
     */
    @ColumnInfo(name = "capabilities")
    @NonNull
    public String capabilities;

    /**
     * Specified the plural resource ID for the unit measured (only for indoor workouts)
     * treadmill -> "steps"
     */
    @Ignore
    @PluralsRes
    public int repeatingExerciseName;

    @Ignore
    public WorkoutType(@NonNull String id, String title, int minDistance, int color, String icon, int MET, String capabilities) {
        this(id, title, minDistance, color, icon, MET, capabilities, -1);
    }

    @Ignore
    public WorkoutType(@NonNull String id, String title, int minDistance, int color, String icon, int MET, String capabilities, int repeatingExerciseName) {
        this.id = id;
        this.title = title;
        this.minDistance = minDistance;
        this.color = color;
        this.icon = icon;
        this.MET = MET;
        this.capabilities = capabilities;
        this.repeatingExerciseName = repeatingExerciseName;
    }

    public WorkoutType() {
    }

    @Ignore
    @JsonIgnore
    public boolean hasCapability(WorkoutCapability capability) {
        return WorkoutCapability.parseSet(capabilities).contains(capability);
    }

    @Ignore
    @JsonIgnore
    public boolean isTimeOnly() {
        return WorkoutCapability.parseSet(capabilities).isEmpty();
    }

    /**
     * Returns the repetition counting capability (e.g. steps or jumps) of this type, or null if it doesn't count repetitions.
     */
    @Ignore
    @JsonIgnore
    @Nullable
    public WorkoutCapability getRepetitionCapability() {
        for (WorkoutCapability capability : WorkoutCapability.parseSet(capabilities)) {
            if (capability.isRepetitionCounting()) return capability;
        }
        return null;
    }

    @Ignore
    @JsonIgnore
    public boolean hasRepetitionCounting() {
        return getRepetitionCapability() != null;
    }

    /**
     * Returns the preference mode key for this workout type.
     * GPS-capable workouts use "gps", time-only workouts use "time_only", others use "indoor".
     * These keys are used in SharedPreferences and must remain stable.
     */
    @Ignore
    @JsonIgnore
    public String getPreferenceMode() {
        if (hasCapability(WorkoutCapability.GPS)) return WorkoutCapability.GPS.getId();
        if (isTimeOnly()) return WorkoutCapability.PREFERENCE_MODE_TIME_ONLY;
        return WorkoutCapability.PREFERENCE_MODE_NON_GPS;
    }

    @Ignore
    @JsonIgnore
    @PluralsRes
    public int getRepeatingExerciseNameOrDefault() {
        if (repeatingExerciseName > 0) return repeatingExerciseName;
        WorkoutCapability repetitionCapability = getRepetitionCapability();
        return repetitionCapability != null ? repetitionCapability.getRepetitionName() : R.plurals.workoutRepetition;
    }

    @NonNull
    @Override
    public String toString() {
        return id;
    }
}
