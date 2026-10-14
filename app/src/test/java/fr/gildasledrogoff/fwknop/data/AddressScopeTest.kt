// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data
import com.google.common.truth.Truth.assertWithMessage
import fr.gildasledrogoff.fwknop.data.remote.api.isPublic
import fr.gildasledrogoff.fwknop.domain.model.isDomainName
import org.junit.Test
import java.net.InetAddress
class AddressScopeTest {
    @Test
    fun `special purpose ranges are not public`() {
        listOf(
            "0.0.0.0", "0.255.255.255",
            "10.1.2.3", "172.16.0.1", "192.168.1.1",
            "100.64.0.1", "100.127.255.255",
            "127.0.0.1", "169.254.1.1", "224.0.0.1",
            "192.0.0.8",
            "192.0.2.1", "198.51.100.200", "203.0.113.9",
            "198.18.0.1", "198.19.255.255",
            "240.0.0.1", "255.255.255.255",
            "::1", "fe80::1", "fd00::1", "ff02::1",
            "2001:db8::1", "2001:db8:ffff::1",
        ).forEach { assertWithMessage(it).that(InetAddress.getByName(it).isPublic()).isFalse() }
    }

    @Test
    fun `addresses just outside the ranges are public`() {
        listOf(
            "1.0.0.1", "8.8.8.8",
            "21.1.2.3", "26.1.2.3",
            "100.63.255.255", "100.128.0.0",
            "192.0.1.255", "192.0.3.0",
            "198.17.255.255", "198.20.0.0",
            "198.51.99.255", "203.0.114.0",
            "223.255.255.255",
            "2001:db9::1", "2001:db7:ffff::1", "2606:4700::1111",
        ).forEach { assertWithMessage(it).that(InetAddress.getByName(it).isPublic()).isTrue() }
    }

    @Test
    fun `internal tlds are accepted, unknown ones still refused`() {
        listOf("nas.lan", "router.internal", "printer.home", "git.corp", "box.local", "ifconfig.me", "localhost")
            .forEach { assertWithMessage(it).that(it.isDomainName()).isTrue() }
        listOf("nas.notatld", "bad_name.lan", "-x.internal", "")
            .forEach { assertWithMessage(it).that(it.isDomainName()).isFalse() }
    }
}
