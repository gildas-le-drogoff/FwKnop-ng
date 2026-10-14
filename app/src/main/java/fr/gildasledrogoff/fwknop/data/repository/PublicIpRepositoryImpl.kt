// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.repository

import fr.gildasledrogoff.fwknop.data.remote.api.NetworkPath
import fr.gildasledrogoff.fwknop.data.remote.api.family
import fr.gildasledrogoff.fwknop.data.remote.api.label
import fr.gildasledrogoff.fwknop.data.remote.api.routeOf
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.repository.PublicIpRepository
import fr.gildasledrogoff.fwknop.domain.repository.SettingsRepository
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import fr.gildasledrogoff.fwknop.utils.helpers.DebugLog
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class PublicIpRepositoryImpl @Inject constructor(
    private val probes: SourceIpProbes,
    private val networkPath: NetworkPath,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
) : PublicIpRepository {
    override suspend fun resolvePublicIp(serverIp: String): DomainResult<ResolvedSourceIp, KnockError> =
        resolvePublicIp(serverIp, networkPath.current())

    override suspend fun resolvePublicIp(
        serverIp: String,
        session: NetworkSession,
    ): DomainResult<ResolvedSourceIp, KnockError> = resolvePublicIp(serverIp, session, KnockObserver.None)

    override suspend fun resolvePublicIp(
        serverIp: String,
        session: NetworkSession,
        observer: KnockObserver,
    ): DomainResult<ResolvedSourceIp, KnockError> {
        val settings = settingsRepository.settings.first()
        val server = InetAddress.getByName(serverIp)
        val lookup = Lookup(server, observer)


        val work = CoroutineScope(dispatchers.io).async {
            val route = networkPath.routeOf(session).towards(server)
            if (route.bypassesVpn) DebugLog.event("public ip lookup bypasses VPN")
            lookup.run(probes.towards(server, settings, route))
        }
        return try {
            withTimeoutOrNull(BUDGET) { work.await() } ?: lookup.timedOut()
        } finally {
            lookup.close()
            work.cancel()
        }
    }


    private class Lookup(private val server: InetAddress, private val observer: KnockObserver) {
        private val failures = ConcurrentLinkedQueue<String>()
        private val lock = Any()
        private var open = true

        @Volatile
        private var kept: ResolvedSourceIp? = null

        suspend fun run(stages: List<SourceIpStage>): DomainResult<ResolvedSourceIp, KnockError> = coroutineScope {

            val checked = stages.flatten().filter { it.crossChecked }.associateWith { probe ->
                report(KnockStep.ThirdPartyQueried(probe.method))
                async { attempt(probe) }
            }
            for (stage in stages) {
                val source = race(stage, checked) ?: continue
                kept = source
                crossCheck(source, checked)
                return@coroutineScope DomainResult.Success(source)
            }
            unresolved()
        }


        fun timedOut(): DomainResult<ResolvedSourceIp, KnockError> =
            kept?.let { DomainResult.Success(it) } ?: unresolved("no answer within ${BUDGET.inWholeSeconds} s")


        fun close() = synchronized(lock) { open = false }

        private fun report(step: KnockStep) = synchronized(lock) { if (open) observer.onStep(step) }


        private suspend fun race(
            stage: SourceIpStage,
            started: Map<SourceIpProbe, Deferred<Result<InetAddress>>>,
        ): ResolvedSourceIp? = coroutineScope {
            stage.filter { it.queriesThirdParty && it !in started }
                .forEach { report(KnockStep.ThirdPartyQueried(it.method)) }
            val answers = Channel<Pair<SourceIpProbe, Result<InetAddress>>>(stage.size)
            val runs = stage.map { probe ->
                launch { answers.send(probe to (started[probe]?.await() ?: attempt(probe))) }
            }
            repeat(stage.size) {
                val (probe, answer) = answers.receive()
                answer.mapCatching { accepted(probe, it) }.fold(
                    onSuccess = {
                        runs.forEach(Job::cancel)
                        return@coroutineScope ResolvedSourceIp(it.literal(), probe.method)
                    },
                    onFailure = { fail(probe, it) },
                )
            }
            null
        }


        private suspend fun crossCheck(kept: ResolvedSourceIp, checked: Map<SourceIpProbe, Deferred<Result<InetAddress>>>) {
            for ((probe, deferred) in checked) {
                val independent = kept.method != probe.method
                deferred.await().fold(
                    onSuccess = {
                        report(KnockStep.SourceCrossChecked(probe.method, it.literal(), kept.takeIf { independent }))
                    },
                    onFailure = { if (independent) DebugLog.failure("public ip cross-check", it) },
                )
            }
        }

        private fun accepted(probe: SourceIpProbe, address: InetAddress): InetAddress =
            if (probe.accepts(address)) address else throw IOException("unusable address ${address.literal()}")

        private fun fail(probe: SourceIpProbe, error: Throwable) {
            DebugLog.failure("public ip lookup", error)
            failures += "${probe.method.describe()}: ${error.message ?: error.javaClass.simpleName}"
        }

        private fun unresolved(reason: String? = null): DomainResult<ResolvedSourceIp, KnockError> {
            val details = (failures.toList() + listOfNotNull(reason)).joinToString("; ")
            return DomainResult.Failure(
                KnockError.SourceIpUnresolved("towards ${server.literal()} (${server.family.label}): $details")
            )
        }
    }

    internal companion object {

        val BUDGET = 10.seconds
    }
}

private suspend fun attempt(probe: SourceIpProbe): Result<InetAddress> = try {
    Result.success(probe.run())
} catch (e: IOException) {
    Result.failure(e)
} catch (e: IllegalArgumentException) {
    Result.failure(e)
} catch (e: SecurityException) {
    Result.failure(e)
}

private fun InetAddress.literal(): String = hostAddress.orEmpty().substringBefore('%')
private fun SourceIpMethod.describe(): String = when (this) {
    SourceIpMethod.Kernel -> "kernel"
    is SourceIpMethod.Stun -> "STUN $server"
    is SourceIpMethod.Https -> "HTTPS $host"
    SourceIpMethod.Dns -> "DNS OpenDNS"
    else -> toString()
}
