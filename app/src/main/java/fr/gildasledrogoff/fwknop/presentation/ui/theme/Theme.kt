// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import fr.gildasledrogoff.fwknop.domain.model.ThemeMode

object DimensTheme {
    val dimens: Dimens
        @Composable get() = LocalDimens.current
    val widthClass: WidthClass
        @Composable get() = LocalWidthClass.current
}

val LocalDarkTheme = staticCompositionLocalOf { false }
val dynamicColorSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
@ReadOnlyComposable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun FwknopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val widthClass = currentWindowAdaptiveInfoV2().windowSizeClass.toWidthClass()
    CompositionLocalProvider(
        LocalWidthClass provides widthClass,
        LocalDimens provides DimensScale.forWidth(widthClass),
        LocalDarkTheme provides darkTheme,
    ) {
        val colors = colorScheme(darkTheme, dynamicColor)
        MaterialTheme(colorScheme = colors, shapes = FwknopShapes) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurface, content = content)
        }
    }
}

@Composable
private fun colorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme {
    val dynamic = dynamicColor && dynamicColorSupported && !LocalInspectionMode.current
    val context = LocalContext.current
    return when {
        dynamic && darkTheme -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
}
