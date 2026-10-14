package fr.gildasledrogoff.fwknop.presentation.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview

/** System font scale from which single-line labels may wrap instead of being cut. */
internal const val LARGE_FONT_SCALE = 1.3f

internal fun scaledMaxLines(lines: Int, fontScale: Float): Int =
    if (fontScale >= LARGE_FONT_SCALE) lines * 2 else lines

/** [lines] at the default font size, twice as many with a large system font: unchanged layout otherwise. */
@Composable
@ReadOnlyComposable
fun scaledMaxLines(lines: Int): Int = scaledMaxLines(lines, LocalDensity.current.fontScale)

/** Largest system font size (Android 14+ goes up to 200 %), light and dark, on a compact phone. */
@Preview(name = "Font 200 %", widthDp = 360, heightDp = 780, fontScale = 2f, showBackground = true)
@Preview(
    name = "Font 200 %, dark",
    widthDp = 360,
    heightDp = 780,
    fontScale = 2f,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class LargeFontPreviews
