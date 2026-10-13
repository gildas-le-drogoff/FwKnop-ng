package fr.gildasledrogoff.fwknop.data.repository

import fr.gildasledrogoff.fwknop.data.remote.api.AddressFamily
import fr.gildasledrogoff.fwknop.data.remote.api.KernelRouteApi
import fr.gildasledrogoff.fwknop.data.remote.api.NetworkPath
import fr.gildasledrogoff.fwknop.data.remote.api.PublicIpApi
import fr.gildasledrogoff.fwknop.data.remote.api.StunApi
import fr.gildasledrogoff.fwknop.data.remote.api.StunServer
import fr.gildasledrogoff.fwknop.data.remote.api.family
import fr.gildasledrogoff.fwknop.data.remote.api.isPublic
import fr.gildasledrogoff.fwknop.domain.model.AppSettings
import fr.gildasledrogoff.fwknop.domain.model.SourceIpMethod
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

class SourceIpProbe(
    val method: SourceIpMethod,
    val accepts: (InetAddress) -> Boolean,
    val run: () -> InetAddress,
)

@Singleton
class SourceIpProbes @Inject constructor(
    private val kernel: KernelRouteApi,
    private val stun: StunApi,
    private val api: PublicIpApi,
) {
    
    fun towards(server: InetAddress, settings: AppSettings, route: NetworkPath.Route): List<SourceIpProbe> = buildList {
        val family = server.family
        add(kernelProbe(server, route))
        if (settings.stunLookupEnabled) StunApi.SERVERS.forEach { add(stunProbe(it, family, route)) }
        add(httpsProbe(settings.ipLookupUrl, family, route))
        if (settings.dnsLookupEnabled) add(dnsProbe(family, route))
    }

    
    private fun kernelProbe(server: InetAddress, route: NetworkPath.Route) = SourceIpProbe(
        SourceIpMethod.Kernel,
        { it.family == server.family && (it.isPublic() || !server.isPublic()) },
    ) { kernel.sourceAddressTowards(server, route) }

    private fun stunProbe(server: StunServer, family: AddressFamily, route: NetworkPath.Route) =
        SourceIpProbe(SourceIpMethod.Stun(server.host), publicIn(family)) { stun.lookup(server, family, route) }

    private fun httpsProbe(url: String, family: AddressFamily, route: NetworkPath.Route) = SourceIpProbe(
        SourceIpMethod.Https(url.toHttpUrlOrNull()?.host ?: url),
        publicIn(family),
    ) { api.lookupViaHttp(url, family, route) }

    private fun dnsProbe(family: AddressFamily, route: NetworkPath.Route) =
        SourceIpProbe(SourceIpMethod.Dns, publicIn(family)) { api.lookupViaDns(family, route) }

    private fun publicIn(family: AddressFamily): (InetAddress) -> Boolean = { it.family == family && it.isPublic() }
}
