// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.state

import android.content.Intent
import androidx.compose.runtime.Immutable
import fr.gildasledrogoff.fwknop.domain.model.*

@Immutable
sealed interface KnockState {
    data object Idle : KnockState
    data object Authenticating : KnockState
    data class ChooseConfig(val nicknames: List<String>) : KnockState
    data class PromptKey(val nickname: String) : KnockState
    data class PromptSourceIp(
        val nickname: String,
        val invalid: Boolean = false,
        val qrUnrecognized: Boolean = false,
    ) : KnockState

    data class ExplainPermission(val nickname: String, val permission: KnockPermission) : KnockState
    data class AwaitingPermission(val permission: KnockPermission) : KnockState
    data class Sending(val nickname: String) : KnockState
    data class Failed(
        val error: UiError,
        val fixInAppSettings: Boolean = false,
        val tagErasable: Boolean = false,
        val retryable: Boolean = false,
    ) : KnockState
}

enum class KnockPermission { NOTIFICATIONS, LOCAL_NETWORK }
enum class SentNotice { KEEP_OPEN_NEEDS_NOTIFICATIONS, KEEP_OPEN_UNAVAILABLE }
sealed interface KnockEvent {
    data class Knock(val nickname: String) : KnockEvent
    data class KnockFromNfc(val digests: List<NfcTagDigest>) : KnockEvent
    data class NfcAuthCompleted(val result: AuthResult) : KnockEvent
    data class ConfigChosen(val nickname: String) : KnockEvent
    data class KeySubmitted(val key: String) : KnockEvent
    data class SourceIpSubmitted(val ip: String) : KnockEvent
    data class SourceIpScanned(val qrContent: String) : KnockEvent
    data object PermissionPromptLaunched : KnockEvent
    data class PermissionResult(val granted: Boolean, val permanentlyDenied: Boolean) : KnockEvent
    data object PermissionDeclined : KnockEvent
    data object Retry : KnockEvent
    data class InteractionFinished(
        val id: InteractionId,
        val completed: Boolean,
        val data: Intent?
    ) : KnockEvent

    data object Cancel : KnockEvent
}

sealed interface KnockEffect {
    data object Authenticate : KnockEffect
    data class Sent(
        val nickname: String,
        val source: ResolvedSourceIp,
        val notice: SentNotice? = null
    ) : KnockEffect

    data class PortChecked(val nickname: String, val check: PortCheck) : KnockEffect

    /** An NFC tag picked a Wake-on-LAN config: sent by the Wake-on-LAN flow, not as a knock. */
    data class Wake(val nickname: String) : KnockEffect
    data class LaunchApp(val app: ExternalApp, val nickname: String) : KnockEffect
    data class LaunchInteraction(val id: InteractionId) : KnockEffect
    data object Aborted : KnockEffect
}
