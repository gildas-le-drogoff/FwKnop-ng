package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.DomainResult.Failure
import fr.gildasledrogoff.fwknop.domain.model.DomainResult.Success
import fr.gildasledrogoff.fwknop.domain.model.MacAddress
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.model.WakeOnLan
import fr.gildasledrogoff.fwknop.domain.model.WakeOnLanDraft
import fr.gildasledrogoff.fwknop.domain.model.isPort
import fr.gildasledrogoff.fwknop.domain.repository.WakeOnLanRepository
import javax.inject.Inject

internal fun WakeOnLanDraft.toWakeOnLan(): DomainResult<WakeOnLan?, ConfigError> {
    if (!enabled) return Success(null)
    val address = MacAddress.parse(mac) ?: return Failure(ConfigError.WakeMacInvalid)
    val rawPort = port.trim()
    val value = when {
        rawPort.isEmpty() -> WakeOnLan.DEFAULT_PORT
        rawPort.isPort() -> rawPort.toInt()
        else -> return Failure(ConfigError.WakePortInvalid)
    }
    val target = host.trim()
    if (target.any { it.isWhitespace() }) return Failure(ConfigError.WakeHostInvalid)
    return Success(WakeOnLan(address, target.ifEmpty { null }, value))
}

class SendWakeOnLanUseCase @Inject constructor(
    private val getConfig: GetConfigUseCase,
    private val repository: WakeOnLanRepository,
) {
    suspend operator fun invoke(nickname: Nickname): DomainResult<Unit, KnockError> {
        val config = when (val result = getConfig(nickname)) {
            is Success -> result.data
            is Failure -> return Failure(
                if (result.error == ConfigError.NotFound) KnockError.ConfigMissing else KnockError.Config(result.error)
            )
        }
        val wake = config.wakeOnLan ?: return Failure(KnockError.Config(ConfigError.WakeMacInvalid))
        return repository.sendMagicPacket(wake.mac, wake.targetHost(config.server), wake.port)
    }
}
