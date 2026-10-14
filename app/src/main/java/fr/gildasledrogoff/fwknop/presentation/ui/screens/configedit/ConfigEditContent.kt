// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.screens.configedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.Composable
import fr.gildasledrogoff.fwknop.domain.model.ConfigDraft
import fr.gildasledrogoff.fwknop.domain.model.ConfigKind
import fr.gildasledrogoff.fwknop.presentation.state.ConfigEditEvent
import fr.gildasledrogoff.fwknop.presentation.state.ConfigEditState
import fr.gildasledrogoff.fwknop.presentation.ui.components.AdaptivePreviews
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

@Composable
fun ConfigEditContent(state: ConfigEditState, onEvent: (ConfigEditEvent) -> Unit) {
    val dimens = DimensTheme.dimens
    val draft = state.draft
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(dimens.sectionMinWidth),
        contentPadding = PaddingValues(dimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
        verticalItemSpacing = dimens.sectionSpacing,
    ) {
        item { GeneralSection(draft.nickname) { onEvent(ConfigEditEvent.NicknameChanged(it)) } }
        if (draft.kind == ConfigKind.WAKE_ON_LAN) {
            item { WakeOnLanSection(draft.wakeOnLan, onEvent) }
            return@LazyVerticalStaggeredGrid
        }
        val portKnocking = draft.kind == ConfigKind.PORT_KNOCK
        item {
            ServerSection(
                draft.server,
                portKnocking
            ) { onEvent(ConfigEditEvent.ServerChanged(it)) }
        }
        item {
            AccessSection(
                draft.access,
                draft.crypto.useGpg && !portKnocking,
                portKnocking,
            ) { onEvent(ConfigEditEvent.AccessChanged(it)) }
        }

        if (!portKnocking) {
            item { SourceIpSection(draft.sourceIp) { onEvent(ConfigEditEvent.SourceIpChanged(it)) } }
            item { CryptoSection(draft.crypto, onEvent) }
        }
        item { ExternalAppSection(draft.externalApp, onEvent) }
    }
}

@AdaptivePreviews
@Composable
private fun ConfigEditContentPreview() {
    FwknopTheme {
        ConfigEditContent(ConfigEditState(draft = ConfigDraft(nickname = "home")), onEvent = {})
    }
}

@AdaptivePreviews
@Composable
private fun WakeOnLanContentPreview() {
    FwknopTheme {
        ConfigEditContent(
            ConfigEditState(draft = ConfigDraft(kind = ConfigKind.WAKE_ON_LAN, nickname = "nas")),
            onEvent = {},
        )
    }
}
