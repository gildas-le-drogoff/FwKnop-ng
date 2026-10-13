package fr.gildasledrogoff.fwknop.presentation.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.presentation.state.BackupEvent
import fr.gildasledrogoff.fwknop.presentation.state.BackupState
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.SectionCard
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

@Composable
fun BackupSection(state: BackupState, onEvent: (BackupEvent) -> Unit) {
    SectionCard(stringResource(R.string.section_backup), R.drawable.ic_key_rounded) {
        Text(
            text = stringResource(R.string.hint_backup),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing)) {
            OutlinedButton(
                onClick = { onEvent(BackupEvent.ExportAllRequested) },
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.action_backup_export)) }
            OutlinedButton(
                onClick = { onEvent(BackupEvent.ImportRequested) },
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.action_backup_import)) }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@AdaptivePreviews
@Composable
private fun BackupSectionPreview() {
    FwknopTheme { BackupSection(BackupState(busy = true)) {} }
}
