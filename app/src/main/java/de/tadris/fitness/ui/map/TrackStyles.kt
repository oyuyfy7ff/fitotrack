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

package de.tadris.fitness.ui.map

object TrackStyles {
    fun gradientStrategy(trackStyle: String): GradientColoringStrategy? = when (trackStyle) {
        "purple_rain" -> GradientColoringStrategy.fromPattern(GradientColoringStrategy.PATTERN_PURPLE, true)
        "pink_mist" -> GradientColoringStrategy.fromPattern(GradientColoringStrategy.PATTERN_PINK, false)
        "rainbow_warrior" -> GradientColoringStrategy.fromPattern(GradientColoringStrategy.PATTERN_MAP, true)
        "height_map" -> GradientColoringStrategy.fromPattern(GradientColoringStrategy.PATTERN_HEIGHT_MAP, true)
        "bright_night" -> GradientColoringStrategy.fromPattern(GradientColoringStrategy.PATTERN_BRIGHT, false)
        "mondriaan" -> GradientColoringStrategy.fromPattern(GradientColoringStrategy.PATTERN_YELLOW_RED_BLUE, false)
        else -> null
    }

    fun coloringStrategy(trackStyle: String, primaryColor: Int): ColoringStrategy =
        gradientStrategy(trackStyle) ?: SimpleColoringStrategy(primaryColor)
}
