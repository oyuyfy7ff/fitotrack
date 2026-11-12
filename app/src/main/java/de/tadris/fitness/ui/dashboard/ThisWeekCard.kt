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
package de.tadris.fitness.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.Workout
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.util.Icon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ThisWeekCard(
    weeklyWorkouts: List<Workout>,
    rangeStartMillis: Long,
) {
    val context = LocalContext.current
    val instance = Instance.getInstance(context)

    val totalDistance = weeklyWorkouts.sumOf { it.distance }.toInt()
    val totalDuration = weeklyWorkouts.sumOf { it.activeDuration }
    val workoutCount = weeklyWorkouts.size

    val distanceText = instance.distanceUnitUtils.getDistance(totalDistance)
    val timeText = instance.distanceUnitUtils.getHourMinuteTime(totalDuration)

    val todayLabel = stringResource(R.string.today)
    val rangeStartLabel = remember(rangeStartMillis) {
        SimpleDateFormat("EEE d", Locale.getDefault()).format(Date(rangeStartMillis))
    }
    val weekRangeText = "$rangeStartLabel — ${todayLabel.lowercase()}"

    val byType = remember(weeklyWorkouts) {
        weeklyWorkouts.groupBy { it.workoutTypeId }
            .entries.sortedByDescending { it.value.size }
            .take(3)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.homeLast7DaysWorkouts).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = weekRangeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryStatColumn(
                    label = "Distance",
                    value = distanceText,
                    modifier = Modifier.weight(1f),
                )
                SummaryStatColumn(
                    label = "Time",
                    value = timeText,
                    modifier = Modifier.weight(1f),
                )
                SummaryStatColumn(
                    label = "Workouts",
                    value = workoutCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }

            if (byType.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val resources = LocalResources.current
                    byType.forEach { (_, workouts) ->
                        val workoutType = workouts.first().getWorkoutType(context)
                        val totalDistance = workouts.sumOf { it.distance }.toInt()
                        val totalReps = workouts.sumOf { it.repetitions }
                        val metric = if (totalDistance == 0 && totalReps > 0) {
                            "$totalReps ${
                                resources.getQuantityString(
                                    workoutType.repeatingExerciseNameOrDefault,
                                    totalReps
                                )
                            }"
                        } else {
                            instance.distanceUnitUtils.getDistance(totalDistance)
                        }
                        SportTypeCard(
                            workoutType = workoutType,
                            metric = metric,
                            count = workouts.size,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - byType.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SummaryStatColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun SportTypeCard(
    workoutType: WorkoutType,
    metric: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = Color(workoutType.color),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(Icon.getIcon(workoutType.icon)),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = metric,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${workoutType.title} · ${count}×",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
