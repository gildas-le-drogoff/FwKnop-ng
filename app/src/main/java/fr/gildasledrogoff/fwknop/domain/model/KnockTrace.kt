package fr.gildasledrogoff.fwknop.domain.model

/** What [fr.gildasledrogoff.fwknop.domain.usecase.SendKnockUseCase] learns, in the order it learns it. */
sealed interface KnockStep {
    /** Every address the server name resolved to (a single one when the source family imposes it). */
    data class ServerResolved(val host: String, val addresses: List<String>) : KnockStep
    data class SourceResolved(val source: ResolvedSourceIp) : KnockStep
    data class RouteSelected(val route: KnockRoute) : KnockStep {
        val family: AddressFamily get() = AddressFamily.of(route.serverIp)
    }

    data class PacketBuilt(val sizeBytes: Int) : KnockStep

    /** Emitted just before the packet leaves: the send itself may still fail. */
    data class Sending(val target: KnockTarget) : KnockStep
}

enum class AddressFamily {
    IPV4,
    IPV6;

    companion object {
        fun of(address: String): AddressFamily = if (':' in address) IPV6 else IPV4
    }
}

/** Instrumentation hook only: observers must not influence the knock. */
fun interface KnockObserver {
    fun onStep(step: KnockStep)

    companion object {
        val None = KnockObserver {}
    }
}
