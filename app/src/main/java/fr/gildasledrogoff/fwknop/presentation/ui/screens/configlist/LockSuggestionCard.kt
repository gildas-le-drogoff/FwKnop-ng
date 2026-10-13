package fr.gildasledrogoff.fwknop.presentation.ui.screens.configlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.components.SectionCard
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme


@Composable
fun LockSuggestionCard(onEnable: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    SectionCard(stringResource(R.string.lock_suggestion_title), R.drawable.ic_lock_rounded, modifier) {
        Text(stringResource(R.string.lock_suggestion_body), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.lock_suggestion_nfc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing, Alignment.End),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_later)) }
            FilledTonalButton(onClick = onEnable) { Text(stringResource(R.string.action_enable_lock)) }
        }
    }
}

@AdaptivePreviews
@Composable
private fun LockSuggestionCardPreview() {
    FwknopTheme { LockSuggestionCard(onEnable = {}, onDismiss = {}) }
}
