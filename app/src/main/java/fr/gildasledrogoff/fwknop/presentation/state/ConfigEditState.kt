// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.state

import android.content.Intent
import androidx.compose.runtime.Immutable
import fr.gildasledrogoff.fwknop.domain.model.*
data class ConfigEditState(
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val draft: ConfigDraft = ConfigDraft(),
    val pendingQr: ParsedQr? = null,
    val sealedQr: SealedQrPrompt? = null,
    val error: UiError? = null,
)

@Immutable
data class SealedQrPrompt(val wrongPassphrase: Boolean = false)
sealed interface ConfigEditEvent {
    data class NicknameChanged(val value: String) : ConfigEditEvent
    data class ServerChanged(val value: ServerDraft) : ConfigEditEvent
    data class AccessChanged(val value: AccessDraft) : ConfigEditEvent
    data class SourceIpChanged(val value: SourceIpDraft) : ConfigEditEvent
    data class CryptoChanged(val value: CryptoDraft) : ConfigEditEvent
    data class ExternalAppChanged(val value: ExternalAppDraft) : ConfigEditEvent
    data class WakeOnLanChanged(val value: WakeOnLanDraft) : ConfigEditEvent
    data class QrScanned(val content: String) : ConfigEditEvent
    data class QrImportSelected(val fullConfig: Boolean) : ConfigEditEvent
    data object QrImportDismissed : ConfigEditEvent
    class SealedQrPassphraseEntered(val passphrase: CharArray) : ConfigEditEvent
    data object SealedQrDismissed : ConfigEditEvent
    data class SelectGpgKey(val role: GpgKeyRole) : ConfigEditEvent
    data class InteractionFinished(
        val id: InteractionId,
        val completed: Boolean,
        val data: Intent?
    ) : ConfigEditEvent

    data object Save : ConfigEditEvent
    data object DismissError : ConfigEditEvent
}

sealed interface ConfigEditEffect {
    data object Saved : ConfigEditEffect
    data class LaunchInteraction(val id: InteractionId) : ConfigEditEffect
}
