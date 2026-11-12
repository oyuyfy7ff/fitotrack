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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.ui.components.BarChart
import de.tadris.fitness.ui.components.BarChartData

@Composable
internal fun StepsTodayCard(
    todaySteps: Int,
    stepGoal: Int,
    todayActiveMinutes: Int,
    todayDistanceMeters: Int,
    weeklyStepChart: BarChartData,
    onGoalClick: () -> Unit = {},
    onActiveMinuteClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val distanceText = remember(todayDistanceMeters) {
        Instance.getInstance(context).distanceUnitUtils.getDistance(todayDistanceMeters)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.homeStepsToday).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactStepRing(
                    steps = todaySteps,
                    goal = stepGoal,
                    modifier = Modifier
                        .size(130.dp)
                        .clickable(onClick = onGoalClick),
                )
                Spacer(Modifier.width(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledValue(
                        label = stringResource(R.string.homeActiveLabel).uppercase(),
                        value = "$todayActiveMinutes min",
                        onClick = onActiveMinuteClick,
                    )
                    LabeledValue(
                        label = stringResource(R.string.homeDistanceLabel).uppercase(),
                        value = distanceText,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = stringResource(R.string.homeLast7DaysSteps).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )

            if (weeklyStepChart.entries.isNotEmpty()) {
                BarChart(
                    data = weeklyStepChart.copy(horizontal = stepGoal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    animate = true,
                )
            }
        }
    }
}

@Composable
internal fun CompactStepRing(
    steps: Int,
    goal: Int,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(steps, goal) {
        progress = if (goal > 0) (steps.toFloat() / goal).coerceIn(0f, 1f) else 0f
    }
    val displayedProgress by animateFloatAsState(
        progress,
        ProgressIndicatorDefaults.ProgressAnimationSpec
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { displayedProgress },
            modifier = Modifier.size(130.dp),
            strokeWidth = 8.dp,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = steps.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "of $goal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun LabeledValue(label: String, value: String, onClick: (() -> Unit)? = null) {
    Column(modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier) {
        Text(
            text = label,
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
