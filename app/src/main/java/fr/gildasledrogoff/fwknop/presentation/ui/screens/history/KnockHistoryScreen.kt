// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.history

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.presentation.state.KnockHistoryEvent
import fr.gildasledrogoff.fwknop.presentation.state.KnockHistoryState
import fr.gildasledrogoff.fwknop.presentation.state.KnockHistoryWipe
import fr.gildasledrogoff.fwknop.presentation.ui.components.*
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme
import fr.gildasledrogoff.fwknop.presentation.viewmodel.KnockHistoryViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun KnockHistoryScreen(onBack: () -> Unit, viewModel: KnockHistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    KnockHistoryScaffold(state, onBack, viewModel::onEvent)
    state.pendingWipe?.let { wipe ->
        ConfirmDialog(
            spec = wipe.confirmSpec(),
            onConfirm = { viewModel.onEvent(KnockHistoryEvent.WipeConfirmed) },
            onDismiss = { viewModel.onEvent(KnockHistoryEvent.WipeDismissed) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KnockHistoryScaffold(
    state: KnockHistoryState,
    onBack: () -> Unit,
    onEvent: (KnockHistoryEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        FwknopIcon(
                            R.drawable.ic_arrow_back_rounded,
                            stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (state.entries.isNotEmpty()) {
                        IconButton(onClick = { onEvent(KnockHistoryEvent.ClearRequested) }) {
                            FwknopIcon(
                                R.drawable.ic_delete_rounded,
                                stringResource(R.string.action_history_clear)
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (!state.isLoading) KnockHistoryContent(state, onEvent)
        }
    }
}

@Composable
fun KnockHistoryContent(state: KnockHistoryState, onEvent: (KnockHistoryEvent) -> Unit) {
    val dimens = DimensTheme.dimens
    val formatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
        contentPadding = PaddingValues(dimens.screenPadding),
        modifier = Modifier.fillMaxWidth(dimens.contentWidthFraction),
    ) {
        item(key = "retention") {
            RetentionSection(state.retention) { onEvent(KnockHistoryEvent.RetentionSelected(it)) }
        }
        if (state.entries.isEmpty()) {
            item(key = "empty") { EmptyHistory(state.retention) }
        } else {
            items(state.entries, key = { it.id }) { KnockLogCard(it, formatter) }
        }
    }
}

@Composable
private fun RetentionSection(
    retention: KnockHistoryRetention,
    onSelect: (KnockHistoryRetention) -> Unit
) {
    SectionCard(stringResource(R.string.section_history_retention), R.drawable.ic_history_rounded) {
        val options = DropdownOptions(
            label = stringResource(R.string.field_history_retention),
            options = KnockHistoryRetention.entries,
            optionLabel = labelsOf(KnockHistoryRetention.entries) { it.labelRes() },
        )
        OptionDropdown(options, retention, onSelect, Modifier.fillMaxWidth())
        Text(
            stringResource(R.string.field_history_retention_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyHistory(retention: KnockHistoryRetention) {
    val body =
        if (retention == KnockHistoryRetention.OFF) R.string.history_empty_off else R.string.history_empty
    Text(
        stringResource(body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DimensTheme.dimens.sectionSpacing),
    )
}

@Composable
private fun KnockLogCard(entry: KnockLogEntry, formatter: DateTimeFormatter) {
    val event = entry.event
    val outcome = event.outcome
    val sent = outcome is KnockLogOutcome.Sent
    val status = stringResource(if (sent) R.string.history_sent else R.string.history_failed)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(DimensTheme.dimens.itemSpacing),
            modifier = Modifier
                .padding(DimensTheme.dimens.cardPadding)
                .semantics(mergeDescendants = true) {},
        ) {
            FwknopIcon(
                if (sent) R.drawable.ic_check_rounded else R.drawable.ic_error_rounded,
                status,
                tint = if (sent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Column(Modifier.weight(1f)) {
                val method = when (event.kind) {
                    KnockLogKind.KNOCK -> event.protocol.name
                    KnockLogKind.WAKE_ON_LAN -> stringResource(R.string.section_wake_on_lan)
                }
                Text(
                    stringResource(R.string.history_entry_title, event.nickname.value, method),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    formatter.format(entry.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                event.source?.let {
                    Text(
                        it.description(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (outcome is KnockLogOutcome.Failed) {
                    Text(
                        listOfNotNull(outcome.code, outcome.detail).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@StringRes
private fun KnockHistoryRetention.labelRes(): Int = when (this) {
    KnockHistoryRetention.OFF -> R.string.history_retention_off
    KnockHistoryRetention.DAYS_7 -> R.string.history_retention_7
    KnockHistoryRetention.DAYS_30 -> R.string.history_retention_30
    KnockHistoryRetention.DAYS_90 -> R.string.history_retention_90
}

@Composable
private fun KnockHistoryWipe.confirmSpec(): ConfirmSpec = when (this) {
    KnockHistoryWipe.Clear -> ConfirmSpec(
        stringResource(R.string.history_clear_confirm_title),
        stringResource(R.string.history_clear_confirm_body),
        stringResource(R.string.action_history_clear),
    )

    KnockHistoryWipe.Disable -> ConfirmSpec(
        stringResource(R.string.history_disable_confirm_title),
        stringResource(R.string.history_disable_confirm_body),
        stringResource(R.string.action_history_disable),
    )
}

@AdaptivePreviews
@Composable
private fun KnockHistoryContentPreview() {
    val source = ResolvedSourceIp("203.0.113.9", SourceIpMethod.Stun("stun.example.org"))
    FwknopTheme {
        KnockHistoryContent(
            KnockHistoryState(
                isLoading = false,
                entries = listOf(
                    KnockLogEntry(
                        3,
                        Instant.parse("2026-10-15T08:31:00Z"),
                        KnockLogEvent.wakeOnLan(Nickname("home"), DomainResult.Success(Unit)),
                    ),
                    KnockLogEntry(
                        2,
                        Instant.parse("2026-10-15T08:30:00Z"),
                        KnockLogEvent(Nickname("home"), Protocol.UDP, source, KnockLogOutcome.Sent),
                    ),
                    KnockLogEntry(
                        1,
                        Instant.parse("2026-10-14T19:05:00Z"),
                        KnockLogEvent(
                            Nickname("office"),
                            Protocol.TCP,
                            null,
                            KnockLogOutcome.Failed("SERVER_UNRESOLVED", "knock.example.org"),
                        ),
                    ),
                ),
            ),
        ) {}
    }
}
