// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.model.DomainResult.Failure
import fr.gildasledrogoff.fwknop.domain.model.DomainResult.Success
import fr.gildasledrogoff.fwknop.domain.repository.KnockHistoryRepository
import fr.gildasledrogoff.fwknop.domain.repository.WakeOnLanRepository
import javax.inject.Inject

internal fun WakeOnLanDraft.toWakeOnLan(): DomainResult<WakeOnLan, ConfigError> {
    val address = MacAddress.parse(mac) ?: return Failure(ConfigError.WakeMacInvalid)
    val rawPort = port.trim()
    val value = when {
        rawPort.isEmpty() -> WakeOnLan.DEFAULT_PORT
        rawPort.isPort() -> rawPort.toInt()
        else -> return Failure(ConfigError.WakePortInvalid)
    }
    val target = host.trim()
    if (target.any { it.isWhitespace() }) return Failure(ConfigError.WakeHostInvalid)
    val secret = password.takeIf { it.isNotBlank() }
        ?.let { WakePassword.parse(it) ?: return Failure(ConfigError.WakePasswordInvalid) }
    return Success(WakeOnLan(address, target.ifEmpty { null }, value, secret))
}

class SendWakeOnLanUseCase @Inject constructor(
    private val getConfig: GetConfigUseCase,
    private val repository: WakeOnLanRepository,
    private val history: KnockHistoryRepository,
) {
    suspend operator fun invoke(nickname: Nickname): DomainResult<Unit, KnockError> {
        val result = send(nickname)
        history.record(KnockLogEvent.wakeOnLan(nickname, result))
        return result
    }

    private suspend fun send(nickname: Nickname): DomainResult<Unit, KnockError> {
        val config = when (val result = getConfig(nickname)) {
            is Success -> result.data
            is Failure -> return Failure(
                if (result.error == ConfigError.NotFound) KnockError.ConfigMissing else KnockError.Config(
                    result.error
                )
            )
        }
        val wake = config.wakeOnLan ?: return Failure(KnockError.Config(ConfigError.WakeMacInvalid))
        return repository.sendMagicPacket(
            wake.magicPacket(),
            wake.targetHost(config.server),
            wake.port
        )
    }
}
