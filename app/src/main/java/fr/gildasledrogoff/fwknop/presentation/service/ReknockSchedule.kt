package fr.gildasledrogoff.fwknop.presentation.service

import fr.gildasledrogoff.fwknop.domain.model.KnockRequest
import fr.gildasledrogoff.fwknop.domain.model.SourceIp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Cadence of a keep-open session: a knock every [intervalMs], or right away after a network change.
 * The new network may not be usable yet, so a knock it triggered is retried every [retryMs] up to
 * [NETWORK_ATTEMPTS] times; only a failed periodic knock, or exhausted retries, ends the session.
 */
internal class ReknockSchedule(private val intervalMs: Long, private val retryMs: Long) {
    private val networkChanges = Channel<Unit>(Channel.CONFLATED)

    fun onNetworkChanged() {
        networkChanges.trySend(Unit)
    }

    /** Returns when the session must stop; [knock] returns whether the packet was sent. */
    suspend fun run(knock: suspend () -> Boolean) {
        var attemptsLeft = 0
        while (true) {
            val wait = if (attemptsLeft > 0) retryMs else intervalMs
            if (withTimeoutOrNull(wait) { networkChanges.receive() } != null) attemptsLeft = NETWORK_ATTEMPTS
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

/**
 * Tells a switch of default network (or of its underlying transport, under a VPN) apart from the
 * callbacks replayed on registration or fired for signal strength and similar updates.
 */
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

/** Only these modes send an address that depends on the network the phone is on. */
internal val KnockRequest.followsNetwork: Boolean
    get() = when (config.sourceIp) {
        SourceIp.Resolve, SourceIp.Source -> true
        is SourceIp.Fixed, SourceIp.Prompt -> false
    }
