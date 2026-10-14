package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.error.AutoKnockError
import fr.gildasledrogoff.fwknop.domain.model.AutoKnockSettings
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.Encryption
import fr.gildasledrogoff.fwknop.domain.model.KnockOutcome
import fr.gildasledrogoff.fwknop.domain.model.KnockRequest
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.model.SpaConfig
import fr.gildasledrogoff.fwknop.domain.model.Ssids
import fr.gildasledrogoff.fwknop.domain.model.VaultState
import fr.gildasledrogoff.fwknop.domain.model.WifiPresence
import fr.gildasledrogoff.fwknop.domain.model.mapError
import fr.gildasledrogoff.fwknop.domain.repository.AutoKnockRepository
import fr.gildasledrogoff.fwknop.domain.repository.ConfigRepository
import fr.gildasledrogoff.fwknop.domain.repository.VaultRepository
import fr.gildasledrogoff.fwknop.domain.repository.WifiNetworkRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import javax.inject.Inject
import kotlin.time.Duration

class ObserveAutoKnockSettingsUseCase @Inject constructor(private val repository: AutoKnockRepository) {
    operator fun invoke(): Flow<AutoKnockSettings> = repository.settings
}

class SaveAutoKnockSettingsUseCase @Inject constructor(private val repository: AutoKnockRepository) {
    suspend operator fun invoke(settings: AutoKnockSettings) {
        val ssids = settings.trustedSsids.mapNotNullTo(sortedSetOf(), Ssids::normalize)
        repository.save(settings.copy(trustedSsids = ssids))
    }
}

/** Only configs that knock without any input can be sent unattended: saved Rijndael key, no source IP prompt. */
class IsAutoKnockableUseCase @Inject constructor(private val prompts: GetRequiredPromptsUseCase) {
    operator fun invoke(config: SpaConfig): Boolean =
        config.crypto.encryption is Encryption.Rijndael && prompts(config).isEmpty()
}

/**
 * Emits once each time the device leaves the trusted networks: TRUSTED, then UNTRUSTED for [settle].
 * Hidden network names (UNKNOWN) are ignored rather than taken as a departure, and so is the state
 * found at start: being away from the trusted networks when monitoring begins does not knock.
 */
class DetectTrustedNetworkExitUseCase @Inject constructor() {
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    operator fun invoke(presence: Flow<WifiPresence>, settle: Duration): Flow<Unit> = presence
        .filter { it != WifiPresence.UNKNOWN }
        // Roaming between access points or a short drop must not knock.
        .debounce(settle)
        .distinctUntilChanged()
        .runningFold(Transition()) { previous, current -> Transition(previous.current, current) }
        .filter { it.previous == WifiPresence.TRUSTED && it.current == WifiPresence.UNTRUSTED }
        .map { }

    private data class Transition(val previous: WifiPresence? = null, val current: WifiPresence? = null)
}

/** Knocks [Nickname] without the user, through the regular [SendKnockUseCase]. */
class AutoKnockUseCase @Inject constructor(
    private val vault: VaultRepository,
    private val configs: ConfigRepository,
    private val autoKnockable: IsAutoKnockableUseCase,
    private val sendKnock: SendKnockUseCase,
) {
    suspend operator fun invoke(nickname: Nickname): DomainResult<KnockOutcome, AutoKnockError> {
        if (!openVault()) return DomainResult.Failure(AutoKnockError.VaultLocked)
        val config = when (val result = configs.getConfig(nickname)) {
            is DomainResult.Success -> result.data
            is DomainResult.Failure -> return DomainResult.Failure(AutoKnockError.ConfigMissing)
        }
        if (!autoKnockable(config)) return DomainResult.Failure(AutoKnockError.NeedsUser)
        return sendKnock(KnockRequest(config)).mapError { AutoKnockError.Knock(it) }
    }

    // Without the app lock the vault opens silently, as on app start; with it, only an already unlocked vault is used.
    private suspend fun openVault(): Boolean {
        val status = vault.status.value
        return when {
            status.state == VaultState.UNLOCKED -> true
            status.lockEnabled -> false
            else -> vault.unlock() is DomainResult.Success
        }
    }
}

class GetCurrentSsidUseCase @Inject constructor(private val wifi: WifiNetworkRepository) {
    suspend operator fun invoke(): String? = wifi.currentSsid()
}
