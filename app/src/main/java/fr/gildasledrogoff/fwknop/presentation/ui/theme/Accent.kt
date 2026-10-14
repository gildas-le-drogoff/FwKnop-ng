// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color


@Immutable
data class ConfigAccent(val container: Color, val content: Color)

@Composable
@ReadOnlyComposable
fun configAccent(nickname: String): ConfigAccent = configAccent(nickname, MaterialTheme.colorScheme)


internal fun configAccent(nickname: String, colors: ColorScheme): ConfigAccent =
    when (Math.floorMod(nickname.hashCode(), 3)) {
        0 -> ConfigAccent(colors.primaryContainer, colors.onPrimaryContainer)
        1 -> ConfigAccent(colors.secondaryContainer, colors.onSecondaryContainer)
        else -> ConfigAccent(colors.tertiaryContainer, colors.onTertiaryContainer)
    }
