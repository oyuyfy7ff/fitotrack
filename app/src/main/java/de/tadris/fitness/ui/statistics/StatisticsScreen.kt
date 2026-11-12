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

package de.tadris.fitness.ui.statistics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.ui.steps.StepStatisticsAdapter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(onBack: () -> Unit) {
    val activity = LocalActivity.current ?: return
    val stepAdapter = remember { StepStatisticsAdapter(activity) }
    val stepDao = remember { Instance.getInstance(activity).db.stepDao() }
    val hasSteps by stepDao.hasAnyStepsFlow().collectAsStateWithLifecycle(initialValue = true)

    val pages = buildList {
        if (hasSteps) add(StatisticsPage.STEPS)
        add(StatisticsPage.OVERVIEW)
        add(StatisticsPage.HISTORY)
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    val titles = pages.map { stringResource(it.titleRes) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statistics)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage
            ) {
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = { Text(title) }
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false
            ) { page ->
                when (pages.getOrNull(page)) {
                    StatisticsPage.STEPS -> de.tadris.fitness.ui.statistics2.StatisticsScreen(
                        adapter = stepAdapter,
                        modifier = Modifier.fillMaxSize(),
                    )
                    StatisticsPage.OVERVIEW -> StatsOverviewScreen()
                    StatisticsPage.HISTORY -> StatsHistoryScreen()
                    null -> {}
                }
            }
        }
    }
}

private enum class StatisticsPage(val titleRes: Int) {
    STEPS(R.string.tabSteps),
    OVERVIEW(R.string.stats_overview_title),
    HISTORY(R.string.stats_history_title),
}
