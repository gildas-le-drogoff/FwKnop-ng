// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.service

import fr.gildasledrogoff.fwknop.domain.model.KnockRequest
import fr.gildasledrogoff.fwknop.domain.model.SourceIp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull


internal class ReknockSchedule(private val intervalMs: Long, private val retryMs: Long) {
    private val networkChanges = Channel<Unit>(Channel.CONFLATED)
    fun onNetworkChanged() {
        networkChanges.trySend(Unit)
    }


    suspend fun run(knock: suspend () -> Boolean) {
        var attemptsLeft = 0
        while (true) {
            val wait = if (attemptsLeft > 0) retryMs else intervalMs
            if (withTimeoutOrNull(wait) { networkChanges.receive() } != null) attemptsLeft =
                NETWORK_ATTEMPTS
            if (knock()) {
                attemptsLeft = 0
            } else if (--attemptsLeft <= 0) {
                return
            }
        }
    }

    companion object {
        const val NETWORK_ATTEMPTS = 3
    }
}


internal class NetworkChangeDetector<N : Any>(network: N?, transports: Set<Int> = emptySet()) {
    private var current: Pair<N, Set<Int>>? = network?.let { it to transports }
    fun onCapabilitiesChanged(network: N, transports: Set<Int>): Boolean {
        val next = network to transports
        return (next != current).also { current = next }
    }

    fun onLost(network: N) {
        if (current?.first == network) current = null
    }
}


internal val KnockRequest.followsNetwork: Boolean
    get() = when (config.sourceIp) {
        SourceIp.Resolve, SourceIp.Source -> true
        is SourceIp.Fixed, SourceIp.Prompt -> false
    }
