// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview


internal const val LARGE_FONT_SCALE = 1.3f
internal fun scaledMaxLines(lines: Int, fontScale: Float): Int =
    if (fontScale >= LARGE_FONT_SCALE) lines * 2 else lines


@Composable
@ReadOnlyComposable
fun scaledMaxLines(lines: Int): Int = scaledMaxLines(lines, LocalDensity.current.fontScale)


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
