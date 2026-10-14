// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data
import android.app.Application
import android.content.Context
import android.security.NetworkSecurityPolicy
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.remote.api.NetworkPath
import fr.gildasledrogoff.fwknop.data.remote.api.SpaTransportApi
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.Security

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SpaHttpCleartextTest {
    @get:Rule
    val loopback = LoopbackServer()
    private val api = SpaTransportApi(OkHttpClient())

    @Before
    fun installAppNetworkSecurityConfig() {
        Class.forName("android.security.net.config.NetworkSecurityConfigProvider")
            .getMethod("install", Context::class.java)
            .invoke(null, RuntimeEnvironment.getApplication())
    }
    @After
    fun removeSecurityProvider() {
        Security.removeProvider(NSC_PROVIDER)
    }
    @Test
    fun `the app network security config allows cleartext to any spa server`() {
        val policy = NetworkSecurityPolicy.getInstance()
        assertThat(policy.isCleartextTrafficPermitted).isTrue()
        assertThat(policy.isCleartextTrafficPermitted("knock.example.org")).isTrue()
        assertThat(policy.isCleartextTrafficPermitted("192.0.2.10")).isTrue()
    }
    @Test
    fun `spa over http is not refused by the cleartext policy`() {
        val port = loopback.http()

        api.sendHttp("8Bq+x/9zAb+/cd", "127.0.0.1", "knock.example.org", port, NetworkPath.Route(null))
        assertThat(loopback.next().text.lines().first()).isEqualTo("GET /8Bq-x_9zAb-_cd HTTP/1.1")
    }
    private companion object {
        const val NSC_PROVIDER = "AndroidNSSP"
    }
}
