// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data

import android.app.Application
import android.net.*
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.remote.api.AndroidNetworkPath
import fr.gildasledrogoff.fwknop.data.remote.api.SpaTransportApi
import fr.gildasledrogoff.fwknop.data.repository.SpaTransportRepositoryImpl
import fr.gildasledrogoff.fwknop.domain.model.AppSettings
import fr.gildasledrogoff.fwknop.domain.model.VpnRouting
import fr.gildasledrogoff.fwknop.fakes.FakeSettingsRepository
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo
import java.net.InetAddress
import java.net.UnknownHostException
@Implements(Network::class)
class SplitDnsShadowNetwork : ShadowNetwork() {
    @Implementation
    protected fun getAllByName(host: String): Array<InetAddress> = when {
        host != VPN_ONLY_NAME -> InetAddress.getAllByName(host)
        netId == ConnectivityManager.TYPE_VPN -> arrayOf(InetAddress.getByName(TAILNET_SERVER))
        else -> throw UnknownHostException(host)
    }
}



@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, shadows = [SplitDnsShadowNetwork::class])
class AndroidNetworkPathTest {
    private val context = RuntimeEnvironment.getApplication()
    private val connectivity = shadowOf(context.getSystemService(ConnectivityManager::class.java))

    private val wifi = ShadowNetwork.newInstance(ConnectivityManager.TYPE_WIFI)
    private val vpn = ShadowNetwork.newInstance(ConnectivityManager.TYPE_VPN)
    private val settings = FakeSettingsRepository()
    private val path = AndroidNetworkPath(context, settings, CoroutineScope(Dispatchers.Unconfined))

    @Before
    fun connectWifi() {
        connectivity.clearAllNetworks()
        val info = info(ConnectivityManager.TYPE_WIFI)
        connectivity.addNetwork(wifi, info)
        connectivity.setActiveNetworkInfo(info)
        connectivity.setNetworkCapabilities(
            wifi,
            capabilities(NetworkCapabilities.TRANSPORT_WIFI).also {
                shadow(it).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                shadow(it).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                shadow(it).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            },
        )
    }

    @Test
    fun `without a vpn nothing is bypassed`() {
        assertThat(path.current().bypassesVpn).isFalse()
        assertThat(path.towards(ip(PUBLIC_SERVER)).bypassesVpn).isFalse()
        assertThat(path.towards(ip(TAILNET_SERVER)).bypassesVpn).isFalse()
    }

    @Test
    fun `auto keeps a server the vpn routes inside it and bypasses the others`() {
        connectVpn(listOf("0.0.0.0" to 0, "100.64.0.0" to 10))
        val session = path.current()
        assertThat(session.bypassesVpn).isTrue()
        assertThat(session.towards(ip(TAILNET_SERVER)).bypassesVpn).isFalse()
        assertThat(session.towards(ip(PUBLIC_SERVER)).bypassesVpn).isTrue()
        assertThat(path.towards(ip(TAILNET_SERVER)).bypassesVpn).isFalse()
    }

    @Test
    fun `auto bypasses as before when the vpn routes cannot be read`() {
        connectVpn(routes = null)
        assertThat(path.towards(ip(TAILNET_SERVER)).bypassesVpn).isTrue()
    }

    @Test
    fun `forced modes ignore the vpn routes and follow setting changes`() {
        connectVpn(listOf("100.64.0.0" to 10))
        settings.state.value = AppSettings(vpnRouting = VpnRouting.VIA_VPN)
        assertThat(path.current().bypassesVpn).isFalse()
        assertThat(path.towards(ip(PUBLIC_SERVER)).bypassesVpn).isFalse()
        settings.state.value = AppSettings(vpnRouting = VpnRouting.OUTSIDE_VPN)
        assertThat(path.towards(ip(TAILNET_SERVER)).bypassesVpn).isTrue()
    }

    @Test
    fun `in auto a name only the vpn dns knows still resolves`() {
        connectVpn(listOf("100.64.0.0" to 10))
        assertThat(path.current().lookup(VPN_ONLY_NAME).toList()).containsExactly(ip(TAILNET_SERVER))
        settings.state.value = AppSettings(vpnRouting = VpnRouting.OUTSIDE_VPN)
        assertThrows(UnknownHostException::class.java) { path.current().lookup(VPN_ONLY_NAME) }
    }

    @Test
    fun `the knock reports whether it bypasses the vpn`() = runTest {
        connectVpn(listOf("0.0.0.0" to 0, "100.64.0.0" to 10))
        val io = UnconfinedTestDispatcher(testScheduler)
        val transport = SpaTransportRepositoryImpl(
            SpaTransportApi(OkHttpClient()),
            path,
            object : DispatcherProvider {
                override val main = io
                override val io = io
                override val default = io
            },
            FakeLocalNetworkAccess(),
        )
        val session = transport.openSession()
        assertThat(transport.bypassesVpn(TAILNET_SERVER, session)).isFalse()
        assertThat(transport.bypassesVpn(PUBLIC_SERVER, session)).isTrue()
    }

    private fun connectVpn(routes: List<Pair<String, Int>>?) {
        val info = info(ConnectivityManager.TYPE_VPN)
        connectivity.addNetwork(vpn, info)
        connectivity.setActiveNetworkInfo(info)
        connectivity.setNetworkCapabilities(vpn, capabilities(NetworkCapabilities.TRANSPORT_VPN))
        if (routes != null) connectivity.setLinkProperties(vpn, linkProperties(routes))
    }


    private fun linkProperties(routes: List<Pair<String, Int>>) = LinkProperties().apply {
        interfaceName = "tun0"
        val route = RouteInfo::class.java.getConstructor(
            IpPrefix::class.java,
            InetAddress::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
        )
        val addRoute = LinkProperties::class.java.getMethod("addRoute", RouteInfo::class.java)
        routes.forEach { (prefix, length) ->
            addRoute.invoke(this, route.newInstance(IpPrefix(ip(prefix), length), null, "tun0", RouteInfo.RTN_UNICAST))
        }
    }

    @Suppress("DEPRECATION")
    private fun info(type: Int): NetworkInfo =
        ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, type, 0, true, NetworkInfo.State.CONNECTED)

    private fun capabilities(transport: Int): NetworkCapabilities =
        ShadowNetworkCapabilities.newInstance().also { shadow(it).addTransportType(transport) }

    private fun shadow(capabilities: NetworkCapabilities): ShadowNetworkCapabilities = Shadow.extract(capabilities)

    private fun ip(literal: String): InetAddress = InetAddress.getByName(literal)
}

private const val VPN_ONLY_NAME = "nas.tailnet.ts.net"
private const val TAILNET_SERVER = "100.101.102.103"
private const val PUBLIC_SERVER = "203.0.113.7"
