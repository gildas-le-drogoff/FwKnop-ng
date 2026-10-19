// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.configedit

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.ConfigIcon
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.FwknopIcon
import fr.gildasledrogoff.fwknop.presentation.ui.components.glyphRes
import fr.gildasledrogoff.fwknop.presentation.ui.components.labelRes
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme
import kotlinx.coroutines.launch

@Composable
fun IconPicker(selected: ConfigIcon, onSelect: (ConfigIcon) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val chooseLabel = stringResource(R.string.action_choose_icon)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = chooseLabel) { open = true }
            .padding(vertical = 4.dp),
    ) {
        IconBadge(selected, highlighted = true, size = 48.dp)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.field_icon), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(selected.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FwknopIcon(R.drawable.ic_chevron_right_rounded, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) {
        IconSheet(
            selected = selected,
            onSelect = {
                onSelect(it)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconSheet(selected: ConfigIcon, onSelect: (ConfigIcon) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val pick: (ConfigIcon) -> Unit = { icon ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { onSelect(icon) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        IconCatalog(selected, pick)
    }
}

@Composable
private fun IconCatalog(selected: ConfigIcon, onSelect: (ConfigIcon) -> Unit) {
    val dimens = DimensTheme.dimens
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CELL_MIN_WIDTH),
        contentPadding = PaddingValues(
            start = dimens.screenPadding,
            end = dimens.screenPadding,
            bottom = dimens.screenPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        fullWidth {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.field_icon),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.hint_icon),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ICON_GROUPS.forEach { group ->
            fullWidth {
                Text(
                    stringResource(group.title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = dimens.itemSpacing)
                        .semantics { heading() },
                )
            }
            items(group.icons, key = { it.name }) { icon ->
                IconCell(icon, icon == selected) { onSelect(icon) }
            }
        }
        if (selected != ConfigIcon.APP) {
            fullWidth {
                Box(Modifier.padding(top = dimens.itemSpacing), contentAlignment = Alignment.Center) {
                    OutlinedButton(onClick = { onSelect(ConfigIcon.APP) }) {
                        FwknopIcon(R.drawable.ic_launcher_monochrome, null, Modifier.size(32.dp))
                        Text(stringResource(R.string.action_reset_icon))
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun IconCell(icon: ConfigIcon, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        IconBadge(icon, highlighted = selected, size = 52.dp)
        Text(
            stringResource(icon.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IconBadge(icon: ConfigIcon, highlighted: Boolean, size: Dp) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (highlighted) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (highlighted) colors.onPrimaryContainer else colors.onSurfaceVariant,
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            FwknopIcon(
                icon.glyphRes(filled = highlighted) ?: R.drawable.ic_launcher_monochrome,
                null,
                Modifier.size(if (icon == ConfigIcon.APP) size * APP_ARTWORK_SCALE else size / 2),
            )
        }
    }
}

private class IconGroup(@StringRes val title: Int, val icons: List<ConfigIcon>)

private val ICON_GROUPS = listOf(
    IconGroup(
        R.string.icon_group_places,
        listOf(
            ConfigIcon.HOME, ConfigIcon.COTTAGE, ConfigIcon.BUILDING, ConfigIcon.GARAGE, ConfigIcon.OFFICE,
            ConfigIcon.STORE, ConfigIcon.FACTORY, ConfigIcon.SCHOOL, ConfigIcon.BOAT, ConfigIcon.CAMPING,
            ConfigIcon.VACATION, ConfigIcon.WORKSHOP,
        ),
    ),
    IconGroup(
        R.string.icon_group_machines,
        listOf(
            ConfigIcon.SERVER, ConfigIcon.DATABASE, ConfigIcon.STORAGE, ConfigIcon.CLOUD, ConfigIcon.WEB,
            ConfigIcon.ROUTER, ConfigIcon.NETWORK, ConfigIcon.COMPUTER, ConfigIcon.LAPTOP, ConfigIcon.TERMINAL,
            ConfigIcon.VPN, ConfigIcon.MAIL, ConfigIcon.BACKUP, ConfigIcon.BOARD,
        ),
    ),
    IconGroup(
        R.string.icon_group_devices,
        listOf(
            ConfigIcon.CAMERA, ConfigIcon.SMART_HOME, ConfigIcon.LIGHTING, ConfigIcon.MEDIA, ConfigIcon.GAMES,
            ConfigIcon.PRINTER, ConfigIcon.TV, ConfigIcon.SPEAKER, ConfigIcon.HEATING, ConfigIcon.DOORBELL,
        ),
    ),
)

private val CELL_MIN_WIDTH = 80.dp
private const val APP_ARTWORK_SCALE = 0.85f

@AdaptivePreviews
@Composable
private fun IconPickerPreview() {
    FwknopTheme { IconPicker(ConfigIcon.GARAGE) {} }
}

@AdaptivePreviews
@Composable
private fun IconCatalogPreview() {
    FwknopTheme { Surface { IconCatalog(ConfigIcon.GARAGE) {} } }
}
