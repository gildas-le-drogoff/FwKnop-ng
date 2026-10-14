// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.gildasledrogoff.fwknop.domain.model.Ssids
import fr.gildasledrogoff.fwknop.domain.repository.WifiNetworkRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


@Singleton
class WifiNetworkRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : WifiNetworkRepository {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    override fun connectedSsids(): Flow<List<String?>> = callbackFlow {

        val networks = LinkedHashMap<Network, String?>()
        val callback = SsidCallback.create(
            onChanged = { network, capabilities ->
                networks[network] = ssidOf(capabilities)
                trySend(networks.values.toList())
            },
            onLost = { network ->
                networks.remove(network)
                trySend(networks.values.toList())
            },
        )
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)

            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivity.registerNetworkCallback(request, callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.conflate()

    override suspend fun currentSsid(): String? =
        withTimeoutOrNull(CURRENT_TIMEOUT) { connectedSsids().first() }?.firstNotNullOfOrNull { it }

    override suspend fun awaitInternet(timeout: Duration): Boolean = withTimeoutOrNull(timeout) {
        callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities
                ) {
                    trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                }
            }
            connectivity.registerDefaultNetworkCallback(callback)
            awaitClose { connectivity.unregisterNetworkCallback(callback) }
        }.first { it }
    } ?: false

    private fun ssidOf(capabilities: NetworkCapabilities): String? {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            capabilities.transportInfo as? WifiInfo
        } else {

            @Suppress("DEPRECATION")
            context.getSystemService(WifiManager::class.java)?.connectionInfo
        }
        return Ssids.normalize(info?.ssid)
    }

    private companion object {
        val CURRENT_TIMEOUT = 2.seconds
    }
}


private class SsidCallback : ConnectivityManager.NetworkCallback {
    private constructor() : super()

    @RequiresApi(Build.VERSION_CODES.S)
    private constructor(flags: Int) : super(flags)

    private var onChanged: (Network, NetworkCapabilities) -> Unit = { _, _ -> }
    private var onGone: (Network) -> Unit = {}
    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
        onChanged(network, capabilities)

    override fun onLost(network: Network) = onGone(network)

    companion object {
        fun create(
            onChanged: (Network, NetworkCapabilities) -> Unit,
            onLost: (Network) -> Unit,
        ): ConnectivityManager.NetworkCallback {
            val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SsidCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO)
            } else {
                SsidCallback()
            }
            return callback.apply {
                this.onChanged = onChanged
                this.onGone = onLost
            }
        }
    }
}
