// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.state

import androidx.compose.runtime.Immutable
import fr.gildasledrogoff.fwknop.domain.model.KnockHistoryRetention
import fr.gildasledrogoff.fwknop.domain.model.KnockLogEntry

@Immutable
data class KnockHistoryState(
    val isLoading: Boolean = true,
    val entries: List<KnockLogEntry> = emptyList(),
    val retention: KnockHistoryRetention = KnockHistoryRetention.DAYS_30,

    val pendingWipe: KnockHistoryWipe? = null,
)

sealed interface KnockHistoryWipe {
    data object Clear : KnockHistoryWipe
    data object Disable : KnockHistoryWipe
}

sealed interface KnockHistoryEvent {
    data class RetentionSelected(val retention: KnockHistoryRetention) : KnockHistoryEvent
    data object ClearRequested : KnockHistoryEvent
    data object WipeConfirmed : KnockHistoryEvent
    data object WipeDismissed : KnockHistoryEvent
}
