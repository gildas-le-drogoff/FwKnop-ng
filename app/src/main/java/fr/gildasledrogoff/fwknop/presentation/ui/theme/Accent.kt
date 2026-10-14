package fr.gildasledrogoff.fwknop.presentation.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/** A tonal container/content pair taken from the Material scheme, so it follows light/dark and dynamic color. */
@Immutable
data class ConfigAccent(val container: Color, val content: Color)

@Composable
@ReadOnlyComposable
fun configAccent(nickname: String): ConfigAccent = configAccent(nickname, MaterialTheme.colorScheme)

/** Stable per nickname: the same config always gets the same role. */
internal fun configAccent(nickname: String, colors: ColorScheme): ConfigAccent =
    when (Math.floorMod(nickname.hashCode(), 3)) {
        0 -> ConfigAccent(colors.primaryContainer, colors.onPrimaryContainer)
        1 -> ConfigAccent(colors.secondaryContainer, colors.onSecondaryContainer)
        else -> ConfigAccent(colors.tertiaryContainer, colors.onTertiaryContainer)
    }
