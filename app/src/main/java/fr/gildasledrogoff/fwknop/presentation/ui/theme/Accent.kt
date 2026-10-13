package fr.gildasledrogoff.fwknop.presentation.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance


@Composable
@ReadOnlyComposable
fun configAccent(nickname: String): Color {
    val hue = Math.floorMod(nickname.hashCode() * HUE_SPREAD, 360).toFloat()
    return if (LocalDarkTheme.current) {
        accent(hue, saturation = 0.60f, lightness = 0.68f, step = LIGHTNESS_STEP) { it >= DARK_MIN_LUMINANCE }
    } else {
        accent(hue, saturation = 0.65f, lightness = 0.38f, step = -LIGHTNESS_STEP) { it <= LIGHT_MAX_LUMINANCE }
    }
}

private inline fun accent(
    hue: Float,
    saturation: Float,
    lightness: Float,
    step: Float,
    contrasted: (luminance: Float) -> Boolean,
): Color {
    var l = lightness
    var color = Color.hsl(hue, saturation, l)
    while (!contrasted(color.luminance()) && l + step in 0f..1f) {
        l += step
        color = Color.hsl(hue, saturation, l)
    }
    return color
}


private const val HUE_SPREAD = 137
private const val LIGHTNESS_STEP = 0.02f
private const val LIGHT_MAX_LUMINANCE = 0.12f
private const val DARK_MIN_LUMINANCE = 0.30f
