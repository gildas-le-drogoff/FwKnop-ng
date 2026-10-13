package fr.gildasledrogoff.fwknop.data.remote.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.gildasledrogoff.fwknop.utils.helpers.DebugLog
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Path used for source IP probes and SPA packets. When the default route goes through a VPN,
 * traffic is pinned to the underlying physical network so the resolved address is the one the
 * fwknopd server sees, not the VPN exit. Falls back to the default route when the VPN forbids bypass.
 */
@Singleton
class NetworkPath @Inject constructor(@param:ApplicationContext context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun current(): Route = Route(bypassNetwork()?.takeIf(::canBind))

    private fun bypassNetwork(): Network? {
        val active = connectivity.activeNetwork ?: return null
        if (connectivity.getNetworkCapabilities(active)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) != true) {
            return null
        }
        @Suppress("DEPRECATION")
        return connectivity.allNetworks
            .mapNotNull { network -> connectivity.getNetworkCapabilities(network)?.let { network to it } }
            .filter { (_, caps) -> caps.isPhysicalInternet() }
            .minByOrNull { (_, caps) -> if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) 0 else 1 }
            ?.first
    }

    private fun NetworkCapabilities.isPhysicalInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    // Non-bypassable VPNs (lockdown, no allowBypass) make bindSocket fail with EPERM.
    private fun canBind(network: Network): Boolean = try {
        DatagramSocket().use { network.bindSocket(it) }
        true
    } catch (e: IOException) {
        DebugLog.failure("vpn bypass", e)
        false
    }

    class Route(private val network: Network?) {
        val bypassesVpn: Boolean get() = network != null

        fun lookup(host: String): Array<InetAddress> =
            network?.getAllByName(host) ?: InetAddress.getAllByName(host)

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
