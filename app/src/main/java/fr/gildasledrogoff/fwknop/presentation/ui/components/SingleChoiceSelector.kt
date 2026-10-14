// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme


private val SegmentIconSpacing = 8.dp


@Composable
fun <T> SingleChoiceSelector(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    val labels = options.map { optionLabel(it) }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val padding = SegmentedButtonDefaults.ContentPadding

        val chrome = with(density) {
            (SegmentedButtonDefaults.IconSize + SegmentIconSpacing +
                padding.calculateStartPadding(layoutDirection) +
                padding.calculateEndPadding(layoutDirection)).roundToPx()
        }
        val widest = labels.maxOfOrNull {
            measurer.measure(it, labelStyle, softWrap = false, maxLines = 1).size.width
        } ?: 0
        val fitsInline = (widest + chrome) * options.size <= constraints.maxWidth
        if (fitsInline) {
            ChoiceSegments(options, labels, selected, onSelected)
        } else {
            ChoiceRadios(options, labels, selected, onSelected)
        }
    }
}

@Composable
private fun <T> ChoiceSegments(
    options: List<T>,
    labels: List<String>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelected(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(labels[index], maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun <T> ChoiceRadios(
    options: List<T>,
    labels: List<String>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        options.forEachIndexed { index, option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .selectable(
                        selected = option == selected,
                        role = Role.RadioButton,
                        onClick = { onSelected(option) },
                    ),
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Text(labels[index], style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@AdaptivePreviews
@LargeFontPreviews
@Composable
private fun SingleChoiceSelectorPreview() {
    FwknopTheme {
        Column {
            SingleChoiceSelector(listOf("Auto", "VPN", "Direct"), "VPN", {}, { it })
            SingleChoiceSelector(
                listOf("Automatique", "Via le VPN", "En dehors du VPN"),
                "Via le VPN",
                {},
                { it },
            )
        }
    }
}
