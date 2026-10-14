// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

enum class WidthClass { Compact, Medium, Expanded }

fun WindowSizeClass.toWidthClass(): WidthClass = when {
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> WidthClass.Expanded
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> WidthClass.Medium
    else -> WidthClass.Compact
}

@Immutable
data class Dimens(
    val screenPadding: Dp,
    val itemSpacing: Dp,
    val sectionSpacing: Dp,
    val cardPadding: Dp,
    val cardMinWidth: Dp,
    val sectionMinWidth: Dp,
    val heroIcon: Dp,
    val contentWidthFraction: Float,
)

object DimensScale {
    val compact = Dimens(16.dp, 12.dp, 16.dp, 16.dp, 280.dp, 320.dp, 72.dp, 1f)
    val medium = Dimens(24.dp, 16.dp, 24.dp, 20.dp, 300.dp, 340.dp, 88.dp, 0.92f)
    val expanded = Dimens(32.dp, 20.dp, 32.dp, 24.dp, 320.dp, 380.dp, 104.dp, 0.85f)
    fun forWidth(widthClass: WidthClass): Dimens = when (widthClass) {
        WidthClass.Compact -> compact
        WidthClass.Medium -> medium
        WidthClass.Expanded -> expanded
    }
}

val LocalWidthClass = staticCompositionLocalOf { WidthClass.Compact }
val LocalDimens = staticCompositionLocalOf { DimensScale.compact }
