// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.configlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.ConfigKind
import fr.gildasledrogoff.fwknop.presentation.ui.components.*
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme


@Composable
fun NewConfigDialog(onChoose: (ConfigKind) -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { FwknopIcon(R.drawable.ic_add_rounded, null) },
        title = { Text(stringResource(R.string.new_config_choose_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ConfigKind.entries.forEach { kind ->
                    ListItem(
                        headlineContent = { Text(stringResource(kind.labelRes())) },
                        supportingContent = { Text(stringResource(kind.hintRes())) },
                        leadingContent = { FwknopIcon(kind.iconRes(), null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(role = Role.Button) { onChoose(kind) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@AdaptivePreviews
@Composable
private fun NewConfigDialogPreview() {
    FwknopTheme { NewConfigDialog({}, {}) }
}
