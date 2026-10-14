package fr.gildasledrogoff.fwknop.presentation.state

import androidx.compose.runtime.Immutable
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.error.AutoKnockError
import fr.gildasledrogoff.fwknop.domain.model.AutoKnockSettings
import fr.gildasledrogoff.fwknop.utils.helpers.LocationAccessStatus

@Immutable
data class AutoKnockState(
    val isLoading: Boolean = true,
    val settings: AutoKnockSettings = AutoKnockSettings(),
    /** Configs that can knock unattended. */
    val candidates: List<String> = emptyList(),
    val location: LocationAccessStatus = LocationAccessStatus.GRANTED,
    /** The security warning is shown: enabling needs its explicit confirmation. */
    val confirmingEnable: Boolean = false,
    val currentSsidUnavailable: Boolean = false,
) {
    /** Saved config that can no longer knock unattended (deleted, renamed or changed since). */
    val configUnavailable: Boolean
        get() = settings.nickname != null && !isLoading && settings.nickname !in candidates
}

sealed interface AutoKnockEvent {
    data class EnableRequested(val enabled: Boolean) : AutoKnockEvent
    data object EnableConfirmed : AutoKnockEvent
    data object EnableDismissed : AutoKnockEvent
    data class ConfigChosen(val nickname: String) : AutoKnockEvent
    data class SsidAdded(val ssid: String) : AutoKnockEvent
    data class SsidRemoved(val ssid: String) : AutoKnockEvent
    data object AddCurrentNetwork : AutoKnockEvent
    data object PermissionsChanged : AutoKnockEvent
}

fun AutoKnockError.toUiError(): UiError = when (this) {
    AutoKnockError.VaultLocked -> UiError.Message(R.string.auto_knock_error_locked)
    AutoKnockError.ConfigMissing -> UiError.Message(R.string.auto_knock_error_config_missing)
    AutoKnockError.NeedsUser -> UiError.Message(R.string.auto_knock_error_needs_user)
    is AutoKnockError.Knock -> error.toUiError()
}
