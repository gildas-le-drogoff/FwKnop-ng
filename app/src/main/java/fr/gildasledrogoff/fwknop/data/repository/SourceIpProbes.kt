// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.repository

import fr.gildasledrogoff.fwknop.data.remote.api.*
import fr.gildasledrogoff.fwknop.domain.model.AppSettings
import fr.gildasledrogoff.fwknop.domain.model.SourceIpMethod
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.Inet6Address
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

class SourceIpProbe(
    val method: SourceIpMethod,
    val accepts: (InetAddress) -> Boolean,

    val crossChecked: Boolean = false,
    val run: suspend () -> InetAddress,
) {

    val queriesThirdParty: Boolean get() = method != SourceIpMethod.Kernel
}


typealias SourceIpStage = List<SourceIpProbe>


@Singleton
open class SourceIpProbes @Inject constructor(
    private val kernel: KernelRouteApi,
    private val stun: StunApi,
    private val api: PublicIpApi,
) {
    open fun towards(
        server: InetAddress,
        settings: AppSettings,
        route: NetworkPath.Route
    ): List<SourceIpStage> = buildList {
        val family = server.family
        val kernel = listOf(kernelProbe(server, route))
        val stun = if (settings.stunLookupEnabled) StunApi.SERVERS.map { stunProbe(it, family, route) } else emptyList()



        val kernelFirst = family == Inet6Address::class.java || !server.isPublic()
        if (kernelFirst) add(kernel)
        if (stun.isNotEmpty()) add(stun)
        if (!kernelFirst) add(kernel)
        add(listOf(httpsProbe(settings.ipLookupUrl, family, route)))
        if (settings.dnsLookupEnabled) add(listOf(dnsProbe(family, route)))
    }

    private fun kernelProbe(server: InetAddress, route: NetworkPath.Route) = SourceIpProbe(
        SourceIpMethod.Kernel,
        { it.family == server.family && (it.isPublic() || !server.isPublic()) },
    ) { kernel.sourceAddressTowards(server, route) }

    private fun stunProbe(server: StunServer, family: AddressFamily, route: NetworkPath.Route) =
        SourceIpProbe(SourceIpMethod.Stun(server.host), publicIn(family)) {
            stun.lookup(
                server,
                family,
                route
            )
        }

    private fun httpsProbe(url: String, family: AddressFamily, route: NetworkPath.Route) =
        SourceIpProbe(
            SourceIpMethod.Https(url.toHttpUrlOrNull()?.host ?: url),
            publicIn(family),
        ) { api.lookupViaHttp(url, family, route) }

    private fun dnsProbe(family: AddressFamily, route: NetworkPath.Route) =
        SourceIpProbe(SourceIpMethod.Dns, publicIn(family), crossChecked = true) { api.lookupViaDns(family, route) }

    private fun publicIn(family: AddressFamily): (InetAddress) -> Boolean =
        { it.family == family && it.isPublic() }
}
