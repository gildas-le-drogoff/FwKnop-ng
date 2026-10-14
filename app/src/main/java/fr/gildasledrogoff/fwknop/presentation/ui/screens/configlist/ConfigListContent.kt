// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.configlist

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.ConfigKind
import fr.gildasledrogoff.fwknop.domain.model.MessageType
import fr.gildasledrogoff.fwknop.domain.model.Protocol
import fr.gildasledrogoff.fwknop.domain.model.uniqueNickname
import fr.gildasledrogoff.fwknop.presentation.state.ConfigListState
import fr.gildasledrogoff.fwknop.presentation.state.ConfigSummary
import fr.gildasledrogoff.fwknop.presentation.ui.components.*
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

@Composable
fun ConfigListContent(
    state: ConfigListState,
    actions: ConfigCardActions,
    modifier: Modifier = Modifier,
    knock: KnockActivity = KnockActivity(),
    onDuplicate: (source: String, nickname: String) -> Unit = { _, _ -> },
) {

    ReportDrawnWhen { !state.isLoading }
    val loading = stringResource(R.string.a11y_loading_configs)
    Box(
        modifier
            .fillMaxSize()

            .semantics { testTagsAsResourceId = true }
            .then(if (state.isLoading) Modifier else Modifier.testTag(CONFIG_LIST_TAG)),
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(
                Modifier
                    .align(Alignment.Center)
                    .semantics { contentDescription = loading },
            )

            state.configs.isEmpty() -> EmptyState(
                title = stringResource(R.string.empty_title),
                body = stringResource(R.string.empty_body),
                icon = R.drawable.ic_shield_rounded,
                illustration = { ShieldLockIllustration() },
            )

            else -> ConfigGrid(state.configs, actions, knock, onDuplicate)
        }
    }
}

@Composable
private fun ConfigGrid(
    configs: List<ConfigSummary>,
    actions: ConfigCardActions,
    knock: KnockActivity,
    onDuplicate: (source: String, nickname: String) -> Unit,
) {
    val dimens = DimensTheme.dimens
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDuplicate by rememberSaveable { mutableStateOf<String?>(null) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(dimens.cardMinWidth),
        modifier = Modifier.testTag(CONFIG_GRID_TAG),

        contentPadding = PaddingValues(
            start = dimens.screenPadding,
            top = dimens.screenPadding,
            end = dimens.screenPadding,
            bottom = dimens.screenPadding + FAB_CLEARANCE,
        ),
        horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
        verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
    ) {
        items(configs, key = { it.nickname }) { summary ->
            ConfigCard(
                summary,
                actions.copy(
                    onDelete = { pendingDelete = it },
                    onDuplicate = { pendingDuplicate = it }),
                Modifier.animateItem(),
                knock.of(summary.nickname),
                waking = knock.waking == summary.nickname,
            )
        }
    }
    pendingDelete?.let {
        DeleteConfirmDialog(
            it,
            { actions.onDelete(it) },
            onDone = { pendingDelete = null })
    }
    pendingDuplicate?.let { source ->
        val taken = configs.mapTo(mutableSetOf()) { it.nickname }
        DuplicateDialog(
            source,
            taken,
            { onDuplicate(source, it) },
            onDone = { pendingDuplicate = null })
    }
}

@Composable
private fun DuplicateDialog(
    source: String,
    taken: Set<String>,
    onConfirm: (String) -> Unit,
    onDone: () -> Unit,
) {
    val spec = NicknameSpec(
        title = stringResource(R.string.duplicate_title, source),
        text = stringResource(R.string.duplicate_body),
        initial = uniqueNickname(source, taken),
        taken = taken,
        confirmLabel = stringResource(R.string.action_duplicate),
    )
    NicknameDialog(spec, onConfirm = { onConfirm(it); onDone() }, onDismiss = onDone)
}

@Composable
private fun DeleteConfirmDialog(nickname: String, onConfirm: () -> Unit, onDone: () -> Unit) {
    val spec = ConfirmSpec(
        title = stringResource(R.string.delete_title, nickname),
        text = stringResource(R.string.delete_body),
        confirmLabel = stringResource(R.string.action_delete),
    )
    ConfirmDialog(spec, onConfirm = { onConfirm(); onDone() }, onDismiss = onDone)
}

private val FAB_CLEARANCE = 72.dp
private const val CONFIG_LIST_TAG = "config_list"
private const val CONFIG_GRID_TAG = "config_grid"
internal val previewSummaries = listOf(
    ConfigSummary(
        "home",
        "knock.example.org:62201",
        Protocol.UDP,
        MessageType.OPEN_PORT,
        "tcp/22",
        true,
    ),
    ConfigSummary(
        "office-nat",
        "203.0.113.7:*",
        Protocol.TCP,
        MessageType.NAT_ACCESS,
        "tcp/3389",
        false
    ),
    ConfigSummary(
        "router",
        "192.0.2.1:80",
        Protocol.HTTP,
        MessageType.SERVER_COMMAND,
        "reboot",
        false
    ),
    ConfigSummary(
        "nas",
        "255.255.255.255",
        Protocol.UDP,
        MessageType.OPEN_PORT,
        "",
        false,
        kind = ConfigKind.WAKE_ON_LAN,
    ),
)

@AdaptivePreviews
@Composable
private fun ConfigListContentPreview() {
    FwknopTheme {
        ConfigListContent(
            ConfigListState(isLoading = false, configs = previewSummaries),
            previewActions
        )
    }
}

@AdaptivePreviews
@Composable
private fun ConfigListEmptyPreview() {
    FwknopTheme { ConfigListContent(ConfigListState(isLoading = false), previewActions) }
}

@LargeFontPreviews
@Composable
private fun ConfigListLargeFontPreview() {
    FwknopTheme {
        ConfigListContent(
            ConfigListState(isLoading = false, configs = previewSummaries),
            previewActions
        )
    }
}
