// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.usecase.FwknopServerBlocks
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

@Composable
fun FwknopBlocksDialog(blocks: FwknopServerBlocks, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        FwknopBlocksScreen(blocks, onDismiss)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FwknopBlocksScreen(blocks: FwknopServerBlocks, onDismiss: () -> Unit) {
    val dimens = DimensTheme.dimens
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fwknop_blocks_title, blocks.nickname)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        FwknopIcon(R.drawable.ic_arrow_back_rounded, stringResource(R.string.close))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
                modifier = Modifier
                    .widthIn(max = MAX_CONTENT_WIDTH)
                    .padding(dimens.screenPadding),
            ) {
                Notice(R.drawable.ic_lock_rounded, stringResource(R.string.fwknop_blocks_body))
                Block(
                    title = stringResource(R.string.fwknop_block_server),
                    hint = stringResource(R.string.fwknop_block_server_hint),
                    icon = R.drawable.ic_dns_rounded,
                    text = blocks.server,
                )
                Block(
                    title = stringResource(R.string.fwknop_block_client),
                    hint = stringResource(R.string.fwknop_block_client_hint),
                    icon = R.drawable.ic_computer_rounded,
                    text = blocks.client,
                )
            }
        }
    }
}

@Composable
private fun Notice(@DrawableRes icon: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing)) {
        FwknopIcon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Block(title: String, hint: String, @DrawableRes icon: Int, text: String) {
    val context = LocalContext.current
    SectionCard(title, icon) {
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SelectionContainer {
                Text(
                    text.trimEnd(),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        FilledTonalButton(
            onClick = { copySensitive(context, title, text) },
            modifier = Modifier.align(Alignment.End),
        ) {
            FwknopIcon(R.drawable.ic_content_copy_rounded, null, Modifier.size(ButtonDefaults.IconSize))
            Text(
                stringResource(R.string.action_copy),
                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
            )
        }
    }
}

private fun copySensitive(context: Context, label: String, text: String) {
    val clip = ClipData.newPlainText(label, text)
    clip.description.extras = PersistableBundle().apply {
        val extra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            "android.content.extra.IS_SENSITIVE"
        }
        putBoolean(extra, true)
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.fwknop_block_copied, Toast.LENGTH_SHORT).show()
    }
}

private val MAX_CONTENT_WIDTH = 720.dp

@AdaptivePreviews
@Composable
private fun FwknopBlocksScreenPreview() {
    FwknopTheme {
        FwknopBlocksScreen(
            FwknopServerBlocks(
                "home",
                "[home]\nSPA_SERVER          knock.example.org\nKEY_BASE64          c2VjcmV0",
                "SOURCE              ANY\nOPEN_PORTS          tcp/22\nKEY_BASE64          c2VjcmV0",
            ),
            onDismiss = {},
        )
    }
}
