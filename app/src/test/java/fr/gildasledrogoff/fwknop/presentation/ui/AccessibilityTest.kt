// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertWithMessage
import fr.gildasledrogoff.fwknop.presentation.ui.components.scaledMaxLines
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DarkColors
import fr.gildasledrogoff.fwknop.presentation.ui.theme.LightColors
import fr.gildasledrogoff.fwknop.presentation.ui.theme.configAccent
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class ThemeContrastTest {
    private val schemes = mapOf("light" to LightColors, "dark" to DarkColors)
    @Test
    fun `text is readable on every surface it is drawn on`() = schemes.forEach { (name, c) ->
        val surfaces = c.surfaces()
        for ((surfaceName, surface) in surfaces) {
            assertContrast("$name onSurface/$surfaceName", c.onSurface, surface, TEXT)
            assertContrast("$name onSurfaceVariant/$surfaceName", c.onSurfaceVariant, surface, TEXT)

            assertContrast("$name primary/$surfaceName", c.primary, surface, TEXT)
            assertContrast("$name error/$surfaceName", c.error, surface, TEXT)
        }
    }
    @Test
    fun `content colors are readable on their containers`() = schemes.forEach { (name, c) ->
        listOf(
            Triple("onPrimary", c.onPrimary, c.primary),
            Triple("onPrimaryContainer", c.onPrimaryContainer, c.primaryContainer),
            Triple("onSecondaryContainer", c.onSecondaryContainer, c.secondaryContainer),
            Triple("onTertiaryContainer", c.onTertiaryContainer, c.tertiaryContainer),
            Triple("onErrorContainer", c.onErrorContainer, c.errorContainer),
        ).forEach { (label, fg, bg) -> assertContrast("$name $label", fg, bg, TEXT) }
    }
    @Test
    fun `field outlines stand out`() = schemes.forEach { (name, c) ->
        c.surfaces().forEach { (surfaceName, surface) ->
            assertContrast("$name outline/$surfaceName", c.outline, surface, GRAPHICS)
        }
    }
    @Test
    fun `config icons are readable on their tonal badge`() = schemes.forEach { (name, c) ->
        NICKNAMES.forEach { nickname ->
            val accent = configAccent(nickname, c)
            assertContrast("$name accent $nickname", accent.content, accent.container, GRAPHICS)
        }
    }
    private fun ColorScheme.surfaces() = mapOf(
        "surface" to surface,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
    )
    private fun assertContrast(label: String, foreground: Color, background: Color, minimum: Float) {
        val ratio = contrast(foreground, background)
        assertWithMessage("$label contrast %s", "%.2f".format(ratio)).that(ratio).isAtLeast(minimum)
    }
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }
    private companion object {
        const val TEXT = 4.5f
        const val GRAPHICS = 3f
        val NICKNAMES = (0 until 360).map { "config-$it" } + listOf("home", "office-nat", "router", "", "é")
    }
}
class LargeFontTest {
    @Test
    fun `labels keep their line count at the default size`() {
        assertWithMessage("1.0").that(scaledMaxLines(1, 1f)).isEqualTo(1)
        assertWithMessage("1.15").that(scaledMaxLines(1, 1.15f)).isEqualTo(1)
    }
    @Test
    fun `labels may wrap with a large system font`() {
        assertWithMessage("1.3").that(scaledMaxLines(1, 1.3f)).isEqualTo(2)
        assertWithMessage("2.0").that(scaledMaxLines(1, 2f)).isEqualTo(2)
    }
}
