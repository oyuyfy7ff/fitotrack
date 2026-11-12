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

package de.tadris.fitness.ui.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.tadris.fitness.Instance
import de.tadris.fitness.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val instance = remember { Instance.getInstance(context) }
    val prefs = remember { instance.userPreferences }

    remember { instance.distanceUnitUtils.setUnit(); true }
    val unitSystem = remember { instance.distanceUnitUtils.distanceUnitSystem }

    var weightKg by remember { mutableIntStateOf(prefs.userWeight) }
    var showWeightDialog by remember { mutableStateOf(false) }

    var stepLengthMeters by remember { mutableFloatStateOf(prefs.stepLength) }
    var showStepLengthDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preferencesProfileTitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                val displayWeight = Math.round(unitSystem.getWeightFromKilogram(weightKg.toDouble()))
                val weightUnit = unitSystem.weightUnit
                ClickableSettingsItem(
                    title = stringResource(R.string.pref_weight),
                    supporting = "$displayWeight $weightUnit",
                    onClick = { showWeightDialog = true },
                )
            }

            item {
                val displayStepLength = Math.round(unitSystem.getDistanceFromCentimeters((stepLengthMeters * 100).toDouble()))
                val stepUnit = unitSystem.reallyShortDistanceUnit
                ClickableSettingsItem(
                    title = stringResource(R.string.pref_step_length),
                    supporting = "$displayStepLength $stepUnit",
                    onClick = { showStepLengthDialog = true },
                )
            }
        }
    }

    if (showWeightDialog) {
        SliderPickerDialog(
            title = stringResource(R.string.pref_weight),
            initialValue = unitSystem.getWeightFromKilogram(weightKg.toDouble()).toFloat(),
            valueRange = unitSystem.getWeightFromKilogram(20.0).toFloat()..unitSystem.getWeightFromKilogram(400.0).toFloat(),
            unit = unitSystem.weightUnit,
            onDismiss = { showWeightDialog = false },
            onConfirm = { displayValue ->
                val kilograms = Math.round(unitSystem.getKilogramFromUnit(displayValue.toDouble())).toInt()
                prefs.preferences.edit().putInt("weight", kilograms).apply()
                weightKg = kilograms
                showWeightDialog = false
            },
        )
    }

    if (showStepLengthDialog) {
        SliderPickerDialog(
            title = stringResource(R.string.pref_step_length),
            initialValue = unitSystem.getDistanceFromCentimeters((stepLengthMeters * 100).toDouble()).toFloat(),
            valueRange = unitSystem.getDistanceFromCentimeters(50.0).toFloat()..unitSystem.getDistanceFromCentimeters(150.0).toFloat(),
            unit = unitSystem.reallyShortDistanceUnit,
            onDismiss = { showStepLengthDialog = false },
            onConfirm = { displayValue ->
                val meters = (unitSystem.getCentimetersFromReallyShortDistance(displayValue.toDouble()) / 100).toFloat()
                prefs.stepLength = meters
                stepLengthMeters = meters
                showStepLengthDialog = false
            },
        )
    }
}
