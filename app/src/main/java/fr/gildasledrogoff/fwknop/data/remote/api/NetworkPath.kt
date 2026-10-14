// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.remote.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.RouteInfo
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.gildasledrogoff.fwknop.di.ApplicationScope
import fr.gildasledrogoff.fwknop.domain.model.NetworkSession
import fr.gildasledrogoff.fwknop.domain.model.VpnRouting
import fr.gildasledrogoff.fwknop.domain.repository.SettingsRepository
import fr.gildasledrogoff.fwknop.utils.helpers.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton


interface NetworkPath {
    fun current(): Route


    fun towards(server: InetAddress): Route = current().towards(server)


    class Route(
        private val network: Network?,
        val bypassesVpn: Boolean = network != null,
        private val keepsVpnFor: (InetAddress) -> Boolean = { false },

        private val vpnDns: Network? = null,
    ) : NetworkSession {

        fun towards(server: InetAddress): Route =
            if (bypassesVpn && keepsVpnFor(server)) Route(null, bypassesVpn = false) else this

        fun lookup(host: String): Array<InetAddress> = when {
            network == null -> InetAddress.getAllByName(host)
            vpnDns == null -> network.getAllByName(host)
            else -> try {
                network.getAllByName(host)
            } catch (_: UnknownHostException) {
                DebugLog.event("name unknown outside the VPN, resolved through it")
                vpnDns.getAllByName(host)
            }
        }

        fun datagramSocket(): DatagramSocket = DatagramSocket().apply {
            try {
                network?.bindSocket(this)
            } catch (e: IOException) {
                close()
                throw e
            }
        }

        fun socket(): Socket = network?.socketFactory?.createSocket() ?: Socket()
        fun httpClient(base: OkHttpClient): OkHttpClient = if (network == null) {
            base
        } else {
            base.newBuilder()
                .socketFactory(network.socketFactory)
                .dns { host -> network.getAllByName(host).toList() }
                .build()
        }
    }
}


fun NetworkPath.routeOf(session: NetworkSession): NetworkPath.Route =
    session as? NetworkPath.Route ?: current()

@Singleton
class AndroidNetworkPath @Inject constructor(
    @param:ApplicationContext context: Context,
    private val settingsRepository: SettingsRepository,
    @param:ApplicationScope scope: CoroutineScope,
) : NetworkPath {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val routing = settingsRepository.settings.map { it.vpnRouting }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override fun current(): NetworkPath.Route {
        val vpn = activeVpn()
            ?: return NetworkPath.Route(physicalActiveNetwork(), bypassesVpn = false)

        val routing = routing.value ?: runBlocking { settingsRepository.settings.first().vpnRouting }
        val bypass = bypassNetwork()
            ?.takeIf { bypassesVpn(routing, server = null, vpnRoutes = null) && canBind(it) }
            ?: return NetworkPath.Route(null, bypassesVpn = false)
        if (routing != VpnRouting.AUTO) return NetworkPath.Route(bypass)
        val routes = vpnRoutes(vpn)
        return NetworkPath.Route(
            bypass,
            keepsVpnFor = { !bypassesVpn(routing, it, routes) },
            vpnDns = vpn,
        )
    }

    private fun activeVpn(): Network? = connectivity.activeNetwork?.takeIf {
        connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }


    private fun vpnRoutes(vpn: Network): List<VpnRoute>? =
        connectivity.getLinkProperties(vpn)?.routes?.map { route ->
            VpnRoute(
                route.destination.address,
                route.destination.prefixLength,

                unicast = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        route.type == RouteInfo.RTN_UNICAST,
            )
        }


    private fun physicalActiveNetwork(): Network? = connectivity.activeNetwork?.takeIf {
        connectivity.getNetworkCapabilities(it)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == false
    }

    private fun bypassNetwork(): Network? {
        val active = connectivity.activeNetwork ?: return null
        if (connectivity.getNetworkCapabilities(active)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) != true
        ) {
            return null
        }
        @Suppress("DEPRECATION")
        return connectivity.allNetworks
            .mapNotNull { network ->
                connectivity.getNetworkCapabilities(network)?.let { network to it }
            }
            .filter { (_, caps) -> caps.isPhysicalInternet() }
            .minByOrNull { (_, caps) -> if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) 0 else 1 }
            ?.first
    }

    private fun NetworkCapabilities.isPhysicalInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)


    private fun canBind(network: Network): Boolean = try {
        DatagramSocket().use { network.bindSocket(it) }
        true
    } catch (e: IOException) {
        DebugLog.failure("vpn bypass", e)
        false
    }
}
