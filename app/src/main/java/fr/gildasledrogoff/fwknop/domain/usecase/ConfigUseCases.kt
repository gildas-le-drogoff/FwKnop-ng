// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.repository.ConfigRepository
import fr.gildasledrogoff.fwknop.domain.repository.ConfigResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveConfigsUseCase @Inject constructor(private val repository: ConfigRepository) {
    operator fun invoke(): Flow<ConfigResult<List<SpaConfig>>> = repository.observeConfigs()
}

class GetConfigUseCase @Inject constructor(private val repository: ConfigRepository) {
    suspend operator fun invoke(nickname: Nickname): DomainResult<SpaConfig, ConfigError> =
        repository.getConfig(nickname)
}

class DeleteConfigUseCase @Inject constructor(private val repository: ConfigRepository) {
    suspend operator fun invoke(nickname: Nickname): ConfigResult<Unit> =
        repository.deleteConfig(nickname)
}

class ValidateConfigUseCase @Inject constructor() {
    operator fun invoke(draft: ConfigDraft): DomainResult<SpaConfig, ConfigError> {
        val nickname = draft.nickname.trim()
        if (nickname.isEmpty()) return DomainResult.Failure(ConfigError.NicknameBlank)
        if (draft.kind == ConfigKind.WAKE_ON_LAN) return wakeConfig(Nickname(nickname), draft)
        val spa = draft.kind == ConfigKind.FWKNOP
        return draft.server.toEndpoint().flatMap { server ->
            draft.server.toKnockMethod(portKnocking = !spa).flatMap { method ->
                val access =
                    if (spa) draft.access.toAccessRule() else draft.access.toKnockAccessRule()
                access.flatMap { access ->
                    draft.sourceIp.toSourceIp().orDormant(spa) { SourceIpDraft().toSourceIp() }
                        .flatMap { sourceIp ->
                            draft.crypto.toCrypto().orDormant(spa) { CryptoDraft().toCrypto() }
                                .flatMap { crypto ->
                                    draft.externalApp.toExternalApp().map { app ->
                                        SpaConfig(
                                            Nickname(nickname),
                                            server,
                                            access,
                                            sourceIp,
                                            crypto,
                                            app,
                                            method
                                        )
                                    }
                                }
                        }
                }
            }
        }
    }


    private fun wakeConfig(
        nickname: Nickname,
        draft: ConfigDraft
    ): DomainResult<SpaConfig, ConfigError> =
        draft.wakeOnLan.toWakeOnLan().flatMap { parsed ->
            val host = parsed.host ?: WakeOnLan.BROADCAST
            CryptoDraft().toCrypto().map { crypto ->
                SpaConfig(
                    nickname = nickname,
                    server = ServerEndpoint(
                        host,
                        ServerPort.Fixed(ServerPort.DEFAULT),
                        Protocol.UDP
                    ),
                    access = AccessRule.OpenPort(
                        PortAccess(
                            "",
                            timeoutSeconds = null,
                            keepOpen = false
                        )
                    ),
                    sourceIp = SourceIp.Resolve,
                    crypto = crypto,
                    externalApp = ExternalApp.None,
                    wakeOnLan = parsed.copy(host = host),
                )
            }
        }


    private fun <T> DomainResult<T, ConfigError>.orDormant(
        spa: Boolean,
        fallback: () -> DomainResult<T, ConfigError>,
    ): DomainResult<T, ConfigError> = if (spa || this is DomainResult.Success) this else fallback()
}

class SaveConfigUseCase @Inject constructor(
    private val validate: ValidateConfigUseCase,
    private val repository: ConfigRepository,
) {
    suspend operator fun invoke(
        draft: ConfigDraft,
        previous: Nickname?
    ): DomainResult<SpaConfig, ConfigError> {
        return validate(draft)
            .flatMap { ensureNicknameAvailable(it, previous) }
            .flatMap { config -> repository.saveConfig(config, previous).map { config } }
    }

    private suspend fun ensureNicknameAvailable(
        config: SpaConfig,
        previous: Nickname?,
    ): DomainResult<SpaConfig, ConfigError> {
        if (config.nickname == previous) return DomainResult.Success(config)
        return repository.exists(config.nickname).flatMap { taken ->
            if (taken) DomainResult.Failure(ConfigError.NicknameTaken) else DomainResult.Success(
                config
            )
        }
    }
}
