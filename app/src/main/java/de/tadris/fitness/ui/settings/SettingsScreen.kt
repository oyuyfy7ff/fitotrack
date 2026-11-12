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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import de.tadris.fitness.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToInterfaceSettings: () -> Unit = {},
    onNavigateToRecordingSettings: () -> Unit = {},
    onNavigateToStepTracking: () -> Unit = {},
    onNavigateToBackupSettings: () -> Unit = {},
    onNavigateToWorkoutTypes: () -> Unit = {},
    onNavigateToDebugSettings: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
                ClickableSettingsItem(
                    title = stringResource(R.string.preferencesProfileTitle),
                    supporting = stringResource(R.string.preferencesProfileSummary),
                    icon = { Icon(painter = painterResource(R.drawable.ic_person), contentDescription = null) },
                    onClick = onNavigateToProfile,
                )
            }
            item {
                ClickableSettingsItem(
                    title = stringResource(R.string.preferencesUserInterfaceTitle),
                    supporting = stringResource(R.string.preferencesUserInterfaceSummary),
                    icon = { Icon(painter = painterResource(R.drawable.ic_palette), contentDescription = null) },
                    onClick = onNavigateToInterfaceSettings,
                )
            }
            item {
                ClickableSettingsItem(
                    title = stringResource(R.string.preferencesRecordingTitle),
                    supporting = stringResource(R.string.preferencesRecordingSummary),
                    icon = { Icon(painter = painterResource(R.drawable.ic_walk), contentDescription = null) },
                    onClick = onNavigateToRecordingSettings,
                )
            }
            item {
                ClickableSettingsItem(
                    title = stringResource(R.string.stepTrackingTitle),
                    supporting = stringResource(R.string.stepTrackingDescription),
                    icon = { Icon(painter = painterResource(R.drawable.ic_steps), contentDescription = null) },
                    onClick = onNavigateToStepTracking,
                )
            }
            item {
                ClickableSettingsItem(
                    title = stringResource(R.string.customWorkoutTypesTitle),
                    icon = { Icon(painter = painterResource(R.drawable.ic_list), contentDescription = null) },
                    onClick = onNavigateToWorkoutTypes,
                )
            }
            item {
                ClickableSettingsItem(
                    title = stringResource(R.string.preferencesBackupTitle),
                    supporting = stringResource(R.string.preferencesBackupSummary),
                    icon = { Icon(painter = painterResource(R.drawable.ic_database), contentDescription = null) },
                    onClick = onNavigateToBackupSettings,
                )
            }
            item {
                ClickableSettingsItem(
                    title = stringResource(R.string.preferencesDebugTitle),
                    icon = { Icon(painter = painterResource(R.drawable.ic_bug_report), contentDescription = null) },
                    onClick = onNavigateToDebugSettings,
                )
            }
            item {
                ClickableSettingsItem(
                    title = stringResource(R.string.aboutTitle),
                    icon = { Icon(painter = painterResource(R.drawable.ic_info), contentDescription = null) },
                    onClick = onNavigateToAbout,
                )
            }
        }
    }
}
