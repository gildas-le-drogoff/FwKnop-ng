package fr.gildasledrogoff.fwknop.data.remote.api

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KernelRouteApi @Inject constructor() {

    fun sourceAddressTowards(server: InetAddress, route: NetworkPath.Route): InetAddress =
        route.datagramSocket().use { socket ->
            socket.connect(InetSocketAddress(server, DISCARD_PORT))
            socket.localAddress?.takeUnless { it.isAnyLocalAddress }
                ?: throw IOException("no route to ${server.hostAddress}")
        }

    private companion object {
        const val DISCARD_PORT = 9
    }
}
