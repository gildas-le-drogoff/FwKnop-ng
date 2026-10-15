package fr.gildasledrogoff.fwknop.presentation.ui.screens.configlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.ConfigKind
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.FwknopIcon
import fr.gildasledrogoff.fwknop.presentation.ui.components.hintRes
import fr.gildasledrogoff.fwknop.presentation.ui.components.iconRes
import fr.gildasledrogoff.fwknop.presentation.ui.components.labelRes
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

/** A config does one thing: the kind is picked before the editor opens. */
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
