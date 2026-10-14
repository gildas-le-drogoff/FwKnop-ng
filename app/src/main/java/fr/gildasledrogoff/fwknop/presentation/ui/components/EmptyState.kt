// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

@Composable
fun EmptyState(
    title: String,
    body: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    illustration: (@Composable () -> Unit)? = null,
) {
    val dimens = DimensTheme.dimens
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxSize()
            .padding(dimens.screenPadding),
    ) {
        illustration?.invoke() ?: FwknopIcon(
            icon,
            null,
            Modifier.size(dimens.heroIcon),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(dimens.contentWidthFraction),
        )
    }
}

@AdaptivePreviews
@Composable
private fun EmptyStatePreview() {
    FwknopTheme {
        EmptyState(
            "No configs yet",
            "Create one to send your first knock.",
            R.drawable.ic_shield_rounded,
            illustration = { ShieldLockIllustration() },
        )
    }
}
