// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.error.AutoKnockError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.repository.AutoKnockRepository
import fr.gildasledrogoff.fwknop.domain.repository.ConfigRepository
import fr.gildasledrogoff.fwknop.domain.repository.VaultRepository
import fr.gildasledrogoff.fwknop.domain.repository.WifiNetworkRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
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


class IsAutoKnockableUseCase @Inject constructor(private val prompts: GetRequiredPromptsUseCase) {
    operator fun invoke(config: SpaConfig): Boolean =
        config.kind != ConfigKind.WAKE_ON_LAN &&
                config.crypto.encryption is Encryption.Rijndael && prompts(config).isEmpty()
}


class DetectTrustedNetworkExitUseCase @Inject constructor() {
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    operator fun invoke(presence: Flow<WifiPresence>, settle: Duration): Flow<Unit> = presence
        .filter { it != WifiPresence.UNKNOWN }

        .debounce(settle)
        .distinctUntilChanged()
        .runningFold(Transition()) { previous, current -> Transition(previous.current, current) }
        .filter { it.previous == WifiPresence.TRUSTED && it.current == WifiPresence.UNTRUSTED }
        .map { }

    private data class Transition(
        val previous: WifiPresence? = null,
        val current: WifiPresence? = null
    )
}


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
