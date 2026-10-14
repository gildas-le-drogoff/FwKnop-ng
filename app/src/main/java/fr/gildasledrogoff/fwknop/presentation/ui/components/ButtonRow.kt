// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.components

import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme


@Composable
fun ButtonRow(
    modifier: Modifier = Modifier,
    spacing: Dp = DimensTheme.dimens.itemSpacing,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(
            constraints.minWidth,
            constraints.minHeight
        ) {}
        val gap = spacing.roundToPx()
        val count = measurables.size
        val widest = measurables.maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        val fitsInline =
            !constraints.hasBoundedWidth || widest * count + gap * (count - 1) <= constraints.maxWidth
        if (fitsInline) {
            val width =
                if (constraints.hasBoundedWidth) (constraints.maxWidth - gap * (count - 1)) / count else widest
            val height = measurables.maxOf { it.minIntrinsicHeight(width) }
            val placeables = measurables.map { it.measure(Constraints.fixed(width, height)) }
            val totalWidth = width * count + gap * (count - 1)
            layout(totalWidth, height) {
                placeables.forEachIndexed { index, placeable ->
                    placeable.placeRelative(
                        index * (width + gap),
                        0
                    )
                }
            }
        } else {
            val width = constraints.maxWidth
            val placeables =
                measurables.map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
            val totalHeight = placeables.sumOf { it.height } + gap * (count - 1)
            layout(width, totalHeight) {
                var y = 0
                placeables.forEach {
                    it.placeRelative(0, y)
                    y += it.height + gap
                }
            }
        }
    }
}

@AdaptivePreviews
@LargeFontPreviews
@Composable
private fun ButtonRowPreview() {
    FwknopTheme {
        ButtonRow {
            OutlinedButton(onClick = {}) { Text("Valeurs par défaut") }
            Button(onClick = {}) { Text("Enregistrer") }
        }
    }
}
